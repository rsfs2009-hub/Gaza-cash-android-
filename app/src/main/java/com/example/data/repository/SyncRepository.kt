package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.data.local.dao.OrderWithItems
import com.example.data.remote.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ConflictStrategy {
    LAST_WRITE_WINS,
    SERVER_WINS,
    LOCAL_WINS
}

data class CustomerConflict(
    val localCustomer: Customer,
    val serverCustomer: Customer
)

class SyncRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val customerDao = db.customerDao()
    private val itemDao = db.itemDao()
    private val orderDao = db.orderDao()
    private val invoiceDao = db.invoiceDao()
    private val syncLogDao = db.syncLogDao()

    private val _activeConflicts = MutableStateFlow<List<CustomerConflict>>(emptyList())
    val activeConflicts: StateFlow<List<CustomerConflict>> = _activeConflicts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // Preferences key for last sync time
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    init {
        _lastSyncTime.value = prefs.getLong("last_sync_timestamp", 0L)
    }

    private fun saveLastSyncTime(timestamp: Long) {
        _lastSyncTime.value = timestamp
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    suspend fun clearLocalCache() {
        customerDao.clearAll()
        itemDao.clearAll()
        orderDao.clearAll()
        invoiceDao.clearAll()
        syncLogDao.clearAll()
        saveLastSyncTime(0L)
        addLog("CLEAR_ALL", "SUCCESS", "Cleared all local tables and reset sync timestamp.")
    }

    suspend fun addLog(action: String, status: String, details: String) {
        syncLogDao.insertLog(SyncLog(action = action, status = status, details = details))
    }

    val recentLogsFlow = syncLogDao.getRecentLogs()

    /**
     * SINC ENGINE (REAL SERVER SYNC)
     */
    suspend fun performSync(ip: String, port: String): Boolean {
        if (_isSyncing.value) return false
        _isSyncing.value = true
        addLog("FULL_SYNC", "SUCCESS", "Starting synchronization with server http://$ip:$port")

        try {
            val api = RetrofitClient.getService(ip, port)
            val currentLastSync = _lastSyncTime.value

            // Step 1: PUSH local modifications
            val pendingCustomers = customerDao.getPendingCustomers()
            val pendingOrdersWithItems = orderDao.getPendingOrdersWithItems()

            val customerDtos = pendingCustomers.map {
                CustomerDto(
                    uuid = it.uuid, name = it.name, phone = it.phone, address = it.address,
                    email = it.email, balance = it.balance, version = it.version, lastModified = it.lastModified
                )
            }

            val orderPushDtos = pendingOrdersWithItems.map { orderWithItems ->
                OrderPushDto(
                    uuid = orderWithItems.order.uuid,
                    customerUuid = orderWithItems.order.customerUuid,
                    customerName = orderWithItems.order.customerName,
                    orderDate = orderWithItems.order.orderDate,
                    totalAmount = orderWithItems.order.totalAmount,
                    notes = orderWithItems.order.notes,
                    items = orderWithItems.items.map {
                        OrderItemDto(itemUuid = it.itemUuid, itemName = it.itemName, price = it.price, quantity = it.quantity)
                    },
                    version = orderWithItems.order.version,
                    lastModified = orderWithItems.order.lastModified
                )
            }

            if (customerDtos.isNotEmpty() || orderPushDtos.isNotEmpty()) {
                val pushResponse = api.pushData(PushRequest(customers = customerDtos, orders = orderPushDtos))
                if (pushResponse.isSuccessful && pushResponse.body() != null) {
                    val body = pushResponse.body()!!
                    if (body.success) {
                        // Mark local pushed changes as synced
                        body.syncedCustomerUuids.forEach { uuid ->
                            val local = customerDao.getCustomerByUuid(uuid)
                            if (local != null) {
                                customerDao.updateSyncStatus(uuid, "synced", local.version)
                            }
                        }
                        body.syncedOrderUuids.forEach { uuid ->
                            orderDao.updateSyncStatus(uuid, "synced", 1)
                        }

                        // Capture server conflicts if reported
                        if (body.conflicts.isNotEmpty()) {
                            val newConflicts = body.conflicts.map { conflictDto ->
                                CustomerConflict(
                                    localCustomer = Customer(
                                        uuid = conflictDto.localCustomer.uuid,
                                        name = conflictDto.localCustomer.name,
                                        phone = conflictDto.localCustomer.phone,
                                        address = conflictDto.localCustomer.address,
                                        email = conflictDto.localCustomer.email,
                                        balance = conflictDto.localCustomer.balance,
                                        syncStatus = "pending_update",
                                        version = conflictDto.localCustomer.version,
                                        lastModified = conflictDto.localCustomer.lastModified
                                    ),
                                    serverCustomer = Customer(
                                        uuid = conflictDto.serverCustomer.uuid,
                                        name = conflictDto.serverCustomer.name,
                                        phone = conflictDto.serverCustomer.phone,
                                        address = conflictDto.serverCustomer.address,
                                        email = conflictDto.serverCustomer.email,
                                        balance = conflictDto.serverCustomer.balance,
                                        syncStatus = "synced",
                                        version = conflictDto.serverCustomer.version,
                                        lastModified = conflictDto.serverCustomer.lastModified
                                    )
                                )
                            }
                            _activeConflicts.value = newConflicts
                            addLog("PUSH_DATA", "FAILED", "Pushed changes, but ${newConflicts.size} customer conflict(s) detected.")
                        } else {
                            addLog("PUSH_DATA", "SUCCESS", "Pushed ${customerDtos.size} customers and ${orderPushDtos.size} orders successfully.")
                        }
                    }
                } else {
                    addLog("PUSH_DATA", "FAILED", "Server rejected push data: ${pushResponse.code()}")
                }
            }

            // Step 2: PULL from server (Incremental)
            val pullResponse = api.pullData(PullRequest(lastSyncTimestamp = currentLastSync))
            if (pullResponse.isSuccessful && pullResponse.body() != null) {
                val body = pullResponse.body()!!

                // Import Items
                val itemsToInsert = body.items.map {
                    Item(
                        uuid = it.uuid, name = it.name, sku = it.sku, price = it.price,
                        stock = it.stock, category = it.category, version = it.version, lastModified = it.lastModified
                    )
                }
                if (itemsToInsert.isNotEmpty()) {
                    itemDao.insertItems(itemsToInsert)
                }

                // Import Invoices
                val invoicesToInsert = body.invoices.map {
                    Invoice(
                        uuid = it.uuid, customerUuid = it.customerUuid, customerName = it.customerName,
                        invoiceDate = it.invoiceDate, totalAmount = it.totalAmount, discount = it.discount,
                        finalAmount = it.finalAmount, paymentType = it.paymentType, itemsJson = it.itemsJson
                    )
                }
                if (invoicesToInsert.isNotEmpty()) {
                    invoiceDao.insertInvoices(invoicesToInsert)
                }

                // Import Customers & Resolve conflicts locally if they modified locally as well
                val customersToInsert = mutableListOf<Customer>()
                val conflictList = mutableListOf<CustomerConflict>()

                body.customers.forEach { sCustomer ->
                    val local = customerDao.getCustomerByUuid(sCustomer.uuid)
                    val serverModel = Customer(
                        uuid = sCustomer.uuid, name = sCustomer.name, phone = sCustomer.phone,
                        address = sCustomer.address, email = sCustomer.email, balance = sCustomer.balance,
                        syncStatus = "synced", version = sCustomer.version, lastModified = sCustomer.lastModified
                    )

                    if (local != null && local.syncStatus != "synced") {
                        // Conflict! Both local and server modified
                        conflictList.add(CustomerConflict(localCustomer = local, serverCustomer = serverModel))
                    } else {
                        customersToInsert.add(serverModel)
                    }
                }

                if (customersToInsert.isNotEmpty()) {
                    customerDao.insertCustomers(customersToInsert)
                }

                if (conflictList.isNotEmpty()) {
                    _activeConflicts.value = _activeConflicts.value + conflictList
                    addLog("PULL_DATA", "FAILED", "Pulled updates, but ${conflictList.size} local conflict(s) require resolution.")
                } else {
                    addLog("PULL_DATA", "SUCCESS", "Pulled ${body.items.size} items, ${body.invoices.size} invoices, ${body.customers.size} customers.")
                }

                saveLastSyncTime(body.serverTime)
            } else {
                addLog("PULL_DATA", "FAILED", "Failed pulling data from server: ${pullResponse.code()}")
            }

            _isSyncing.value = false
            return _activeConflicts.value.isEmpty()

        } catch (e: Exception) {
            Log.e("SyncRepository", "Sync error", e)
            addLog("FULL_SYNC", "FAILED", "Network/Connection error during sync: ${e.message}")
            _isSyncing.value = false
            return false
        }
    }

    /**
     * CONFLICT RESOLVER
     */
    suspend fun resolveConflict(conflict: CustomerConflict, strategy: ConflictStrategy) {
        val resolvedCustomer = when (strategy) {
            ConflictStrategy.LAST_WRITE_WINS -> {
                if (conflict.localCustomer.lastModified >= conflict.serverCustomer.lastModified) {
                    conflict.localCustomer.copy(syncStatus = "pending_update")
                } else {
                    conflict.serverCustomer
                }
            }
            ConflictStrategy.SERVER_WINS -> {
                conflict.serverCustomer
            }
            ConflictStrategy.LOCAL_WINS -> {
                conflict.localCustomer.copy(syncStatus = "pending_update")
            }
        }

        customerDao.insertCustomer(resolvedCustomer)
        _activeConflicts.value = _activeConflicts.value.filter { it.localCustomer.uuid != conflict.localCustomer.uuid }

        val details = "Resolved conflict for ${conflict.localCustomer.name} using ${strategy.name}. Selected: ${resolvedCustomer.name} (v${resolvedCustomer.version})"
        addLog("CONFLICT_RESOLVED", "SUCCESS", details)
    }

    /**
     * SIMULATE SYNC & SEED SCENARIO (DEMO MODE)
     * For demonstration in the AI Studio streaming sandbox where real server connectivity might not be configured.
     */
    suspend fun generateMockData() {
        // Clear all first to start clean
        clearLocalCache()

        // 1. Seed default products/items
        val mockItems = listOf(
            Item(name = "أرز الياسمين 5 كجم", sku = "871040001", price = 12.5, stock = 150, category = "مواد غذائية"),
            Item(name = "زيت طهي عافية 1.5 لتر", sku = "628101002", price = 4.8, stock = 80, category = "زيوت"),
            Item(name = "سكر ناعم الأسرة 2 كجم", sku = "628104003", price = 3.5, stock = 200, category = "مواد غذائية"),
            Item(name = "حليب نيدو مجفف 900 جرام", sku = "761303504", price = 18.2, stock = 45, category = "ألبان"),
            Item(name = "مكرونة قودي 500 جرام", sku = "628105005", price = 1.2, stock = 300, category = "مواد غذائية"),
            Item(name = "شاي ربيع علاقي 100 كيس", sku = "628106006", price = 5.5, stock = 90, category = "مشروبات")
        )
        itemDao.insertItems(mockItems)

        // 2. Seed default customers (as if already synced)
        val mockCustomers = listOf(
            Customer(name = "سوبرماركت الأمل", phone = "0599123456", address = "غزة - الرمال", email = "amal@gmail.com", balance = 450.0, syncStatus = "synced", version = 2),
            Customer(name = "بقالة القدس", phone = "0599765432", address = "خان يونس - وسط البلد", email = "quds@gmail.com", balance = -120.0, syncStatus = "synced", version = 1),
            Customer(name = "شركة النور للتجارة", phone = "0599443322", address = "غزة - النصر", email = "noor@trade.ps", balance = 2500.0, syncStatus = "synced", version = 5),
            Customer(name = "محلات البركة", phone = "0598112233", address = "رفح - السوق", email = "baraka@gmail.com", balance = 0.0, syncStatus = "synced", version = 3)
        )
        customerDao.insertCustomers(mockCustomers)

        // 3. Seed some historic invoices
        val mockInvoices = listOf(
            Invoice(
                customerUuid = mockCustomers[0].uuid,
                customerName = mockCustomers[0].name,
                totalAmount = 250.0,
                discount = 10.0,
                finalAmount = 240.0,
                paymentType = "cash",
                itemsJson = """[{"name":"أرز الياسمين 5 كجم","price":12.5,"quantity":16},{"name":"سكر ناعم الأسرة 2 كجم","price":3.5,"quantity":14}]"""
            ),
            Invoice(
                customerUuid = mockCustomers[2].uuid,
                customerName = mockCustomers[2].name,
                totalAmount = 1450.0,
                discount = 50.0,
                finalAmount = 1400.0,
                paymentType = "credit",
                itemsJson = """[{"name":"حليب نيدو مجفف 900 جرام","price":18.2,"quantity":50},{"name":"شاي ربيع علاقي 100 كيس","price":5.5,"quantity":100}]"""
            )
        )
        invoiceDao.insertInvoices(mockInvoices)

        addLog("MOCK_SEEDING", "SUCCESS", "Generated mock items, customers, and historic invoices.")
    }

    /**
     * SIMULATE AN INCREMENTAL SYNC THAT TRIGGERS A CUSTOMER CONFLICT
     */
    suspend fun simulateSyncConflictScenario() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        addLog("SIMULATED_SYNC", "SUCCESS", "Starting simulated offline-first sync engine...")

        // Let's create an actual conflict scenario in the database!
        // We'll target "بقالة القدس" (uuid fetched from local database)
        val customerList = db.customerDao().getPendingCustomers()
        var targetCustomer = customerList.firstOrNull { it.name.contains("القدس") || it.name.contains("الأمل") }

        if (targetCustomer == null) {
            // No local pending update exists, let's create a local modification to force a conflict!
            val allLocalCustomers = db.customerDao().getPendingCustomers().ifEmpty {
                val customers = mutableListOf<Customer>()
                customerDao.getAllCustomers().collect { customers.addAll(it) } // collect is blocking inside suspend but flow is fast
                customers
            }
            val base = allLocalCustomers.firstOrNull { it.name.contains("القدس") }
            if (base != null) {
                targetCustomer = base.copy(
                    name = "بقالة القدس (تعديل محلي)",
                    phone = "0599765432",
                    syncStatus = "pending_update",
                    lastModified = System.currentTimeMillis() - 5000, // 5 seconds ago
                    version = base.version + 1
                )
                customerDao.insertCustomer(targetCustomer)
                addLog("LOCAL_MODIFICATION", "SUCCESS", "Simulated local modification on customer: ${targetCustomer.name}")
            }
        }

        val conflictCustomer = targetCustomer ?: Customer(
            name = "بقالة القدس (تعديل محلي)",
            phone = "0599765432",
            address = "خان يونس",
            syncStatus = "pending_update",
            version = 2,
            lastModified = System.currentTimeMillis() - 5000
        ).also {
            customerDao.insertCustomer(it)
        }

        // Simulate incoming server customer which has ALSO been updated independently on the Electron desktop app
        val serverCustomerVersion = conflictCustomer.version + 1
        val serverCustomer = Customer(
            uuid = conflictCustomer.uuid,
            name = "بقالة القدس - خادم الرئيسي",
            phone = "0599765111", // Changed phone on server
            address = "خان يونس - الحي الإداري", // Changed address on server
            balance = -150.0, // Updated balance on server
            syncStatus = "synced",
            version = serverCustomerVersion,
            lastModified = System.currentTimeMillis() // Modified just now (newer)
        )

        // Simulating the push-pull conflict detector
        val detectedConflicts = listOf(CustomerConflict(localCustomer = conflictCustomer, serverCustomer = serverCustomer))
        _activeConflicts.value = detectedConflicts

        // Sync and pull other general updates (e.g. mock updates for items and invoices)
        val newMockItem = Item(
            name = "حلاوة طحينية الهلال 1 كجم",
            sku = "628108008",
            price = 6.2,
            stock = 75,
            category = "مواد غذائية",
            lastModified = System.currentTimeMillis()
        )
        itemDao.insertItem(newMockItem)

        addLog("SIMULATED_SYNC", "FAILED", "Simulated sync detected 1 conflict on Customer [بقالة القدس]. Incremental pull complete.")
        _isSyncing.value = false
    }
}

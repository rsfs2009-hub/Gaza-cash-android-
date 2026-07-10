package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.data.local.dao.OrderWithItems
import com.example.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val customerRepository = CustomerRepository(db.customerDao())
    private val itemRepository = ItemRepository(db.itemDao())
    private val orderRepository = OrderRepository(db.orderDao())
    val syncRepository = SyncRepository(application, db)

    // Current logged-in user state
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    // Server IP & Port settings state
    private val _serverIp = MutableStateFlow("192.168.1.100")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow("3000")
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    // Search and filter states
    val customerSearchQuery = MutableStateFlow("")
    val itemSearchQuery = MutableStateFlow("")

    // Raw database Flows
    val customers: StateFlow<List<Customer>> = customerRepository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<Item>> = itemRepository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<OrderWithItems>> = orderRepository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<Invoice>> = db.invoiceDao().getAllInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLog>> = syncRepository.recentLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConflicts: StateFlow<List<CustomerConflict>> = syncRepository.activeConflicts
    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing
    val lastSyncTime: StateFlow<Long> = syncRepository.lastSyncTime

    // Filtered lists
    val filteredCustomers: StateFlow<List<Customer>> = customerSearchQuery
        .combine(customers) { query, list ->
            if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<Item>> = itemSearchQuery
        .combine(items) { query, list ->
            if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) || it.sku.contains(query) || it.category.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // CART STATE (for creating a new order)
    private val _cartItems = MutableStateFlow<Map<String, Int>>(emptyMap()) // itemUuid -> quantity
    val cartItems: StateFlow<Map<String, Int>> = _cartItems.asStateFlow()

    private val _selectedCartCustomer = MutableStateFlow<Customer?>(null)
    val selectedCartCustomer: StateFlow<Customer?> = _selectedCartCustomer.asStateFlow()

    private val _cartNotes = MutableStateFlow("")
    val cartNotes: StateFlow<String> = _cartNotes.asStateFlow()

    private val _editingOrderUuid = MutableStateFlow<String?>(null)
    val editingOrderUuid: StateFlow<String?> = _editingOrderUuid.asStateFlow()

    init {
        // Load default delegate username and server settings
        val sharedPrefs = application.getSharedPreferences("delegate_prefs", Application.MODE_PRIVATE)
        _currentUser.value = sharedPrefs.getString("username", null)
        _serverIp.value = sharedPrefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100"
        _serverPort.value = sharedPrefs.getString("server_port", "3000") ?: "3000"

        // Seed data if database is empty to make it immediately operational and beautiful
        viewModelScope.launch {
            customers.first() // Wait for database response
            if (db.customerDao().getPendingCustomers().isEmpty() && db.itemDao().getItemBySku("871040001") == null) {
                syncRepository.generateMockData()
            }
        }
    }

    // AUTH
    fun login(username: String, password: String): Boolean {
        if (username.isNotBlank() && password.length >= 4) {
            _currentUser.value = username
            val sharedPrefs = getApplication<Application>().getSharedPreferences("delegate_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit().putString("username", username).apply()
            viewModelScope.launch {
                syncRepository.addLog("LOGIN", "SUCCESS", "Delegate user '$username' logged in successfully.")
            }
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
        val sharedPrefs = getApplication<Application>().getSharedPreferences("delegate_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().remove("username").apply()
        viewModelScope.launch {
            syncRepository.addLog("LOGOUT", "SUCCESS", "Delegate user logged out.")
        }
    }

    // SERVER SETTINGS
    fun saveServerSettings(ip: String, port: String) {
        _serverIp.value = ip
        _serverPort.value = port
        val sharedPrefs = getApplication<Application>().getSharedPreferences("delegate_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("server_ip", ip)
            .putString("server_port", port)
            .apply()
        viewModelScope.launch {
            syncRepository.addLog("CONFIG_UPDATE", "SUCCESS", "Updated server endpoint config to http://$ip:$port")
        }
    }

    // CUSTOMER ACTIONS
    fun addCustomer(name: String, phone: String, address: String, email: String) {
        viewModelScope.launch {
            val uuid = customerRepository.addCustomer(name, phone, address, email)
            syncRepository.addLog("CUSTOMER_ADD", "SUCCESS", "Added customer locally: $name")
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            customerRepository.updateCustomer(customer)
            syncRepository.addLog("CUSTOMER_UPDATE", "SUCCESS", "Updated customer locally: ${customer.name}")
        }
    }

    // CART / ORDER ACTIONS
    fun selectCustomerForCart(customer: Customer?) {
        _selectedCartCustomer.value = customer
    }

    fun updateCartNotes(notes: String) {
        _cartNotes.value = notes
    }

    fun updateCartItemQuantity(itemUuid: String, qty: Int) {
        val current = _cartItems.value.toMutableMap()
        if (qty <= 0) {
            current.remove(itemUuid)
        } else {
            current[itemUuid] = qty
        }
        _cartItems.value = current
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
        _selectedCartCustomer.value = null
        _cartNotes.value = ""
        _editingOrderUuid.value = null
    }

    fun loadOrderIntoCart(orderUuid: String) {
        viewModelScope.launch {
            val orderWithItems = orderRepository.getOrderWithItemsByUuid(orderUuid)
            if (orderWithItems != null) {
                _editingOrderUuid.value = orderUuid
                _cartNotes.value = orderWithItems.order.notes
                
                // Set selected customer
                val customer = customers.value.firstOrNull { it.uuid == orderWithItems.order.customerUuid }
                _selectedCartCustomer.value = customer ?: Customer(
                    uuid = orderWithItems.order.customerUuid,
                    name = orderWithItems.order.customerName,
                    phone = "",
                    address = "",
                    email = "",
                    balance = 0.0,
                    version = 1,
                    lastModified = System.currentTimeMillis()
                )
                
                // Populate cart items map
                val itemsMap = orderWithItems.items.associate { it.itemUuid to it.quantity }
                _cartItems.value = itemsMap
            }
        }
    }

    fun submitOrder(onComplete: (Boolean) -> Unit) {
        val customer = _selectedCartCustomer.value
        val itemsInCart = _cartItems.value
        if (customer == null || itemsInCart.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            try {
                val editUuid = _editingOrderUuid.value
                if (editUuid != null) {
                    orderRepository.updateOrder(
                        orderUuid = editUuid,
                        customerUuid = customer.uuid,
                        customerName = customer.name,
                        notes = _cartNotes.value,
                        items = itemsInCart.toList()
                    ) { itemUuid ->
                        val product = itemRepository.getItemByUuid(itemUuid)
                        if (product != null) Pair(product.name, product.price) else null
                    }
                    syncRepository.addLog("ORDER_UPDATE", "SUCCESS", "Updated local pending order for customer ${customer.name}.")
                } else {
                    orderRepository.createOrder(
                        customerUuid = customer.uuid,
                        customerName = customer.name,
                        notes = _cartNotes.value,
                        items = itemsInCart.toList()
                    ) { itemUuid ->
                        val product = itemRepository.getItemByUuid(itemUuid)
                        if (product != null) Pair(product.name, product.price) else null
                    }
                    syncRepository.addLog("ORDER_CREATE", "SUCCESS", "Created new local pending order for customer ${customer.name}.")
                }
                clearCart()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deleteOrder(orderUuid: String) {
        viewModelScope.launch {
            orderRepository.deleteOrder(orderUuid)
            syncRepository.addLog("ORDER_DELETE", "SUCCESS", "Deleted local pending order.")
        }
    }

    // SYNC OPERATIONS
    fun triggerSync() {
        viewModelScope.launch {
            syncRepository.performSync(_serverIp.value, _serverPort.value)
        }
    }

    fun simulateSyncConflict() {
        viewModelScope.launch {
            syncRepository.simulateSyncConflictScenario()
        }
    }

    fun resolveConflict(conflict: CustomerConflict, strategy: ConflictStrategy) {
        viewModelScope.launch {
            syncRepository.resolveConflict(conflict, strategy)
        }
    }

    fun resetAndSeedDemo() {
        viewModelScope.launch {
            syncRepository.generateMockData()
        }
    }
}

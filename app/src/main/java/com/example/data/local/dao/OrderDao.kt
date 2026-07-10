package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.Order
import com.example.data.local.entities.OrderItem
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @Embedded val order: Order,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "orderUuid"
    )
    val items: List<OrderItem>
)

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY orderDate DESC")
    fun getAllOrders(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE uuid = :uuid LIMIT 1")
    suspend fun getOrderWithItemsByUuid(uuid: String): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE syncStatus = 'pending_insert'")
    suspend fun getPendingOrdersWithItems(): List<OrderWithItems>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Query("DELETE FROM orders WHERE uuid = :orderUuid")
    suspend fun deleteOrder(orderUuid: String)

    @Query("DELETE FROM order_items WHERE orderUuid = :orderUuid")
    suspend fun deleteOrderItems(orderUuid: String)

    @Transaction
    suspend fun deleteOrderWithItems(orderUuid: String) {
        deleteOrderItems(orderUuid)
        deleteOrder(orderUuid)
    }

    @Query("UPDATE orders SET syncStatus = :syncStatus, version = :version WHERE uuid = :uuid")
    suspend fun updateSyncStatus(uuid: String, syncStatus: String, version: Int)

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Transaction
    suspend fun clearAll() {
        clearAllOrderItems()
        clearAllOrders()
    }
}

package com.example.data.repository

import com.example.data.local.dao.OrderDao
import com.example.data.local.dao.OrderWithItems
import com.example.data.local.entities.Order
import com.example.data.local.entities.OrderItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OrderRepository(private val orderDao: OrderDao) {
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrders()

    suspend fun createOrder(
        customerUuid: String,
        customerName: String,
        notes: String,
        items: List<Pair<String, Int>>, // Item UUID to quantity map
        itemFetcher: suspend (String) -> Pair<String, Double>? // Fetches item name and price
    ): String {
        val orderUuid = UUID.randomUUID().toString()
        var totalAmount = 0.0
        val orderItems = mutableListOf<OrderItem>()

        for ((itemUuid, qty) in items) {
            val fetched = itemFetcher(itemUuid) ?: continue
            val price = fetched.second
            val name = fetched.first
            totalAmount += price * qty
            orderItems.add(
                OrderItem(
                    orderUuid = orderUuid,
                    itemUuid = itemUuid,
                    itemName = name,
                    price = price,
                    quantity = qty
                )
            )
        }

        val order = Order(
            uuid = orderUuid,
            customerUuid = customerUuid,
            customerName = customerName,
            totalAmount = totalAmount,
            notes = notes,
            syncStatus = "pending_insert",
            version = 1,
            lastModified = System.currentTimeMillis()
        )

        orderDao.insertOrder(order)
        orderDao.insertOrderItems(orderItems)
        return orderUuid
    }

    suspend fun deleteOrder(orderUuid: String) {
        orderDao.deleteOrderWithItems(orderUuid)
    }

    suspend fun getOrderWithItemsByUuid(uuid: String): OrderWithItems? {
        return orderDao.getOrderWithItemsByUuid(uuid)
    }

    suspend fun updateOrder(
        orderUuid: String,
        customerUuid: String,
        customerName: String,
        notes: String,
        items: List<Pair<String, Int>>, // Item UUID to quantity map
        itemFetcher: suspend (String) -> Pair<String, Double>? // Fetches item name and price
    ) {
        var totalAmount = 0.0
        val orderItems = mutableListOf<OrderItem>()

        for ((itemUuid, qty) in items) {
            val fetched = itemFetcher(itemUuid) ?: continue
            val price = fetched.second
            val name = fetched.first
            totalAmount += price * qty
            orderItems.add(
                OrderItem(
                    orderUuid = orderUuid,
                    itemUuid = itemUuid,
                    itemName = name,
                    price = price,
                    quantity = qty
                )
            )
        }

        val order = Order(
            uuid = orderUuid,
            customerUuid = customerUuid,
            customerName = customerName,
            totalAmount = totalAmount,
            notes = notes,
            syncStatus = "pending_insert",
            version = 1,
            lastModified = System.currentTimeMillis()
        )

        orderDao.deleteOrderItems(orderUuid) // Clear existing order items
        orderDao.insertOrder(order)          // Replace the order header
        orderDao.insertOrderItems(orderItems)// Re-insert current items
    }
}

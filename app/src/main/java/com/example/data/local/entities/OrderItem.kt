package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val orderUuid: String,
    val itemUuid: String,
    val itemName: String,
    val price: Double,
    val quantity: Int
)

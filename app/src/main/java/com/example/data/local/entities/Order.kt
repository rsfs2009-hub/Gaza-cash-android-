package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val customerUuid: String,
    val customerName: String,
    val orderDate: Long = System.currentTimeMillis(),
    val totalAmount: Double = 0.0,
    val notes: String = "",
    val syncStatus: String = "pending_insert", // "pending_insert", "synced"
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis()
)

package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String,
    val email: String = "",
    val balance: Double = 0.0,
    val syncStatus: String = "synced", // "synced", "pending_insert", "pending_update"
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis()
)

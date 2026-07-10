package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val sku: String, // Barcode or SKU
    val price: Double,
    val stock: Int, // Available quantity from last sync
    val category: String,
    val syncStatus: String = "synced", // Items are usually pull-only
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis()
)

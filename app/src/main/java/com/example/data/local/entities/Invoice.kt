package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val customerUuid: String,
    val customerName: String,
    val invoiceDate: Long = System.currentTimeMillis(),
    val totalAmount: Double = 0.0,
    val discount: Double = 0.0,
    val finalAmount: Double = 0.0,
    val paymentType: String = "cash", // "cash", "credit"
    val itemsJson: String = "" // Simple JSON string listing items: quantity, name, price for detail view
)

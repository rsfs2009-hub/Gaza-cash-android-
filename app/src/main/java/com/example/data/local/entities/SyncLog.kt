package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // e.g. "PULL_CUSTOMERS", "PUSH_ORDERS", "CONFLICT_RESOLVED", "FULL_SYNC"
    val status: String, // "SUCCESS", "FAILED"
    val details: String
)

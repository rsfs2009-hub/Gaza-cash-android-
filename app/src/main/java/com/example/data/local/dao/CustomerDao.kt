package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE uuid = :uuid LIMIT 1")
    suspend fun getCustomerByUuid(uuid: String): Customer?

    @Query("SELECT * FROM customers WHERE syncStatus != 'synced'")
    suspend fun getPendingCustomers(): List<Customer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("UPDATE customers SET syncStatus = :syncStatus, version = :version WHERE uuid = :uuid")
    suspend fun updateSyncStatus(uuid: String, syncStatus: String, version: Int)

    @Query("DELETE FROM customers")
    suspend fun clearAll()
}

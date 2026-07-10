package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.Invoice
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY invoiceDate DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE uuid = :uuid LIMIT 1")
    suspend fun getInvoiceByUuid(uuid: String): Invoice?

    @Query("SELECT * FROM invoices WHERE customerUuid = :customerUuid ORDER BY invoiceDate DESC")
    fun getInvoicesByCustomer(customerUuid: String): Flow<List<Invoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<Invoice>)

    @Query("DELETE FROM invoices")
    suspend fun clearAll()
}

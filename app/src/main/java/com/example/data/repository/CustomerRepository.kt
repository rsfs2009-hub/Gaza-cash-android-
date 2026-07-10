package com.example.data.repository

import com.example.data.local.dao.CustomerDao
import com.example.data.local.entities.Customer
import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val customerDao: CustomerDao) {
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()

    suspend fun getCustomerByUuid(uuid: String): Customer? {
        return customerDao.getCustomerByUuid(uuid)
    }

    suspend fun addCustomer(name: String, phone: String, address: String, email: String): String {
        val customer = Customer(
            name = name,
            phone = phone,
            address = address,
            email = email,
            syncStatus = "pending_insert",
            version = 1,
            lastModified = System.currentTimeMillis()
        )
        customerDao.insertCustomer(customer)
        return customer.uuid
    }

    suspend fun updateCustomer(customer: Customer) {
        val updated = customer.copy(
            syncStatus = if (customer.syncStatus == "pending_insert") "pending_insert" else "pending_update",
            lastModified = System.currentTimeMillis(),
            version = customer.version + 1
        )
        customerDao.updateCustomer(updated)
    }

    suspend fun insertOrUpdateFromServer(customer: Customer) {
        customerDao.insertCustomer(customer)
    }

    suspend fun deleteCustomer(customer: Customer) {
        customerDao.deleteCustomer(customer)
    }
}

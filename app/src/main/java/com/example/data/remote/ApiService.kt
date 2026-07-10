package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// Data Transfer Objects (DTOs)
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val username: String,
    val role: String,
    val success: Boolean
)

data class PullRequest(
    val lastSyncTimestamp: Long
)

data class CustomerDto(
    val uuid: String,
    val name: String,
    val phone: String,
    val address: String,
    val email: String,
    val balance: Double,
    val version: Int,
    val lastModified: Long
)

data class ItemDto(
    val uuid: String,
    val name: String,
    val sku: String,
    val price: Double,
    val stock: Int,
    val category: String,
    val version: Int,
    val lastModified: Long
)

data class InvoiceDto(
    val uuid: String,
    val customerUuid: String,
    val customerName: String,
    val invoiceDate: Long,
    val totalAmount: Double,
    val discount: Double,
    val finalAmount: Double,
    val paymentType: String,
    val itemsJson: String
)

data class PullResponse(
    val serverTime: Long,
    val customers: List<CustomerDto>,
    val items: List<ItemDto>,
    val invoices: List<InvoiceDto>
)

data class OrderItemDto(
    val itemUuid: String,
    val itemName: String,
    val price: Double,
    val quantity: Int
)

data class OrderPushDto(
    val uuid: String,
    val customerUuid: String,
    val customerName: String,
    val orderDate: Long,
    val totalAmount: Double,
    val notes: String,
    val items: List<OrderItemDto>,
    val version: Int,
    val lastModified: Long
)

data class PushRequest(
    val customers: List<CustomerDto>,
    val orders: List<OrderPushDto>
)

data class CustomerConflictDto(
    val localCustomer: CustomerDto,
    val serverCustomer: CustomerDto
)

data class PushResponse(
    val success: Boolean,
    val syncedCustomerUuids: List<String>,
    val syncedOrderUuids: List<String>,
    val conflicts: List<CustomerConflictDto>
)

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/sync/pull")
    suspend fun pullData(@Body request: PullRequest): Response<PullResponse>

    @POST("api/sync/push")
    suspend fun pushData(@Body request: PushRequest): Response<PushResponse>
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var currentIp = "192.168.1.100"
    private var currentPort = "3000"
    private var apiService: ApiService? = null

    fun getService(ip: String = currentIp, port: String = currentPort): ApiService {
        if (apiService == null || ip != currentIp || port != currentPort) {
            currentIp = ip
            currentPort = port
            val baseUrl = "http://$ip:$port/"
            apiService = try {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(ApiService::class.java)
            } catch (e: Exception) {
                // Return a dummy service or handle appropriately
                null
            }
        }
        return apiService!!
    }
}

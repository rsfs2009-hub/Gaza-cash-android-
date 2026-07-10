package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object CustomerList : Screen("customer_list")
    object CustomerDetail : Screen("customer_detail/{customerUuid}") {
        fun createRoute(customerUuid: String) = "customer_detail/$customerUuid"
    }
    object CustomerForm : Screen("customer_form?customerUuid={customerUuid}") {
        fun createRoute(customerUuid: String? = null) = 
            if (customerUuid != null) "customer_form?customerUuid=$customerUuid" else "customer_form"
    }
    object ItemList : Screen("item_list")
    object OrderForm : Screen("order_form?orderUuid={orderUuid}") {
        fun createRoute(orderUuid: String? = null) = 
            if (orderUuid != null) "order_form?orderUuid=$orderUuid" else "order_form"
    }
    object SyncSettings : Screen("sync_settings")
    object Reports : Screen("reports")
}

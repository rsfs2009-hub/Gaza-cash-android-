package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    val currentUser by viewModel.currentUser.collectAsState()

    // Determine starting route depending on login state
    val startDestination = if (currentUser != null) Screen.Dashboard.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 1. Secure Login Screen
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Core Hub: Dashboard
        composable(Screen.Dashboard.route) {
            // Keep user gated
            if (currentUser == null) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            } else {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToCustomers = { navController.navigate(Screen.CustomerList.route) },
                    onNavigateToItems = { navController.navigate(Screen.ItemList.route) },
                    onNavigateToNewOrder = { navController.navigate(Screen.OrderForm.createRoute(null)) },
                    onNavigateToSync = { navController.navigate(Screen.SyncSettings.route) },
                    onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                    onNavigateToEditOrder = { orderUuid ->
                        navController.navigate(Screen.OrderForm.createRoute(orderUuid))
                    }
                )
            }
        }

        // 3. Customer Directory Directory
        composable(Screen.CustomerList.route) {
            CustomerListScreen(
                viewModel = viewModel,
                onNavigateToDetails = { uuid -> navController.navigate(Screen.CustomerDetail.createRoute(uuid)) },
                onNavigateToAddCustomer = { navController.navigate(Screen.CustomerForm.createRoute(null)) },
                onBackClick = { navController.popBackStack() }
            )
        }

        // 4. Detailed Customer Card
        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerUuid") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerUuid = backStackEntry.arguments?.getString("customerUuid") ?: ""
            CustomerDetailScreen(
                viewModel = viewModel,
                customerUuid = customerUuid,
                onNavigateToEdit = { uuid -> navController.navigate(Screen.CustomerForm.createRoute(uuid)) },
                onNavigateToNewOrder = { navController.navigate(Screen.OrderForm.route) },
                onBackClick = { navController.popBackStack() }
            )
        }

        // 5. Add / Edit Customer Form
        composable(
            route = Screen.CustomerForm.route,
            arguments = listOf(
                navArgument("customerUuid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val customerUuid = backStackEntry.arguments?.getString("customerUuid")
            CustomerFormScreen(
                viewModel = viewModel,
                customerUuid = customerUuid,
                onSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        // 6. Product Stock Catalogue
        composable(Screen.ItemList.route) {
            ItemListScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 7. Order Creator Point-of-Sale
        composable(
            route = Screen.OrderForm.route,
            arguments = listOf(
                navArgument("orderUuid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val orderUuid = backStackEntry.arguments?.getString("orderUuid")
            LaunchedEffect(orderUuid) {
                if (orderUuid != null) {
                    viewModel.loadOrderIntoCart(orderUuid)
                }
            }
            OrderFormScreen(
                viewModel = viewModel,
                onSuccess = { navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Dashboard.route) { inclusive = false } } },
                onBackClick = { navController.popBackStack() }
            )
        }

        // 8. Control Panel Sync settings & conflicts
        composable(Screen.SyncSettings.route) {
            SyncSettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 9. Dashboard Sales Reports
        composable(Screen.Reports.route) {
            ReportsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

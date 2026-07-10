package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Item
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(
    viewModel: MainViewModel,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.filteredCustomers.collectAsState()
    val items by viewModel.filteredItems.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val selectedCustomer by viewModel.selectedCartCustomer.collectAsState()
    val cartNotes by viewModel.cartNotes.collectAsState()
    val editingOrderUuid by viewModel.editingOrderUuid.collectAsState()
    val isEditing = editingOrderUuid != null

    var customerSearch by remember { mutableStateOf("") }
    var itemSearch by remember { mutableStateOf("") }

    // Synchronize searches with viewModel filter flows
    LaunchedEffect(customerSearch) {
        viewModel.customerSearchQuery.value = customerSearch
    }
    LaunchedEffect(itemSearch) {
        viewModel.itemSearchQuery.value = itemSearch
    }

    // Compute active summary metrics
    val totalQuantity = cartItems.values.sum()
    val totalPrice = cartItems.entries.sumOf { (itemUuid, qty) ->
        val product = items.firstOrNull { it.uuid == itemUuid }
        (product?.price ?: 0.0) * qty
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "تعديل الطلبية الميدانية المعلقة" else "إنشاء طلبية ميدانية جديدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (selectedCustomer != null || cartItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearCart() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ السلة", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // STEP 1: SELECT CUSTOMER (if not selected)
            if (selectedCustomer == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الخطوة الأولى: اختر العميل المستهدف للطلبية",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = customerSearch,
                        onValueChange = { customerSearch = it },
                        placeholder = { Text("ابحث باسم العميل أو رقم هاتفه الميداني...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customers, key = { it.uuid }) { customer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { viewModel.selectCustomerForCart(customer) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, fontWeight = FontWeight.Bold)
                                    Text("الهاتف: ${customer.phone} • الرصيد: ${customer.balance} $", style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            } else {
                // Customer Selected Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "العميل: ${selectedCustomer!!.name}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "العنوان: ${selectedCustomer!!.address} • الهاتف: ${selectedCustomer!!.phone}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { viewModel.selectCustomerForCart(null) }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "تغيير العميل", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // STEP 2: CATALOGUE PRODUCTS LIST
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الخطوة الثانية: حدد المنتجات والكميات المطلوبة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = itemSearch,
                        onValueChange = { itemSearch = it },
                        placeholder = { Text("ابحث باسم المنتج أو الباركود SKU...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items, key = { it.uuid }) { item ->
                        val qty = cartItems[item.uuid] ?: 0
                        ProductCartRow(
                            item = item,
                            quantity = qty,
                            onQuantityChange = { newQty -> viewModel.updateCartItemQuantity(item.uuid, newQty) }
                        )
                    }
                }

                // STEP 3: ORDER SUMMARY DRAWER FOOTER (FIXED)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = cartNotes,
                            onValueChange = { viewModel.updateCartNotes(it) },
                            placeholder = { Text("أدخل ملاحظات الطلبية كشروط الدفع أو التسليم...") },
                            label = { Text("ملاحظات المندوب") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إجمالي السلة: $totalQuantity أصناف",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.2f", totalPrice)} $",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Button(
                                onClick = {
                                    if (cartItems.isEmpty()) {
                                        Toast.makeText(context, "الرجاء اختيار صنف واحد على الأقل!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.submitOrder { success ->
                                            if (success) {
                                                val msg = if (isEditing) "تم حفظ تعديلات الطلبية بنجاح!" else "تم حفظ الطلبية بنجاح في السجل المعلق!"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                onSuccess()
                                            } else {
                                                val errorMsg = if (isEditing) "فشل تعديل الطلبية الميدانية." else "فشل إنشاء الطلبية الميدانية."
                                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("submit_order_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Done, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isEditing) "حفظ وتحديث الطلبية" else "تأكيد وإصدار الطلبية", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCartRow(
    item: Item,
    quantity: Int,
    onQuantityChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "سعر: ${String.format("%.1f", item.price)} $ • مخزون: ${item.stock}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (quantity > 0) {
                    IconButton(
                        onClick = { onQuantityChange(quantity - 1) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "تنقيص", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Text(
                        text = "$quantity",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { onQuantityChange(quantity + 1) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "زيادة", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

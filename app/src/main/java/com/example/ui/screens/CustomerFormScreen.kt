package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.Customer
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    viewModel: MainViewModel,
    customerUuid: String?,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val isEditing = customerUuid != null
    val customerToEdit = if (isEditing) customers.firstOrNull { it.uuid == customerUuid } else null

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Load existing values if editing
    LaunchedEffect(customerToEdit) {
        if (isEditing && customerToEdit != null) {
            name = customerToEdit.name
            phone = customerToEdit.phone
            address = customerToEdit.address
            email = customerToEdit.email
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "تعديل بيانات العميل" else "إضافة عميل جديد", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "يرجى ملء تفاصيل ملف العميل الميداني بالكامل. سيتم حفظ هذا التعديل محلياً وتلقائياً ومزامنته بمجرد الاتصال بخادم ERP الرئيسي.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { Text("اسم العميل أو المحل التجاري *") },
                    placeholder = { Text("مثال: سوبرماركت السعادة") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_customer_name"),
                    isError = showError && name.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; showError = false },
                    label = { Text("رقم الهاتف الميداني *") },
                    placeholder = { Text("مثال: 0599000111") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_customer_phone"),
                    isError = showError && phone.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; showError = false },
                    label = { Text("عنوان المتجر / الموقع التفصيلي *") },
                    placeholder = { Text("مثال: غزة - شارع الجلاء") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_customer_address"),
                    isError = showError && address.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني للعميل (اختياري)") },
                    placeholder = { Text("مثال: customer@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showError) {
                item {
                    Text(
                        text = "يرجى تعبئة جميع الحقول المطلوبة والمؤشر عليها بـ (*).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
                            showError = true
                        } else {
                            if (isEditing && customerToEdit != null) {
                                val updated = customerToEdit.copy(
                                    name = name,
                                    phone = phone,
                                    address = address,
                                    email = email
                                )
                                viewModel.updateCustomer(updated)
                            } else {
                                viewModel.addCustomer(
                                    name = name,
                                    phone = phone,
                                    address = address,
                                    email = email
                                )
                            }
                            onSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("save_customer_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditing) "حفظ التعديلات" else "إضافة العميل وتفعيله محلياً",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

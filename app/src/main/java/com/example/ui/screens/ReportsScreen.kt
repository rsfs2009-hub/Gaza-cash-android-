package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Invoice
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.InvoicePrinter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val orders by viewModel.orders.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var selectedInvoiceForPrint by remember { mutableStateOf<Invoice?>(null) }
    var showThermalPreviewText by remember { mutableStateOf<String?>(null) }

    // Sales metrics
    val totalPendingOrders = orders.filter { it.order.syncStatus == "pending_insert" }
    val totalSyncedOrders = orders.filter { it.order.syncStatus == "synced" }

    val pendingSalesValue = totalPendingOrders.sumOf { it.order.totalAmount }
    val syncedSalesValue = totalSyncedOrders.sumOf { it.order.totalAmount } + invoices.sumOf { it.finalAmount }
    val grandTotalSales = pendingSalesValue + syncedSalesValue

    // Top Customers calculation
    val customerSales = mutableMapOf<String, Double>()
    orders.forEach {
        val current = customerSales[it.order.customerName] ?: 0.0
        customerSales[it.order.customerName] = current + it.order.totalAmount
    }
    invoices.forEach {
        val current = customerSales[it.customerName] ?: 0.0
        customerSales[it.customerName] = current + it.finalAmount
    }
    val topCustomers = customerSales.entries.sortedByDescending { it.value }.take(5)

    // Sales by Product calculation
    val productSales = mutableMapOf<String, Double>()
    orders.forEach { orderWithItems ->
        orderWithItems.items.forEach { item ->
            val current = productSales[item.itemName] ?: 0.0
            productSales[item.itemName] = current + (item.price * item.quantity)
        }
    }
    // Simple mock breakdown for historic invoices
    productSales["أرز الياسمين 5 كجم"] = (productSales["أرز الياسمين 5 كجم"] ?: 0.0) + 200.0
    productSales["حليب نيدو مجفف 900 جرام"] = (productSales["حليب نيدو مجفف 900 جرام"] ?: 0.0) + 910.0
    productSales["شاي ربيع علاقي 100 كيس"] = (productSales["شاي ربيع علاقي 100 كيس"] ?: 0.0) + 550.0

    val topProducts = productSales.entries.sortedByDescending { it.value }.take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تقارير وإحصاءات المبيعات", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "ملخص المبيعات الميدانية الإجمالي",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                        ReportSummaryRow(label = "مبيعات معلقة محلياً", value = "${String.format("%.2f", pendingSalesValue)} $", color = MaterialTheme.colorScheme.error)
                        ReportSummaryRow(label = "مبيعات متزامنة ومؤكدة", value = "${String.format("%.2f", syncedSalesValue)} $", color = Color(0xFF4CAF50))
                        ReportSummaryRow(label = "إجمالي مبيعات الدورة", value = "${String.format("%.2f", grandTotalSales)} $", color = MaterialTheme.colorScheme.primary, isBold = true)
                    }
                }
            }

            // Top Customers Progress Bars
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = "العملاء الأكثر طلباً وقيمة (تراكمي)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (topCustomers.isEmpty()) {
                            Text("لا توجد مبيعات مسجلة حتى الآن لحساب نسب العملاء.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            val maxCustomerValue = topCustomers.maxOf { it.value }
                            topCustomers.forEach { (customerName, value) ->
                                val progress = if (maxCustomerValue > 0) (value / maxCustomerValue).toFloat() else 0f
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(customerName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${String.format("%.1f", value)} $", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary))
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Products progress bars
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "أكثر السلع طلباً ورواجاً بقيم المبيعات",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (topProducts.isEmpty()) {
                            Text("لا توجد سلع مسجلة مبيعاتها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            val maxProductValue = topProducts.maxOf { it.value }
                            topProducts.forEach { (itemName, value) ->
                                val progress = if (maxProductValue > 0) (value / maxProductValue).toFloat() else 0f
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(itemName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                        Text("${String.format("%.1f", value)} $", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.secondary))
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Synced Confirmed Invoices Log
            item {
                Text(
                    text = "سجل الفواتير المؤكدة المتزامنة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            if (invoices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد فواتير مؤكدة متزامنة بعد في هذا الوردية الميدانية.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(invoices, key = { it.uuid }) { invoice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invoice.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("فاتورة رقم: ${invoice.uuid.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "القيمة: ${String.format("%.2f", invoice.finalAmount)} $ • ${if (invoice.paymentType == "cash") "نقدي" else "ذمم (آجل)"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            IconButton(
                                onClick = { selectedInvoiceForPrint = invoice },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "طباعة الفاتورة", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Invoice Print Options Dialog
    if (selectedInvoiceForPrint != null) {
        val invoice = selectedInvoiceForPrint!!
        AlertDialog(
            onDismissRequest = { selectedInvoiceForPrint = null },
            title = {
                Text(
                    text = "خيارات طباعة الفاتورة المؤكدة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "اختر كيفية طباعة الفاتورة المؤكدة للعميل ${invoice.customerName}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // PDF Button
                    Button(
                        onClick = {
                            InvoicePrinter.printInvoiceAsPdf(context, invoice)
                            selectedInvoiceForPrint = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طباعة وتصدير كـ PDF رسمية", fontWeight = FontWeight.Bold)
                    }

                    // Thermal Button
                    Button(
                        onClick = {
                            val txt = InvoicePrinter.generateThermalTextForInvoice(invoice)
                            showThermalPreviewText = txt
                            selectedInvoiceForPrint = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طباعة حرارية (معاينة ومحاكاة 58mm)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedInvoiceForPrint = null }) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Thermal Receipt Simulator Dialog for Invoice
    if (showThermalPreviewText != null) {
        val receiptText = showThermalPreviewText!!
        AlertDialog(
            onDismissRequest = { showThermalPreviewText = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("معاينة الفاتورة الحرارية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showThermalPreviewText = null }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Simulated thermal receipt container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .background(Color(0xFFF9F9F9), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = receiptText,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color.Black,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy Button
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Thermal Receipt", receiptText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ نص الفاتورة بنجاح للحافظة!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ النص", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share Button
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, receiptText)
                                }
                                context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun ReportSummaryRow(
    label: String,
    value: String,
    color: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.SyncLog
import com.example.data.repository.ConflictStrategy
import com.example.data.repository.CustomerConflict
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val activeConflicts by viewModel.activeConflicts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()

    var ipInput by remember { mutableStateOf(serverIp) }
    var portInput by remember { mutableStateOf(serverPort) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المزامنة والتعارضات", fontWeight = FontWeight.Bold) },
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
            // Server Endpoint Form
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
                        Text(
                            text = "إعدادات الاتصال بالخادم الرئيسي للمحل (ERP)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = ipInput,
                                onValueChange = { ipInput = it },
                                label = { Text("عنوان IP الخادم") },
                                placeholder = { Text("مثال: 192.168.1.100") },
                                modifier = Modifier.weight(2f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = portInput,
                                onValueChange = { portInput = it },
                                label = { Text("المنفذ (Port)") },
                                placeholder = { Text("3000") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveServerSettings(ipInput, portInput)
                                Toast.makeText(context, "تم حفظ إعدادات الاتصال بالخادم الرئيسي.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ إعدادات الخادم")
                        }
                    }
                }
            }

            // Dual Sync Actions
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
                        Text(
                            text = "لوحة التحكم بالمزامنة الميدانية",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "آخر مزامنة ناجحة: " + if (lastSyncTime == 0L) "لم يتم المزامنة بعد" else formatFullDate(lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (isSyncing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.triggerSync() },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("run_live_sync_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isSyncing
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مزامنة حقيقية", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.simulateSyncConflict() },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp)
                                    .testTag("simulate_conflict_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                enabled = !isSyncing
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("محاكاة سيناريو تعارض", fontSize = 12.sp)
                            }
                        }

                        // Danger Zone (Reset)
                        OutlinedButton(
                            onClick = {
                                viewModel.resetAndSeedDemo()
                                Toast.makeText(context, "تم مسح البيانات وإعادة تهيئة قاعدة البيانات المحلية التجريبية.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح وتهيئة ديمو تجريبي")
                        }
                    }
                }
            }

            // Interactive Conflict Resolver Block
            if (activeConflicts.isNotEmpty()) {
                item {
                    Text(
                        text = "يوجد تعارضات بالبيانات بانتظار الحل الميداني (${activeConflicts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                items(activeConflicts) { conflict ->
                    ConflictResolutionCard(
                        conflict = conflict,
                        onResolve = { strategy -> viewModel.resolveConflict(conflict, strategy) }
                    )
                }
            }

            // Live Sync Terminal/Console Logs
            item {
                Text(
                    text = "سجل عمليات المزامنة (الكونسول)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)) // Dark terminal color
                ) {
                    if (syncLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("السجل فارغ. ابدأ المزامنة لرؤية المخرجات.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(syncLogs) { log ->
                                ConsoleLogLine(log = log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConflictResolutionCard(
    conflict: CustomerConflict,
    onResolve: (ConflictStrategy) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = "تعارض في ملف العميل: ${conflict.localCustomer.name}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            // Side-by-side local vs server comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Version Panel
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("النسخة المحلية للجهاز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الاسم: ${conflict.localCustomer.name}", fontSize = 11.sp, maxLines = 1)
                        Text("هاتف: ${conflict.localCustomer.phone}", fontSize = 11.sp, maxLines = 1)
                        Text("عنوان: ${conflict.localCustomer.address}", fontSize = 11.sp, maxLines = 1)
                        Text("رصيد: ${conflict.localCustomer.balance} $", fontSize = 11.sp)
                        Text("معدل: ${formatFullDate(conflict.localCustomer.lastModified)}", fontSize = 9.sp, color = Color.Gray)
                    }
                }

                // Server Version Panel
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("نسخة خادم المتجر الرئيسي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الاسم: ${conflict.serverCustomer.name}", fontSize = 11.sp, maxLines = 1)
                        Text("هاتف: ${conflict.serverCustomer.phone}", fontSize = 11.sp, maxLines = 1)
                        Text("عنوان: ${conflict.serverCustomer.address}", fontSize = 11.sp, maxLines = 1)
                        Text("رصيد: ${conflict.serverCustomer.balance} $", fontSize = 11.sp)
                        Text("معدل: ${formatFullDate(conflict.serverCustomer.lastModified)}", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }

            // Resolution Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("اختر استراتيجية حل التعارض وحفظها:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onResolve(ConflictStrategy.LOCAL_WINS) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("تثبيت المحلي", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onResolve(ConflictStrategy.SERVER_WINS) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("اعتماد الخادم", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onResolve(ConflictStrategy.LAST_WRITE_WINS) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
                    ) {
                        Text("الأحدث يفوز (دمج)", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleLogLine(log: SyncLog) {
    val statusColor = if (log.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFE53935)
    val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "[$timeStr]",
                color = Color.Cyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = log.action,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = log.details,
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
        Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 4.dp))
    }
}

fun formatFullDate(timestamp: Long): String {
    if (timestamp == 0L) return "-"
    val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

package com.dfshine.launcher.ui.dashboard

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.vehicle.QingganCanBusClient
import com.dfshine.launcher.vehicle.RawLog
import com.dfshine.launcher.vehicle.VehicleRepository
import com.dfshine.launcher.vehicle.VendorCodeResolver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Is the car talking to us?" screen: service discovery, which AIDL codes
 * were resolved and from where, live decoded values, and the raw traffic.
 * "Export" writes everything to a text file so it can be shared and the
 * decoders fixed for this firmware.
 */
@Composable
fun VehicleDiagnosticsScreen(viewModel: LauncherViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    val status by VehicleRepository.status.collectAsState()
    val state by VehicleRepository.state.collectAsState()
    val log by RawLog.entries.collectAsState()
    var mode by remember { mutableStateOf(prefs.vehicleDataMode) }
    var allowDefaults by remember { mutableStateOf(prefs.vehicleAllowDefaultCodes) }
    val services = remember { listVendorServices() }
    val packages = remember { listVendorPackages(context) }

    fun apply() {
        prefs.vehicleDataMode = mode
        prefs.vehicleAllowDefaultCodes = allowDefaults
        VehicleRepository.restart(context, mode, allowDefaults)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
                Text("تشخيص بيانات العربية (CAN)", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مصدر البيانات")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            VehicleRepository.MODE_AUTO to "العربية",
                            VehicleRepository.MODE_DEMO to "عرض تجريبي",
                            VehicleRepository.MODE_OFF to "إيقاف"
                        ).forEach { (value, label) ->
                            if (mode == value) Button(onClick = {}) { Text(label) }
                            else OutlinedButton(onClick = { mode = value; apply() }) { Text(label) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.fillMaxWidth(0.78f)) {
                            Text("استخدام الأرقام الافتراضية (غير متأكد منها)")
                            Text(
                                "لو التطبيق مقدرش يقرا أرقام الـ AIDL من نظام العربية. الأرقام دي " +
                                    "متجربة على عربيات تانية بنفس النظام، ولو مختلفة عندك ممكن نكلّم دالة غلط. " +
                                    "جرّبها والعربية واقفة بس.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(checked = allowDefaults, onCheckedChange = { allowDefaults = it; apply() })
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الحالة", style = MaterialTheme.typography.titleMedium)
                    StatusLine("الخدمة qg.canbus موجودة", status.serviceFound)
                    StatusLine("الواجهة ICanBusService مطابقة", status.descriptorOk)
                    StatusLine("متصل وبيستقبل", status.connected)
                    Text("مصدر الأرقام: ${status.codesSource}")
                    Text("أحداث مستلمة: ${status.eventCount}")
                    if (status.message.isNotBlank()) Text(status.message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("القيم الحالية", style = MaterialTheme.typography.titleMedium)
                    Mono(
                        "speed=${state.speedKmh} rpm=${state.rpm} gear=${state.gear}\n" +
                            "outside=${state.outsideTempC} coolant=${state.coolantTempC} fuel%=${state.fuelPercent}\n" +
                            "range=${state.rangeKm} odo=${state.odometerKm} inst=${state.instantConsumption} avg=${state.avgConsumption}\n" +
                            "batt=${state.batteryVolts} handbrake=${state.handBrake}\n" +
                            "doors=${state.doors}\nlights=${state.lights}\ntpms=${state.tpms}"
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val f = exportReport(context, status, services, packages)
                    Toast.makeText(context, if (f != null) "اتحفظ في: ${f.absolutePath}" else "فشل الحفظ", Toast.LENGTH_LONG).show()
                }) { Text("تصدير تقرير") }
                OutlinedButton(onClick = { RawLog.clear() }) { Text("مسح السجل") }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("خدمات النظام (qg./qinggan)", style = MaterialTheme.typography.titleMedium)
                    Mono(services.ifEmpty { listOf("(مفيش)") }.joinToString("\n"))
                    Text("باكدجات com.qinggan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Mono(packages.ifEmpty { listOf("(مفيش)") }.joinToString("\n"))
                    if (status.resolvedServiceCodes.isNotEmpty()) {
                        Text("أرقام ICanBusService من النظام", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Mono(status.resolvedServiceCodes.entries.sortedBy { it.value }.joinToString("\n") { "${it.value} ${it.key}" })
                    }
                }
            }
        }

        item {
            val crash = remember { com.dfshine.launcher.util.CrashLog.read(context) }
            if (crash != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("آخر كراش للتطبيق", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF6B6B))
                        Mono(crash.take(3000))
                        OutlinedButton(onClick = { com.dfshine.launcher.util.CrashLog.clear(context) }) { Text("مسح") }
                    }
                }
            }
        }
        item { Text("السجل الخام (آخر ${log.size})", style = MaterialTheme.typography.titleMedium) }
        items(log.asReversed()) { e -> Mono(e.format()) }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Text((if (ok) "✅ " else "❌ ") + label, color = if (ok) Color(0xFF3DDC97) else Color(0xFFFF6B6B))
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}

private fun listVendorServices(): List<String> = try {
    val sm = Class.forName("android.os.ServiceManager")
    @Suppress("UNCHECKED_CAST")
    val all = sm.getMethod("listServices").invoke(null) as Array<String>
    all.filter { it.startsWith("qg") || it.contains("qinggan", true) || it.contains("can", true) || it.contains("tbox", true) }
        .sorted()
} catch (_: Throwable) {
    emptyList()
}

private fun listVendorPackages(context: Context): List<String> = runCatching {
    context.packageManager.getInstalledPackages(0)
        .map { it.packageName }
        .filter { it.startsWith("com.qinggan") || it.contains("pateo", true) || it.contains("dfm", true) || it.contains("dongfeng", true) }
        .sorted()
}.getOrDefault(emptyList())

private fun exportReport(
    context: Context,
    status: QingganCanBusClient.Status,
    services: List<String>,
    packages: List<String>
): File? {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val text = buildString {
        appendLine("Shine Launcher vehicle report $stamp")
        appendLine("device=${Build.MANUFACTURER}/${Build.MODEL} board=${Build.BOARD} android=${Build.VERSION.RELEASE} build=${Build.DISPLAY}")
        appendLine("status=$status")
        appendLine("resolver=${VendorCodeResolver.resolve(context)}")
        appendLine("\n# services"); services.forEach { appendLine(it) }
        appendLine("\n# packages"); packages.forEach { appendLine(it) }
        appendLine("\n# last crash"); appendLine(com.dfshine.launcher.util.CrashLog.read(context) ?: "(none)")
        appendLine("\n# raw log"); appendLine(RawLog.dump())
    }
    val dirs = listOfNotNull(
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }.getOrNull(),
        context.getExternalFilesDir(null),
        context.filesDir
    )
    for (dir in dirs) {
        val f = File(dir, "shine_vehicle_report_$stamp.txt")
        if (runCatching { dir.mkdirs(); f.writeText(text) }.isSuccess) return f
    }
    return null
}

package com.dfshine.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.service.NotificationBadgeService
import com.dfshine.launcher.ui.components.AppPickerDialog
import com.dfshine.launcher.ui.theme.toHex
import com.dfshine.launcher.util.DefaultLauncherUtil
import com.dfshine.launcher.util.PermissionUtils

private val ACCENT_PRESETS = listOf(
    Color(0xFF2FB6A6), Color(0xFFFF8A3D), Color(0xFF4C8DFF),
    Color(0xFFE23D6B), Color(0xFF8E6BFF), Color(0xFF41C46B)
)

@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onOpenSplitScreen: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenMusicPlayer: () -> Unit,
    onOpenVideoPlayer: () -> Unit
) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    var showReverseCameraPicker by remember { mutableStateOf(false) }
    var showHvacAppPicker by remember { mutableStateOf(false) }
    var showGpsAppPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
                Text(
                    "الإعدادات",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp, start = 8.dp)
                )
            }
        }

        item { SectionTitle("المظهر") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("لون الواجهة")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENT_PRESETS.forEach { color ->
                            val selected = prefs.accentColorHex.equals(color.toHex(), ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        prefs.accentColorHex = color.toHex()
                                        viewModel.notifySettingsChanged()
                                    },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }

                    Text("وضع الإضاءة", modifier = Modifier.padding(top = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf("system" to "تلقائي", "dark" to "داكن", "light" to "فاتح").forEach { (value, label) ->
                            val selected = prefs.themeMode == value
                            if (selected) {
                                Button(onClick = {}) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { prefs.themeMode = value; viewModel.notifySettingsChanged() }) { Text(label) }
                            }
                        }
                    }

                    Text("عدد الأعمدة في الشاشة الرئيسية", modifier = Modifier.padding(top = 16.dp))
                    var columns by remember { mutableFloatStateOf(prefs.gridColumns.toFloat()) }
                    Slider(
                        value = columns,
                        valueRange = 3f..6f,
                        steps = 2,
                        onValueChange = {
                            columns = it
                            prefs.gridColumns = it.toInt()
                            viewModel.notifySettingsChanged()
                        }
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إظهار أسماء التطبيقات")
                        Switch(
                            checked = prefs.showIconLabels,
                            onCheckedChange = { prefs.showIconLabels = it; viewModel.notifySettingsChanged() }
                        )
                    }
                }
            }
        }

        item { SectionTitle("الأدوات العائمة والتشغيل") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSwitchRow(
                        title = "تشغيل الأدوات العائمة تلقائياً عند بدء التشغيل",
                        checked = prefs.floatingToolsOnBoot,
                        onCheckedChange = { prefs.floatingToolsOnBoot = it; viewModel.notifySettingsChanged() }
                    )
                    SettingsSwitchRow(
                        title = "أزرار الوسائط بمفاتيح المقود",
                        checked = prefs.steeringKeysEnabled,
                        onCheckedChange = {
                            prefs.steeringKeysEnabled = it
                            viewModel.notifySettingsChanged()
                            val serviceIntent = android.content.Intent(context, com.dfshine.launcher.service.MediaKeyService::class.java)
                            if (it) {
                                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.stopService(serviceIntent)
                            }
                        }
                    )
                    SettingsSwitchRow(
                        title = "شارات الإشعارات على الأيقونات",
                        checked = prefs.notificationBadgesEnabled,
                        onCheckedChange = { prefs.notificationBadgesEnabled = it; viewModel.notifySettingsChanged() }
                    )
                    OutlinedButton(
                        onClick = onOpenSplitScreen,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("إعداد الشاشة المقسمة") }
                }
            }
        }

        item { SectionTitle("كاميرا الرجوع للخلف") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsSwitchRow(
                        title = "فتح تطبيق الكاميرا تلقائياً عند الرجوع للخلف",
                        checked = prefs.reverseCameraEnabled,
                        onCheckedChange = { prefs.reverseCameraEnabled = it; viewModel.notifySettingsChanged() }
                    )
                    OutlinedButton(
                        onClick = { showReverseCameraPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        val current = prefs.reverseCameraTargetApp?.substringBefore("/")
                        Text(if (current != null) "التطبيق المحدد: $current" else "اختر تطبيق الكاميرا")
                    }
                    var customAction by remember { mutableStateOf(prefs.reverseCameraCustomAction ?: "") }
                    OutlinedTextField(
                        value = customAction,
                        onValueChange = {
                            customAction = it
                            prefs.reverseCameraCustomAction = it
                        },
                        label = { Text("Broadcast Action مخصص (اختياري)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        "إن لم تكن كاميرا الرجوع تعمل تلقائياً، اسأل فني تركيب الشاشة عن اسم الـ broadcast الذي ترسله الوحدة عند تفعيل الرجوع للخلف وأدخله هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item { SectionTitle("GPS والملاحة") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "لا توجد خدمات Google على هذه الشاشة، فـ Android Auto الحقيقي (المكوّن اللي بيستقبل من الموبايل) مش ممكن تقنياً هنا. اختر بدل منه تطبيق ملاحة مثبَّت بالفعل (مثل OsmAnd أو Maps.me) عشان يفتح من زر \"الملاحة\" في الشريط السفلي.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { showGpsAppPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        val current = prefs.gpsTargetApp?.substringBefore("/")
                        Text(if (current != null) "تطبيق الملاحة: $current" else "اختر تطبيق الملاحة")
                    }
                    SettingsSwitchRow(
                        title = "إظهار ويدجت الموسيقى فوق نص الشاشة أثناء الملاحة",
                        checked = prefs.musicWidgetDuringGps,
                        onCheckedChange = { prefs.musicWidgetDuringGps = it; viewModel.notifySettingsChanged() }
                    )
                    PermissionRow(
                        title = "إذن الوصول لسجل الاستخدام (لمعرفة متى تطبيق الملاحة مفتوح)",
                        granted = PermissionUtils.hasUsageAccess(context),
                        onClick = { context.startActivity(PermissionUtils.usageAccessSettingsIntent()) }
                    )
                }
            }
        }

        item { SectionTitle("ودجت التكييف الاحتياطي (Fallback)") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "شريط التكييف الأصلي بتاع شاشتك (الحرارة/AUTO/المروحة) بترسمه الوحدة نفسها كطبقة نظام مستقلة عن أي تطبيق - مش جزء من اللانشر الأصلي. لهذا السبب المفروض يفضل شغال لوحده حتى بعد تفعيل هذا اللانشر كشاشة رئيسية افتراضية، بدون أي تدخل من هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "فعّل الودجت اللي تحت بس لو جرّبت على الجهاز الحقيقي ولاحظت إن الشريط الأصلي اختفى فعلاً.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SettingsSwitchRow(
                        title = "إظهار ودجت التكييف الاحتياطي فوق كل التطبيقات",
                        checked = prefs.navBarEnabled,
                        onCheckedChange = {
                            prefs.navBarEnabled = it
                            viewModel.notifySettingsChanged()
                            val serviceIntent = com.dfshine.launcher.service.PersistentNavBarService.startIntent(context)
                            if (it) {
                                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.stopService(serviceIntent)
                            }
                        }
                    )
                    Text(
                        "الودجت فيه كل الأزرار (حرارة، AUTO/OFF، اتجاه الهواء، تشغيل/إيقاف، إزالة الضباب، تدوير الهواء، سرعة المروحة) زي شريط التكييف الأصلي بتاعك بالظبط. لكن لا يوجد API عام في أندرويد يقرأ أو يغيّر حرارة/مروحة التكييف الحقيقية، فالأرقام الظاهرة في الودجت هي بس اللي إحنا سجّلناها محلياً (مش القيمة الحقيقية في العربية) - إلا لو حطيت اسم الـ broadcast الصحيح لكل زرار تحت، وهيبقى فعلاً بيتحكم في السيارة.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Text(
                        "إزاي تلاقي اسم الـ broadcast الصحيح: وصّل الشاشة بلابتوب عن طريق adb، شغّل \"adb logcat\" وانت بتدوس على أزرار التكييف الأصلية في السيارة، وشوف أي Intent/Action بيظهر في السجل وقت الضغط - انسخه هنا. التفاصيل في README.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    var tempUp by remember { mutableStateOf(prefs.hvacTempUpAction ?: "") }
                    var tempDown by remember { mutableStateOf(prefs.hvacTempDownAction ?: "") }
                    var autoAction by remember { mutableStateOf(prefs.hvacAutoToggleAction ?: "") }
                    var offAction by remember { mutableStateOf(prefs.hvacOffAction ?: "") }
                    var ventAction by remember { mutableStateOf(prefs.hvacVentAction ?: "") }
                    var powerAction by remember { mutableStateOf(prefs.hvacPowerToggleAction ?: "") }
                    var defrostAction by remember { mutableStateOf(prefs.hvacDefrostToggleAction ?: "") }
                    var recircAction by remember { mutableStateOf(prefs.hvacRecircToggleAction ?: "") }
                    var fanUp by remember { mutableStateOf(prefs.hvacFanUpAction ?: "") }
                    var fanDown by remember { mutableStateOf(prefs.hvacFanDownAction ?: "") }

                    HvacActionField("Broadcast: تبريد أكثر ▲", tempUp, { tempUp = it; prefs.hvacTempUpAction = it })
                    HvacActionField("Broadcast: تبريد أقل ▼", tempDown, { tempDown = it; prefs.hvacTempDownAction = it })
                    HvacActionField("Broadcast: وضع AUTO", autoAction, { autoAction = it; prefs.hvacAutoToggleAction = it })
                    HvacActionField("Broadcast: وضع OFF", offAction, { offAction = it; prefs.hvacOffAction = it })
                    HvacActionField("Broadcast: اتجاه الهواء 💨", ventAction, { ventAction = it; prefs.hvacVentAction = it })
                    HvacActionField("Broadcast: تشغيل/إيقاف التكييف ⏻", powerAction, { powerAction = it; prefs.hvacPowerToggleAction = it })
                    HvacActionField("Broadcast: إزالة الضباب ❄", defrostAction, { defrostAction = it; prefs.hvacDefrostToggleAction = it })
                    HvacActionField("Broadcast: تدوير الهواء ↻", recircAction, { recircAction = it; prefs.hvacRecircToggleAction = it })
                    HvacActionField("Broadcast: مروحة أسرع ▲", fanUp, { fanUp = it; prefs.hvacFanUpAction = it })
                    HvacActionField("Broadcast: مروحة أبطأ ▼", fanDown, { fanDown = it; prefs.hvacFanDownAction = it })

                    Text(
                        "اختياري: شاشة/تطبيق تكييف منفصل تقدر تفتحه بالكامل (غير الودجت نفسه).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    OutlinedButton(
                        onClick = { showHvacAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val current = prefs.hvacTargetApp?.substringBefore("/")
                        Text(if (current != null) "شاشة التكييف الكاملة: $current" else "اختر شاشة/تطبيق تكييف كامل (اختياري)")
                    }
                }
            }
        }

        item { SectionTitle("الأدوات المدمجة") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "لا يوجد متجر Google Play على هذه الشاشة - استخدم المتصفح المدمج لتنزيل أي APK (مثل تطبيق خرائط)، ثم مدير الملفات لتثبيته.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    BuiltInToolRow("مدير الملفات وتثبيت التطبيقات", onOpenFileManager) {
                        PermissionRow(
                            title = "إذن الوصول لكل الملفات",
                            granted = PermissionUtils.hasAllFilesAccess(context),
                            onClick = { context.startActivity(PermissionUtils.manageAllFilesIntent(context)) }
                        )
                    }
                    BuiltInToolRow("المتصفح", onOpenBrowser, null)
                    BuiltInToolRow("مشغل الموسيقى", onOpenMusicPlayer, null)
                    BuiltInToolRow("مشغل الفيديو", onOpenVideoPlayer, null)
                }
            }
        }

        item { SectionTitle("قفل التطبيقات") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    var pin by remember { mutableStateOf("") }
                    Text(if (prefs.appLockPin != null) "تم تعيين رمز قفل" else "لم يتم تعيين رمز قفل بعد")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it.filter { c -> c.isDigit() } },
                        label = { Text("رمز مكوّن من 4 أرقام") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (pin.length >= 4) prefs.appLockPin = pin },
                            enabled = pin.length >= 4
                        ) { Text("حفظ الرمز") }
                        OutlinedButton(onClick = { prefs.appLockPin = null; prefs.lockedApps = emptySet() }) {
                            Text("إزالة القفل من الكل")
                        }
                    }
                }
            }
        }

        item { SectionTitle("التطبيقات المخفية") }
        item {
            val hiddenApps = viewModel.allApps.filter { it.key in prefs.hiddenApps }
            if (hiddenApps.isEmpty()) {
                Text("لا توجد تطبيقات مخفية", modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        hiddenApps.forEach { app ->
                            Row(
                                Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.label)
                                OutlinedButton(onClick = { viewModel.toggleHidden(app.key) }) { Text("إظهار") }
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("الأذونات والنظام") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    PermissionRow(
                        title = "التطبيق الافتراضي للشاشة الرئيسية",
                        granted = DefaultLauncherUtil.isDefaultLauncher(context),
                        onClick = { context.startActivity(DefaultLauncherUtil.requestDefaultLauncherIntent(context)) }
                    )
                    PermissionRow(
                        title = "العرض فوق التطبيقات الأخرى (للأدوات العائمة)",
                        granted = PermissionUtils.canDrawOverlays(context),
                        onClick = { context.startActivity(PermissionUtils.overlayPermissionIntent(context)) }
                    )
                    PermissionRow(
                        title = "تعديل إعدادات النظام (للسطوع)",
                        granted = PermissionUtils.canWriteSettings(context),
                        onClick = { context.startActivity(PermissionUtils.writeSettingsPermissionIntent(context)) }
                    )
                    PermissionRow(
                        title = "الوصول للإشعارات (للشارات ومشغل الوسائط)",
                        granted = PermissionUtils.isNotificationListenerEnabled(context, NotificationBadgeService::class.java),
                        onClick = { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) }
                    )
                }
            }
        }

        item {
            Text(
                "لانشر شاين - Dongfeng Shine",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }

    if (showReverseCameraPicker) {
        AppPickerDialog(
            title = "اختر تطبيق الكاميرا",
            apps = viewModel.allApps,
            onDismiss = { showReverseCameraPicker = false },
            onPick = { app ->
                prefs.reverseCameraTargetApp = app.key
                showReverseCameraPicker = false
            }
        )
    }

    if (showHvacAppPicker) {
        AppPickerDialog(
            title = "اختر شاشة/تطبيق التكييف",
            apps = viewModel.allApps,
            onDismiss = { showHvacAppPicker = false },
            onPick = { app ->
                prefs.hvacTargetApp = app.key
                showHvacAppPicker = false
            }
        )
    }

    if (showGpsAppPicker) {
        AppPickerDialog(
            title = "اختر تطبيق الملاحة",
            apps = viewModel.allApps,
            onDismiss = { showGpsAppPicker = false },
            onPick = { app ->
                prefs.gpsTargetApp = app.key
                showGpsAppPicker = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.fillMaxWidth(0.75f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HvacActionField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

@Composable
private fun BuiltInToolRow(title: String, onOpen: () -> Unit, extra: (@Composable () -> Unit)?) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.fillMaxWidth(0.7f))
            OutlinedButton(onClick = onOpen) { Text("فتح") }
        }
        extra?.invoke()
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.fillMaxWidth(0.7f))
        if (granted) {
            Icon(Icons.Filled.Check, contentDescription = "مفعّل", tint = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onClick) { Text("تفعيل") }
        }
    }
}

package com.liberium.blelab

import android.Manifest
import android.bluetooth.BluetoothManager
import android.hardware.ConsumerIrManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.UUID

private val Bg = Color(0xFF080B13)
private val Panel = Color(0xFF111827)
private val Panel2 = Color(0xFF172235)
private val Cyan = Color(0xFF55D6FF)
private val Violet = Color(0xFF9A7BFF)
private val Green = Color(0xFF50E3B0)
private val Muted = Color(0xFF91A0B8)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var irManager: ConsumerIrManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        scanner = adapter?.bluetoothLeScanner
        advertiser = adapter?.bluetoothLeAdvertiser
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        setContent {
            val devices = remember { mutableStateListOf<DeviceRow>() }
            var scanning by remember { mutableStateOf(false) }
            var advertising by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Готов к работе") }
            var selected by remember { mutableStateOf("Обзор") }
            var selectedPreset by remember { mutableIntStateOf(0) }

            val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val ok = if (Build.VERSION.SDK_INT >= 31) grants[Manifest.permission.BLUETOOTH_SCAN] == true else grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                if (ok) beginScan(devices) { scanning = it; status = if (it) "Поиск устройств…" else "Сканирование остановлено" }
                else status = "Нет разрешения на BLE-сканирование"
            }
            val advertisePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val ok = Build.VERSION.SDK_INT < 31 || grants[Manifest.permission.BLUETOOTH_ADVERTISE] == true
                if (ok) beginAdvertising(selectedPreset) { active, message -> advertising = active; status = message }
                else status = "Нет разрешения на BLE-трансляцию"
            }
            DisposableEffect(Unit) {
                onDispose { endScan(); endAdvertising() }
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
                Scaffold(
                    containerColor = Bg,
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {}
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        NavigationRail(containerColor = Color(0xFF0D1421)) {
                            Box(Modifier.padding(vertical = 10.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Cyan, Violet))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bluetooth, null, tint = Bg) }
                            listOf(NavItem("Обзор", Icons.Rounded.Dashboard), NavItem("Сканер", Icons.Rounded.Radar), NavItem("Библиотека", Icons.Rounded.Memory), NavItem("Лаборатория", Icons.Rounded.Tune), NavItem("Пульт", Icons.Rounded.Tv), NavItem("Точка доступа", Icons.Rounded.WifiTethering)).forEach { tab ->
                                NavigationRailItem(selected == tab.name, { selected = tab.name }, icon = { Icon(tab.icon, null) }, label = { Text(tab.name, fontSize = 9.sp) }, alwaysShowLabel = true)
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Header(status)
                            AnimatedContent(targetState = selected, label = "screen") { screen ->
                            when (screen) {
                                "Сканер" -> ScannerScreen(devices, scanning,
                                    onScan = { if (hasScanPermission()) beginScan(devices) { scanning = it; status = if (it) "Поиск устройств…" else "Сканирование остановлено" } else scanPermission.launch(scanPermissions()) },
                                    onStop = { endScan(); scanning = false; status = "Сканирование остановлено" })
                                "Библиотека" -> LibraryScreen(selectedPreset) { selectedPreset = it; selected = "Лаборатория" }
                                "Лаборатория" -> LabScreen(selectedPreset, { selectedPreset = it }, advertising,
                                    onStart = { if (hasAdvertisePermission()) beginAdvertising(selectedPreset) { active, msg -> advertising = active; status = msg } else advertisePermission.launch(advertisePermissions()) },
                                    onStop = { endAdvertising(); advertising = false; status = "Трансляция остановлена" })
                                "Пульт" -> RemoteScreen(irManager?.hasIrEmitter() == true) { brand, cmd -> sendIr(brand, cmd) { status = it } }
                                "Точка доступа" -> HotspotScreen(onOpenSettings = { openHotspotSettings { status = it } })
                                else -> DashboardScreen(devices.size, scanning, advertising,
                                    onScan = { selected = "Сканер"; if (!hasScanPermission()) scanPermission.launch(scanPermissions()) else beginScan(devices) { scanning = it; status = if (it) "Поиск устройств…" else "Сканирование остановлено" } },
                                    onLab = { selected = "Лаборатория" })
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    private fun openHotspotSettings(result: (String) -> Unit) {
        val intents = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        val intent = intents.firstOrNull { it.resolveActivity(packageManager) != null }
        if (intent == null) {
            result("Открой настройки Android → Точка доступа и модем")
            return
        }
        try {
            startActivity(intent)
            result("Открыты системные настройки точки доступа")
        } catch (_: Exception) {
            result("Не удалось открыть настройки. Перейди в Настройки Android → Точка доступа и модем")
        }
    }

    private fun scanPermissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun advertisePermissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    private fun hasScanPermission() = if (Build.VERSION.SDK_INT >= 31) ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED else ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasAdvertisePermission() = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    private fun beginScan(rows: MutableList<DeviceRow>, state: (Boolean) -> Unit) {
        if (!hasScanPermission()) return
        val bleScanner = scanner ?: run { state(false); return }
        endScan()
        rows.clear()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name ?: result.scanRecord?.deviceName ?: "Без имени" } catch (_: SecurityException) { "BLE-устройство" }
                val address = try { result.device.address } catch (_: SecurityException) { "адрес скрыт" }
                val item = DeviceRow(name, address, result.rssi, result.scanRecord?.bytes?.size ?: 0)
                val old = rows.indexOfFirst { it.address == address }
                if (old >= 0) rows[old] = item else rows.add(item)
            }
            override fun onScanFailed(errorCode: Int) { state(false) }
        }
        try {
            bleScanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            state(true)
        } catch (_: SecurityException) { state(false) }
    }
    private fun endScan() {
        try { scanCallback?.let { scanner?.stopScan(it) } } catch (_: Exception) { }
        scanCallback = null
    }

    private fun beginAdvertising(index: Int, state: (Boolean, String) -> Unit) {
        val bleAdvertiser = advertiser
        if (bleAdvertiser == null) { state(false, "BLE advertising не поддерживается телефоном"); return }
        endAdvertising()
        val preset = profiles[index.coerceIn(profiles.indices)]
        val uuid = ParcelUuid(UUID.fromString(preset.uuid))
        val payload = AdvertiseData.Builder().addServiceUuid(uuid).addServiceData(uuid, preset.payload).setIncludeDeviceName(false).build()
        val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW).setConnectable(false).setTimeout(30_000).build()
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { state(true, "Тестовый BLE-маяк активен · максимум 30 секунд") }
            override fun onStartFailure(errorCode: Int) { state(false, "BLE запуск не удался · код $errorCode") }
        }
        try { bleAdvertiser.startAdvertising(settings, payload, advertiseCallback) }
        catch (_: SecurityException) { state(false, "Нет разрешения на BLE-трансляцию") }
        catch (_: IllegalArgumentException) { state(false, "Пакет не поддерживается Bluetooth-контроллером") }
    }
    private fun endAdvertising() {
        try { advertiseCallback?.let { advertiser?.stopAdvertising(it) } } catch (_: Exception) { }
        advertiseCallback = null
    }

    private fun sendIr(brand: String, command: String, result: (String) -> Unit) {
        val ir = irManager
        if (ir == null || !ir.hasIrEmitter()) { result("Нет ИК-передатчика. Нужен встроенный ИК-порт или внешний адаптер; Wi-Fi телевизору не требуется."); return }
        val code = when (brand) {
            "Samsung" -> when (command) { "power" -> 0xE0E040BF.toInt(); "volume_up" -> 0xE0E0E01F.toInt(); "volume_down" -> 0xE0E0D02F.toInt(); "mute" -> 0xE0E0F00F.toInt(); else -> null }
            "LG" -> when (command) { "power" -> 0x20DF10EF; "volume_up" -> 0x20DF40BF; "volume_down" -> 0x20DFC03F; "mute" -> 0x20DF906F; else -> null }
            else -> null
        }
        if (code == null) { result("Для этой марки пока нет проверенного ИК-кода."); return }
        val pattern = mutableListOf(9000, 4500)
        for (bit in 0 until 32) { pattern.add(560); pattern.add(if (((code ushr bit) and 1) == 1) 1690 else 560) }
        pattern.add(560)
        try { ir.transmit(38000, pattern.toIntArray()); result("$brand · ИК-команда отправлена") }
        catch (e: Exception) { result("Ошибка ИК-передачи: ${e.message ?: "unknown"}") }
    }
}

private data class DeviceRow(val name: String, val address: String, val rssi: Int, val bytes: Int)
private data class NavItem(val name: String, val icon: ImageVector)
private data class Profile(val name: String, val category: String, val description: String, val uuid: String, val payload: ByteArray)
private val profiles = listOf(
    Profile("Тестовый маяк", "BASIC", "Демонстрационный BLE advertising с тестовым UUID.", "7f510001-1b15-4f0d-9a12-123456789001", byteArrayOf(0x4C, 0x42, 0x4C, 0x01)),
    Profile("Датчик температуры", "SENSOR", "Учебный пакет телеметрии с условным значением температуры.", "7f510002-1b15-4f0d-9a12-123456789002", byteArrayOf(0x19, 0x00, 0x64)),
    Profile("Лабораторный узел", "LAB", "Идентификатор собственного лабораторного BLE-узла.", "7f510003-1b15-4f0d-9a12-123456789003", byteArrayOf(0x4C, 0x41, 0x42, 0x00, 0x01))
)

@Composable private fun Header(status: String) {
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF111B2C), Color(0xFF17152D)))).padding(horizontal = 20.dp, vertical = 17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Cyan, Violet))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bluetooth, null, tint = Bg, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LIBERIUM", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 18.sp)
                Text("BLE LAB  /  ANDROID TOOLKIT", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp)
            }
            Box(Modifier.clip(CircleShape).background(Color(0x223FE5B1)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text("● READY", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(13.dp))
        Text(status, color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun DashboardScreen(count: Int, scanning: Boolean, advertising: Boolean, onScan: () -> Unit, onLab: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Spacer(Modifier.height(3.dp))
            Text("Контрольная панель", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Инструменты анализа Bluetooth Low Energy", color = Muted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF172C42), Color(0xFF211A3B)))).padding(18.dp)) {
                    Text("BLE ENVIRONMENT", color = Cyan, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Исследуй эфир.\nИзучай протокол.", color = Color.White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Устройства", count.toString()); Metric("Сканер", if (scanning) "LIVE" else "IDLE"); Metric("TX", if (advertising) "ON" else "OFF") }
                }
            }
        }
        item { SectionTitle("БЫСТРЫЙ ДОСТУП") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("BLE Scanner", "Поиск устройств", Icons.Rounded.Radar, Modifier.weight(1f), onScan)
            ActionCard("Packet Lab", "Тестовые пакеты", Icons.Rounded.Memory, Modifier.weight(1f), onLab)
        } }
        item { SectionTitle("ВОЗМОЖНОСТИ") }
        item { FeatureRow(Icons.Rounded.BluetoothSearching, "Live Scanner", "RSSI, имя, адрес и размер advertising-записи", Cyan) }
        item { FeatureRow(Icons.Rounded.Memory, "Packet Library", "Готовые учебные BLE-профили и UUID", Violet) }
        item { FeatureRow(Icons.Rounded.GraphicEq, "Advertising Lab", "Тестовая трансляция с автоостановкой", Green) }
        item { FeatureRow(Icons.Rounded.Hub, "GATT Explorer", "Следующий этап: сервисы и характеристики", Color(0xFFFFC66D)) }
        item { Text("Возможности зависят от Bluetooth-контроллера телефона.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

@Composable private fun ScannerScreen(devices: List<DeviceRow>, scanning: Boolean, onScan: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE Scanner", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Устройства поблизости и их advertising-данные", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScan, enabled = !scanning, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)) { Text("Начать поиск") }
            OutlinedButton(onClick = onStop, enabled = scanning) { Icon(Icons.Rounded.Stop, null); Text("Стоп") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("НАЙДЕНО: ${devices.size}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(if (scanning) "● SCANNING" else "○ IDLE", color = if (scanning) Green else Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Radar, null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Пока ничего не найдено", color = Muted)
                    Text("Запусти поиск рядом с BLE-устройствами", color = Muted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.address }) { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Panel2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bluetooth, null, tint = Cyan) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(device.address, color = Muted, fontSize = 10.sp)
                                Text("${device.bytes} bytes advertising", color = Muted, fontSize = 10.sp)
                            }
                            Text("${device.rssi} dBm", color = if (device.rssi > -65) Green else Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun LibraryScreen(selected: Int, onSelect: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Text("Packet Library", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Встроенные безопасные профили BLE advertising", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }
        items(profiles.indices.toList()) { index ->
            val profile = profiles[index]
            Card(Modifier.fillMaxWidth().clickable { onSelect(index) }, colors = CardDefaults.cardColors(containerColor = if (selected == index) Color(0xFF1B2940) else Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.category, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.weight(1f))
                        if (selected == index) Text("ВЫБРАН", color = Green, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(profile.description, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("UUID  ${profile.uuid}", color = Color(0xFFB6A8FF), fontSize = 9.sp)
                    Text("DATA  ${profile.payload.joinToString(" ") { "%02X".format(it) }}", color = Cyan, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onSelect(index) }) { Text("Использовать профиль") }
                }
            }
        }
        item { Text("Профили встроены в приложение: интернет для них не нужен.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable private fun LabScreen(selected: Int, onSelect: (Int) -> Unit, advertising: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Advertising Lab", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Запуск собственного тестового BLE-маяка", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(15.dp))
        Text("ВЫБЕРИ ПРОФИЛЬ", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(8.dp))
        profiles.forEachIndexed { index, profile ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(enabled = !advertising) { onSelect(index) }, colors = CardDefaults.cardColors(containerColor = if (selected == index) Color(0xFF1B2940) else Panel), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(profile.name, color = Color.White, fontWeight = FontWeight.SemiBold); Text(profile.category, color = Muted, fontSize = 10.sp) }
                    if (selected == index) Text("✓", color = Green, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Параметры передачи", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• Низкая мощность передачи", color = Muted, fontSize = 12.sp)
                Text("• Сбалансированный интервал", color = Muted, fontSize = 12.sp)
                Text("• Автоостановка через 30 секунд", color = Muted, fontSize = 12.sp)
                Text("• Неконнектируемый тестовый маяк", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(15.dp))
        if (!advertising) Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Rounded.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("ЗАПУСТИТЬ ТЕСТОВЫЙ МАЯК", fontWeight = FontWeight.Bold)
        } else Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF647C)), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("ОСТАНОВИТЬ ПЕРЕДАЧУ", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        Text("Передача ограничена 30 секундами и предназначена для проверки своих устройств.", color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun Metric(label: String, value: String) {
    Column(Modifier.width(88.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x332A405B)).padding(10.dp)) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = Muted, fontSize = 9.sp)
    }
}
@Composable private fun ActionCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(13.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}
@Composable private fun FeatureRow(icon: ImageVector, title: String, description: String, tint: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
        Spacer(Modifier.width(11.dp))
        Column { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(description, color = Muted, fontSize = 10.sp) }
    }
}
@Composable private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}


@Composable private fun RemoteScreen(irAvailable: Boolean, onCommand: (String, String) -> Unit) {
    var brand by remember { mutableStateOf("Samsung") }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Универсальный пульт", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Управление по ИК — телевизору не нужен Wi-Fi", color = Muted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("МАРКА УСТРОЙСТВА", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(brand, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Samsung", "LG", "Sony", "Philips", "Xiaomi", "Hisense", "TCL", "Проектор").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { brand = it; expanded = false }) }
                    }
                }
                Text(if (irAvailable) "ИК-передатчик обнаружен" else "ИК-передатчик не обнаружен", color = if (irAvailable) Green else Color(0xFFFFC66D), fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LIBERIUM REMOTE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Button(onClick = { onCommand(brand, "power") }, modifier = Modifier.size(72.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB53B59))) { Icon(Icons.Rounded.PowerSettingsNew, null, modifier = Modifier.size(30.dp)) }
                Text("ПИТАНИЕ", color = Muted, fontSize = 9.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { RemoteButton("+", "ГРОМЧЕ") { onCommand(brand, "volume_up") }; Spacer(Modifier.height(8.dp)); RemoteButton("−", "ТИШЕ") { onCommand(brand, "volume_down") } }
                    Column(Modifier.weight(1f)) { RemoteButton("M", "MUTE") { onCommand(brand, "mute") }; Spacer(Modifier.height(8.dp)); RemoteButton("↗", "SOURCE") { onCommand(brand, "source") } }
                }
            }
        }
        Text("Полностью офлайн: команды идут по ИК прямо на телевизор или проектор. Нужен встроенный ИК-передатчик телефона либо внешний адаптер. Базовые коды сейчас предусмотрены для Samsung и LG; остальные марки требуют проверенных кодов конкретных моделей.", color = Muted, fontSize = 11.sp)
    }
}
@Composable private fun RemoteButton(symbol: String, caption: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel2)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(caption, fontSize = 8.sp, color = Muted) }
    }
}


@Composable private fun HotspotScreen(onOpenSettings: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text("Точка доступа", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Управление раздачей интернета с этого Android-устройства", color = Muted, fontSize = 12.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF173348), Color(0xFF211A3B)))
                    ).padding(17.dp)
                ) {
                    Icon(Icons.Rounded.WifiTethering, null, tint = Cyan, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("HOTSPOT CONTROL", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Раздавай интернет
под своим контролем.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Включение и настройка выполняются системными средствами Android. Так приложение не обходит ограничения производителя и оператора.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Rounded.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("ОТКРЫТЬ НАСТРОЙКИ ТОЧКИ ДОСТУПА", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionTitle("ИНСТРУМЕНТЫ ВЛАДЕЛЬЦА") }
        item {
            FeatureRow(Icons.Rounded.Devices, "Подключённые устройства", "Список клиентов зависит от версии Android и прошивки. В этой версии не показываем выдуманные данные.", Cyan)
        }
        item {
            FeatureRow(Icons.Rounded.QueryStats, "Статистика трафика", "Можно учитывать общий расход сети там, где Android предоставляет системную статистику; содержимое соединений недоступно.", Violet)
        }
        item {
            FeatureRow(Icons.Rounded.History, "Журнал событий", "В дальнейшем: включение точки доступа, локальные события приложения и диагностика соединения.", Green)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1D2A)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Защита приватности", color = Color(0xFFFFB4C2), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Liberium не перехватывает ввод с клавиатуры, пароли, коды подтверждения, переписки или данные входа в соцсети. HTTPS и шифрование приложений защищают содержимое трафика. Для проверки своих устройств можно использовать отдельный тестовый стенд с демонстрационными данными.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("Важно: Android не предоставляет обычному приложению универсальный доступ к содержимому трафика клиентов хотспота. Реальные возможности управления зависят от модели телефона, версии Android и оператора.", color = Muted, fontSize = 11.sp)
        }
    }
}

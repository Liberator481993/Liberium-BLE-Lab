package com.liberium.blelab

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AutoBg = Color(0xFF080B13)
private val AutoPanel = Color(0xFF111827)
private val AutoCyan = Color(0xFF55D6FF)
private val AutoGreen = Color(0xFF50E3B0)
private val AutoMuted = Color(0xFF91A0B8)

private data class AutoBleDevice(
    val key: String,
    val name: String,
    val addressHint: String,
    val rssi: Int,
    val uuids: List<String>,
    val manufacturerIds: List<Int>,
    val bytes: Int
) {
    fun category(): String {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.contains("airpod") || n.contains("buds") || n.contains("headphone") -> "Аудиоаксессуар"
            n.contains("watch") || n.contains("band") || n.contains("fit") -> "Носимое устройство"
            n.contains("iphone") || n.contains("pixel") || n.contains("galaxy") || n.contains("phone") -> "Вероятно, телефон"
            n.contains("beacon") || n.contains("ibeacon") || n.contains("tag") -> "Маяк / метка"
            n.startsWith("liberium") || uuids.any { it.startsWith("7f5100") } -> "Тестовый узел Liberium"
            else -> "Неизвестное BLE-устройство"
        }
    }
    fun compatibility(): String = when {
        uuids.any { it.startsWith("7f5100") } -> "Тестовый профиль Liberium обнаружен"
        uuids.isNotEmpty() -> "BLE-реклама и UUID служб обнаружены"
        bytes > 0 -> "BLE-реклама обнаружена; UUID служб не объявлены"
        else -> "Недостаточно данных"
    }
}

class AutoScannerActivity : ComponentActivity() {
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scanner = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner
        val prefs = getSharedPreferences("auto_scanner", MODE_PRIVATE)
        setContent {
            val found = remember { mutableStateListOf<AutoBleDevice>() }
            val events = remember { mutableStateListOf<String>().apply {
                addAll(prefs.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(60))
            } }
            var running by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Готов к автоматическому сканированию") }
            var tab by remember { mutableIntStateOf(0) }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val ok = if (Build.VERSION.SDK_INT >= 31) grants[Manifest.permission.BLUETOOTH_SCAN] == true && grants[Manifest.permission.BLUETOOTH_CONNECT] == true else grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                status = if (ok) "Разрешение выдано · нажми АВТОСКАН ещё раз" else "Разрешение Bluetooth не выдано"
            }

            fun log(message: String) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                events.add(0, time + "  " + message)
                while (events.size > 60) events.removeAt(events.lastIndex)
                prefs.edit().putString("events", events.joinToString("\n")).apply()
            }
            fun stopScan() {
                timeout?.let(handler::removeCallbacks)
                timeout = null
                try { scanCallback?.let { scanner?.stopScan(it) } } catch (_: Exception) { }
                scanCallback = null
                running = false
            }
            fun startScan() {
                val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (permissions.any { ContextCompat.checkSelfPermission(this@AutoScannerActivity, it) != PackageManager.PERMISSION_GRANTED }) {
                    status = "Запрашиваю разрешение Bluetooth…"
                    permissionLauncher.launch(permissions)
                    return
                }
                if (scanner == null) {
                    status = "BLE-сканер недоступен. Проверь Bluetooth."
                    log("Ошибка: BLE-сканер недоступен")
                    return
                }
                stopScan()
                found.clear()
                status = "Автоматический поиск · максимум 45 секунд"
                log("Сканирование запущено")
                scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val record = result.scanRecord
                        val rawName = try { record?.deviceName ?: result.device.name } catch (_: SecurityException) { null }
                        val name = rawName?.takeIf { it.isNotBlank() } ?: "Без имени"
                        val address = try { result.device.address } catch (_: SecurityException) { "скрыт" }
                        val uuids = record?.serviceUuids?.map { it.uuid.toString().lowercase(Locale.ROOT) }.orEmpty()
                        val manufacturer = record?.manufacturerSpecificData
                        val ids = if (manufacturer == null) emptyList() else (0 until manufacturer.size()).map { manufacturer.keyAt(it) }
                        val key = if (address == "скрыт") name + "|" + uuids.joinToString() else address
                        val item = AutoBleDevice(key, name, if (address == "скрыт") "MAC скрыт системой" else "…" + address.takeLast(5), result.rssi, uuids, ids, record?.bytes?.size ?: 0)
                        val index = found.indexOfFirst { it.key == key }
                        if (index >= 0) found[index] = item else {
                            found.add(0, item)
                            log("Обнаружено: " + name + " · " + item.category())
                        }
                    }
                    override fun onScanFailed(errorCode: Int) {
                        status = "Ошибка сканирования · код " + errorCode
                        log("Ошибка сканирования: " + errorCode)
                        stopScan()
                    }
                }
                try {
                    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
                    running = true
                    timeout = Runnable {
                        stopScan()
                        status = "Автосканирование завершено"
                        log("Сканирование завершено · найдено " + found.size)
                    }.also { handler.postDelayed(it, 45_000L) }
                } catch (e: Exception) {
                    status = "Не удалось запустить BLE-сканер"
                    log("Ошибка запуска BLE-сканера")
                    stopScan()
                }
            }

            DisposableEffect(Unit) { onDispose { stopScan() } }
            MaterialTheme(colorScheme = darkColorScheme(primary = AutoCyan, background = AutoBg, surface = AutoPanel)) {
                Scaffold(containerColor = AutoBg, topBar = {
                    TopAppBar(
                        title = { Column { Text("AUTO SCANNER", fontWeight = FontWeight.Black); Text("LIBERIUM BLE LAB", fontSize = 10.sp, color = AutoMuted) } },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Rounded.ArrowBack, null) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AutoBg, titleContentColor = Color.White, navigationIconContentColor = Color.White)
                    )
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = AutoPanel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Radar, null, tint = AutoCyan, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (running) "СКАНИРОВАНИЕ АКТИВНО" else "АВТОМАТИЧЕСКИЙ АНАЛИЗ", color = AutoCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(status, color = AutoMuted, fontSize = 11.sp)
                                    }
                                    Text(found.size.toString(), color = AutoGreen, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { startScan() }, enabled = !running, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AutoCyan, contentColor = AutoBg)) {
                                        Icon(Icons.Rounded.PlayArrow, null); Text("АВТОСКАН")
                                    }
                                    OutlinedButton(onClick = {
                                        stopScan()
                                        status = "Сканирование остановлено"
                                        log("Сканирование остановлено вручную")
                                    }, enabled = running, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.Stop, null); Text("СТОП")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TabRow(selectedTabIndex = tab, containerColor = AutoBg, contentColor = AutoCyan) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Устройства") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Журнал") })
                            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("О тесте") })
                        }
                        when (tab) {
                            0 -> if (found.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.BluetoothSearching, null, tint = AutoMuted, modifier = Modifier.size(44.dp))
                                        Text("Пока устройств нет", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Нажми АВТОСКАН рядом со своими BLE-устройствами", color = AutoMuted, fontSize = 11.sp)
                                    }
                                }
                            } else LazyColumn(contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(found, key = { it.key }) { device ->
                                    Card(colors = CardDefaults.cardColors(containerColor = AutoPanel), shape = RoundedCornerShape(15.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(device.name, color = Color.White, fontWeight = FontWeight.Bold)
                                                    Text(device.category(), color = AutoCyan, fontSize = 10.sp)
                                                }
                                                Text(device.rssi.toString() + " dBm", color = if (device.rssi > -65) AutoGreen else AutoMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Spacer(Modifier.height(7.dp))
                                            Text(device.compatibility(), color = AutoGreen, fontSize = 11.sp)
                                            Text(device.addressHint + " · " + device.bytes + " байт · manufacturer ID: " + device.manufacturerIds.joinToString().ifBlank { "нет данных" }, color = AutoMuted, fontSize = 9.sp)
                                            if (device.uuids.isNotEmpty()) Text("UUID: " + device.uuids.take(4).joinToString(), color = AutoMuted, fontSize = 9.sp)
                                            Text("Уязвимость не проверялась — только анализ открытых BLE-данных", color = AutoMuted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                            1 -> Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("СОБЫТИЯ · " + events.size, color = AutoCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { events.clear(); prefs.edit().remove("events").apply() }) { Text("Очистить") }
                                }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                                    items(events) { event -> Text(event, color = AutoMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AutoPanel).padding(10.dp)) }
                                }
                            }
                            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                                item { InfoCard("Что анализируется", "Имя, RSSI, размер advertising-пакета, объявленные UUID служб и идентификаторы производителя.") }
                                item { InfoCard("Классификация", "Тип устройства — предположение по имени и UUID. Точную модель по BLE-рекламе определить удаётся не всегда.") }
                                item { InfoCard("Совместимость", "Показываются только реально доступные признаки BLE. Это не проверка уязвимости и не тестирование защищённых функций.") }
                                item { InfoCard("Свои устройства", "На втором телефоне открой «Лаборатория» и запусти профиль Liberium. AUTO SCANNER отметит его по тестовому UUID, если он попал в рекламу.") }
                                item { InfoCard("Ограничения", "Android может скрывать MAC и имена. GATT-проверка требует отдельного подключения с разрешения владельца; этот сканер не подключается к найденным устройствам.") }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try { scanCallback?.let { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) { }
        timeout?.let(handler::removeCallbacks)
        super.onDestroy()
    }
}

@Composable private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = AutoPanel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, color = AutoCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(body, color = AutoMuted, fontSize = 11.sp)
        }
    }
}

package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BleConnectionStatus
import com.example.data.model.HealthConnectState
import com.example.data.model.UiState
import com.example.hardware.HealthConnectManager
import com.example.hardware.HuaweiBleManager
import com.example.ui.MainViewModel
import com.example.ui.components.MetricCell
import com.example.ui.components.SleepStagesBar
import com.example.ui.theme.AmberZone
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WearablesScreen(viewModel: MainViewModel) {
    val ble by viewModel.bleState.collectAsStateWithLifecycle()
    val hc by viewModel.healthConnectState.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val healthLogsState by viewModel.healthLogsState.collectAsStateWithLifecycle()
    val isBandSyncing by viewModel.isBandSyncing.collectAsStateWithLifecycle()

    val savedDevices = (devicesState as? UiState.Success)?.data.orEmpty()
    val latestLog = (healthLogsState as? UiState.Success)?.data?.firstOrNull()

    val blePerms = remember { HuaweiBleManager.requiredPermissions() }
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.bleManager.refreshAdapterState()
        if (viewModel.bleManager.hasBlePermissions()) {
            viewModel.bleManager.startScan()
        }
    }

    val hcPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract()
        ) {
            viewModel.syncHealthConnectAndPersist()
        }
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Native HUAWEI Band 11 Pro Pairing Hero
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_band_huawei_guide),
                            contentDescription = "HUAWEI Band 11 Pro BLE and Health Connect pairing guide",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xD90C0F14))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "NATIVE WEARABLE BRIDGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCyan
                            )
                            Text(
                                text = "HUAWEI Band 11 Pro",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Setup Checklist for Real-Time Sync:", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "1. On HUAWEI Band 11 Pro: Settings -> Workout -> Heart Rate Broadcast -> ON for real-time BLE GATT BPM, HRV & Running Cadence.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "2. In Huawei Health app: Me -> Privacy Management -> Data Sharing -> enable Health Connect for daily Steps, Calories & Sleep Stages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. BLE GATT Scanner & Auto-Reconnect Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (ble.status) {
                                    BleConnectionStatus.CONNECTED -> Icons.Rounded.BluetoothConnected
                                    BleConnectionStatus.SCANNING -> Icons.AutoMirrored.Rounded.BluetoothSearching
                                    else -> Icons.Rounded.Bluetooth
                                },
                                contentDescription = "BLE Status",
                                tint = when (ble.status) {
                                    BleConnectionStatus.CONNECTED -> EmeraldPeak
                                    BleConnectionStatus.SCANNING -> SignalCyan
                                    else -> VeloOrange
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = ble.connectedDeviceName ?: "BLE GATT Scanner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ble.statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Reconnect (Backoff)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = ble.autoReconnectEnabled,
                            onCheckedChange = { viewModel.bleManager.setAutoReconnect(it) },
                            modifier = Modifier.testTag("ble_auto_reconnect_switch")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (!viewModel.bleManager.hasBlePermissions()) {
                                    blePermissionLauncher.launch(blePerms)
                                } else if (ble.isScanning) {
                                    viewModel.bleManager.stopScan()
                                } else {
                                    viewModel.bleManager.startScan()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ble_scan_btn")
                        ) {
                            if (ble.isScanning) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Scan")
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (viewModel.bleManager.hasBlePermissions()) "Scan BLE Sensors" else "Grant BLE & Scan")
                            }
                        }

                        if (ble.status == BleConnectionStatus.CONNECTED || ble.status == BleConnectionStatus.CONNECTING) {
                            OutlinedButton(
                                onClick = { viewModel.bleManager.disconnect() },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("ble_disconnect_btn")
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }

        // 3. Live BLE Telemetry & GATT Inspector (when connected or streaming)
        if (ble.status == BleConnectionStatus.CONNECTED || ble.liveHeartRateBpm > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CrimsonPulse)
                                Spacer(Modifier.width(6.dp))
                                Text("Real-Time GATT Telemetry", style = MaterialTheme.typography.titleMedium)
                            }
                            if (ble.batteryPercent >= 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.BatteryChargingFull, contentDescription = "Battery", tint = EmeraldPeak, modifier = Modifier.size(18.dp))
                                    Text("${ble.batteryPercent}%", fontFamily = JetBrainsMonoFontFamily, color = EmeraldPeak)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell(
                                label = "Live HR",
                                value = if (ble.liveHeartRateBpm > 0) "${ble.liveHeartRateBpm} bpm" else "--",
                                valueColor = CrimsonPulse
                            )
                            MetricCell(
                                label = "HRV RMSSD",
                                value = if (ble.hrvRmssdMs > 0) "${ble.hrvRmssdMs} ms" else "--",
                                valueColor = SignalCyan
                            )
                            MetricCell("Cadence", if (ble.liveCadenceSpm > 0) "${ble.liveCadenceSpm} spm" else "--")
                            MetricCell("BLE Kcal", "${ble.sessionBleCalories}")
                        }

                        if (ble.discoveredServices.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ble.discoveredServices.forEach { svc ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = svc,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.syncLiveBleHealthSnapshot() },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_ble_snapshot_btn")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Live Snapshot to Health Log")
                        }
                    }
                }
            }
        }

        // 4. Discovered BLE Devices List
        if (ble.discoveredDevices.isNotEmpty()) {
            item {
                Text("Nearby BLE Peripherals (${ble.discoveredDevices.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(ble.discoveredDevices, key = { it.macAddress }) { dev ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Watch,
                                    contentDescription = null,
                                    tint = if (dev.isHuaweiDevice) VeloOrange else SignalCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(dev.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (dev.isHuaweiDevice) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = VeloOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "HUAWEI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VeloOrange,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${dev.macAddress} • ${dev.rssi} dBm • ${dev.manufacturerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.bleManager.connectToDevice(dev.macAddress, dev.isHuaweiDevice) },
                            modifier = Modifier.testTag("connect_ble_${dev.macAddress}")
                        ) {
                            Text("Pair")
                        }
                    }
                }
            }
        }

        // 5. Health Connect Real-Time Health Sync (Steps, Calories, HR, HRV, Sleep Stages)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = EmeraldPeak)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Health Connect Sync", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = hc.lastSyncMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (hc.state == HealthConnectState.AVAILABLE_NEEDS_PERMISSION && hcPermissionLauncher != null) {
                                    hcPermissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                                } else {
                                    viewModel.syncHealthConnectAndPersist()
                                }
                            },
                            enabled = hc.state != HealthConnectState.UNAVAILABLE && !isBandSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_health_connect_btn")
                        ) {
                            if (isBandSyncing) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Syncing Band...")
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (hc.state) {
                                        HealthConnectState.AVAILABLE_NEEDS_PERMISSION -> "Grant Permissions & Sync"
                                        HealthConnectState.UNAVAILABLE -> "Health Connect Unavailable"
                                        HealthConnectState.UPDATE_REQUIRED -> "Update Health Connect"
                                        else -> "Sync Band Workouts & Health"
                                    }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.syncHealthConnectAndPersist() },
                            enabled = !isBandSyncing,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("refresh_health_connect_btn")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh status")
                        }
                    }

                    val steps = maxOf(hc.stepsToday, latestLog?.steps ?: 0) + ble.sessionBleSteps
                    val activeCal = maxOf(hc.activeCaloriesToday, latestLog?.activeCalories ?: 0) + ble.sessionBleCalories
                    val rhr = hc.restingHeartRateBpm.takeIf { it > 0 } ?: (latestLog?.restingHr ?: 0)
                    val sleepMin = maxOf(hc.sleepTotalMinutes, latestLog?.sleepTotalMinutes ?: 0)
                    val spo2 = maxOf(hc.spo2Percent, latestLog?.spo2Percent ?: 0.0)
                    val dailyDistKm = maxOf(hc.distanceMetersToday, latestLog?.distanceMetersToday ?: 0.0) / 1000.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell("Steps", "$steps", valueColor = EmeraldPeak)
                        MetricCell("Active Kcal", "$activeCal", valueColor = VeloOrange)
                        MetricCell("Rest HR", if (rhr > 0) "$rhr bpm" else "--")
                        MetricCell("Sleep", if (sleepMin > 0) "${sleepMin / 60}h ${sleepMin % 60}m" else "--")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell("SpO2", if (spo2 > 0) "${spo2}%" else "--", valueColor = SignalCyan)
                        MetricCell("Dist Today", if (dailyDistKm > 0) String.format("%.2f km", dailyDistKm) else "--")
                        MetricCell("Workouts", "${hc.importedExercises.size} synced")
                        MetricCell("Elev", if (hc.elevationGainedToday > 0) "${hc.elevationGainedToday.toInt()}m" else "DEM Auto")
                    }

                    val deep = maxOf(hc.sleepDeepMinutes, latestLog?.sleepDeepMinutes ?: 0)
                    val light = maxOf(hc.sleepLightMinutes, latestLog?.sleepLightMinutes ?: 0)
                    val rem = maxOf(hc.sleepRemMinutes, latestLog?.sleepRemMinutes ?: 0)
                    val awake = maxOf(hc.sleepAwakeMinutes, latestLog?.sleepAwakeMinutes ?: 0)
                    if (deep + light + rem + awake > 0) {
                        SleepStagesBar(deepMin = deep, lightMin = light, remMin = rem, awakeMin = awake)
                    }
                }
            }
        }

        // 6. Saved Cloud Wearables
        if (savedDevices.isNotEmpty()) {
            item {
                Text("Saved Wearables (${savedDevices.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(savedDevices, key = { it.id }) { dev ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dev.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${dev.manufacturer} • ${dev.macAddress} • ${dev.lastSyncedLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.bleManager.connectToDevice(dev.macAddress, dev.huaweiNative) },
                                modifier = Modifier.testTag("reconnect_saved_${dev.id}")
                            ) {
                                Text("Connect")
                            }
                            IconButton(
                                onClick = { viewModel.deletePairedDevice(dev.id) },
                                modifier = Modifier.testTag("delete_saved_device_${dev.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove wearable", tint = AmberZone)
                            }
                        }
                    }
                }
            }
        }
    }
}

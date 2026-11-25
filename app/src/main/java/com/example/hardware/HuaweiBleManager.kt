package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.BleConnectionStatus
import com.example.data.model.BleDeviceCandidate
import com.example.data.model.BleTelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.sqrt

class HuaweiBleManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleTelemetryState())
    val state: StateFlow<BleTelemetryState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var scanTimeoutJob: Job? = null
    private var connectJob: Job? = null
    private var bondTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var servicePollJob: Job? = null
    private var userRequestedDisconnect = false
    private var reconnectAttempts = 0
    private var handshakeRetries = 0
    private var connectPlan: List<BleDiscoveryHelper.GattConnectMode> = emptyList()
    private var connectPlanIndex = 0
    private var serviceRefreshAttemptedForConnection = false
    private var authBondRequestedForConnection = false
    private var lastTargetMac: String? = null
    private var pendingBondMac: String? = null
    private var lastRscSampleMs: Long = 0L
    private var receiverRegistered = false

    private val discoveredMap = LinkedHashMap<String, BleDeviceCandidate>()
    private val recentRrIntervalsMs = ArrayDeque<Double>(32)
    private val gattOpQueue = ArrayDeque<() -> Unit>()
    private var gattOpInFlight = false

    private val systemBleReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshAdapterState()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val mac = device?.address ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val devName = runCatching { device.name }.getOrNull() ?: discoveredMap[mac]?.name ?: "BLE Wearable"

                    discoveredMap[mac]?.let { existing ->
                        discoveredMap[mac] = existing.copy(isBonded = bondState == BluetoothDevice.BOND_BONDED)
                        _state.update { it.copy(discoveredDevices = sortCandidates(discoveredMap.values)) }
                    }

                    if (pendingBondMac == mac) {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDING -> {
                                _state.update {
                                    it.copy(
                                        status = BleConnectionStatus.CONNECTING,
                                        statusMessage = "Pairing with $devName — confirm prompt on your watch/phone..."
                                    )
                                }
                            }
                            BluetoothDevice.BOND_BONDED -> {
                                pendingBondMac = null
                                bondTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        status = BleConnectionStatus.CONNECTING,
                                        statusMessage = "Paired! Refreshing GATT telemetry link with $devName..."
                                    )
                                }
                                connectJob?.cancel()
                                connectJob = scope.launch {
                                    delay(350L)
                                    val gatt = activeGatt
                                    if (gatt != null && gatt.device?.address == mac) {
                                        refreshGattCache(gatt)
                                        delay(250L)
                                        gatt.discoverServices()
                                    } else {
                                        launchConnectStep(device, connectPlanIndex)
                                    }
                                }
                            }
                            BluetoothDevice.BOND_NONE -> {
                                pendingBondMac = null
                                bondTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        status = BleConnectionStatus.CONNECTING,
                                        statusMessage = "Opening unbonded BLE GATT link to $devName..."
                                    )
                                }
                                connectJob?.cancel()
                                connectJob = scope.launch {
                                    delay(300L)
                                    launchConnectStep(device, connectPlanIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        registerBleReceiver()
    }

    private fun registerBleReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(appContext, systemBleReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }.onFailure { err ->
            Log.w(TAG, "Unable to register BLE receiver: ${err.message}")
        }
    }

    fun release() {
        scanTimeoutJob?.cancel()
        connectJob?.cancel()
        bondTimeoutJob?.cancel()
        reconnectJob?.cancel()
        servicePollJob?.cancel()
        closeActiveGattOnly()
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(systemBleReceiver) }
            receiverRegistered = false
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "HuaweiBleManager"
        private const val HUAWEI_MANUFACTURER_ID = 0x027D // 637 decimal
        private const val HONOR_MANUFACTURER_ID = 0x04D3 // 1235 decimal

        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val PLX_SERVICE: UUID = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")
        val PLX_SPOT_CHECK: UUID = UUID.fromString("00002a5e-0000-1000-8000-00805f9b34fb")
        val PLX_CONTINUOUS: UUID = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb")
        val RSC_SERVICE: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
        val RSC_MEASUREMENT: UUID = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
        val CSC_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REV: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val CCCD_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Huawei Wear / Health BLE Advertisement Service UUIDs
        val HUAWEI_WEAR_UUID_FE86: UUID = UUID.fromString("0000fe86-0000-1000-8000-00805f9b34fb")
        val HUAWEI_WEAR_UUID_3802: UUID = UUID.fromString("00003802-0000-1000-8000-00805f9b34fb")
        val HUAWEI_WEAR_UUID_FD82: UUID = UUID.fromString("0000fd82-0000-1000-8000-00805f9b34fb")
        val HUAWEI_WEAR_UUID_FEEA: UUID = UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")

        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    fun hasBlePermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshAdapterState() {
        val hasBleFeature = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val adapter = bluetoothAdapter
        when {
            !hasBleFeature || adapter == null -> {
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.UNAVAILABLE,
                        statusMessage = "BLE hardware unavailable on this device/emulator. Health Connect sync remains active."
                    )
                }
            }
            !hasBlePermissions() -> {
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.PERMISSION_REQUIRED,
                        statusMessage = "Bluetooth permissions needed to scan and pair Wearable Device."
                    )
                }
            }
            !adapter.isEnabled -> {
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.BLUETOOTH_OFF,
                        isScanning = false,
                        statusMessage = "Bluetooth is turned off. Enable Bluetooth to pair your wearable."
                    )
                }
            }
            activeGatt == null -> {
                populateSystemAndConnectedDevices(adapter)
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.DISCONNECTED,
                        discoveredDevices = sortCandidates(discoveredMap.values),
                        statusMessage = if (discoveredMap.isNotEmpty()) {
                            "${discoveredMap.size} wearable/BLE device(s) ready to pair or connect."
                        } else {
                            "Ready to scan for Wearable Device or BLE heart rate sensors."
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun populateSystemAndConnectedDevices(adapter: BluetoothAdapter) {
        if (!hasBlePermissions()) return
        try {
            bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { dev ->
                val rawName = runCatching { dev.name }.getOrNull().orEmpty()
                val displayName = rawName.ifBlank { "Connected BLE (${dev.address.takeLast(5)})" }
                val isHuawei = isHuaweiName(displayName)
                BleDiscoveryHelper.mergeScannedCandidate(
                    discoveredMap,
                    BleDeviceCandidate(
                        name = displayName,
                        macAddress = dev.address,
                        rssi = -48,
                        isHuaweiDevice = isHuawei,
                        supportsHeartRate = true,
                        supportsRsc = false,
                        manufacturerName = if (isHuawei) "Wearable Device (Active GATT)" else "Active BLE GATT",
                        advertisedServices = listOf("Active Link"),
                        isBonded = dev.bondState == BluetoothDevice.BOND_BONDED,
                        isLiveScan = false,
                        deviceTypeLabel = BleDiscoveryHelper.deviceTypeLabel(dev.type)
                    )
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot read GATT connected devices: ${se.message}")
        }

        try {
            adapter.bondedDevices?.forEach { bonded ->
                val rawName = runCatching { bonded.name }.getOrNull().orEmpty()
                val uuids = runCatching { bonded.uuids?.map { it.uuid.toString() }.orEmpty() }.getOrDefault(emptyList())
                val hasFitnessUuid = uuids.any {
                    it.contains("180d", ignoreCase = true) ||
                        it.contains("1814", ignoreCase = true) ||
                        it.contains("fe86", ignoreCase = true) ||
                        it.contains("3802", ignoreCase = true)
                }
                if (!BleDiscoveryHelper.shouldIncludeBondedDevice(bonded.type, rawName, hasFitnessUuid)) {
                    return@forEach
                }
                val displayName = rawName.ifBlank { "Bonded BLE (${bonded.address.takeLast(5)})" }
                val isHuawei = BleDiscoveryHelper.isLikelyHuaweiWearable(displayName, serviceUuidStrings = uuids)
                if (!discoveredMap.containsKey(bonded.address)) {
                    val alreadyHasLiveScanSameName = discoveredMap.values.any {
                        it.isLiveScan && it.name.equals(displayName, ignoreCase = true)
                    }
                    if (!alreadyHasLiveScanSameName) {
                        discoveredMap[bonded.address] = BleDeviceCandidate(
                            name = displayName,
                            macAddress = bonded.address,
                            rssi = -58,
                            isHuaweiDevice = isHuawei,
                            supportsHeartRate = isHuawei || hasFitnessUuid,
                            supportsRsc = false,
                            manufacturerName = if (isHuawei) "Wearable Device (Paired)" else "Paired BLE",
                            advertisedServices = listOf("System Paired"),
                            isBonded = true,
                            isLiveScan = false,
                            deviceTypeLabel = BleDiscoveryHelper.deviceTypeLabel(bonded.type)
                        )
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot read bonded devices: ${se.message}")
        }
    }

    fun setShowAllBleDevices(showAll: Boolean) {
        _state.update { it.copy(showAllBleDevices = showAll) }
        if (hasBlePermissions() && bluetoothAdapter?.isEnabled == true && !_state.value.isScanning) {
            startScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshAdapterState()
        val adapter = bluetoothAdapter ?: return
        if (!hasBlePermissions() || !adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.update {
                it.copy(
                    status = BleConnectionStatus.UNAVAILABLE,
                    isScanning = false,
                    statusMessage = "Bluetooth LE scanner unavailable on this hardware."
                )
            }
            return
        }

        discoveredMap.clear()
        populateSystemAndConnectedDevices(adapter)

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && adapter.isLeExtendedAdvertisingSupported) {
            settingsBuilder.setLegacy(false)
        }
        val settings = settingsBuilder.build()

        _state.update {
            it.copy(
                status = if (activeGatt != null) BleConnectionStatus.CONNECTED else BleConnectionStatus.SCANNING,
                isScanning = true,
                discoveredDevices = sortCandidates(discoveredMap.values),
                statusMessage = "Scanning BLE peripherals (Wearable Device & 0x180D HR)..."
            )
        }

        try {
            runCatching { scanner.stopScan(scanCallback) }
            scanner.startScan(null, settings, scanCallback)
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(15_000L)
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _state.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "BLE scan error: ${e.localizedMessage}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!hasBlePermissions()) {
            _state.update { it.copy(isScanning = false) }
            return
        }
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan ignored: ${e.message}")
        }
        _state.update { current ->
            val nextStatus = if (current.status == BleConnectionStatus.SCANNING) {
                BleConnectionStatus.DISCONNECTED
            } else {
                current.status
            }
            current.copy(
                isScanning = false,
                status = nextStatus,
                statusMessage = if (discoveredMap.isEmpty() && activeGatt == null) {
                    "No BLE devices found nearby. Enable Heart Rate Broadcast on Wearable Device or toggle 'Show All BLE'."
                } else {
                    current.statusMessage
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(macAddress: String, isHuaweiHint: Boolean = false) {
        if (!hasBlePermissions()) {
            refreshAdapterState()
            return
        }
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            refreshAdapterState()
            return
        }
        stopScan()
        userRequestedDisconnect = false
        reconnectAttempts = 0
        handshakeRetries = 0
        serviceRefreshAttemptedForConnection = false
        authBondRequestedForConnection = false
        lastTargetMac = macAddress
        reconnectJob?.cancel()
        connectJob?.cancel()
        bondTimeoutJob?.cancel()

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(statusMessage = "Invalid Bluetooth MAC address: $macAddress") }
            return
        }

        val devType = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        if (devType == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            _state.update {
                it.copy(
                    status = BleConnectionStatus.DISCONNECTED,
                    statusMessage = "Selected entry is Bluetooth Classic. Enable Heart Rate Broadcast on your Wearable Device and tap Scan BLE Sensors."
                )
            }
            startScan()
            return
        }

        val candidate = discoveredMap[macAddress]
        val isLiveScan = candidate?.isLiveScan == true
        val isSystemGattConnected = runCatching {
            bluetoothManager?.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)

        connectPlan = BleDiscoveryHelper.buildConnectPlan(
            deviceType = devType,
            isLiveScan = isLiveScan,
            isSystemGattConnected = isSystemGattConnected
        )
        connectPlanIndex = 0

        closeActiveGattOnly()
        val devName = try {
            device.name ?: candidate?.name ?: "BLE Wearable"
        } catch (_: SecurityException) {
            candidate?.name ?: "BLE Wearable"
        }
        val huaweiFlag = isHuaweiHint || isHuaweiName(devName)
        val firstMode = connectPlan.firstOrNull() ?: BleDiscoveryHelper.GattConnectMode.DIRECT_LE

        _state.update {
            it.copy(
                status = BleConnectionStatus.CONNECTING,
                connectedDeviceName = devName,
                connectedMacAddress = macAddress,
                isHuaweiNative = huaweiFlag,
                statusMessage = "Connecting to $devName (${firstMode.label}, 1/${connectPlan.size})..."
            )
        }

        // Wait 300ms after stopScan so Android BT stack clears scan state (prevents GATT 133 errors),
        // then execute the prioritized connection plan (TRANSPORT_AUTO / passive / TRANSPORT_LE).
        connectJob = scope.launch {
            delay(300L)
            launchConnectStep(device, 0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun launchConnectStep(device: BluetoothDevice, stepIndex: Int) {
        if (userRequestedDisconnect) return
        val plan = connectPlan.ifEmpty {
            listOf(BleDiscoveryHelper.GattConnectMode.DIRECT_LE)
        }
        if (stepIndex >= plan.size) {
            handleConnectExhausted(
                lastStatus = 133,
                devName = _state.value.connectedDeviceName ?: "Wearable Device"
            )
            return
        }
        connectPlanIndex = stepIndex
        val mode = plan[stepIndex]
        val devName = _state.value.connectedDeviceName
            ?: discoveredMap[device.address]?.name
            ?: "Wearable Device"

        closeDeadGattOnly(refreshCache = stepIndex > 0)

        _state.update {
            it.copy(
                status = BleConnectionStatus.CONNECTING,
                statusMessage = "Connecting to $devName (${mode.label}, ${stepIndex + 1}/${plan.size})..."
            )
        }

        val transport = if (mode.useTransportAuto) {
            BluetoothDevice.TRANSPORT_AUTO
        } else {
            BluetoothDevice.TRANSPORT_LE
        }

        val newGatt = runCatching {
            device.connectGatt(appContext, mode.autoConnect, gattCallback, transport)
        }.getOrElse {
            runCatching { device.connectGatt(appContext, mode.autoConnect, gattCallback) }.getOrNull()
        }
        activeGatt = newGatt

        if (newGatt == null) {
            connectJob?.cancel()
            connectJob = scope.launch {
                delay(400L)
                launchConnectStep(device, stepIndex + 1)
            }
            return
        }

        bondTimeoutJob?.cancel()
        bondTimeoutJob = scope.launch {
            delay(mode.timeoutMs)
            if (activeGatt === newGatt &&
                _state.value.status == BleConnectionStatus.CONNECTING &&
                !userRequestedDisconnect
            ) {
                Log.w(TAG, "Step ${stepIndex + 1}/${plan.size} (${mode.label}) timed out after ${mode.timeoutMs}ms")
                closeDeadGattOnly(newGatt, refreshCache = true)
                val nextIdx = stepIndex + 1
                if (nextIdx < plan.size) {
                    delay(350L)
                    launchConnectStep(device, nextIdx)
                } else {
                    handleConnectExhausted(lastStatus = 133, devName = devName)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectExhausted(lastStatus: Int, devName: String) {
        val shouldRetry = !userRequestedDisconnect &&
            _state.value.autoReconnectEnabled &&
            lastTargetMac != null &&
            reconnectAttempts < 2

        if (shouldRetry) {
            reconnectAttempts++
            val backoffMs = reconnectAttempts * 2500L
            _state.update {
                it.copy(
                    status = BleConnectionStatus.CONNECTING,
                    statusMessage = "Waiting for $devName BLE link (status $lastStatus). Auto-retrying ($reconnectAttempts/2)..."
                )
            }
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(backoffMs)
                lastTargetMac?.let { mac ->
                    connectToDevice(mac, _state.value.isHuaweiNative)
                }
            }
        } else {
            val candidate = lastTargetMac?.let { discoveredMap[it] }
            val message = when {
                userRequestedDisconnect -> "Disconnected."
                candidate != null && !candidate.isLiveScan && candidate.deviceTypeLabel == "Dual" ->
                    "BLE GATT rejected (status $lastStatus) on paired Dual link. On $devName: turn ON Heart Rate Broadcast / Workout, tap Scan BLE Sensors & pick the 'LIVE BLE' entry — or use Health Connect Sync below."
                else ->
                    "Disconnected (status $lastStatus). Turn ON Heart Rate Broadcast on your Wearable Device and close exclusive companion workout locks, or use Health Connect Sync."
            }
            _state.update {
                it.copy(
                    status = BleConnectionStatus.DISCONNECTED,
                    liveHeartRateBpm = 0,
                    statusMessage = message
                )
            }
        }
    }

    private fun refreshGattCache(gatt: BluetoothGatt?): Boolean {
        if (gatt == null) return false
        return runCatching {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            refreshMethod.isAccessible = true
            (refreshMethod.invoke(gatt) as? Boolean) ?: false
        }.getOrDefault(false)
    }

    fun setAutoReconnect(enabled: Boolean) {
        _state.update { it.copy(autoReconnectEnabled = enabled) }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userRequestedDisconnect = true
        pendingBondMac = null
        connectJob?.cancel()
        bondTimeoutJob?.cancel()
        reconnectJob?.cancel()
        servicePollJob?.cancel()
        closeActiveGattOnly()
        recentRrIntervalsMs.clear()
        prevWheelRevs = -1
        prevWheelEventTime = -1
        prevCrankRevs = -1
        prevCrankEventTime = -1
        _state.update {
            it.copy(
                status = BleConnectionStatus.DISCONNECTED,
                connectedDeviceName = null,
                connectedMacAddress = null,
                liveHeartRateBpm = 0,
                hrvRmssdMs = 0.0,
                liveSpo2Percent = 0.0,
                sensorContactDetected = null,
                liveCadenceSpm = 0,
                liveSpeedKmh = 0.0,
                batteryPercent = -1,
                discoveredServices = emptyList(),
                statusMessage = "Disconnected from wearable."
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectedServices() {
        val gatt = activeGatt ?: return
        if (!hasBlePermissions() || _state.value.status != BleConnectionStatus.CONNECTED) return
        serviceRefreshAttemptedForConnection = false
        refreshGattCache(gatt)
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(350L)
            if (activeGatt === gatt && !userRequestedDisconnect) {
                _state.update {
                    it.copy(statusMessage = "Refreshing GATT telemetry services on ${it.connectedDeviceName}...")
                }
                gatt.discoverServices()
            }
        }
    }

    fun resetSessionCounters() {
        _state.update { it.copy(sessionBleSteps = 0, sessionBleCalories = 0) }
    }

    @SuppressLint("MissingPermission")
    private fun closeDeadGattOnly(gatt: BluetoothGatt? = activeGatt, refreshCache: Boolean = false) {
        gattOpQueue.clear()
        gattOpInFlight = false
        if (gatt != null) {
            if (refreshCache) {
                refreshGattCache(gatt)
            }
            runCatching { gatt.close() }
                .onFailure { Log.w(TAG, "Error closing dead GATT: ${it.message}") }
        }
        if (gatt === activeGatt) {
            activeGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeActiveGattOnly() {
        gattOpQueue.clear()
        gattOpInFlight = false
        activeGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT: ${e.message}")
            }
        }
        activeGatt = null
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val rawName = try {
                record?.deviceName ?: device.name
            } catch (_: SecurityException) {
                record?.deviceName
            }
            val advertisedUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()
            val serviceDataUuids = record?.serviceData?.keys?.map { it.uuid } ?: emptyList()
            val serviceUuids = (advertisedUuids + serviceDataUuids).distinct()
            val serviceUuidStrings = serviceUuids.map { it.toString() }
            val hasHuaweiMfg = record?.getManufacturerSpecificData(HUAWEI_MANUFACTURER_ID) != null ||
                record?.getManufacturerSpecificData(HONOR_MANUFACTURER_ID) != null
            val hasHuaweiService = serviceUuids.any {
                it == HUAWEI_WEAR_UUID_FE86 || it == HUAWEI_WEAR_UUID_3802 || it == HUAWEI_WEAR_UUID_FD82 || it == HUAWEI_WEAR_UUID_FEEA
            }
            val supportsHr = serviceUuids.contains(HEART_RATE_SERVICE)
            val supportsRsc = serviceUuids.contains(RSC_SERVICE)
            val isHuawei = BleDiscoveryHelper.isLikelyHuaweiWearable(
                name = rawName.orEmpty(),
                hasHuaweiManufacturerData = hasHuaweiMfg,
                serviceUuidStrings = serviceUuidStrings
            ) || hasHuaweiService

            // Filter unnamed generic clutter unless Show All BLE is enabled or it advertises fitness/Huawei data
            if (rawName.isNullOrBlank() && !isHuawei && !supportsHr && !supportsRsc && !_state.value.showAllBleDevices) {
                return
            }

            val displayName = rawName?.takeIf { it.isNotBlank() } ?: when {
                isHuawei -> "Wearable Device"
                supportsHr || supportsRsc -> "BLE Fitness Sensor"
                else -> "BLE Device (${device.address.takeLast(5)})"
            }
            val labels = serviceUuids.map { uuid ->
                when (uuid) {
                    HEART_RATE_SERVICE -> "HR 0x180D"
                    RSC_SERVICE -> "RSC 0x1814"
                    CSC_SERVICE -> "CSC 0x1816"
                    BATTERY_SERVICE -> "Battery 0x180F"
                    HUAWEI_WEAR_UUID_FE86 -> "Wearable 0xFE86"
                    HUAWEI_WEAR_UUID_3802 -> "Wearable 0x3802"
                    HUAWEI_WEAR_UUID_FD82 -> "Wearable 0xFD82"
                    HUAWEI_WEAR_UUID_FEEA -> "Wearable 0xFEEA"
                    else -> uuid.toString().substring(4, 8).uppercase()
                }
            }
            val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
            val devType = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_LE)

            BleDiscoveryHelper.mergeScannedCandidate(
                discoveredMap,
                BleDeviceCandidate(
                    name = displayName,
                    macAddress = device.address,
                    rssi = result.rssi,
                    isHuaweiDevice = isHuawei,
                    supportsHeartRate = supportsHr || isHuawei,
                    supportsRsc = supportsRsc,
                    manufacturerName = when {
                        isHuawei && supportsHr -> "Wearable Device • Live HR"
                        isHuawei -> "Wearable Device (BLE)"
                        supportsHr -> "Standard HR 0x180D"
                        else -> "Standard BLE"
                    },
                    advertisedServices = labels,
                    isBonded = bonded,
                    isLiveScan = true,
                    deviceTypeLabel = BleDiscoveryHelper.deviceTypeLabel(devType)
                )
            )

            _state.update {
                it.copy(discoveredDevices = sortCandidates(discoveredMap.values))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with code=$errorCode")
            _state.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "BLE scan failed (code $errorCode). Ensure Location & Bluetooth are enabled."
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== activeGatt) {
                Log.d(TAG, "Ignoring stale GATT callback (status=$status, newState=$newState)")
                runCatching { gatt.close() }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                reconnectAttempts = 0
                handshakeRetries = 0
                bondTimeoutJob?.cancel()
                runCatching {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                }
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.CONNECTED,
                        statusMessage = "Connected to ${it.connectedDeviceName}. Syncing GATT services..."
                    )
                }
                // Brief settle delay before discoverServices() prevents HarmonyOS / BLE controller cache races
                connectJob?.cancel()
                connectJob = scope.launch {
                    delay(450L)
                    if (activeGatt === gatt && !userRequestedDisconnect) {
                        gatt.discoverServices()
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val targetDevice = gatt.device
                bondTimeoutJob?.cancel()
                closeDeadGattOnly(gatt, refreshCache = status != BluetoothGatt.GATT_SUCCESS)

                val isRecoverableHandshakeError = status == 133 || status == 8 || status == 19 || status == 22 || status == 62 || status == 147 || status == 257
                val nextStep = connectPlanIndex + 1
                if (!userRequestedDisconnect && isRecoverableHandshakeError && nextStep < connectPlan.size) {
                    val nextMode = connectPlan[nextStep]
                    _state.update {
                        it.copy(
                            status = BleConnectionStatus.CONNECTING,
                            statusMessage = "Status $status — trying ${nextMode.label} (${nextStep + 1}/${connectPlan.size})..."
                        )
                    }
                    connectJob?.cancel()
                    connectJob = scope.launch {
                        delay(450L)
                        launchConnectStep(targetDevice, nextStep)
                    }
                    return
                }

                handleConnectExhausted(
                    lastStatus = status,
                    devName = _state.value.connectedDeviceName ?: "Wearable Device"
                )
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== activeGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(statusMessage = "GATT service discovery failed ($status).") }
                return
            }
            val hasHr = gatt.getService(HEART_RATE_SERVICE) != null
            if (!hasHr && !serviceRefreshAttemptedForConnection) {
                // Android caches GATT service tables per MAC; refresh once so newly enabled 0x180D HR Broadcast appears
                serviceRefreshAttemptedForConnection = true
                if (refreshGattCache(gatt)) {
                    connectJob?.cancel()
                    connectJob = scope.launch {
                        delay(450L)
                        if (activeGatt == gatt && !userRequestedDisconnect) {
                            gatt.discoverServices()
                        }
                    }
                    return
                }
            }

            val serviceSummary = gatt.services.map { service ->
                when (service.uuid) {
                    HEART_RATE_SERVICE -> "Heart Rate (0x180D)"
                    PLX_SERVICE -> "Pulse Oximeter SpO2 (0x1822)"
                    RSC_SERVICE -> "Running Speed & Cadence (0x1814)"
                    CSC_SERVICE -> "Cycling Speed & Cadence (0x1816)"
                    BATTERY_SERVICE -> "Battery Service (0x180F)"
                    DEVICE_INFO_SERVICE -> "Device Information (0x180A)"
                    HUAWEI_WEAR_UUID_FE86 -> "Wearable Channel (0xFE86)"
                    HUAWEI_WEAR_UUID_3802 -> "Wearable Health Link (0x3802)"
                    HUAWEI_WEAR_UUID_FEEA -> "Wearable Service (0xFEEA)"
                    else -> service.uuid.toString().take(8).uppercase()
                }
            }
            val hasHuaweiSvc = gatt.getService(HUAWEI_WEAR_UUID_FE86) != null ||
                gatt.getService(HUAWEI_WEAR_UUID_3802) != null ||
                gatt.getService(HUAWEI_WEAR_UUID_FEEA) != null

            _state.update {
                it.copy(
                    discoveredServices = serviceSummary,
                    isHuaweiNative = it.isHuaweiNative || hasHuaweiSvc,
                    statusMessage = if (hasHr) {
                        "Streaming live telemetry from ${it.connectedDeviceName}"
                    } else {
                        "Connected (${serviceSummary.size} services). On your Wearable Device, start a Workout or turn ON Heart Rate Broadcast to stream live BPM."
                    }
                )
            }

            gattOpQueue.clear()
            gattOpInFlight = false

            // Queue characteristic notifications & reads cleanly
            gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)?.let { hrChar ->
                enqueueGattOp { enableNotifications(gatt, hrChar) }
            }
            gatt.getService(PLX_SERVICE)?.let { plxSvc ->
                plxSvc.getCharacteristic(PLX_CONTINUOUS)?.let { c -> enqueueGattOp { enableNotifications(gatt, c) } }
                plxSvc.getCharacteristic(PLX_SPOT_CHECK)?.let { c -> enqueueGattOp { enableNotifications(gatt, c) } }
            }
            gatt.getService(RSC_SERVICE)?.getCharacteristic(RSC_MEASUREMENT)?.let { rscChar ->
                enqueueGattOp { enableNotifications(gatt, rscChar) }
            }
            gatt.getService(CSC_SERVICE)?.getCharacteristic(CSC_MEASUREMENT)?.let { cscChar ->
                enqueueGattOp { enableNotifications(gatt, cscChar) }
            }
            gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)?.let { batChar ->
                enqueueGattOp { gatt.readCharacteristic(batChar) }
                enqueueGattOp { enableNotifications(gatt, batChar) }
            }
            gatt.getService(DEVICE_INFO_SERVICE)?.let { infoSvc ->
                infoSvc.getCharacteristic(MANUFACTURER_NAME)?.let { c -> enqueueGattOp { gatt.readCharacteristic(c) } }
                infoSvc.getCharacteristic(MODEL_NUMBER)?.let { c -> enqueueGattOp { gatt.readCharacteristic(c) } }
                infoSvc.getCharacteristic(FIRMWARE_REV)?.let { c -> enqueueGattOp { gatt.readCharacteristic(c) } }
            }

            processNextGattOp()

            // If connected without 0x180D HR yet, periodically re-check GATT table so newly enabled
            // Heart Rate Broadcast on the band begins streaming automatically.
            servicePollJob?.cancel()
            if (!hasHr) {
                servicePollJob = scope.launch {
                    repeat(5) {
                        delay(10_000L)
                        if (activeGatt !== gatt || userRequestedDisconnect || _state.value.liveHeartRateBpm > 0) {
                            return@launch
                        }
                        refreshGattCache(gatt)
                        delay(300L)
                        if (activeGatt === gatt && !userRequestedDisconnect) {
                            gatt.discoverServices()
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== activeGatt) return
            if ((status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                    status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION ||
                    status == 137) && !authBondRequestedForConnection
            ) {
                authBondRequestedForConnection = true
                val dev = gatt.device
                pendingBondMac = dev.address
                runCatching { dev.createBond() }
            }
            gattOpInFlight = false
            processNextGattOp()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (gatt !== activeGatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
            gattOpInFlight = false
            processNextGattOp()
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== activeGatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { handleCharacteristicValue(characteristic.uuid, it) }
            }
            gattOpInFlight = false
            processNextGattOp()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== activeGatt) return
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (gatt !== activeGatt) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { handleCharacteristicValue(characteristic.uuid, it) }
        }
    }

    private fun enqueueGattOp(op: () -> Unit) {
        gattOpQueue.addLast(op)
    }

    private fun processNextGattOp() {
        if (gattOpInFlight || gattOpQueue.isEmpty()) return
        gattOpInFlight = true
        val next = gattOpQueue.removeFirst()
        try {
            next()
        } catch (e: Exception) {
            Log.w(TAG, "GATT queued op failed: ${e.message}")
            gattOpInFlight = false
            processNextGattOp()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR)
        if (descriptor == null) {
            gattOpInFlight = false
            processNextGattOp()
            return
        }
        val props = characteristic.properties
        val cccdValue = if (
            (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
            (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
        ) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeDescriptor(descriptor, cccdValue)
            if (res != BluetoothStatusCodes.SUCCESS) {
                gattOpInFlight = false
                processNextGattOp()
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = cccdValue
            @Suppress("DEPRECATION")
            if (!gatt.writeDescriptor(descriptor)) {
                gattOpInFlight = false
                processNextGattOp()
            }
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (uuid) {
            HEART_RATE_MEASUREMENT -> parseHeartRatePacket(bytes)
            PLX_SPOT_CHECK, PLX_CONTINUOUS -> parsePlxPacket(bytes)
            RSC_MEASUREMENT -> parseRscPacket(bytes)
            CSC_MEASUREMENT -> parseCscPacket(bytes)
            BATTERY_LEVEL -> {
                val pct = bytes[0].toInt() and 0xFF
                _state.update { it.copy(batteryPercent = pct.coerceIn(0, 100)) }
            }
            MANUFACTURER_NAME -> {
                val text = String(bytes, Charsets.UTF_8).trim()
                _state.update { it.copy(deviceManufacturer = text) }
            }
            MODEL_NUMBER -> {
                val text = String(bytes, Charsets.UTF_8).trim()
                _state.update { it.copy(deviceModel = text) }
            }
            FIRMWARE_REV -> {
                val text = String(bytes, Charsets.UTF_8).trim()
                _state.update { it.copy(firmwareVersion = text) }
            }
        }
    }

    private fun parsePlxPacket(bytes: ByteArray) {
        if (bytes.size < 5) return
        // Standard Bluetooth SIG PLX Spot-Check / Continuous:
        // [0] Flags, [1..2] SpO2 SFLOAT, [3..4] PR SFLOAT
        val spo2Raw = decodeSfloat(bytes[1], bytes[2])
        val prRaw = decodeSfloat(bytes[3], bytes[4])
        val validSpo2 = spo2Raw?.takeIf { it in 70.0..100.0 }
        val validPr = prRaw?.toInt()?.takeIf { it in 35..220 }
        if (validSpo2 != null || validPr != null) {
            _state.update { current ->
                current.copy(
                    liveSpo2Percent = validSpo2?.let { (it * 10.0).toInt() / 10.0 } ?: current.liveSpo2Percent,
                    liveHeartRateBpm = if (current.liveHeartRateBpm <= 0 && validPr != null) validPr else current.liveHeartRateBpm,
                    lastPacketTimestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    private fun decodeSfloat(lsb: Byte, msb: Byte): Double? {
        val raw = (lsb.toInt() and 0xFF) or ((msb.toInt() and 0xFF) shl 8)
        if (raw == 0x07FF || raw == 0x0800 || raw == 0x07FE || raw == 0x0802 || raw == 0x0FFF) return null
        var mantissa = raw and 0x0FFF
        if ((mantissa and 0x0800) != 0) mantissa = mantissa or -0x1000
        var exponent = (raw ushr 12) and 0x0F
        if ((exponent and 0x08) != 0) exponent = exponent or -0x10
        return mantissa * Math.pow(10.0, exponent.toDouble())
    }

    private fun parseHeartRatePacket(bytes: ByteArray) {
        val flags = bytes[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0
        val contactSupported = (flags and 0x04) != 0
        val contactDetected = if (contactSupported) (flags and 0x02) != 0 else null
        val hasEnergyExpended = (flags and 0x08) != 0
        val hasRrIntervals = (flags and 0x10) != 0

        var offset = 1
        val bpm = if (isUint16 && bytes.size >= offset + 2) {
            val v = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            v
        } else if (bytes.size >= offset + 1) {
            val v = bytes[offset].toInt() and 0xFF
            offset += 1
            v
        } else 0

        var energyKj = 0
        if (hasEnergyExpended && bytes.size >= offset + 2) {
            energyKj = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        }

        if (hasRrIntervals) {
            while (bytes.size >= offset + 2) {
                val rawRr = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
                val rrMs = (rawRr / 1024.0) * 1000.0
                if (rrMs in 300.0..2000.0) {
                    if (recentRrIntervalsMs.size >= 30) recentRrIntervalsMs.removeFirst()
                    recentRrIntervalsMs.addLast(rrMs)
                }
            }
        }

        val rmssd = computeRmssd(recentRrIntervalsMs)
        _state.update { current ->
            val extraKcal = if (energyKj > 0) (energyKj * 0.239).toInt() else current.sessionBleCalories
            current.copy(
                liveHeartRateBpm = bpm,
                hrvRmssdMs = rmssd,
                sensorContactDetected = contactDetected,
                sessionBleCalories = extraKcal,
                lastPacketTimestampMs = System.currentTimeMillis()
            )
        }
    }

    private fun parseRscPacket(bytes: ByteArray) {
        if (bytes.size < 4) return
        val speedRaw = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        val speedMs = speedRaw / 256.0
        val speedKmh = speedMs * 3.6
        val cadenceSpm = bytes[3].toInt() and 0xFF

        val now = System.currentTimeMillis()
        val deltaSteps = if (lastRscSampleMs > 0L && cadenceSpm > 0) {
            val elapsedMin = ((now - lastRscSampleMs).coerceAtMost(5000L)) / 60_000.0
            (cadenceSpm * elapsedMin).toInt()
        } else 0
        lastRscSampleMs = now

        _state.update { current ->
            current.copy(
                liveCadenceSpm = cadenceSpm,
                liveSpeedKmh = speedKmh,
                sessionBleSteps = current.sessionBleSteps + deltaSteps,
                lastPacketTimestampMs = now
            )
        }
    }

    private var prevWheelRevs: Long = -1L
    private var prevWheelEventTime: Int = -1
    private var prevCrankRevs: Int = -1
    private var prevCrankEventTime: Int = -1

    private fun parseCscPacket(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val flags = bytes[0].toInt() and 0xFF
        val hasWheel = (flags and 0x01) != 0
        val hasCrank = (flags and 0x02) != 0
        var offset = 1
        var computedSpeedKmh: Double? = null
        if (hasWheel && bytes.size >= offset + 6) {
            val wheelRevs = (bytes[offset].toLong() and 0xFFL) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
            val evtTime = (bytes[offset + 4].toInt() and 0xFF) or ((bytes[offset + 5].toInt() and 0xFF) shl 8)
            if (prevWheelRevs >= 0L && wheelRevs > prevWheelRevs) {
                val deltaRevs = wheelRevs - prevWheelRevs
                val deltaTime = ((evtTime - prevWheelEventTime) and 0xFFFF) / 1024.0
                if (deltaTime > 0.1) {
                    val meters = deltaRevs * 2.105 // standard 700x25c wheel circumference
                    computedSpeedKmh = ((meters / deltaTime) * 3.6).coerceIn(0.0, 95.0)
                }
            }
            prevWheelRevs = wheelRevs
            prevWheelEventTime = evtTime
            offset += 6
        }
        var computedCadenceRpm: Int? = null
        if (hasCrank && bytes.size >= offset + 4) {
            val revs = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            val evtTime = (bytes[offset + 2].toInt() and 0xFF) or ((bytes[offset + 3].toInt() and 0xFF) shl 8)
            if (prevCrankRevs >= 0 && revs != prevCrankRevs) {
                val deltaRevs = (revs - prevCrankRevs) and 0xFFFF
                val deltaTime = ((evtTime - prevCrankEventTime) and 0xFFFF) / 1024.0
                if (deltaTime > 0.1) {
                    computedCadenceRpm = ((deltaRevs / deltaTime) * 60.0).toInt().coerceIn(20, 180)
                }
            }
            prevCrankRevs = revs
            prevCrankEventTime = evtTime
        }
        if (computedSpeedKmh != null || computedCadenceRpm != null) {
            _state.update { current ->
                current.copy(
                    liveSpeedKmh = computedSpeedKmh ?: current.liveSpeedKmh,
                    liveCadenceSpm = computedCadenceRpm ?: current.liveCadenceSpm,
                    lastPacketTimestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    private fun computeRmssd(rrList: Collection<Double>): Double {
        if (rrList.size < 3) return 0.0
        val items = rrList.toList()
        var sumSqDiff = 0.0
        var count = 0
        for (i in 1 until items.size) {
            val diff = items[i] - items[i - 1]
            sumSqDiff += diff * diff
            count++
        }
        return if (count > 0) ((sqrt(sumSqDiff / count) * 10.0).toInt() / 10.0) else 0.0
    }

    private fun isHuaweiName(name: String): Boolean =
        BleDiscoveryHelper.isLikelyHuaweiWearable(name)

    private fun sortCandidates(items: Collection<BleDeviceCandidate>): List<BleDeviceCandidate> =
        BleDiscoveryHelper.sortCandidates(items)
}

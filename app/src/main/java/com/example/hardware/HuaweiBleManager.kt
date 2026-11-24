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
    private var userRequestedDisconnect = false
    private var reconnectAttempts = 0
    private var status133Retries = 0
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
                                        statusMessage = "Paired! Opening GATT telemetry link to $devName..."
                                    )
                                }
                                connectJob?.cancel()
                                connectJob = scope.launch {
                                    delay(300L)
                                    connectGattInternal(device)
                                }
                            }
                            BluetoothDevice.BOND_NONE -> {
                                pendingBondMac = null
                                bondTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        status = BleConnectionStatus.CONNECTING,
                                        statusMessage = "Connecting direct BLE GATT to $devName..."
                                    )
                                }
                                connectJob?.cancel()
                                connectJob = scope.launch {
                                    delay(250L)
                                    connectGattInternal(device)
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

        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
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
                        statusMessage = "Bluetooth permissions needed to scan and pair HUAWEI Band 11 Pro."
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
                            "Ready to scan for HUAWEI Band 11 Pro or BLE heart rate sensors."
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
                discoveredMap[dev.address] = BleDeviceCandidate(
                    name = displayName,
                    macAddress = dev.address,
                    rssi = -48,
                    isHuaweiDevice = isHuawei,
                    supportsHeartRate = true,
                    supportsRsc = false,
                    manufacturerName = if (isHuawei) "Huawei (Active GATT)" else "Active BLE GATT",
                    advertisedServices = listOf("Active Link"),
                    isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot read GATT connected devices: ${se.message}")
        }

        try {
            adapter.bondedDevices?.forEach { bonded ->
                val rawName = runCatching { bonded.name }.getOrNull().orEmpty()
                val displayName = rawName.ifBlank { "Bonded Device (${bonded.address.takeLast(5)})" }
                val isHuawei = isHuaweiName(displayName)
                discoveredMap.putIfAbsent(
                    bonded.address,
                    BleDeviceCandidate(
                        name = displayName,
                        macAddress = bonded.address,
                        rssi = -55,
                        isHuaweiDevice = isHuawei,
                        supportsHeartRate = isHuawei,
                        supportsRsc = false,
                        manufacturerName = if (isHuawei) "Huawei (Bonded)" else "Bonded BLE",
                        advertisedServices = listOf("System Bonded"),
                        isBonded = true
                    )
                )
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

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _state.update {
            it.copy(
                status = if (activeGatt != null) BleConnectionStatus.CONNECTED else BleConnectionStatus.SCANNING,
                isScanning = true,
                discoveredDevices = sortCandidates(discoveredMap.values),
                statusMessage = "Scanning BLE peripherals (0x027D Huawei & 0x180D HR)..."
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
                    "No BLE devices found nearby. Enable Heart Rate Broadcast on Band or toggle 'Show All BLE'."
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
        status133Retries = 0
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

        closeActiveGattOnly()
        val devName = try {
            device.name ?: discoveredMap[macAddress]?.name ?: "BLE Wearable"
        } catch (_: SecurityException) {
            discoveredMap[macAddress]?.name ?: "BLE Wearable"
        }
        val huaweiFlag = isHuaweiHint || isHuaweiName(devName)

        _state.update {
            it.copy(
                status = BleConnectionStatus.CONNECTING,
                connectedDeviceName = devName,
                connectedMacAddress = macAddress,
                isHuaweiNative = huaweiFlag,
                statusMessage = "Preparing BLE pairing & GATT link with $devName..."
            )
        }

        // Wait 250ms after stopScan so Android BT stack clears scan state (prevents GATT 133 errors)
        connectJob = scope.launch {
            delay(250L)
            val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    _state.update { it.copy(statusMessage = "Connecting GATT LE to $devName...") }
                    connectGattInternal(device)
                }
                BluetoothDevice.BOND_BONDING -> {
                    pendingBondMac = macAddress
                    _state.update { it.copy(statusMessage = "Completing OS Bluetooth bond with $devName...") }
                    scheduleBondFallback(device, devName)
                }
                else -> {
                    val bondInitiated = runCatching { device.createBond() }.getOrDefault(false)
                    if (bondInitiated) {
                        pendingBondMac = macAddress
                        _state.update {
                            it.copy(statusMessage = "Pairing requested for $devName — accept dialog if prompted...")
                        }
                        scheduleBondFallback(device, devName)
                    } else {
                        _state.update { it.copy(statusMessage = "Connecting GATT LE to $devName...") }
                        connectGattInternal(device)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleBondFallback(device: BluetoothDevice, devName: String) {
        bondTimeoutJob?.cancel()
        bondTimeoutJob = scope.launch {
            delay(10_000L)
            if (pendingBondMac == device.address && activeGatt == null && !userRequestedDisconnect) {
                pendingBondMac = null
                _state.update {
                    it.copy(statusMessage = "Opening direct BLE GATT telemetry link to $devName...")
                }
                connectGattInternal(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectGattInternal(device: BluetoothDevice) {
        closeActiveGattOnly()
        activeGatt = runCatching {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            device.connectGatt(appContext, false, gattCallback)
        }
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
        closeActiveGattOnly()
        recentRrIntervalsMs.clear()
        _state.update {
            it.copy(
                status = BleConnectionStatus.DISCONNECTED,
                connectedDeviceName = null,
                connectedMacAddress = null,
                liveHeartRateBpm = 0,
                hrvRmssdMs = 0.0,
                sensorContactDetected = null,
                liveCadenceSpm = 0,
                liveSpeedKmh = 0.0,
                batteryPercent = -1,
                discoveredServices = emptyList(),
                statusMessage = "Disconnected from wearable."
            )
        }
    }

    fun resetSessionCounters() {
        _state.update { it.copy(sessionBleSteps = 0, sessionBleCalories = 0) }
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
            val serviceUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()
            val hasHuaweiMfg = record?.getManufacturerSpecificData(HUAWEI_MANUFACTURER_ID) != null
            val hasHuaweiService = serviceUuids.any {
                it == HUAWEI_WEAR_UUID_FE86 || it == HUAWEI_WEAR_UUID_3802 || it == HUAWEI_WEAR_UUID_FD82
            }
            val supportsHr = serviceUuids.contains(HEART_RATE_SERVICE)
            val supportsRsc = serviceUuids.contains(RSC_SERVICE)
            val isHuawei = hasHuaweiMfg || hasHuaweiService || isHuaweiName(rawName.orEmpty())

            // Filter unnamed generic clutter unless Show All BLE is enabled or it advertises fitness/Huawei data
            if (rawName.isNullOrBlank() && !isHuawei && !supportsHr && !supportsRsc && !_state.value.showAllBleDevices) {
                return
            }

            val displayName = rawName?.takeIf { it.isNotBlank() } ?: when {
                isHuawei -> "HUAWEI Band / Wearable"
                supportsHr || supportsRsc -> "BLE Fitness Sensor"
                else -> "BLE Device (${device.address.takeLast(5)})"
            }
            val labels = serviceUuids.map { uuid ->
                when (uuid) {
                    HEART_RATE_SERVICE -> "HR 0x180D"
                    RSC_SERVICE -> "RSC 0x1814"
                    CSC_SERVICE -> "CSC 0x1816"
                    BATTERY_SERVICE -> "Battery 0x180F"
                    HUAWEI_WEAR_UUID_FE86 -> "Huawei Wear 0xFE86"
                    HUAWEI_WEAR_UUID_3802 -> "Huawei Wear 0x3802"
                    HUAWEI_WEAR_UUID_FD82 -> "Huawei Wear 0xFD82"
                    else -> uuid.toString().substring(4, 8).uppercase()
                }
            }
            val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)

            discoveredMap[device.address] = BleDeviceCandidate(
                name = displayName,
                macAddress = device.address,
                rssi = result.rssi,
                isHuaweiDevice = isHuawei,
                supportsHeartRate = supportsHr || isHuawei,
                supportsRsc = supportsRsc,
                manufacturerName = if (isHuawei) "Huawei Technologies (0x027D)" else "Standard BLE",
                advertisedServices = labels,
                isBonded = bonded
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
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                reconnectAttempts = 0
                status133Retries = 0
                try {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) {}
                _state.update {
                    it.copy(
                        status = BleConnectionStatus.CONNECTED,
                        statusMessage = "Connected to ${it.connectedDeviceName}. Discovering GATT services..."
                    )
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val targetDevice = gatt.device
                closeActiveGattOnly()
                if (!userRequestedDisconnect && status == 133 && status133Retries < 2) {
                    status133Retries++
                    _state.update {
                        it.copy(
                            status = BleConnectionStatus.CONNECTING,
                            statusMessage = "Retrying BLE GATT handshake (status 133, attempt $status133Retries/2)..."
                        )
                    }
                    connectJob?.cancel()
                    connectJob = scope.launch {
                        delay(450L)
                        connectGattInternal(targetDevice)
                    }
                    return
                }
                val shouldRetry = !userRequestedDisconnect &&
                    _state.value.autoReconnectEnabled &&
                    lastTargetMac != null &&
                    reconnectAttempts < 3

                if (shouldRetry) {
                    reconnectAttempts++
                    val backoffMs = reconnectAttempts * 2500L
                    _state.update {
                        it.copy(
                            status = BleConnectionStatus.CONNECTING,
                            statusMessage = "Link dropped (status $status). Auto-reconnecting ($reconnectAttempts/3)..."
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
                    _state.update {
                        it.copy(
                            status = BleConnectionStatus.DISCONNECTED,
                            liveHeartRateBpm = 0,
                            statusMessage = if (userRequestedDisconnect) {
                                "Disconnected."
                            } else {
                                "Disconnected (status $status). Verify Band is awake, paired in Bluetooth, & broadcasting HR."
                            }
                        )
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(statusMessage = "GATT service discovery failed ($status).") }
                return
            }
            val serviceSummary = gatt.services.map { service ->
                when (service.uuid) {
                    HEART_RATE_SERVICE -> "Heart Rate (0x180D)"
                    RSC_SERVICE -> "Running Speed & Cadence (0x1814)"
                    CSC_SERVICE -> "Cycling Speed & Cadence (0x1816)"
                    BATTERY_SERVICE -> "Battery Service (0x180F)"
                    DEVICE_INFO_SERVICE -> "Device Information (0x180A)"
                    HUAWEI_WEAR_UUID_FE86 -> "Huawei Wear Channel (0xFE86)"
                    HUAWEI_WEAR_UUID_3802 -> "Huawei Health Link (0x3802)"
                    else -> service.uuid.toString().take(8).uppercase()
                }
            }
            val hasHr = gatt.getService(HEART_RATE_SERVICE) != null
            val hasHuaweiSvc = gatt.getService(HUAWEI_WEAR_UUID_FE86) != null ||
                gatt.getService(HUAWEI_WEAR_UUID_3802) != null

            _state.update {
                it.copy(
                    discoveredServices = serviceSummary,
                    isHuaweiNative = it.isHuaweiNative || hasHuaweiSvc,
                    statusMessage = if (hasHr) {
                        "Streaming live telemetry from ${it.connectedDeviceName}"
                    } else {
                        "Connected (${serviceSummary.size} services). Enable Heart Rate Broadcast on Band 11 Pro for live BPM."
                    }
                )
            }

            gattOpQueue.clear()
            gattOpInFlight = false

            // Queue characteristic notifications & reads cleanly
            gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)?.let { hrChar ->
                enqueueGattOp { enableNotifications(gatt, hrChar) }
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
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            gattOpInFlight = false
            processNextGattOp()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
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
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
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

    private var prevCrankRevs: Int = -1
    private var prevCrankEventTime: Int = -1

    private fun parseCscPacket(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val flags = bytes[0].toInt() and 0xFF
        val hasWheel = (flags and 0x01) != 0
        val hasCrank = (flags and 0x02) != 0
        var offset = 1
        if (hasWheel) offset += 6
        if (hasCrank && bytes.size >= offset + 4) {
            val revs = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            val evtTime = (bytes[offset + 2].toInt() and 0xFF) or ((bytes[offset + 3].toInt() and 0xFF) shl 8)
            if (prevCrankRevs >= 0 && revs != prevCrankRevs) {
                val deltaRevs = (revs - prevCrankRevs) and 0xFFFF
                val deltaTime = ((evtTime - prevCrankEventTime) and 0xFFFF) / 1024.0
                if (deltaTime > 0.1) {
                    val rpm = ((deltaRevs / deltaTime) * 60.0).toInt().coerceIn(20, 180)
                    _state.update { it.copy(liveCadenceSpm = rpm, lastPacketTimestampMs = System.currentTimeMillis()) }
                }
            }
            prevCrankRevs = revs
            prevCrankEventTime = evtTime
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

    private fun isHuaweiName(name: String): Boolean {
        val upper = name.uppercase()
        return upper.contains("HUAWEI") ||
            upper.contains("BAND 11") ||
            upper.contains("BAND 10") ||
            upper.contains("BAND 9") ||
            upper.contains("HONOR BAND") ||
            upper.contains("WATCH GT") ||
            upper.contains("WATCH FIT")
    }

    private fun sortCandidates(items: Collection<BleDeviceCandidate>): List<BleDeviceCandidate> {
        return items.sortedWith(
            compareByDescending<BleDeviceCandidate> { it.isHuaweiDevice }
                .thenByDescending { it.supportsHeartRate }
                .thenByDescending { it.rssi }
        )
    }
}

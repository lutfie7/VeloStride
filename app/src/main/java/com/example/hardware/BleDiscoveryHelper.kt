package com.example.hardware

import com.example.data.model.BleDeviceCandidate

/**
 * Pure helper utilities for filtering, identifying, and prioritizing BLE wearables
 * (Huawei Band 11 Pro / HarmonyOS wearables, Xiaomi bands, and standard 0x180D HR sensors).
 * Free of direct Android framework static dependencies so it can be unit-tested on JVM.
 */
object BleDiscoveryHelper {

    const val DEVICE_TYPE_UNKNOWN = 0
    const val DEVICE_TYPE_CLASSIC = 1
    const val DEVICE_TYPE_LE = 2
    const val DEVICE_TYPE_DUAL = 3

    private val HUAWEI_UUID_TOKENS = setOf("FE86", "3802", "FD82", "FEEA", "FD8C")

    private val HUAWEI_NAME_TOKENS = listOf(
        "HUAWEI",
        "HONOR",
        "BAND 11",
        "WATCH GT",
        "WATCH FIT",
        "WATCH D",
        "WATCH ULTIMATE"
    )

    private val NON_HUAWEI_BRAND_TOKENS = listOf(
        "XIAOMI",
        "MI BAND",
        "MI SMART BAND",
        "REDMI",
        "AMAZFIT",
        "GARMIN",
        "POLAR",
        "COROS",
        "SUUNTO",
        "FITBIT",
        "SAMSUNG"
    )

    private val FITNESS_NAME_TOKENS = listOf(
        "BAND",
        "WATCH",
        "MI ",
        "XIAOMI",
        "AMAZFIT",
        "GARMIN",
        "POLAR",
        "COROS",
        "SUUNTO",
        "WAHOO",
        "HRM",
        "HEART",
        "STRIDE",
        "RUNNER"
    )

    fun isLikelyHuaweiWearable(
        name: String,
        hasHuaweiManufacturerData: Boolean = false,
        serviceUuidStrings: List<String> = emptyList(),
    ): Boolean {
        if (hasHuaweiManufacturerData) return true
        val upperName = name.uppercase()
        if (NON_HUAWEI_BRAND_TOKENS.any { upperName.contains(it) }) return false
        if (HUAWEI_NAME_TOKENS.any { upperName.contains(it) }) return true
        return serviceUuidStrings.any { uuid ->
            val u = uuid.uppercase()
            HUAWEI_UUID_TOKENS.any { token -> u.contains(token) }
        }
    }

    fun shouldIncludeBondedDevice(
        deviceType: Int,
        name: String,
        hasFitnessServiceUuid: Boolean,
    ): Boolean {
        // Never treat Classic BR/EDR-only bonded profiles as direct BLE GATT targets;
        // calling connectGatt(..., TRANSPORT_LE) on a Classic MAC fails with GATT 133.
        if (deviceType == DEVICE_TYPE_CLASSIC) return false
        if (deviceType == DEVICE_TYPE_LE) return true

        val upperName = name.uppercase()
        val looksLikeFitness = hasFitnessServiceUuid ||
            isLikelyHuaweiWearable(name) ||
            FITNESS_NAME_TOKENS.any { upperName.contains(it) }

        return when (deviceType) {
            DEVICE_TYPE_DUAL -> looksLikeFitness
            else -> looksLikeFitness || name.isNotBlank()
        }
    }

    fun deviceTypeLabel(deviceType: Int): String = when (deviceType) {
        DEVICE_TYPE_CLASSIC -> "Classic"
        DEVICE_TYPE_LE -> "BLE"
        DEVICE_TYPE_DUAL -> "Dual"
        else -> "BLE"
    }

    fun mergeScannedCandidate(
        map: MutableMap<String, BleDeviceCandidate>,
        incoming: BleDeviceCandidate,
    ) {
        if (incoming.isLiveScan && incoming.name.isNotBlank()) {
            val normalizedIncoming = incoming.name.trim().lowercase()
            val staleKeys = map.entries
                .filter { (mac, candidate) ->
                    mac != incoming.macAddress &&
                        !candidate.isLiveScan &&
                        candidate.name.trim().lowercase() == normalizedIncoming
                }
                .map { it.key }
            staleKeys.forEach { map.remove(it) }
        }
        map[incoming.macAddress] = incoming
    }

    fun sortCandidates(items: Collection<BleDeviceCandidate>): List<BleDeviceCandidate> {
        return items.sortedWith(
            compareByDescending<BleDeviceCandidate> { it.isLiveScan }
                .thenByDescending { it.isHuaweiDevice }
                .thenByDescending { it.supportsHeartRate }
                .thenByDescending { it.rssi }
        )
    }
}

package com.example

import com.example.data.model.BleDeviceCandidate
import com.example.hardware.BleDiscoveryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDiscoveryHelperTest {

    @Test
    fun isLikelyHuaweiWearable_detectsBand11ProAndUuid() {
        assertTrue(BleDiscoveryHelper.isLikelyHuaweiWearable("HUAWEI Band 11 Pro-A3F"))
        assertTrue(BleDiscoveryHelper.isLikelyHuaweiWearable("Band 11 Pro-123"))
        assertTrue(BleDiscoveryHelper.isLikelyHuaweiWearable("", hasHuaweiManufacturerData = true))
        assertTrue(
            BleDiscoveryHelper.isLikelyHuaweiWearable(
                name = "Watch",
                serviceUuidStrings = listOf("0000fe86-0000-1000-8000-00805f9b34fb")
            )
        )
        assertFalse(BleDiscoveryHelper.isLikelyHuaweiWearable("Xiaomi Smart Band 9 Active"))
    }

    @Test
    fun shouldIncludeBondedDevice_excludesClassicAndKeepsLeWearables() {
        assertFalse(
            BleDiscoveryHelper.shouldIncludeBondedDevice(
                deviceType = BleDiscoveryHelper.DEVICE_TYPE_CLASSIC,
                name = "HUAWEI Band 11 Pro-A3F",
                hasFitnessServiceUuid = true
            )
        )
        assertTrue(
            BleDiscoveryHelper.shouldIncludeBondedDevice(
                deviceType = BleDiscoveryHelper.DEVICE_TYPE_LE,
                name = "HUAWEI Band 11 Pro-A3F",
                hasFitnessServiceUuid = false
            )
        )
        assertTrue(
            BleDiscoveryHelper.shouldIncludeBondedDevice(
                deviceType = BleDiscoveryHelper.DEVICE_TYPE_DUAL,
                name = "HUAWEI Band 11 Pro-A3F",
                hasFitnessServiceUuid = false
            )
        )
        assertFalse(
            BleDiscoveryHelper.shouldIncludeBondedDevice(
                deviceType = BleDiscoveryHelper.DEVICE_TYPE_DUAL,
                name = "Sony WH-1000XM5",
                hasFitnessServiceUuid = false
            )
        )
    }

    @Test
    fun mergeScannedCandidate_replacesStaleBondedEntryWithLiveRpaScan() {
        val map = LinkedHashMap<String, BleDeviceCandidate>()
        map["AA:BB:CC:DD:EE:01"] = BleDeviceCandidate(
            name = "HUAWEI Band 11 Pro-A3F",
            macAddress = "AA:BB:CC:DD:EE:01",
            rssi = -58,
            isHuaweiDevice = true,
            supportsHeartRate = true,
            supportsRsc = false,
            manufacturerName = "Wearable Device (Paired)",
            advertisedServices = listOf("System Paired"),
            isBonded = true,
            isLiveScan = false
        )

        BleDiscoveryHelper.mergeScannedCandidate(
            map,
            BleDeviceCandidate(
                name = "HUAWEI Band 11 Pro-A3F",
                macAddress = "7C:1E:52:11:22:33",
                rssi = -49,
                isHuaweiDevice = true,
                supportsHeartRate = true,
                supportsRsc = false,
                manufacturerName = "Wearable Device • Live HR",
                advertisedServices = listOf("HR 0x180D"),
                isBonded = false,
                isLiveScan = true
            )
        )

        assertEquals(1, map.size)
        assertTrue(map.containsKey("7C:1E:52:11:22:33"))
        assertFalse(map.containsKey("AA:BB:CC:DD:EE:01"))
    }

    @Test
    fun sortCandidates_prioritizesLiveScannedDevices() {
        val bonded = BleDeviceCandidate(
            name = "Paired Wearable",
            macAddress = "00:11:22:33:44:55",
            rssi = -40,
            isHuaweiDevice = true,
            supportsHeartRate = true,
            supportsRsc = false,
            manufacturerName = "Paired",
            advertisedServices = emptyList(),
            isBonded = true,
            isLiveScan = false
        )
        val live = BleDeviceCandidate(
            name = "HUAWEI Band 11 Pro-A3F",
            macAddress = "66:77:88:99:AA:BB",
            rssi = -62,
            isHuaweiDevice = true,
            supportsHeartRate = true,
            supportsRsc = false,
            manufacturerName = "Live",
            advertisedServices = listOf("HR 0x180D"),
            isBonded = false,
            isLiveScan = true
        )

        val sorted = BleDiscoveryHelper.sortCandidates(listOf(bonded, live))
        assertEquals("66:77:88:99:AA:BB", sorted.first().macAddress)
    }

    @Test
    fun buildConnectPlan_prioritizesAutoAndPassiveForBondedDualDevice() {
        val bondedDualPlan = BleDiscoveryHelper.buildConnectPlan(
            deviceType = BleDiscoveryHelper.DEVICE_TYPE_DUAL,
            isLiveScan = false,
            isSystemGattConnected = false
        )
        assertEquals(
            listOf(
                BleDiscoveryHelper.GattConnectMode.DIRECT_AUTO,
                BleDiscoveryHelper.GattConnectMode.PASSIVE_LE,
                BleDiscoveryHelper.GattConnectMode.PASSIVE_AUTO,
                BleDiscoveryHelper.GattConnectMode.DIRECT_LE
            ),
            bondedDualPlan
        )

        val liveDualPlan = BleDiscoveryHelper.buildConnectPlan(
            deviceType = BleDiscoveryHelper.DEVICE_TYPE_DUAL,
            isLiveScan = true,
            isSystemGattConnected = false
        )
        assertEquals(BleDiscoveryHelper.GattConnectMode.DIRECT_LE, liveDualPlan.first())

        val systemConnectedPlan = BleDiscoveryHelper.buildConnectPlan(
            deviceType = BleDiscoveryHelper.DEVICE_TYPE_DUAL,
            isLiveScan = false,
            isSystemGattConnected = true
        )
        assertEquals(BleDiscoveryHelper.GattConnectMode.PASSIVE_AUTO, systemConnectedPlan.first())

        val lePlan = BleDiscoveryHelper.buildConnectPlan(
            deviceType = BleDiscoveryHelper.DEVICE_TYPE_LE,
            isLiveScan = true,
            isSystemGattConnected = false
        )
        assertEquals(BleDiscoveryHelper.GattConnectMode.DIRECT_LE, lePlan.first())
    }
}

package com.example

import com.example.data.model.GearCategory
import com.example.data.model.GearItem
import com.example.data.model.SportType
import com.example.data.model.WorkoutActivity
import com.example.data.model.toSessionSummary
import com.example.data.repository.VeloStrideRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GearAndActivityEditTest {

    @Test
    fun gearCategoryFromRaw_mapsLegacyAndNewCategories() {
        assertEquals(GearCategory.RUNNING_SHOES, GearCategory.fromRaw("SHOES"))
        assertEquals(GearCategory.ROAD_BIKE, GearCategory.fromRaw("BIKE"))
        assertEquals(GearCategory.TRAIL_RUN_SHOES, GearCategory.fromRaw("TRAIL_RUN_SHOES"))
        assertEquals(GearCategory.HIKING_SHOES, GearCategory.fromRaw("hiking_shoes"))
        assertEquals(GearCategory.WALKING_SHOES, GearCategory.fromRaw("WALKING_SHOES"))
        assertEquals(GearCategory.TRACK_SPIKES, GearCategory.fromRaw("TRACK_SPIKES"))
        assertEquals(GearCategory.GRAVEL_BIKE, GearCategory.fromRaw("GRAVEL_BIKE"))
        assertEquals(GearCategory.MTB_BIKE, GearCategory.fromRaw("MTB_BIKE"))
        assertEquals(GearCategory.WEARABLE_SENSOR, GearCategory.fromRaw("WEARABLE_SENSOR"))
        assertEquals(GearCategory.RUNNING_SHOES, GearCategory.fromRaw("UNKNOWN_GEAR"))
    }

    @Test
    fun gearItemParsedCategory_preservesFlagsAndLabels() {
        val hiking = GearItem(name = "Salomon X Ultra", gearType = GearCategory.HIKING_SHOES.name)
        assertEquals("Hiking Shoes", hiking.parsedCategory.label)
        assertTrue(hiking.parsedCategory.isShoes)
        assertFalse(hiking.parsedCategory.isBike)

        val gravel = GearItem(name = "Canyon Grail", gearType = GearCategory.GRAVEL_BIKE.name)
        assertEquals("Gravel Bike", gravel.parsedCategory.label)
        assertFalse(gravel.parsedCategory.isShoes)
        assertTrue(gravel.parsedCategory.isBike)

        val wearable = GearItem(name = "Wearable Device", gearType = GearCategory.WEARABLE_SENSOR.name)
        assertEquals("Wearable / Sensor", wearable.parsedCategory.label)
        assertFalse(wearable.parsedCategory.isShoes)
        assertFalse(wearable.parsedCategory.isBike)
    }

    @Test
    fun workoutToSessionSummary_preservesMediaCountAndPrivacy() {
        val workout = WorkoutActivity(
            id = "w1",
            title = "Morning Trail Run",
            sportType = SportType.TRAIL_RUN.name,
            distanceMeters = 8400.0,
            movingTimeSeconds = 2700L,
            privateActivity = true,
            gearId = "g1",
            gearName = "Trail Runner",
            mediaUris = listOf("content://media/1", "content://media/2")
        )

        val summary = workout.toSessionSummary()
        assertEquals("Morning Trail Run", summary.title)
        assertEquals(SportType.TRAIL_RUN, summary.sportType)
        assertEquals(2, summary.mediaCount)
        assertTrue(summary.privateActivity)
        assertEquals("Trail Runner", summary.gearName)
    }

    @Test
    fun computeDecrementedDistanceMeters_subtractsAndClampsAtZero() {
        assertEquals(12_500.0, VeloStrideRepository.computeDecrementedDistanceMeters(20_000.0, 7_500.0), 0.01)
        assertEquals(0.0, VeloStrideRepository.computeDecrementedDistanceMeters(5_000.0, 8_200.0), 0.01)
        assertEquals(0.0, VeloStrideRepository.computeDecrementedDistanceMeters(-100.0, 500.0), 0.01)
        assertEquals(10_000.0, VeloStrideRepository.computeDecrementedDistanceMeters(10_000.0, -300.0), 0.01)
    }
}

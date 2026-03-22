package com.mapkit.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MKMapOptionsTest {

    @Test
    fun cameraZoomRange_acceptsValidBounds() {
        val range = MKCameraZoomRange(
            minDistanceMeter = 100.0,
            maxDistanceMeter = 1000.0
        )
        assertEquals(100.0, range.minDistanceMeter ?: -1.0, 0.0)
        assertEquals(1000.0, range.maxDistanceMeter ?: -1.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cameraZoomRange_rejectsInvertedBounds() {
        MKCameraZoomRange(
            minDistanceMeter = 2000.0,
            maxDistanceMeter = 1000.0
        )
    }

    @Test
    fun mapOptions_defaultsToPoiAllAndNoZoomRange() {
        val options = MKMapOptions()
        assertEquals(MKPoiFilter.All, options.poiFilter)
        assertNull(options.cameraZoomRange)
    }
}

package com.studiomk.mapkit.webview.internal

import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKMapRenderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBridgeContractsTest {

    @Test
    fun toInternal_mapsRegionValues() {
        val state = MKMapRenderState(
            region = MKCoordinateRegion.fromCenter(
                center = MKCoordinate(35.681236, 139.767125),
                latitudeDelta = 0.05,
                longitudeDelta = 0.08
            )
        )

        val internal = MKBridgeMapper.toInternal(state)

        assertEquals(35.681236, internal.centerLat, 0.0)
        assertEquals(139.767125, internal.centerLng, 0.0)
        assertEquals(0.05, internal.latDelta, 0.0)
        assertEquals(0.08, internal.lngDelta, 0.0)
    }

    @Test
    fun toRegion_convertsInternalStateToMKCoordinateRegion() {
        val internal = InternalMapState(
            centerLat = 35.681236,
            centerLng = 139.767125,
            latDelta = 0.05,
            lngDelta = 0.08
        )

        val region = MKBridgeMapper.toRegion(internal)

        assertEquals(35.681236, region.center.latitude, 0.0)
        assertEquals(139.767125, region.center.longitude, 0.0)
        assertEquals(0.05, region.span.latitudeDelta, 0.0)
        assertEquals(0.08, region.span.longitudeDelta, 0.0)
    }

    @Test
    fun approximatelyEquals_returnsTrueWithinTolerance() {
        val a = InternalMapState(35.0, 139.0, 0.05, 0.08)
        val b = InternalMapState(
            centerLat = 35.0 + 5e-7,
            centerLng = 139.0 - 5e-7,
            latDelta = 0.05 + 5e-6,
            lngDelta = 0.08 - 5e-6
        )

        assertTrue(a.approximatelyEquals(b))
    }

    @Test
    fun approximatelyEquals_returnsFalseOutsideTolerance() {
        val a = InternalMapState(35.0, 139.0, 0.05, 0.08)
        val b = InternalMapState(
            centerLat = 35.0 + 1e-4,
            centerLng = 139.0,
            latDelta = 0.05,
            lngDelta = 0.08
        )

        assertFalse(a.approximatelyEquals(b))
    }
}

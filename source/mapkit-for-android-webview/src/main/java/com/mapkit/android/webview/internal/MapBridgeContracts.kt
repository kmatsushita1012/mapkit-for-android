package com.mapkit.android.webview.internal

import com.mapkit.android.model.MKCoordinateRegion
import com.mapkit.android.model.MKMapState

internal data class InternalMapState(
    val centerLat: Double,
    val centerLng: Double,
    val latDelta: Double,
    val lngDelta: Double
)

internal object MKBridgeMapper {
    fun toInternal(state: MKMapState): InternalMapState = InternalMapState(
        centerLat = state.region.center.latitude,
        centerLng = state.region.center.longitude,
        latDelta = state.region.span.latitudeDelta,
        lngDelta = state.region.span.longitudeDelta
    )

    fun toRegion(state: InternalMapState): MKCoordinateRegion = MKCoordinateRegion.fromCenter(
        center = com.mapkit.android.model.MKCoordinate(
            latitude = state.centerLat,
            longitude = state.centerLng
        ),
        latitudeDelta = state.latDelta,
        longitudeDelta = state.lngDelta
    )
}

internal fun InternalMapState.approximatelyEquals(other: InternalMapState): Boolean {
    val coordEpsilon = 1e-6
    val spanEpsilon = 1e-5
    return kotlin.math.abs(centerLat - other.centerLat) <= coordEpsilon &&
        kotlin.math.abs(centerLng - other.centerLng) <= coordEpsilon &&
        kotlin.math.abs(latDelta - other.latDelta) <= spanEpsilon &&
        kotlin.math.abs(lngDelta - other.lngDelta) <= spanEpsilon
}

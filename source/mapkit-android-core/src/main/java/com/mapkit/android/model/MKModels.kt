package com.mapkit.android.model

data class MKCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class MKCoordinateSpan(
    val latitudeDelta: Double,
    val longitudeDelta: Double
)

data class MKEdgePadding(
    val top: Double,
    val left: Double,
    val bottom: Double,
    val right: Double
)

data class MKCoordinateRegion(
    val center: MKCoordinate,
    val span: MKCoordinateSpan
) {
    companion object {
        fun fromCenter(
            center: MKCoordinate,
            latitudeDelta: Double,
            longitudeDelta: Double
        ): MKCoordinateRegion = MKCoordinateRegion(
            center = center,
            span = MKCoordinateSpan(latitudeDelta, longitudeDelta)
        )

        fun fromCoordinates(
            coordinates: List<MKCoordinate>,
            edgePadding: MKEdgePadding = MKEdgePadding(0.0, 0.0, 0.0, 0.0)
        ): MKCoordinateRegion {
            require(coordinates.isNotEmpty()) { "coordinates must not be empty" }

            val minLat = coordinates.minOf { it.latitude }
            val maxLat = coordinates.maxOf { it.latitude }
            val minLng = coordinates.minOf { it.longitude }
            val maxLng = coordinates.maxOf { it.longitude }

            val center = MKCoordinate(
                latitude = (minLat + maxLat) / 2.0,
                longitude = (minLng + maxLng) / 2.0
            )
            return fromCenter(
                center = center,
                latitudeDelta = (maxLat - minLat) + edgePadding.top + edgePadding.bottom,
                longitudeDelta = (maxLng - minLng) + edgePadding.left + edgePadding.right
            )
        }
    }
}

sealed interface MKImageSource {
    data class Url(val value: String) : MKImageSource
    data class Base64Png(val value: String) : MKImageSource
    data class ResourceName(val value: String) : MKImageSource
}

sealed interface MKAnnotationStyle {
    data class DefaultPin(val tintHex: String? = null) : MKAnnotationStyle
    data class DefaultMarker(
        val tintHex: String? = null,
        val glyphText: String? = null
    ) : MKAnnotationStyle
    data class Image(
        val source: MKImageSource,
        val widthDp: Int,
        val heightDp: Int,
        val anchorX: Double = 0.5,
        val anchorY: Double = 1.0
    ) : MKAnnotationStyle
}

data class MKAnnotation(
    val id: String,
    val coordinate: MKCoordinate,
    val title: String? = null,
    val subtitle: String? = null,
    val isVisible: Boolean = true,
    val isSelected: Boolean = false,
    val style: MKAnnotationStyle = MKAnnotationStyle.DefaultMarker()
)

data class MKOverlayStyle(
    val strokeColorHex: String = "#007AFF",
    val strokeWidth: Double = 2.0,
    val fillColorHex: String? = null,
    val lineDashPattern: List<Double>? = null
)

sealed interface MKOverlay {
    val id: String
    val isVisible: Boolean
    val zIndex: Int
    val style: MKOverlayStyle
}

data class MKPolylineOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKPolygonOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    val holes: List<List<MKCoordinate>> = emptyList(),
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKCircleOverlay(
    override val id: String,
    val center: MKCoordinate,
    val radiusMeter: Double,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

enum class MKMapStyle {
    standard,
    mutedStandard,
    satellite,
    hybrid
}

enum class MKAppearanceOption {
    auto,
    light,
    dark
}

enum class MKMapLanguage {
    auto,
    ja,
    en
}

data class MKCameraZoomRange(
    val minDistanceMeter: Double? = null,
    val maxDistanceMeter: Double? = null
) {
    init {
        require(minDistanceMeter == null || minDistanceMeter >= 0.0) {
            "minDistanceMeter must be >= 0"
        }
        require(maxDistanceMeter == null || maxDistanceMeter >= 0.0) {
            "maxDistanceMeter must be >= 0"
        }
        if (minDistanceMeter != null && maxDistanceMeter != null) {
            require(minDistanceMeter <= maxDistanceMeter) {
                "minDistanceMeter must be <= maxDistanceMeter"
            }
        }
    }
}

sealed interface MKPoiFilter {
    data object All : MKPoiFilter
    data object None : MKPoiFilter
    data class Include(val categories: List<String>) : MKPoiFilter
    data class Exclude(val categories: List<String>) : MKPoiFilter
}

data class MKUserLocationOptions(
    val isEnabled: Boolean = false,
    val followsHeading: Boolean = false,
    val showsAccuracyRing: Boolean = true
)

data class MKMapOptions(
    val mapStyle: MKMapStyle = MKMapStyle.standard,
    val showsCompass: Boolean = true,
    val showsScale: Boolean = false,
    val showsZoomControl: Boolean = false,
    val showsMapTypeControl: Boolean = false,
    val showsPointsOfInterest: Boolean = true,
    val poiFilter: MKPoiFilter = MKPoiFilter.All,
    val cameraZoomRange: MKCameraZoomRange? = null,
    val isRotateEnabled: Boolean = true,
    val isScrollEnabled: Boolean = true,
    val isZoomEnabled: Boolean = true,
    val isPitchEnabled: Boolean = true,
    val appearance: MKAppearanceOption = MKAppearanceOption.auto,
    val language: MKMapLanguage = MKMapLanguage.auto,
    val userLocation: MKUserLocationOptions = MKUserLocationOptions()
)

data class MKMapState(
    val region: MKCoordinateRegion,
    val annotations: List<MKAnnotation> = emptyList(),
    val overlays: List<MKOverlay> = emptyList(),
    val options: MKMapOptions = MKMapOptions()
)

sealed interface MKMapErrorCause {
    data object NotInitialized : MKMapErrorCause
    data object TokenUnavailable : MKMapErrorCause
    data class BridgeFailure(val message: String) : MKMapErrorCause
}

sealed interface MKMapEvent {
    data object MapLoaded : MKMapEvent
    data class MapError(val cause: MKMapErrorCause) : MKMapEvent
    data class RegionDidChange(val region: MKCoordinateRegion, val settled: Boolean) : MKMapEvent
    data class MapTapped(val coordinate: MKCoordinate) : MKMapEvent
    data class LongPress(val coordinate: MKCoordinate) : MKMapEvent
    data class AnnotationTapped(val id: String) : MKMapEvent
    data class OverlayTapped(val id: String) : MKMapEvent
    data class UserLocationUpdated(val coordinate: MKCoordinate) : MKMapEvent
}

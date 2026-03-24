package com.studiomk.mapkit.model

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
    data class Marker(
        val tintHex: String = "#FF3B30",
        val glyphText: String? = null,
        val glyphImageSource: MKImageSource? = MKImageSource.ResourceName("pin")
    ) : MKAnnotationStyle
    data class Image(
        val source: MKImageSource,
        val widthDp: Int,
        val heightDp: Int,
        val anchorX: Double = 0.5,
        val anchorY: Double = 1.0
    ) : MKAnnotationStyle
}

open class MKAnnotation(
    open val id: String,
    open val coordinate: MKCoordinate,
    open val title: String? = null,
    open val subtitle: String? = null,
    open val isVisible: Boolean = true,
    isSelected: Boolean = false
) {
    private var selectedState: Boolean = isSelected

    open val isSelected: Boolean
        get() = selectedState

    internal fun setSelectedFromLibrary(value: Boolean) {
        selectedState = value
    }

    open fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker()

    protected open fun extraEquals(other: MKAnnotation): Boolean = true

    protected open fun extraHashCode(): Int = 0

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MKAnnotation

        return id == other.id &&
            coordinate == other.coordinate &&
            title == other.title &&
            subtitle == other.subtitle &&
            isVisible == other.isVisible &&
            extraEquals(other)
    }

    final override fun hashCode(): Int = listOf(
        id,
        coordinate,
        title,
        subtitle,
        isVisible,
        extraHashCode()
    ).hashCode()

    override fun toString(): String = "${this::class.simpleName}(id=$id)"
}

class MKMarkerAnnotation(
    override val id: String,
    override val coordinate: MKCoordinate,
    override val title: String? = null,
    override val subtitle: String? = null,
    override val isVisible: Boolean = true,
    isSelected: Boolean = false,
    val tintHex: String = "#FF3B30",
    val glyphText: String? = null,
    val glyphImageSource: MKImageSource? = MKImageSource.ResourceName("pin")
) : MKAnnotation(
    id = id,
    coordinate = coordinate,
    title = title,
    subtitle = subtitle,
    isVisible = isVisible,
    isSelected = isSelected
) {
    override fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker(
        tintHex = tintHex,
        glyphText = glyphText,
        glyphImageSource = glyphImageSource
    )

    override fun extraEquals(other: MKAnnotation): Boolean {
        other as MKMarkerAnnotation
        return tintHex == other.tintHex &&
            glyphText == other.glyphText &&
            glyphImageSource == other.glyphImageSource
    }

    override fun extraHashCode(): Int = listOf(
        tintHex,
        glyphText,
        glyphImageSource
    ).hashCode()
}

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
) {
    companion object {
        private val sharedCommandChannel = MKMapCommandChannel()
    }

    @Synchronized
    fun selectAnnotation(annotation: MKAnnotation, animated: Boolean = true) {
        selectAnnotation(annotation.id, animated = animated)
    }

    @Synchronized
    fun selectAnnotation(annotationId: String, animated: Boolean = true) {
        if (annotations.none { it.id == annotationId }) return
        enqueueOrDispatch(MKMapCommand.SelectAnnotation(annotationId, animated))
    }

    @Synchronized
    fun deselectAnnotation(annotation: MKAnnotation, animated: Boolean = false) {
        deselectAnnotation(annotation.id, animated = animated)
    }

    @Synchronized
    fun deselectAnnotation(annotationId: String, animated: Boolean = false) {
        if (annotations.none { it.id == annotationId }) return
        enqueueOrDispatch(MKMapCommand.DeselectAnnotation(annotationId, animated))
    }

    @Synchronized
    fun bindCommandDispatcher(dispatcher: (MKMapCommand) -> Unit) {
        sharedCommandChannel.bindDispatcher(dispatcher)
    }

    @Synchronized
    fun clearCommandDispatcher() {
        sharedCommandChannel.clearDispatcher()
    }

    @Synchronized
    fun syncSelectedFromMap(annotationId: String, isSelected: Boolean): MKAnnotation? {
        var target: MKAnnotation? = null
        annotations.forEach { annotation ->
            val nextSelected = if (isSelected) {
                annotation.id == annotationId
            } else {
                if (annotation.id != annotationId) {
                    annotation.isSelected
                } else {
                    false
                }
            }
            annotation.setSelectedFromLibrary(nextSelected)
            if (annotation.id == annotationId) {
                target = annotation
            }
        }
        return target
    }

    @Synchronized
    private fun enqueueOrDispatch(command: MKMapCommand) {
        sharedCommandChannel.dispatch(command)
    }
}

class MKMapCommandChannel {
    private val pendingCommands = ArrayDeque<MKMapCommand>()
    private var dispatcher: ((MKMapCommand) -> Unit)? = null

    @Synchronized
    fun bindDispatcher(nextDispatcher: (MKMapCommand) -> Unit) {
        dispatcher = nextDispatcher
        while (pendingCommands.isNotEmpty()) {
            nextDispatcher(pendingCommands.removeFirst())
        }
    }

    @Synchronized
    fun clearDispatcher() {
        dispatcher = null
    }

    @Synchronized
    fun dispatch(command: MKMapCommand) {
        val currentDispatcher = dispatcher
        if (currentDispatcher != null) {
            currentDispatcher(command)
        } else {
            pendingCommands.addLast(command)
        }
    }
}

sealed interface MKMapCommand {
    data class SelectAnnotation(val annotationId: String, val animated: Boolean = true) : MKMapCommand
    data class DeselectAnnotation(val annotationId: String, val animated: Boolean = false) : MKMapCommand
}

sealed interface MKMapErrorCause {
    data object NotInitialized : MKMapErrorCause
    data object TokenUnavailable : MKMapErrorCause
    data class BridgeFailure(val message: String) : MKMapErrorCause
}

sealed interface MKMapEvent {
    data object MapLoaded : MKMapEvent
    data class MapError(val cause: MKMapErrorCause) : MKMapEvent
    data class RegionWillChange(val region: MKCoordinateRegion) : MKMapEvent
    data class RegionDidChange(val region: MKCoordinateRegion) : MKMapEvent
    data class MapTapped(val coordinate: MKCoordinate) : MKMapEvent
    data class LongPress(val coordinate: MKCoordinate) : MKMapEvent
    data class AnnotationSelected(val annotation: MKAnnotation) : MKMapEvent {
        val id: String get() = annotation.id
    }
    data class AnnotationDeselected(val annotation: MKAnnotation) : MKMapEvent {
        val id: String get() = annotation.id
    }
    data class OverlayTapped(val overlay: MKOverlay) : MKMapEvent {
        val id: String get() = overlay.id
    }
    data class UserLocationUpdated(val coordinate: MKCoordinate) : MKMapEvent
}

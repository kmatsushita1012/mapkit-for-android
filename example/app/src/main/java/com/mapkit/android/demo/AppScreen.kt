package com.studiomk.mapkit.demo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studiomk.mapkit.api.MKMapView
import com.studiomk.mapkit.api.rememberMKMapController
import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKAppearanceOption
import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKImageSource
import com.studiomk.mapkit.model.MKMapLanguage
import com.studiomk.mapkit.model.MKMapOptions
import com.studiomk.mapkit.model.MKMapStyle
import com.studiomk.mapkit.model.MKOverlayStyle
import com.studiomk.mapkit.model.MKPoiFilter
import com.studiomk.mapkit.model.MKUserLocationOptions
import java.util.UUID

private data class Point(
    val id: String,
    val coordinate: MKCoordinate,
    val title: String?,
    val subtitle: String?,
    val isVisible: Boolean = true,
    val isDraggable: Boolean = true,
    val style: MKAnnotationStyle
)

private data class Segment(
    val id: String,
    val points: List<MKCoordinate>,
    val style: MKOverlayStyle,
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)

private data class Area(
    val id: String,
    val points: List<MKCoordinate>,
    val holes: List<List<MKCoordinate>> = emptyList(),
    val style: MKOverlayStyle,
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)

private data class CircleArea(
    val id: String,
    val center: MKCoordinate,
    val radiusMeter: Double,
    val style: MKOverlayStyle,
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreen() {
    val controller = rememberMKMapController()

    var region by remember {
        mutableStateOf(
            MKCoordinateRegion.fromCenter(
                center = MKCoordinate(35.681236, 139.767125),
                latitudeDelta = 0.05,
                longitudeDelta = 0.05
            )
        )
    }
    var pointEntities by remember {
        mutableStateOf(
            listOf(
                Point(
                    id = "tokyo-station",
                    coordinate = MKCoordinate(35.681236, 139.767125),
                    title = "Tokyo Station",
                    subtitle = null,
                    style = MKAnnotationStyle.Marker()
                )
            )
        )
    }
    var segmentEntities by remember { mutableStateOf<List<Segment>>(emptyList()) }
    var areaEntities by remember { mutableStateOf<List<Area>>(emptyList()) }
    var circleAreaEntities by remember { mutableStateOf<List<CircleArea>>(emptyList()) }

    var options by remember {
        mutableStateOf(
            MKMapOptions(
                userLocation = MKUserLocationOptions(isEnabled = false),
                showsZoomControl = true
            )
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeDrawMode by remember { mutableStateOf(DrawMode.None) }
    var draftPoints by remember { mutableStateOf<List<MKCoordinate>>(emptyList()) }
    var lastEventText by remember { mutableStateOf("No events yet") }
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }

    // 要望: press初期アクションは none, longpress は annotation add
    var tapAction by remember { mutableStateOf(DrawMode.None) }
    var longPressAction by remember { mutableStateOf(DrawMode.Annotation) }
    var immediateDeselectOnAnnotationTap by remember { mutableStateOf(false) }

    var annotationVisualStyle by remember { mutableStateOf(AnnotationVisualStyle.Marker) }
    var annotationTitle by remember { mutableStateOf("Pinned") }
    var annotationSubtitle by remember { mutableStateOf("") }
    var annotationTintHex by remember { mutableStateOf("#0ea5e9") }
    var annotationGlyph by remember { mutableStateOf("A") }
    var markerGlyphMode by remember { mutableStateOf(MarkerGlyphMode.GlyphText) }
    var customImageColorHex by remember { mutableStateOf("#f97316") }

    var baseConfigExpanded by remember { mutableStateOf(false) }
    var annotationConfigExpanded by remember { mutableStateOf(false) }
    var polylineConfigExpanded by remember { mutableStateOf(false) }
    var polygonConfigExpanded by remember { mutableStateOf(false) }
    var circleConfigExpanded by remember { mutableStateOf(false) }

    var polylineColorHex by remember { mutableStateOf("#0ea5e9") }
    var polylineWidthText by remember { mutableStateOf("4.0") }
    var polylineDashed by remember { mutableStateOf(false) }
    var polylineDashLengthText by remember { mutableStateOf("10") }
    var polylineGapLengthText by remember { mutableStateOf("6") }

    var polygonStrokeColorHex by remember { mutableStateOf("#22c55e") }
    var polygonFillColorHex by remember { mutableStateOf("#22c55e") }
    var polygonFillAlphaText by remember { mutableStateOf("0.20") }
    var polygonStrokeWidthText by remember { mutableStateOf("3.0") }
    var polygonDashed by remember { mutableStateOf(false) }
    var polygonDashLengthText by remember { mutableStateOf("10") }
    var polygonGapLengthText by remember { mutableStateOf("6") }

    var circleStrokeColorHex by remember { mutableStateOf("#2563eb") }
    var circleFillColorHex by remember { mutableStateOf("#2563eb") }
    var circleFillAlphaText by remember { mutableStateOf("0.20") }
    var circleStrokeWidthText by remember { mutableStateOf("3.0") }
    var circleRadiusText by remember { mutableStateOf("120") }

    val selectedTab = if (selectedTabIndex == 0) AppTab.Map else AppTab.Settings

    val draftSegment = if (activeDrawMode == DrawMode.Polyline && draftPoints.size >= 2) {
        Segment(
            id = "draft-polyline",
            points = draftPoints,
            style = buildPolylineOverlayStyle(
                colorHex = polylineColorHex,
                widthText = polylineWidthText,
                dashed = polylineDashed,
                dashLengthText = polylineDashLengthText,
                gapLengthText = polylineGapLengthText
            )
        )
    } else {
        null
    }

    val draftArea = if (activeDrawMode == DrawMode.Polygon && draftPoints.size >= 3) {
        Area(
            id = "draft-polygon",
            points = draftPoints,
            style = buildPolygonOverlayStyle(
                strokeColorHex = polygonStrokeColorHex,
                fillColorHex = polygonFillColorHex,
                fillAlphaText = polygonFillAlphaText,
                widthText = polygonStrokeWidthText,
                dashed = polygonDashed,
                dashLengthText = polygonDashLengthText,
                gapLengthText = polygonGapLengthText
            )
        )
    } else {
        null
    }

    val draftCircleArea = if (activeDrawMode == DrawMode.Circle && draftPoints.isNotEmpty()) {
        CircleArea(
            id = "draft-circle",
            center = draftPoints.last(),
            radiusMeter = parsePositiveDouble(circleRadiusText, fallback = 120.0),
            style = buildCircleOverlayStyle(
                strokeColorHex = circleStrokeColorHex,
                fillColorHex = circleFillColorHex,
                fillAlphaText = circleFillAlphaText,
                widthText = circleStrokeWidthText
            )
        )
    } else {
        null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val enabled = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        options = options.copy(
            userLocation = options.userLocation.copy(isEnabled = enabled)
        )
    }

    fun addGeometryPoint(action: DrawMode, coordinate: MKCoordinate) {
        when (action) {
            DrawMode.None -> Unit
            DrawMode.Annotation -> {
                val normalizedTitle = annotationTitle.ifBlank { "Pinned" }
                val style = when (annotationVisualStyle) {
                    AnnotationVisualStyle.Marker -> MKAnnotationStyle.Marker(
                        tintHex = annotationTintHex.ifBlank { "#FF3B30" },
                        glyphText = if (markerGlyphMode == MarkerGlyphMode.GlyphText) {
                            annotationGlyph.ifBlank { null }
                        } else {
                            null
                        },
                        glyphImageSource = if (markerGlyphMode == MarkerGlyphMode.GlyphImage) {
                            MKImageSource.Url("https://appassets.androidplatform.net/assets/demo/custom-glyph.svg")
                        } else {
                            null
                        }
                    )

                    AnnotationVisualStyle.Custom -> MKAnnotationStyle.Image(
                        source = MKImageSource.Base64Png(
                            renderFilledCircleBase64Png(
                                fillColorHex = customImageColorHex,
                                sizePx = 48
                            )
                        ),
                        widthDp = 48,
                        heightDp = 48
                    )
                }
                pointEntities = pointEntities + Point(
                    id = UUID.randomUUID().toString(),
                    coordinate = coordinate,
                    title = normalizedTitle,
                    subtitle = annotationSubtitle.ifBlank { null },
                    style = style
                )
            }

            DrawMode.Polyline,
            DrawMode.Polygon -> {
                if (activeDrawMode != action) {
                    activeDrawMode = action
                    draftPoints = emptyList()
                }
                draftPoints = draftPoints + coordinate
            }

            DrawMode.Circle -> {
                activeDrawMode = DrawMode.Circle
                draftPoints = listOf(coordinate)
            }
        }
    }

    val canConfirm = (activeDrawMode == DrawMode.Polyline && draftPoints.size >= 2) ||
        (activeDrawMode == DrawMode.Polygon && draftPoints.size >= 3) ||
        (activeDrawMode == DrawMode.Circle && draftPoints.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTab == AppTab.Map,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Map") }
            )
            Tab(
                selected = selectedTab == AppTab.Settings,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Settings") }
            )
        }

        when (selectedTab) {
            AppTab.Map -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Last Event: $lastEventText",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                    EnumSelector(
                        label = "Tap Action",
                        value = tapAction,
                        values = DrawMode.entries,
                        onSelected = { tapAction = it }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { controller.deselectAnnotation(animated = false) },
                            enabled = selectedAnnotationId != null
                        ) {
                            Text("Deselect")
                        }
                        Button(
                            onClick = { if (draftPoints.isNotEmpty()) draftPoints = draftPoints.dropLast(1) },
                            enabled = draftPoints.isNotEmpty()
                        ) {
                            Text("Undo")
                        }
                        Button(
                            onClick = {
                                draftPoints = emptyList()
                                activeDrawMode = DrawMode.None
                            },
                            enabled = draftPoints.isNotEmpty() || activeDrawMode != DrawMode.None
                        ) {
                            Text("Clear")
                        }
                        Button(
                            onClick = {
                                val id = UUID.randomUUID().toString()
                                when (activeDrawMode) {
                                    DrawMode.Polyline -> {
                                        segmentEntities = segmentEntities + Segment(
                                            id = id,
                                            points = draftPoints,
                                            style = buildPolylineOverlayStyle(
                                                colorHex = polylineColorHex,
                                                widthText = polylineWidthText,
                                                dashed = polylineDashed,
                                                dashLengthText = polylineDashLengthText,
                                                gapLengthText = polylineGapLengthText
                                            )
                                        )
                                    }

                                    DrawMode.Polygon -> {
                                        areaEntities = areaEntities + Area(
                                            id = id,
                                            points = draftPoints,
                                            style = buildPolygonOverlayStyle(
                                                strokeColorHex = polygonStrokeColorHex,
                                                fillColorHex = polygonFillColorHex,
                                                fillAlphaText = polygonFillAlphaText,
                                                widthText = polygonStrokeWidthText,
                                                dashed = polygonDashed,
                                                dashLengthText = polygonDashLengthText,
                                                gapLengthText = polygonGapLengthText
                                            )
                                        )
                                    }

                                    DrawMode.Circle -> {
                                        circleAreaEntities = circleAreaEntities + CircleArea(
                                            id = id,
                                            center = draftPoints.last(),
                                            radiusMeter = parsePositiveDouble(circleRadiusText, fallback = 120.0),
                                            style = buildCircleOverlayStyle(
                                                strokeColorHex = circleStrokeColorHex,
                                                fillColorHex = circleFillColorHex,
                                                fillAlphaText = circleFillAlphaText,
                                                widthText = circleStrokeWidthText
                                            )
                                        )
                                    }

                                    else -> Unit
                                }
                                draftPoints = emptyList()
                                activeDrawMode = DrawMode.None
                            },
                            enabled = canConfirm
                        ) {
                            Text("Confirm")
                        }
                    }
                }

                MKMapView(
                    region = region,
                    controller = controller,
                    options = options,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onRegionDidChange = { changed ->
                        region = changed
                        val center = changed.center
                        lastEventText =
                            "RegionDidChange(center=${center.latitude.format6()},${center.longitude.format6()})"
                    },
                    onMapTapped = { coordinate ->
                        addGeometryPoint(tapAction, coordinate)
                        lastEventText =
                            "MapTapped(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
                    },
                    onLongPress = { coordinate ->
                        addGeometryPoint(longPressAction, coordinate)
                        lastEventText =
                            "LongPress(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
                    },
                    onMapLoaded = {
                        lastEventText = "MapLoaded"
                    },
                    onMapError = { cause ->
                        lastEventText = "MapError: $cause"
                    }
                ) {
                    pointEntities.forEach { point ->
                        MKAnnotation(
                            id = point.id,
                            coordinate = point.coordinate,
                            title = point.title,
                            subtitle = point.subtitle,
                            isVisible = point.isVisible,
                            isDraggable = point.isDraggable,
                            style = point.style,
                            onSelected = {
                                selectedAnnotationId = point.id
                                lastEventText = "AnnotationSelected(id=${point.id})"
                                if (immediateDeselectOnAnnotationTap) {
                                    controller.deselectAnnotation(animated = false)
                                }
                            },
                            onDeselected = {
                                if (selectedAnnotationId == point.id) {
                                    selectedAnnotationId = null
                                }
                                lastEventText = "AnnotationDeselected(id=${point.id})"
                            },
                            onDrag = { nextCoordinate ->
                                pointEntities = pointEntities.map {
                                    if (it.id == point.id) it.copy(coordinate = nextCoordinate) else it
                                }
                            }
                        )
                    }

                    segmentEntities.forEach { segment ->
                        MKPolylineOverlay(
                            id = segment.id,
                            points = segment.points,
                            style = segment.style,
                            isVisible = segment.isVisible,
                            zIndex = segment.zIndex,
                            onTap = { lastEventText = "OverlayTapped(id=${segment.id})" }
                        )
                    }
                    areaEntities.forEach { area ->
                        MKPolygonOverlay(
                            id = area.id,
                            points = area.points,
                            holes = area.holes,
                            style = area.style,
                            isVisible = area.isVisible,
                            zIndex = area.zIndex,
                            onTap = { lastEventText = "OverlayTapped(id=${area.id})" }
                        )
                    }
                    circleAreaEntities.forEach { circleArea ->
                        MKCircleOverlay(
                            id = circleArea.id,
                            center = circleArea.center,
                            radiusMeter = circleArea.radiusMeter,
                            style = circleArea.style,
                            isVisible = circleArea.isVisible,
                            zIndex = circleArea.zIndex,
                            onTap = { lastEventText = "OverlayTapped(id=${circleArea.id})" }
                        )
                    }

                    draftSegment?.let { segment ->
                        MKPolylineOverlay(
                            id = segment.id,
                            points = segment.points,
                            style = segment.style,
                            isVisible = segment.isVisible,
                            zIndex = segment.zIndex
                        )
                    }
                    draftArea?.let { area ->
                        MKPolygonOverlay(
                            id = area.id,
                            points = area.points,
                            holes = area.holes,
                            style = area.style,
                            isVisible = area.isVisible,
                            zIndex = area.zIndex
                        )
                    }
                    draftCircleArea?.let { circleArea ->
                        MKCircleOverlay(
                            id = circleArea.id,
                            center = circleArea.center,
                            radiusMeter = circleArea.radiusMeter,
                            style = circleArea.style,
                            isVisible = circleArea.isVisible,
                            zIndex = circleArea.zIndex
                        )
                    }
                }
            }

            AppTab.Settings -> {
                val poiFilterPreset = when (val f = options.poiFilter) {
                    MKPoiFilter.All -> PoiFilterPreset.all
                    MKPoiFilter.None -> PoiFilterPreset.none
                    is MKPoiFilter.Include -> {
                        val s = f.categories.map { it.lowercase() }.toSet()
                        if (s == setOf("cafe", "park")) PoiFilterPreset.includeCafePark else PoiFilterPreset.all
                    }

                    is MKPoiFilter.Exclude -> {
                        val s = f.categories.map { it.lowercase() }.toSet()
                        if (s == setOf("cafe", "park")) PoiFilterPreset.excludeCafePark else PoiFilterPreset.all
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpandableSectionHeader(
                        title = "Base Config",
                        expanded = baseConfigExpanded,
                        onExpandedChange = { baseConfigExpanded = it }
                    )
                    if (baseConfigExpanded) {
                        EnumSelector(
                            label = "Map Style",
                            value = options.mapStyle,
                            values = MKMapStyle.entries,
                            onSelected = { options = options.copy(mapStyle = it) }
                        )
                        EnumSelector(
                            label = "Language",
                            value = options.language,
                            values = MKMapLanguage.entries,
                            onSelected = { options = options.copy(language = it) }
                        )
                        EnumSelector(
                            label = "Appearance",
                            value = options.appearance,
                            values = MKAppearanceOption.entries,
                            onSelected = { options = options.copy(appearance = it) }
                        )
                        EnumSelector(
                            label = "POI Filter",
                            value = poiFilterPreset,
                            values = PoiFilterPreset.entries,
                            onSelected = { preset ->
                                val filter = when (preset) {
                                    PoiFilterPreset.all -> MKPoiFilter.All
                                    PoiFilterPreset.none -> MKPoiFilter.None
                                    PoiFilterPreset.includeCafePark -> MKPoiFilter.Include(listOf("cafe", "park"))
                                    PoiFilterPreset.excludeCafePark -> MKPoiFilter.Exclude(listOf("cafe", "park"))
                                }
                                options = options.copy(poiFilter = filter)
                            }
                        )
                        EnumSelector(
                            label = "Long Press Action",
                            value = longPressAction,
                            values = DrawMode.entries,
                            onSelected = { longPressAction = it }
                        )
                        ToggleRow(
                            label = "Immediate Deselect on Annotation Tap",
                            checked = immediateDeselectOnAnnotationTap,
                            onCheckedChange = { immediateDeselectOnAnnotationTap = it }
                        )

                        ToggleRow(
                            label = "User Location",
                            checked = options.userLocation.isEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    options = options.copy(
                                        userLocation = options.userLocation.copy(isEnabled = false)
                                    )
                                }
                            }
                        )
                        ToggleRow(
                            label = "Compass",
                            checked = options.showsCompass,
                            onCheckedChange = { options = options.copy(showsCompass = it) }
                        )
                        ToggleRow(
                            label = "Scale",
                            checked = options.showsScale,
                            onCheckedChange = { options = options.copy(showsScale = it) }
                        )
                        ToggleRow(
                            label = "Points Of Interest",
                            checked = options.showsPointsOfInterest,
                            onCheckedChange = { options = options.copy(showsPointsOfInterest = it) }
                        )
                        ToggleRow(
                            label = "Zoom Control",
                            checked = options.showsZoomControl,
                            onCheckedChange = { options = options.copy(showsZoomControl = it) }
                        )
                    }

                    ExpandableSectionHeader(
                        title = "Annotation Config",
                        expanded = annotationConfigExpanded,
                        onExpandedChange = { annotationConfigExpanded = it }
                    )
                    if (annotationConfigExpanded) {
                        EnumSelector(
                            label = "Annotation Style",
                            value = annotationVisualStyle,
                            values = AnnotationVisualStyle.entries,
                            onSelected = { annotationVisualStyle = it }
                        )
                        OutlinedTextField(
                            value = annotationTitle,
                            onValueChange = { annotationTitle = it },
                            label = { Text("Annotation Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = annotationSubtitle,
                            onValueChange = { annotationSubtitle = it },
                            label = { Text("Annotation Subtitle") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (annotationVisualStyle == AnnotationVisualStyle.Marker) {
                            EnumSelector(
                                label = "Marker Glyph Mode",
                                value = markerGlyphMode,
                                values = MarkerGlyphMode.entries,
                                onSelected = { markerGlyphMode = it }
                            )
                            OutlinedTextField(
                                value = annotationTintHex,
                                onValueChange = { annotationTintHex = it },
                                label = { Text("Marker Tint (hex)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (markerGlyphMode == MarkerGlyphMode.GlyphText) {
                                OutlinedTextField(
                                    value = annotationGlyph,
                                    onValueChange = { annotationGlyph = it.take(2) },
                                    label = { Text("Marker Glyph") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = customImageColorHex,
                                onValueChange = { customImageColorHex = it },
                                label = { Text("Custom Circle Color (hex)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    ExpandableSectionHeader(
                        title = "Polyline Config",
                        expanded = polylineConfigExpanded,
                        onExpandedChange = { polylineConfigExpanded = it }
                    )
                    if (polylineConfigExpanded) {
                        OutlinedTextField(
                            value = polylineColorHex,
                            onValueChange = { polylineColorHex = it },
                            label = { Text("Polyline Color (hex)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = polylineWidthText,
                            onValueChange = { polylineWidthText = it },
                            label = { Text("Polyline Width") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ToggleRow(
                            label = "Polyline Dashed",
                            checked = polylineDashed,
                            onCheckedChange = { polylineDashed = it }
                        )
                    }

                    ExpandableSectionHeader(
                        title = "Polygon Config",
                        expanded = polygonConfigExpanded,
                        onExpandedChange = { polygonConfigExpanded = it }
                    )
                    if (polygonConfigExpanded) {
                        OutlinedTextField(
                            value = polygonStrokeColorHex,
                            onValueChange = { polygonStrokeColorHex = it },
                            label = { Text("Polygon Stroke Color (hex)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = polygonFillColorHex,
                            onValueChange = { polygonFillColorHex = it },
                            label = { Text("Polygon Fill Color (hex)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = polygonFillAlphaText,
                            onValueChange = { polygonFillAlphaText = it },
                            label = { Text("Polygon Fill Alpha (0.0-1.0)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ExpandableSectionHeader(
                        title = "Circle Config",
                        expanded = circleConfigExpanded,
                        onExpandedChange = { circleConfigExpanded = it }
                    )
                    if (circleConfigExpanded) {
                        OutlinedTextField(
                            value = circleStrokeColorHex,
                            onValueChange = { circleStrokeColorHex = it },
                            label = { Text("Circle Stroke Color (hex)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = circleFillColorHex,
                            onValueChange = { circleFillColorHex = it },
                            label = { Text("Circle Fill Color (hex)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = circleFillAlphaText,
                            onValueChange = { circleFillAlphaText = it },
                            label = { Text("Circle Fill Alpha (0.0-1.0)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = circleRadiusText,
                            onValueChange = { circleRadiusText = it },
                            label = { Text("Circle Radius (meter)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

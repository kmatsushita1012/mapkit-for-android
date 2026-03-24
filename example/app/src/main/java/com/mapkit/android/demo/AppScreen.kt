package com.studiomk.mapkit.demo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.studiomk.mapkit.model.MKAnnotation
import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKAppearanceOption
import com.studiomk.mapkit.model.MKCameraZoomRange
import com.studiomk.mapkit.model.MKCircleOverlay
import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKImageSource
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKMapLanguage
import com.studiomk.mapkit.model.MKMapOptions
import com.studiomk.mapkit.model.MKMapState
import com.studiomk.mapkit.model.MKMapStyle
import com.studiomk.mapkit.model.MKMarkerAnnotation
import com.studiomk.mapkit.model.MKOverlay
import com.studiomk.mapkit.model.MKPoiFilter
import com.studiomk.mapkit.model.MKPolygonOverlay
import com.studiomk.mapkit.model.MKPolylineOverlay
import com.studiomk.mapkit.model.MKUserLocationOptions
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreen() {
    var region by remember {
        mutableStateOf(
            MKCoordinateRegion.fromCenter(
                center = MKCoordinate(35.681236, 139.767125),
                latitudeDelta = 0.05,
                longitudeDelta = 0.05
            )
        )
    }
    var annotations by remember {
        mutableStateOf<List<MKAnnotation>>(
            listOf(
                MKMarkerAnnotation(
                    id = "tokyo-station",
                    coordinate = MKCoordinate(35.681236, 139.767125),
                    title = "Tokyo Station"
                )
            )
        )
    }
    var committedOverlays by remember { mutableStateOf<List<MKOverlay>>(emptyList()) }
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

    var tapAction by remember { mutableStateOf(DrawMode.None) }
    var longPressAction by remember { mutableStateOf(DrawMode.Annotation) }
    var immediateDeselectOnAnnotationTap by remember { mutableStateOf(true) }
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

    val draftOverlay = when (activeDrawMode) {
        DrawMode.Polyline -> if (draftPoints.size >= 2) {
            listOf(
                MKPolylineOverlay(
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
            )
        } else {
            emptyList()
        }

        DrawMode.Polygon -> if (draftPoints.size >= 3) {
            listOf(
                MKPolygonOverlay(
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
            )
        } else {
            emptyList()
        }

        DrawMode.Circle -> if (draftPoints.isNotEmpty()) {
            listOf(
                MKCircleOverlay(
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
            )
        } else {
            emptyList()
        }

        else -> emptyList()
    }

    val mapState = MKMapState(
        region = region,
        annotations = annotations,
        overlays = committedOverlays + draftOverlay,
        options = options
    )
    val requestImmediateDeselect: (MKAnnotation) -> Unit = { annotation ->
        mapState.deselectAnnotation(annotation.id, animated = false)
    }
    val requestExplicitDeselect: () -> Unit = {
        selectedAnnotationId?.let { annotationId ->
            mapState.deselectAnnotation(annotationId, animated = false)
        }
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
                    Button(
                        onClick = requestExplicitDeselect,
                        enabled = selectedAnnotationId != null
                    ) {
                        Text("Deselect Selected Annotation")
                    }
                    if (activeDrawMode == DrawMode.Polyline || activeDrawMode == DrawMode.Polygon || activeDrawMode == DrawMode.Circle) {
                        DraftGeometryActionRow(
                            drawMode = activeDrawMode,
                            draftPointCount = draftPoints.size,
                            onUndo = {
                                if (draftPoints.isNotEmpty()) {
                                    draftPoints = draftPoints.dropLast(1)
                                }
                            },
                            onClear = {
                                draftPoints = emptyList()
                                activeDrawMode = DrawMode.None
                            },
                            onConfirm = {
                                val id = UUID.randomUUID().toString()
                                committedOverlays = committedOverlays + when (activeDrawMode) {
                                    DrawMode.Polyline -> MKPolylineOverlay(
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

                                    DrawMode.Polygon -> MKPolygonOverlay(
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

                                    DrawMode.Circle -> MKCircleOverlay(
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

                                    else -> return@DraftGeometryActionRow
                                }
                                draftPoints = emptyList()
                                activeDrawMode = DrawMode.None
                            }
                        )
                    }
                }

                MKMapView(
                    state = mapState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onEvent = { event ->
                        lastEventText = event.toDisplayText()

                        fun addGeometryPoint(action: DrawMode, coordinate: MKCoordinate) {
                            when (action) {
                                DrawMode.None -> Unit
                                DrawMode.Annotation -> {
                                    val normalizedTitle = annotationTitle.ifBlank { "Pinned" }
                                    annotations = when (annotationVisualStyle) {
                                        AnnotationVisualStyle.Marker -> annotations + MKMarkerAnnotation(
                                            id = UUID.randomUUID().toString(),
                                            coordinate = coordinate,
                                            title = normalizedTitle,
                                            subtitle = annotationSubtitle.ifBlank { null },
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

                                        AnnotationVisualStyle.Custom -> {
                                            val payload = Point(
                                                title = normalizedTitle,
                                                image = renderFilledCircleBase64Png(
                                                    fillColorHex = customImageColorHex,
                                                    sizePx = 48
                                                )
                                            )
                                            annotations + PointAnnotation(
                                                id = UUID.randomUUID().toString(),
                                                coordinate = coordinate,
                                                title = normalizedTitle,
                                                subtitle = annotationSubtitle.ifBlank { null },
                                                point = payload
                                            )
                                        }
                                    }
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
                        when (event) {
                            is MKMapEvent.RegionWillChange -> Unit
                            is MKMapEvent.RegionDidChange -> {
                                region = event.region
                            }
                            is MKMapEvent.AnnotationSelected -> {
                                selectedAnnotationId = event.annotation.id
                                if (immediateDeselectOnAnnotationTap) {
                                    requestImmediateDeselect(event.annotation)
                                }
                            }
                            is MKMapEvent.AnnotationDeselected -> {
                                if (selectedAnnotationId == event.annotation.id) {
                                    selectedAnnotationId = null
                                }
                            }

                            is MKMapEvent.LongPress -> {
                                addGeometryPoint(longPressAction, event.coordinate)
                            }

                            is MKMapEvent.MapTapped -> {
                                addGeometryPoint(tapAction, event.coordinate)
                            }

                            else -> Unit
                        }
                    }
                )
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
                val zoomRangePreset = when (val z = options.cameraZoomRange) {
                    null -> ZoomRangePreset.none
                    MKCameraZoomRange(minDistanceMeter = 150.0, maxDistanceMeter = 20_000.0) ->
                        ZoomRangePreset.city

                    MKCameraZoomRange(minDistanceMeter = 50.0, maxDistanceMeter = 3_000.0) ->
                        ZoomRangePreset.district

                    else -> ZoomRangePreset.none
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
                            label = "Zoom Range",
                            value = zoomRangePreset,
                            values = ZoomRangePreset.entries,
                            onSelected = { preset ->
                                options = options.copy(
                                    cameraZoomRange = when (preset) {
                                        ZoomRangePreset.none -> null
                                        ZoomRangePreset.city -> MKCameraZoomRange(
                                            minDistanceMeter = 150.0,
                                            maxDistanceMeter = 20_000.0
                                        )

                                        ZoomRangePreset.district -> MKCameraZoomRange(
                                            minDistanceMeter = 50.0,
                                            maxDistanceMeter = 3_000.0
                                        )
                                    }
                                )
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
                            label = "User Location Follows Heading",
                            checked = options.userLocation.followsHeading,
                            onCheckedChange = {
                                options = options.copy(
                                    userLocation = options.userLocation.copy(followsHeading = it)
                                )
                            }
                        )
                        ToggleRow(
                            label = "User Location Accuracy Ring",
                            checked = options.userLocation.showsAccuracyRing,
                            onCheckedChange = {
                                options = options.copy(
                                    userLocation = options.userLocation.copy(showsAccuracyRing = it)
                                )
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
                        ToggleRow(
                            label = "Map Type Control",
                            checked = options.showsMapTypeControl,
                            onCheckedChange = { options = options.copy(showsMapTypeControl = it) }
                        )
                        ToggleRow(
                            label = "Rotate Enabled",
                            checked = options.isRotateEnabled,
                            onCheckedChange = { options = options.copy(isRotateEnabled = it) }
                        )
                        ToggleRow(
                            label = "Scroll Enabled",
                            checked = options.isScrollEnabled,
                            onCheckedChange = { options = options.copy(isScrollEnabled = it) }
                        )
                        ToggleRow(
                            label = "Zoom Enabled",
                            checked = options.isZoomEnabled,
                            onCheckedChange = { options = options.copy(isZoomEnabled = it) }
                        )
                        ToggleRow(
                            label = "Pitch Enabled",
                            checked = options.isPitchEnabled,
                            onCheckedChange = { options = options.copy(isPitchEnabled = it) }
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
                            } else {
                                Text(
                                    text = "Marker glyph image: https://appassets.androidplatform.net/assets/demo/custom-glyph.svg",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = customImageColorHex,
                                onValueChange = { customImageColorHex = it },
                                label = { Text("Custom Circle Color (hex)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Custom mode stores Point(title, image) payload and renders a title+image badge.",
                                style = MaterialTheme.typography.bodySmall
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
                        if (polylineDashed) {
                            OutlinedTextField(
                                value = polylineDashLengthText,
                                onValueChange = { polylineDashLengthText = it },
                                label = { Text("Dash Length") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = polylineGapLengthText,
                                onValueChange = { polylineGapLengthText = it },
                                label = { Text("Gap Length") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                        OutlinedTextField(
                            value = polygonStrokeWidthText,
                            onValueChange = { polygonStrokeWidthText = it },
                            label = { Text("Polygon Stroke Width") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ToggleRow(
                            label = "Polygon Dashed",
                            checked = polygonDashed,
                            onCheckedChange = { polygonDashed = it }
                        )
                        if (polygonDashed) {
                            OutlinedTextField(
                                value = polygonDashLengthText,
                                onValueChange = { polygonDashLengthText = it },
                                label = { Text("Polygon Dash Length") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = polygonGapLengthText,
                                onValueChange = { polygonGapLengthText = it },
                                label = { Text("Polygon Gap Length") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                            value = circleStrokeWidthText,
                            onValueChange = { circleStrokeWidthText = it },
                            label = { Text("Circle Stroke Width") },
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

private data class Point(
    val title: String,
    val image: String
)

private class PointAnnotation(
    override val id: String,
    override val coordinate: MKCoordinate,
    override val title: String?,
    override val subtitle: String?,
    val point: Point
) : MKAnnotation(
    id = id,
    coordinate = coordinate,
    title = title,
    subtitle = subtitle
) {
    override fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Image(
        source = MKImageSource.Base64Png(
            renderPointTitleImageBase64Png(
                title = point.title,
                imageBase64Png = point.image
            )
        ),
        widthDp = 110,
        heightDp = 28,
        anchorX = 0.5,
        anchorY = 1.0
    )

    override fun extraEquals(other: MKAnnotation): Boolean {
        other as PointAnnotation
        return point == other.point
    }

    override fun extraHashCode(): Int = point.hashCode()
}

private fun MKMapEvent.toDisplayText(): String {
    return when (this) {
        is MKMapEvent.MapLoaded -> "MapLoaded"
        is MKMapEvent.MapError -> "MapError: $cause"
        is MKMapEvent.MapTapped -> "MapTapped(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        is MKMapEvent.RegionWillChange -> {
            val center = region.center
            "RegionWillChange(center=${center.latitude.format6()},${center.longitude.format6()})"
        }
        is MKMapEvent.RegionDidChange -> {
            val center = region.center
            "RegionDidChange(center=${center.latitude.format6()},${center.longitude.format6()})"
        }

        is MKMapEvent.LongPress -> "LongPress(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        is MKMapEvent.AnnotationSelected -> "AnnotationSelected(id=$id)"
        is MKMapEvent.AnnotationDeselected -> "AnnotationDeselected(id=$id)"
        is MKMapEvent.OverlayTapped -> "OverlayTapped(id=$id)"
        is MKMapEvent.UserLocationUpdated -> {
            "UserLocationUpdated(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        }
    }
}

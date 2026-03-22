package com.mapkit.android.demo

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mapkit.android.api.MKMapKit
import com.mapkit.android.api.MKMapView
import com.mapkit.android.model.MKAnnotation
import com.mapkit.android.model.MKAnnotationStyle
import com.mapkit.android.model.MKAppearanceOption
import com.mapkit.android.model.MKCameraZoomRange
import com.mapkit.android.model.MKCoordinate
import com.mapkit.android.model.MKCoordinateRegion
import com.mapkit.android.model.MKImageSource
import com.mapkit.android.model.MKMapEvent
import com.mapkit.android.model.MKMapLanguage
import com.mapkit.android.model.MKMapOptions
import com.mapkit.android.model.MKMapState
import com.mapkit.android.model.MKMapStyle
import com.mapkit.android.model.MKOverlay
import com.mapkit.android.model.MKOverlayStyle
import com.mapkit.android.model.MKPoiFilter
import com.mapkit.android.model.MKPolygonOverlay
import com.mapkit.android.model.MKPolylineOverlay
import com.mapkit.android.model.MKUserLocationOptions
import java.util.UUID

private enum class DemoTab { Map, Settings }
private enum class DrawMode { Browse, Annotation, Polyline, Polygon }
private enum class PlacementTrigger { Tap, LongPress }
private enum class AnnotationVisualStyle { Default, CustomImage }
private enum class ZoomRangePreset {
    none,
    city,
    district
}
private enum class PoiFilterPreset {
    all,
    none,
    includeCafePark,
    excludeCafePark
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MKMapKit.isInitialized()) {
            MKMapKit.init(BuildConfig.MAPKIT_JS_TOKEN)
        }

        setContent {
            DemoScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen() {
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
        mutableStateOf(
            listOf(
                MKAnnotation(
                    id = "tokyo-station",
                    coordinate = MKCoordinate(35.681236, 139.767125),
                    title = "Tokyo Station"
                )
            )
        )
    }
    var committedOverlays by remember {
        mutableStateOf<List<MKOverlay>>(emptyList())
    }
    var options by remember {
        mutableStateOf(MKMapOptions(userLocation = MKUserLocationOptions(isEnabled = false)))
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var drawMode by remember { mutableStateOf(DrawMode.Browse) }
    var modeExpanded by remember { mutableStateOf(false) }
    var draftPoints by remember { mutableStateOf<List<MKCoordinate>>(emptyList()) }
    var lastEventText by remember { mutableStateOf("No events yet") }
    var placementTrigger by remember { mutableStateOf(PlacementTrigger.LongPress) }
    var annotationVisualStyle by remember { mutableStateOf(AnnotationVisualStyle.Default) }
    var annotationTitle by remember { mutableStateOf("Pinned") }
    var annotationSubtitle by remember { mutableStateOf("") }
    var annotationTintHex by remember { mutableStateOf("#0ea5e9") }
    var annotationGlyph by remember { mutableStateOf("A") }
    var baseConfigExpanded by remember { mutableStateOf(true) }
    var annotationConfigExpanded by remember { mutableStateOf(true) }

    val selectedTab = if (selectedTabIndex == 0) DemoTab.Map else DemoTab.Settings

    val draftOverlay = when (drawMode) {
        DrawMode.Polyline -> if (draftPoints.size >= 2) {
            listOf(
                MKPolylineOverlay(
                    id = "draft-polyline",
                    points = draftPoints,
                    style = MKOverlayStyle(strokeColorHex = "#ef4444", strokeWidth = 4.0)
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
                    style = MKOverlayStyle(
                        strokeColorHex = "#f97316",
                        strokeWidth = 3.0,
                        fillColorHex = "rgba(249, 115, 22, 0.22)"
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
                selected = selectedTab == DemoTab.Map,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Map") }
            )
            Tab(
                selected = selectedTab == DemoTab.Settings,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Settings") }
            )
        }

        when (selectedTab) {
            DemoTab.Map -> {
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
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = !modeExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = drawMode.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            DrawMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name) },
                                    onClick = {
                                        drawMode = mode
                                        draftPoints = emptyList()
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (drawMode == DrawMode.Polyline || drawMode == DrawMode.Polygon) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (draftPoints.isNotEmpty()) {
                                        draftPoints = draftPoints.dropLast(1)
                                    }
                                }
                            ) {
                                Text("Undo")
                            }
                            OutlinedButton(onClick = { draftPoints = emptyList() }) {
                                Text("Clear")
                            }
                            Button(
                                enabled = (drawMode == DrawMode.Polyline && draftPoints.size >= 2) ||
                                    (drawMode == DrawMode.Polygon && draftPoints.size >= 3),
                                onClick = {
                                    val id = UUID.randomUUID().toString()
                                    committedOverlays = committedOverlays + when (drawMode) {
                                        DrawMode.Polyline -> MKPolylineOverlay(
                                            id = id,
                                            points = draftPoints,
                                            style = MKOverlayStyle(strokeColorHex = "#0ea5e9", strokeWidth = 4.0)
                                        )

                                        DrawMode.Polygon -> MKPolygonOverlay(
                                            id = id,
                                            points = draftPoints,
                                            style = MKOverlayStyle(
                                                strokeColorHex = "#22c55e",
                                                strokeWidth = 3.0,
                                                fillColorHex = "rgba(34, 197, 94, 0.2)"
                                            )
                                        )

                                        else -> return@Button
                                    }
                                    draftPoints = emptyList()
                                }
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }

                MKMapView(
                    state = mapState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onEvent = { event ->
                        lastEventText = event.toDisplayText()

                        fun addGeometryPoint(coordinate: MKCoordinate) {
                            when (drawMode) {
                                DrawMode.Browse -> Unit
                                DrawMode.Annotation -> {
                                    val annotationStyle = when (annotationVisualStyle) {
                                        AnnotationVisualStyle.Default -> MKAnnotationStyle.DefaultMarker(
                                            tintHex = annotationTintHex.ifBlank { null },
                                            glyphText = annotationGlyph.ifBlank { null }
                                        )

                                        AnnotationVisualStyle.CustomImage -> MKAnnotationStyle.Image(
                                            source = MKImageSource.Url("file:///android_asset/demo/custom-annotation.svg"),
                                            widthDp = 40,
                                            heightDp = 40
                                        )
                                    }
                                    annotations = annotations + MKAnnotation(
                                        id = UUID.randomUUID().toString(),
                                        coordinate = coordinate,
                                        title = annotationTitle.ifBlank { "Pinned" },
                                        subtitle = annotationSubtitle.ifBlank { null },
                                        style = annotationStyle
                                    )
                                }

                                DrawMode.Polyline,
                                DrawMode.Polygon -> {
                                    draftPoints = draftPoints + coordinate
                                }
                            }
                        }
                        Log.d("MainActivity", "$event")
                        when (event) {
                            is MKMapEvent.RegionDidChange -> {
                                if (event.settled) {
                                    region = event.region
                                }
                            }

                            is MKMapEvent.LongPress -> {
                                if (placementTrigger == PlacementTrigger.LongPress) {
                                    addGeometryPoint(event.coordinate)
                                }
                            }
                            is MKMapEvent.MapTapped -> {
                                if (placementTrigger == PlacementTrigger.Tap) {
                                    addGeometryPoint(event.coordinate)
                                }
                            }

                            else -> Unit
                        }
                    }
                )
            }

            DemoTab.Settings -> {
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
                    ConfigSectionHeader(
                        title = "Annotation Config",
                        expanded = annotationConfigExpanded,
                        onExpandedChange = { annotationConfigExpanded = it }
                    )
                    if (annotationConfigExpanded) {
                        EnumSelector(
                            label = "Placement Trigger",
                            value = placementTrigger,
                            values = PlacementTrigger.entries,
                            onSelected = { placementTrigger = it }
                        )
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
                        if (annotationVisualStyle == AnnotationVisualStyle.Default) {
                            OutlinedTextField(
                                value = annotationTintHex,
                                onValueChange = { annotationTintHex = it },
                                label = { Text("Marker Tint (hex)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = annotationGlyph,
                                onValueChange = { annotationGlyph = it.take(2) },
                                label = { Text("Marker Glyph") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Custom image: file:///android_asset/demo/custom-annotation.svg",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    ConfigSectionHeader(
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
                                    PoiFilterPreset.includeCafePark -> {
                                        MKPoiFilter.Include(listOf("cafe", "park"))
                                    }
                                    PoiFilterPreset.excludeCafePark -> {
                                        MKPoiFilter.Exclude(listOf("cafe", "park"))
                                    }
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
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = expanded, onCheckedChange = onExpandedChange)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumSelector(
    label: String,
    value: T,
    values: List<T>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun MKMapEvent.toDisplayText(): String {
    return when (this) {
        is MKMapEvent.MapLoaded -> "MapLoaded"
        is MKMapEvent.MapError -> "MapError: $cause"
        is MKMapEvent.MapTapped -> "MapTapped(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        is MKMapEvent.RegionDidChange -> {
            val center = region.center
            "RegionDidChange(settled=$settled, center=${center.latitude.format6()},${center.longitude.format6()})"
        }

        is MKMapEvent.LongPress -> "LongPress(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        is MKMapEvent.AnnotationTapped -> "AnnotationTapped(id=$id)"
        is MKMapEvent.OverlayTapped -> "OverlayTapped(id=$id)"
        is MKMapEvent.UserLocationUpdated -> {
            "UserLocationUpdated(${coordinate.latitude.format6()},${coordinate.longitude.format6()})"
        }
    }
}

private fun Double.format6(): String = String.format("%.6f", this)

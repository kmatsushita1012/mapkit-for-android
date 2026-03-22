package com.mapkit.android.demo

import android.Manifest
import android.os.Bundle
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
import com.mapkit.android.model.MKAppearanceOption
import com.mapkit.android.model.MKCoordinate
import com.mapkit.android.model.MKCoordinateRegion
import com.mapkit.android.model.MKMapEvent
import com.mapkit.android.model.MKMapLanguage
import com.mapkit.android.model.MKMapOptions
import com.mapkit.android.model.MKMapState
import com.mapkit.android.model.MKMapStyle
import com.mapkit.android.model.MKNavigationEmphasis
import com.mapkit.android.model.MKOverlay
import com.mapkit.android.model.MKOverlayStyle
import com.mapkit.android.model.MKPolygonOverlay
import com.mapkit.android.model.MKPolylineOverlay
import com.mapkit.android.model.MKUserLocationOptions
import java.util.UUID

private enum class DemoTab { Map, Settings }
private enum class DrawMode { Browse, Annotation, Polyline, Polygon }

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
        mutableStateOf(MKMapOptions(userLocation = MKUserLocationOptions(isEnabled = true)))
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var drawMode by remember { mutableStateOf(DrawMode.Browse) }
    var modeExpanded by remember { mutableStateOf(false) }
    var draftPoints by remember { mutableStateOf<List<MKCoordinate>>(emptyList()) }
    var lastEventText by remember { mutableStateOf("No events yet") }

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

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
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
                        when (event) {
                            is MKMapEvent.RegionDidChange -> {
                                if (event.settled) {
                                    region = event.region
                                }
                            }

                            is MKMapEvent.LongPress -> {
                                when (drawMode) {
                                    DrawMode.Browse -> Unit
                                    DrawMode.Annotation -> {
                                        annotations = annotations + MKAnnotation(
                                            id = UUID.randomUUID().toString(),
                                            coordinate = event.coordinate,
                                            title = "Pinned"
                                        )
                                    }

                                    DrawMode.Polyline,
                                    DrawMode.Polygon -> {
                                        draftPoints = draftPoints + event.coordinate
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }
                )
            }

            DemoTab.Settings -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        label = "Navigation Emphasis",
                        value = options.navigationEmphasis,
                        values = MKNavigationEmphasis.entries,
                        onSelected = { options = options.copy(navigationEmphasis = it) }
                    )

                    ToggleRow(
                        label = "Traffic",
                        checked = options.showsTraffic,
                        onCheckedChange = { options = options.copy(showsTraffic = it) }
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
                }
            }
        }
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

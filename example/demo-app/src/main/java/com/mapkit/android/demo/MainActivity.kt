package com.mapkit.android.demo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mapkit.android.api.MKMapKit
import com.mapkit.android.api.MKMapView
import com.mapkit.android.model.MKCoordinate
import com.mapkit.android.model.MKCoordinateRegion
import com.mapkit.android.model.MKMapErrorCause
import com.mapkit.android.model.MKMapEvent
import com.mapkit.android.model.MKMapOptions
import com.mapkit.android.model.MKMapState
import com.mapkit.android.model.MKOverlayStyle
import com.mapkit.android.model.MKPolylineOverlay
import com.mapkit.android.model.MKUserLocationOptions
import com.mapkit.android.model.MKAnnotation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MKMapKit.isInitialized()) {
            MKMapKit.init(BuildConfig.MAPKIT_JS_TOKEN)
        }

        setContent {
            var state by remember {
                mutableStateOf(
                    MKMapState(
                        region = MKCoordinateRegion.fromCenter(
                            center = MKCoordinate(35.681236, 139.767125),
                            latitudeDelta = 0.05,
                            longitudeDelta = 0.05
                        ),
                        annotations = listOf(
                            MKAnnotation(
                                id = "tokyo-station",
                                coordinate = MKCoordinate(35.681236, 139.767125),
                                title = "Tokyo Station"
                            )
                        ),
                        overlays = listOf(
                            MKPolylineOverlay(
                                id = "sample-route",
                                points = listOf(
                                    MKCoordinate(35.680000, 139.765000),
                                    MKCoordinate(35.681236, 139.767125),
                                    MKCoordinate(35.683000, 139.770000)
                                ),
                                style = MKOverlayStyle(strokeColorHex = "#0EA5E9", strokeWidth = 4.0)
                            )
                        ),
                        options = MKMapOptions(
                            userLocation = MKUserLocationOptions(isEnabled = true)
                        )
                    )
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { granted ->
                val enabled = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                state = state.copy(
                    options = state.options.copy(
                        userLocation = state.options.userLocation.copy(isEnabled = enabled)
                    )
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

            MKMapView(
                state = state,
                onEvent = { event ->
                    when (event) {
                        is MKMapEvent.RegionDidChange -> if (event.settled) {
                            state = state.copy(region = event.region)
                        }

                        is MKMapEvent.AnnotationTapped -> {
                            android.util.Log.d("MKDemo", "Annotation tapped: ${event.id}")
                        }

                        is MKMapEvent.OverlayTapped -> {
                            android.util.Log.d("MKDemo", "Overlay tapped: ${event.id}")
                        }

                        is MKMapEvent.MapError -> {
                            if (event.cause is MKMapErrorCause.BridgeFailure) {
                                android.util.Log.e("MKDemo", "Bridge error: ${(event.cause as MKMapErrorCause.BridgeFailure).message}")
                            }
                        }

                        else -> Unit
                    }
                }
            )
        }
    }
}

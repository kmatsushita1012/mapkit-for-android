package com.mapkit.android.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mapkit.android.model.MKMapErrorCause
import com.mapkit.android.model.MKMapEvent
import com.mapkit.android.model.MKMapState
import com.mapkit.android.webview.MKBridgeWebView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapkit.android.model.MKCoordinate

class MKMapDebugController {
    internal var onSimulateAnnotationTap: (() -> Unit)? = null
    internal var onSimulateOverlayTap: (() -> Unit)? = null
    internal var onSimulatePan: (() -> Unit)? = null

    fun simulateAnnotationTap() {
        onSimulateAnnotationTap?.invoke()
    }

    fun simulateOverlayTap() {
        onSimulateOverlayTap?.invoke()
    }

    fun simulatePan() {
        onSimulatePan?.invoke()
    }
}

@Composable
fun MKMapView(
    state: MKMapState,
    onEvent: (MKMapEvent) -> Unit,
    debugController: MKMapDebugController? = null,
    modifier: Modifier = Modifier
) {
    val latestOnEvent = rememberUpdatedState(onEvent)
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<MKBridgeWebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MKBridgeWebView(context).also { webView ->
                webViewRef.value = webView
                webView.setEventListener { event -> latestOnEvent.value(event) }
                debugController?.apply {
                    onSimulateAnnotationTap = { webView.simulateAnnotationTap() }
                    onSimulateOverlayTap = { webView.simulateOverlayTap() }
                    onSimulatePan = { webView.simulatePan() }
                }
                val token = MKMapKit.currentTokenOrNull()
                if (token != null) {
                    webView.ensureInitialized(token)
                    webView.applyState(state)
                } else {
                    latestOnEvent.value(MKMapEvent.MapError(MKMapErrorCause.TokenUnavailable))
                }
            }
        },
        update = { webView ->
            webViewRef.value = webView
            webView.setEventListener { event -> latestOnEvent.value(event) }
            debugController?.apply {
                onSimulateAnnotationTap = { webView.simulateAnnotationTap() }
                onSimulateOverlayTap = { webView.simulateOverlayTap() }
                onSimulatePan = { webView.simulatePan() }
            }
            val token = MKMapKit.currentTokenOrNull()
            if (token != null) {
                webView.ensureInitialized(token)
                webView.applyState(state)
            } else {
                latestOnEvent.value(MKMapEvent.MapError(MKMapErrorCause.TokenUnavailable))
            }
        }
    )

    val userLocationEnabled = state.options.userLocation.isEnabled
    DisposableEffect(
        context,
        webViewRef.value,
        userLocationEnabled,
        state.options.userLocation.followsHeading,
        state.options.userLocation.showsAccuracyRing
    ) {
        if (!userLocationEnabled || !hasLocationPermission(context)) {
            onDispose { }
        }
        else {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val coordinate = MKCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    latestOnEvent.value(MKMapEvent.UserLocationUpdated(coordinate))
                    webViewRef.value?.applyUserLocation(
                        coordinate = coordinate,
                        followsHeading = state.options.userLocation.followsHeading,
                        showsAccuracyRing = state.options.userLocation.showsAccuracyRing
                    )
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            onDispose {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

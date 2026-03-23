package com.studiomk.mapkit.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.studiomk.mapkit.model.MKMapErrorCause
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKMapState
import com.studiomk.mapkit.webview.MKBridgeWebView
import androidx.compose.ui.viewinterop.AndroidView

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

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MKBridgeWebView(context).also { webView ->
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
}

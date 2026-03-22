package com.mapkit.android.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mapkit.android.model.MKCoordinate
import com.mapkit.android.model.MKCoordinateRegion
import com.mapkit.android.model.MKCircleOverlay
import com.mapkit.android.model.MKMapErrorCause
import com.mapkit.android.model.MKMapEvent
import com.mapkit.android.model.MKMapState
import com.mapkit.android.model.MKPolygonOverlay
import com.mapkit.android.model.MKPolylineOverlay
import com.mapkit.android.webview.internal.InternalMapState
import com.mapkit.android.webview.internal.MKBridgeMapper
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class MKBridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private var onEvent: ((MKMapEvent) -> Unit)? = null
    private var isPageReady = false
    private var isJsInitSent = false
    private var pendingToken: String? = null
    private var lastAppliedPayload: String? = null
    private var pendingState: MKMapState? = null

    private val androidBridge = object {
        @JavascriptInterface
        fun emitEvent(payload: String) {
            try {
                val event = parseEvent(JSONObject(payload))
                post { onEvent?.invoke(event) }
            } catch (t: Throwable) {
                post {
                    onEvent?.invoke(
                        MKMapEvent.MapError(
                            MKMapErrorCause.BridgeFailure("Failed to parse JS event: ${t.message}")
                        )
                    )
                }
            }
        }
    }

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageReady = true
                sendInitIfPossible()
                flushPendingState()
            }
        }
        addJavascriptInterface(androidBridge, "AndroidMKBridge")
        loadUrl("file:///android_asset/mkbridge/index.html")
    }

    fun setEventListener(listener: (MKMapEvent) -> Unit) {
        this.onEvent = listener
    }

    fun ensureInitialized(token: String) {
        // DEBUG_BREAKPOINT_1: ensureInitialized() が呼ばれて token が来ているか確認
        Log.d("MKBridgeWebView", "ensureInitialized called. tokenLength=${token.length}")
        pendingToken = token
        sendInitIfPossible()
    }

    fun applyState(state: MKMapState) {
        pendingState = state
        if (!isPageReady || !isJsInitSent) return
        flushPendingState()
    }

    fun applyUserLocation(
        coordinate: MKCoordinate,
        followsHeading: Boolean,
        showsAccuracyRing: Boolean
    ) {
        if (!isPageReady || !isJsInitSent) return
        val payload = JSONObject()
            .put("lat", coordinate.latitude)
            .put("lng", coordinate.longitude)
            .put("followsHeading", followsHeading)
            .put("showsAccuracyRing", showsAccuracyRing)
            .toString()
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.applyUserLocation($payload);")
    }

    private fun flushPendingState() {
        val latest = pendingState ?: return
        if (!isPageReady || !isJsInitSent) return

        val payload = serializeState(latest)
        if (lastAppliedPayload == payload) return
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.applyState($payload);")
        lastAppliedPayload = payload
    }

    private fun sendInitIfPossible() {
        if (!isPageReady || isJsInitSent) return
        val token = pendingToken ?: return
        // DEBUG_BREAKPOINT_2: JS init 実行直前。isPageReady=true, token!=null を確認
        Log.d("MKBridgeWebView", "sendInitIfPossible -> init() to JS")
        val escaped = JSONObject.quote(token)
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.init($escaped);")
        isJsInitSent = true
    }

    private fun evaluateJavascriptSafe(script: String) {
        evaluateJavascript(script, ValueCallback { })
    }

    fun simulateAnnotationTap() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulateAnnotationTap && window.MKBridge.simulateAnnotationTap();")
    }

    fun simulateOverlayTap() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulateOverlayTap && window.MKBridge.simulateOverlayTap();")
    }

    fun simulatePan() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulatePan && window.MKBridge.simulatePan();")
    }

    private fun serializeState(state: MKMapState): String {
        val regionJson = JSONObject()
            .put("centerLat", state.region.center.latitude)
            .put("centerLng", state.region.center.longitude)
            .put("latDelta", state.region.span.latitudeDelta)
            .put("lngDelta", state.region.span.longitudeDelta)

        val annotations = JSONArray().apply {
            state.annotations.forEach { annotation ->
                put(
                    JSONObject()
                        .put("id", annotation.id)
                        .put("lat", annotation.coordinate.latitude)
                        .put("lng", annotation.coordinate.longitude)
                        .put("title", annotation.title)
                        .put("subtitle", annotation.subtitle)
                )
            }
        }

        val overlays = JSONArray().apply {
            state.overlays.forEach { overlay ->
                val overlayJson = JSONObject()
                    .put("id", overlay.id)
                    .put("type", overlay::class.simpleName)
                when (overlay) {
                    is MKPolylineOverlay -> {
                        overlayJson.put(
                            "points",
                            JSONArray().apply {
                                overlay.points.forEach { point ->
                                    put(JSONObject().put("lat", point.latitude).put("lng", point.longitude))
                                }
                            }
                        )
                        overlayJson
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                    }
                    is MKPolygonOverlay -> {
                        overlayJson.put(
                            "points",
                            JSONArray().apply {
                                overlay.points.forEach { point ->
                                    put(JSONObject().put("lat", point.latitude).put("lng", point.longitude))
                                }
                            }
                        )
                        overlayJson
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("fillColor", overlay.style.fillColorHex)
                    }
                    is MKCircleOverlay -> {
                        overlayJson
                            .put("centerLat", overlay.center.latitude)
                            .put("centerLng", overlay.center.longitude)
                            .put("radiusMeter", overlay.radiusMeter)
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("fillColor", overlay.style.fillColorHex)
                    }
                    else -> Unit
                }
                put(overlayJson)
            }
        }

        return JSONObject()
            .put("region", regionJson)
            .put("annotations", annotations)
            .put("overlays", overlays)
            .put("mapStyle", state.options.mapStyle.name)
            .put("navigationEmphasis", state.options.navigationEmphasis.name)
            .put("showsTraffic", state.options.showsTraffic)
            .put("showsCompass", state.options.showsCompass)
            .put("showsScale", state.options.showsScale)
            .put("showsPointsOfInterest", state.options.showsPointsOfInterest)
            .put("isRotateEnabled", state.options.isRotateEnabled)
            .put("isScrollEnabled", state.options.isScrollEnabled)
            .put("isZoomEnabled", state.options.isZoomEnabled)
            .put("isPitchEnabled", state.options.isPitchEnabled)
            .put("appearance", state.options.appearance.name)
            .put("language", state.options.language.name)
            .put("userLocationEnabled", state.options.userLocation.isEnabled)
            .put("userLocationFollowsHeading", state.options.userLocation.followsHeading)
            .put("userLocationShowsAccuracyRing", state.options.userLocation.showsAccuracyRing)
            .toString()
    }

    private fun parseEvent(json: JSONObject): MKMapEvent {
        // DEBUG_BREAKPOINT_3: JS -> Android の受信イベントを確認
        Log.d("MKBridgeWebView", "parseEvent type=${json.optString("type")}")
        return when (json.optString("type")) {
            "mapLoaded" -> MKMapEvent.MapLoaded
            "regionDidChange" -> {
                val region = json.getJSONObject("region")
                val internal = InternalMapState(
                    centerLat = region.getDouble("centerLat"),
                    centerLng = region.getDouble("centerLng"),
                    latDelta = region.getDouble("latDelta"),
                    lngDelta = region.getDouble("lngDelta")
                )
                val settled = json.optBoolean("settled", true)
                MKMapEvent.RegionDidChange(MKBridgeMapper.toRegion(internal), settled = settled)
            }
            "longPress" -> MKMapEvent.LongPress(
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )

            "annotationTapped" -> MKMapEvent.AnnotationTapped(json.getString("id"))
            "overlayTapped" -> MKMapEvent.OverlayTapped(json.getString("id"))
            "bridgeError" -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure(json.optString("message", "bridge error"))
            )
            "debug" -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure(json.optString("message", "debug"))
            )
            "userLocationUpdated" -> MKMapEvent.UserLocationUpdated(
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )

            else -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure("Unknown event type: ${json.optString("type")}")
            )
        }
    }
}

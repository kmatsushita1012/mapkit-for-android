package com.studiomk.mapkit.webview

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKCircleOverlay
import com.studiomk.mapkit.model.MKMapErrorCause
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKMapState
import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKImageSource
import com.studiomk.mapkit.model.MKPoiFilter
import com.studiomk.mapkit.model.MKPolygonOverlay
import com.studiomk.mapkit.model.MKPolylineOverlay
import com.studiomk.mapkit.webview.internal.InternalMapState
import com.studiomk.mapkit.webview.internal.MKBridgeMapper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class MKBridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

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
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        isLongClickable = false
        setOnLongClickListener { true }
        webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val granted = hasLocationPermission(context)
                callback?.invoke(origin, granted, false)
                if (!granted) {
                    onEvent?.invoke(
                        MKMapEvent.MapError(
                            MKMapErrorCause.BridgeFailure(
                                "Location permission not granted for geolocation origin=$origin"
                            )
                        )
                    )
                }
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isPageReady = true
                sendInitIfPossible()
                flushPendingState()
            }
        }
        addJavascriptInterface(androidBridge, "AndroidMKBridge")
        loadUrl("https://appassets.androidplatform.net/assets/mkbridge/index.html")
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
                        .put("isVisible", annotation.isVisible)
                        .put("isSelected", annotation.isSelected)
                        .put("style", serializeAnnotationStyle(annotation.style))
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
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
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
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
                    }
                    is MKCircleOverlay -> {
                        overlayJson
                            .put("centerLat", overlay.center.latitude)
                            .put("centerLng", overlay.center.longitude)
                            .put("radiusMeter", overlay.radiusMeter)
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("fillColor", overlay.style.fillColorHex)
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
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
            .put("showsCompass", state.options.showsCompass)
            .put("showsScale", state.options.showsScale)
            .put("showsZoomControl", state.options.showsZoomControl)
            .put("showsMapTypeControl", state.options.showsMapTypeControl)
            .put("showsPointsOfInterest", state.options.showsPointsOfInterest)
            .put(
                "poiFilter",
                when (val filter = state.options.poiFilter) {
                    MKPoiFilter.All -> JSONObject().put("type", "all")
                    MKPoiFilter.None -> JSONObject().put("type", "none")
                    is MKPoiFilter.Include -> JSONObject()
                        .put("type", "include")
                        .put("categories", JSONArray(filter.categories))
                    is MKPoiFilter.Exclude -> JSONObject()
                        .put("type", "exclude")
                        .put("categories", JSONArray(filter.categories))
                }
            )
            .put(
                "cameraZoomRange",
                state.options.cameraZoomRange?.let { zoomRange ->
                    JSONObject()
                        .put("minDistanceMeter", zoomRange.minDistanceMeter)
                        .put("maxDistanceMeter", zoomRange.maxDistanceMeter)
                }
            )
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
            "mapTapped" -> MKMapEvent.MapTapped(
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

    private fun serializeAnnotationStyle(style: MKAnnotationStyle): JSONObject {
        return when (style) {
            is MKAnnotationStyle.Default -> JSONObject()
                .put("kind", "default")
                .put("tintHex", style.tintHex)
                .put("glyphText", style.glyphText)
                .put(
                    "glyphImageSource",
                    style.glyphImageSource?.let { serializeImageSource(it) }
                )

            is MKAnnotationStyle.CustomImage -> JSONObject()
                .put("kind", "customImage")
                .put("source", serializeImageSource(style.source))
                .put("widthDp", style.widthDp)
                .put("heightDp", style.heightDp)
                .put("anchorX", style.anchorX)
                .put("anchorY", style.anchorY)
        }
    }

    private fun serializeImageSource(source: MKImageSource): JSONObject {
        return when (source) {
            is MKImageSource.Url -> JSONObject()
                .put("kind", "url")
                .put("value", source.value)

            is MKImageSource.Base64Png -> JSONObject()
                .put("kind", "base64Png")
                .put("value", source.value)

            is MKImageSource.ResourceName -> JSONObject()
                .put("kind", "resourceName")
                .put("value", source.value)
        }
    }
}

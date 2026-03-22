(function () {
  const state = {
    token: null,
    mapReady: false,
    map: null,
    region: {
      centerLat: 35.681236,
      centerLng: 139.767125,
      latDelta: 0.05,
      lngDelta: 0.05,
    },
    annotations: [],
    overlays: [],
    userLocation: null,
    userLocationEnabled: false,
    userLocationFollowsHeading: false,
    userLocationShowsAccuracyRing: true,
    userLocationAnnotation: null,
    annotationsById: {},
    overlaysById: {},
    annotationHashesById: {},
    overlayHashesById: {},
  };
  const DEBUG_EMIT_TO_ANDROID = false;

  function emit(payload) {
    if (window.AndroidMKBridge && window.AndroidMKBridge.emitEvent) {
      window.AndroidMKBridge.emitEvent(JSON.stringify(payload));
    }
  }

  function debugLog(message) {
    const text = "[MKBridgeJS] " + message;
    try {
      console.log(text);
      if (DEBUG_EMIT_TO_ANDROID) {
        emit({ type: "debug", message: text });
      }
    } catch (_) {}
  }

  function emitBridgeError(message) {
    emit({
      type: "bridgeError",
      message: String(message || "unknown bridge error"),
    });
  }

  function renderStatus() {
    const status = document.getElementById("status");
    if (!status) return;
    status.textContent =
      "mode: " + (state.mapReady ? "mapkit" : "not-ready") + "\n" +
      "center: " + state.region.centerLat.toFixed(6) + ", " + state.region.centerLng.toFixed(6) + "\n" +
      "span: " + state.region.latDelta.toFixed(5) + ", " + state.region.lngDelta.toFixed(5) + "\n" +
      "annotations: " + Object.keys(state.annotationsById).length + "\n" +
      "overlays: " + Object.keys(state.overlaysById).length + "\n" +
      "userLocation: " + (state.userLocation ? "set" : "unset") + "\n" +
      "token: " + (state.token ? "set" : "unset");
  }

  function stableHash(value) {
    return JSON.stringify(value);
  }

  function loadMapKitScriptIfNeeded() {
    return new Promise((resolve, reject) => {
      if (window.mapkit) {
        resolve();
        return;
      }
      const existing = document.getElementById("apple-mapkit-script");
      if (existing) {
        existing.addEventListener("load", () => resolve());
        existing.addEventListener("error", () => reject(new Error("failed to load mapkit script")));
        return;
      }
      const script = document.createElement("script");
      script.id = "apple-mapkit-script";
      script.src = "https://cdn.apple-mapkit.com/mk/5.x.x/mapkit.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("failed to load mapkit script"));
      document.head.appendChild(script);
    });
  }

  function attachMapEvents() {
    if (!state.map) return;
    state.map.addEventListener("region-change-start", function () {
      try {
        emit({ type: "regionDidChange", region: state.region, settled: false });
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    });
    state.map.addEventListener("region-change-end", function () {
      try {
        const r = state.map.region;
        if (!r || !r.center || !r.span) return;
        state.region = {
          centerLat: r.center.latitude,
          centerLng: r.center.longitude,
          latDelta: r.span.latitudeDelta,
          lngDelta: r.span.longitudeDelta,
        };
        renderStatus();
        emit({ type: "regionDidChange", region: state.region, settled: true });
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    });
  }

  function applyMapKitRegion(region) {
    if (!state.map || !window.mapkit || !region) return;
    const center = new window.mapkit.Coordinate(region.centerLat, region.centerLng);
    const span = new window.mapkit.CoordinateSpan(region.latDelta, region.lngDelta);
    state.map.region = new window.mapkit.CoordinateRegion(center, span);
  }

  function buildAnnotation(item) {
    const coord = new window.mapkit.Coordinate(item.lat, item.lng);
    const marker = new window.mapkit.MarkerAnnotation(coord, {
      title: item.title || item.id,
      subtitle: item.subtitle || undefined,
    });
    marker.data = { id: item.id };
    marker.addEventListener("select", function () {
      emit({ type: "annotationTapped", id: item.id });
    });
    return marker;
  }

  function reconcileAnnotations(nextItems) {
    const nextMap = {};
    nextItems.forEach((item) => {
      if (item && item.id) nextMap[item.id] = item;
    });

    Object.keys(state.annotationsById).forEach((id) => {
      if (!nextMap[id]) {
        try {
          state.map.removeAnnotation(state.annotationsById[id]);
        } catch (_) {}
        delete state.annotationsById[id];
        delete state.annotationHashesById[id];
      }
    });

    Object.keys(nextMap).forEach((id) => {
      const item = nextMap[id];
      const nextHash = stableHash(item);
      const prevHash = state.annotationHashesById[id];
      if (prevHash === nextHash && state.annotationsById[id]) return;

      if (state.annotationsById[id]) {
        try {
          state.map.removeAnnotation(state.annotationsById[id]);
        } catch (_) {}
      }
      const marker = buildAnnotation(item);
      state.map.addAnnotation(marker);
      state.annotationsById[id] = marker;
      state.annotationHashesById[id] = nextHash;
    });
  }

  function buildPolyline(item) {
    const points = (item.points || []).map((p) => new window.mapkit.Coordinate(p.lat, p.lng));
    if (!points.length) return null;
    return new window.mapkit.PolylineOverlay(points, {
      style: new window.mapkit.Style({
        lineWidth: item.strokeWidth || 4,
        strokeColor: item.strokeColor || "#0EA5E9",
      }),
    });
  }

  function reconcileOverlays(nextItems) {
    const nextMap = {};
    nextItems.forEach((item) => {
      if (item && item.id) nextMap[item.id] = item;
    });

    Object.keys(state.overlaysById).forEach((id) => {
      if (!nextMap[id]) {
        try {
          state.map.removeOverlay(state.overlaysById[id]);
        } catch (_) {}
        delete state.overlaysById[id];
        delete state.overlayHashesById[id];
      }
    });

    Object.keys(nextMap).forEach((id) => {
      const item = nextMap[id];
      const nextHash = stableHash(item);
      const prevHash = state.overlayHashesById[id];
      if (prevHash === nextHash && state.overlaysById[id]) return;

      if (state.overlaysById[id]) {
        try {
          state.map.removeOverlay(state.overlaysById[id]);
        } catch (_) {}
      }

      let overlay = null;
      if (item.type === "MKPolylineOverlay") {
        overlay = buildPolyline(item);
      }
      if (!overlay) return;

      overlay.data = { id: item.id };
      state.map.addOverlay(overlay);
      state.overlaysById[id] = overlay;
      state.overlayHashesById[id] = nextHash;
    });
  }

  function initializeMapKit() {
    // DEBUG_BREAKPOINT_JS_1: init(token) 後にここへ入るか確認
    debugLog("initializeMapKit called");
    return loadMapKitScriptIfNeeded().then(() => {
      if (!window.mapkit) throw new Error("mapkit is unavailable");
      if (!state.token || !String(state.token).startsWith("eyJ")) {
        emitBridgeError("MAPKIT_JS_TOKEN does not look like Apple JWT token (expected prefix: eyJ...)");
      }
      debugLog("mapkit script loaded");
      window.mapkit.init({
        authorizationCallback: function (done) {
          // DEBUG_BREAKPOINT_JS_2: authorizationCallback が呼ばれて token を返せているか
          debugLog("authorizationCallback called");
          done(state.token);
        },
      });
      state.map = new window.mapkit.Map("mapCanvas", {
        isRotationEnabled: true,
        isZoomEnabled: true,
        isScrollEnabled: true,
        showsCompass: window.mapkit.FeatureVisibility && window.mapkit.FeatureVisibility.Adaptive,
      });
      state.map.addEventListener("error", function (e) {
        const msg = (e && e.message) ? e.message : "map error";
        emitBridgeError("MapKit map error: " + msg);
      });
      attachMapEvents();
      state.mapReady = true;
      debugLog("map instance created");
    });
  }

  function applyMapUserLocationConfig() {
    if (!state.mapReady || !state.map) return;
    try {
      if (typeof state.map.showsUserLocation !== "undefined") {
        state.map.showsUserLocation = !!state.userLocationEnabled;
      }
    } catch (_) {}
  }

  function applyUserLocationPoint() {
    if (!state.mapReady || !state.map) return;
    if (!state.userLocationEnabled || !state.userLocation) {
      if (state.userLocationAnnotation) {
        try {
          state.map.removeAnnotation(state.userLocationAnnotation);
        } catch (_) {}
        state.userLocationAnnotation = null;
      }
      return;
    }
    const c = new window.mapkit.Coordinate(state.userLocation.lat, state.userLocation.lng);
    if (!state.userLocationAnnotation) {
      state.userLocationAnnotation = new window.mapkit.MarkerAnnotation(c, { title: "Current Location" });
      try {
        state.map.addAnnotation(state.userLocationAnnotation);
      } catch (_) {}
    } else {
      state.userLocationAnnotation.coordinate = c;
    }
  }

  function applyStateToMap() {
    if (!state.mapReady || !state.map) return;
    applyMapKitRegion(state.region);
    reconcileAnnotations(state.annotations || []);
    reconcileOverlays(state.overlays || []);
    applyMapUserLocationConfig();
    applyUserLocationPoint();
    renderStatus();
  }

  window.MKBridge = {
    init: function (token) {
      // DEBUG_BREAKPOINT_JS_3: Kotlin から init(token) が届いているか
      debugLog("init called from kotlin");
      state.token = token;
      initializeMapKit()
        .then(function () {
          applyStateToMap();
          emit({ type: "mapLoaded" });
        })
        .catch(function (e) {
          state.mapReady = false;
          renderStatus();
          emitBridgeError(e && e.message ? e.message : e);
        });
    },
    applyState: function (payload) {
      debugLog("applyState called");
      if (payload && payload.region) state.region = payload.region;
      if (payload && payload.annotations) state.annotations = payload.annotations;
      if (payload && payload.overlays) state.overlays = payload.overlays;
      if (payload && typeof payload.userLocationEnabled !== "undefined") {
        state.userLocationEnabled = !!payload.userLocationEnabled;
      }
      if (payload && typeof payload.userLocationFollowsHeading !== "undefined") {
        state.userLocationFollowsHeading = !!payload.userLocationFollowsHeading;
      }
      if (payload && typeof payload.userLocationShowsAccuracyRing !== "undefined") {
        state.userLocationShowsAccuracyRing = !!payload.userLocationShowsAccuracyRing;
      }
      try {
        applyStateToMap();
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    },
    applyUserLocation: function (payload) {
      if (!payload) return;
      state.userLocation = {
        lat: payload.lat,
        lng: payload.lng,
      };
      if (typeof payload.followsHeading !== "undefined") {
        state.userLocationFollowsHeading = !!payload.followsHeading;
      }
      if (typeof payload.showsAccuracyRing !== "undefined") {
        state.userLocationShowsAccuracyRing = !!payload.showsAccuracyRing;
      }
      applyUserLocationPoint();
      emit({ type: "userLocationUpdated", lat: payload.lat, lng: payload.lng });
      renderStatus();
    },
    simulatePan: function () {
      if (state.mapReady) {
        state.region.centerLat = state.region.centerLat + 0.001;
        state.region.centerLng = state.region.centerLng + 0.001;
        applyMapKitRegion(state.region);
        emit({ type: "regionDidChange", region: state.region, settled: true });
        renderStatus();
      }
    },
    simulateAnnotationTap: function () {
      const id = (state.annotations[0] && state.annotations[0].id) || "sample-annotation";
      emit({ type: "annotationTapped", id: id });
    },
    simulateOverlayTap: function () {
      const id = (state.overlays[0] && state.overlays[0].id) || "sample-overlay";
      emit({ type: "overlayTapped", id: id });
    },
  };

  renderStatus();
})();

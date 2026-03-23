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
    mapStyle: "standard",
    appearance: "auto",
    language: "auto",
    showsCompass: true,
    showsScale: false,
    showsZoomControl: true,
    showsMapTypeControl: false,
    showsPointsOfInterest: true,
    poiFilter: { type: "all", categories: [] },
    cameraZoomRange: null,
    isRotateEnabled: true,
    isScrollEnabled: true,
    isZoomEnabled: true,
    isPitchEnabled: true,
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
    longPressTimer: null,
    longPressStart: null,
    longPressHandlersInstalled: false,
    lastMapTapAt: 0,
  };

  function emit(payload) {
    if (window.AndroidMKBridge && window.AndroidMKBridge.emitEvent) {
      window.AndroidMKBridge.emitEvent(JSON.stringify(payload));
    }
  }

  function debugLog(message) {
    try {
      console.log("[MKBridgeJS] " + message);
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
      "span: " + state.region.latDelta.toFixed(5) + ", " + state.region.lngDelta.toFixed(5);
  }

  function stableHash(value) {
    return JSON.stringify(value);
  }

  function mapTypeFor(style) {
    if (!window.mapkit || !window.mapkit.Map || !window.mapkit.Map.MapTypes) return null;
    const types = window.mapkit.Map.MapTypes;
    switch (style) {
      case "mutedStandard":
        return types.MutedStandard || types.Standard;
      case "satellite":
        return types.Satellite;
      case "hybrid":
        return types.Hybrid;
      case "standard":
      default:
        return types.Standard;
    }
  }

  function colorSchemeFor(appearance) {
    if (!window.mapkit || !window.mapkit.Map || !window.mapkit.Map.ColorSchemes) return null;
    const schemes = window.mapkit.Map.ColorSchemes;
    switch (appearance) {
      case "dark":
        return schemes.Dark;
      case "light":
        return schemes.Light;
      case "auto":
      default:
        return schemes.Adaptive || null;
    }
  }

  function featureVisibilityFor(enabled) {
    if (!window.mapkit || !window.mapkit.FeatureVisibility) return enabled;
    return enabled
      ? (window.mapkit.FeatureVisibility.Visible || window.mapkit.FeatureVisibility.Adaptive)
      : (window.mapkit.FeatureVisibility.Hidden || window.mapkit.FeatureVisibility.Adaptive);
  }

  function toPoiCategory(category) {
    if (!window.mapkit || !window.mapkit.PointOfInterestCategory) return null;
    const c = window.mapkit.PointOfInterestCategory;
    if (typeof category !== "string") return null;
    if (c[category]) return c[category];
    const key = category.replace(/[-_ ]/g, "").toLowerCase();
    const map = {
      airport: c.Airport,
      amusementpark: c.AmusementPark,
      aquarium: c.Aquarium,
      atm: c.ATM,
      bakery: c.Bakery,
      bank: c.Bank,
      beach: c.Beach,
      brewery: c.Brewery,
      cafe: c.Cafe,
      campground: c.Campground,
      carrental: c.CarRental,
      evcharger: c.EVCharger,
      firestation: c.FireStation,
      fitnesscenter: c.FitnessCenter,
      foodmarket: c.FoodMarket,
      gasstation: c.GasStation,
      hospital: c.Hospital,
      hotel: c.Hotel,
      laundry: c.Laundry,
      library: c.Library,
      marina: c.Marina,
      movieheater: c.MovieTheater,
      museum: c.Museum,
      nationalpark: c.NationalPark,
      nightlife: c.Nightlife,
      park: c.Park,
      parking: c.Parking,
      pharmacy: c.Pharmacy,
      policestation: c.Police,
      postoffice: c.PostOffice,
      publictransport: c.PublicTransport,
      restaurant: c.Restaurant,
      restroom: c.Restroom,
      school: c.School,
      stadium: c.Stadium,
      store: c.Store,
      theater: c.Theater,
      university: c.University,
      winery: c.Winery,
      zoo: c.Zoo,
    };
    return map[key] || null;
  }

  function categoriesFromFilter(filter) {
    const src = (filter && filter.categories) ? filter.categories : [];
    return src
      .map((v) => toPoiCategory(v))
      .filter((v) => !!v);
  }

  function applyPoiFilter(filter, defaultPoiVisible) {
    if (!state.mapReady || !state.map) return;
    const effectiveFilter = filter || { type: "all", categories: [] };
    const type = (effectiveFilter.type || "all").toLowerCase();
    const categories = categoriesFromFilter(effectiveFilter);

    try {
      if (window.mapkit && window.mapkit.PointOfInterestFilter) {
        if (type === "none" || (!defaultPoiVisible && type === "all")) {
          if (typeof window.mapkit.PointOfInterestFilter.excludingAll === "function") {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.excludingAll();
            return;
          }
        }

        if (type === "include" && categories.length > 0) {
          if (typeof window.mapkit.PointOfInterestFilter.including === "function") {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.including(categories);
            return;
          }
        }

        if (type === "exclude" && categories.length > 0) {
          if (typeof window.mapkit.PointOfInterestFilter.excluding === "function") {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.excluding(categories);
            return;
          }
        }

        state.map.pointOfInterestFilter = null;
      }
    } catch (_) {}
  }

  function applyCameraZoomRange(range) {
    if (!state.mapReady || !state.map) return;
    try {
      if (!window.mapkit || typeof window.mapkit.CameraZoomRange === "undefined") return;
      if (!range || (range.minDistanceMeter == null && range.maxDistanceMeter == null)) {
        state.map.cameraZoomRange = null;
        return;
      }
      const minD = (range.minDistanceMeter == null) ? undefined : range.minDistanceMeter;
      const maxD = (range.maxDistanceMeter == null) ? undefined : range.maxDistanceMeter;
      state.map.cameraZoomRange = new window.mapkit.CameraZoomRange(minD, maxD);
    } catch (_) {}
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

  function waitForCanvasSizeReady(canvasId, timeoutMs) {
    return new Promise((resolve, reject) => {
      const startedAt = Date.now();
      const check = function () {
        const el = document.getElementById(canvasId);
        const width = el ? el.clientWidth : 0;
        const height = el ? el.clientHeight : 0;
        if (width > 0 && height > 0) {
          resolve({ width: width, height: height });
          return;
        }
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error("mapCanvas size is not ready: " + width + "x" + height));
          return;
        }
        requestAnimationFrame(check);
      };
      check();
    });
  }

  function pointToCoordinate(pageX, pageY) {
    if (!state.map || !window.mapkit) return null;
    try {
      if (typeof state.map.convertPointOnPageToCoordinate === "function") {
        const domPoint =
          (typeof DOMPoint !== "undefined")
            ? new DOMPoint(pageX, pageY)
            : { x: pageX, y: pageY };
        return state.map.convertPointOnPageToCoordinate(domPoint);
      }
      const point = new window.mapkit.Point(pageX, pageY);
      if (typeof state.map.convertPointToCoordinate === "function") {
        return state.map.convertPointToCoordinate(point);
      }
    } catch (_) {}
    return null;
  }

  function setupLongPressDetection() {
    const target = state.map && state.map.element ? state.map.element : null;
    if (!target) return;
    if (state.longPressHandlersInstalled) return;
    state.longPressHandlersInstalled = true;

    let primaryPointerId = null;
    let pointerDownAt = 0;
    let moved = false;
    let longPressFired = false;
    const moveThresholdSq = 100;

    const clearTimer = function () {
      if (state.longPressTimer) {
        clearTimeout(state.longPressTimer);
        state.longPressTimer = null;
      }
    };

    const resetGestureState = function () {
      clearTimer();
      state.longPressStart = null;
      primaryPointerId = null;
      pointerDownAt = 0;
      moved = false;
      longPressFired = false;
    };

    const onPointerDown = function (event) {
      if (!event.isPrimary) {
        resetGestureState();
        return;
      }
      resetGestureState();
      primaryPointerId = event.pointerId;
      pointerDownAt = Date.now();
      state.longPressStart = { x: event.pageX, y: event.pageY };
      state.longPressTimer = setTimeout(function () {
        if (!state.longPressStart) return;
        const c = pointToCoordinate(state.longPressStart.x, state.longPressStart.y);
        if (!c) return;
        longPressFired = true;
        debugLog("emit longPress lat=" + c.latitude + " lng=" + c.longitude);
        emit({ type: "longPress", lat: c.latitude, lng: c.longitude });
      }, 550);
    };

    const onPointerMove = function (event) {
      if (primaryPointerId == null) return;
      if (!event.isPrimary || event.pointerId !== primaryPointerId) {
        resetGestureState();
        return;
      }
      if (!state.longPressStart || !state.longPressTimer) return;
      const dx = event.pageX - state.longPressStart.x;
      const dy = event.pageY - state.longPressStart.y;
      const moveSq = dx * dx + dy * dy;
      if (moveSq > moveThresholdSq) {
        moved = true;
        clearTimer();
      }
    };

    const onPointerEnd = function (event) {
      if (primaryPointerId == null) return;
      if (event.pointerId === primaryPointerId && state.longPressStart) {
        const duration = Date.now() - pointerDownAt;
        const c = pointToCoordinate(event.pageX, event.pageY) ||
          pointToCoordinate(state.longPressStart.x, state.longPressStart.y);
        if (!longPressFired && !moved && duration < 550 && c) {
          const now = Date.now();
          if (now - state.lastMapTapAt >= 180) {
            state.lastMapTapAt = now;
            debugLog("emit mapTapped lat=" + c.latitude + " lng=" + c.longitude);
            emit({ type: "mapTapped", lat: c.latitude, lng: c.longitude });
          }
        }
      }
      resetGestureState();
    };

    target.addEventListener("pointerdown", onPointerDown);
    target.addEventListener("pointermove", onPointerMove);
    target.addEventListener("pointerup", onPointerEnd);
    target.addEventListener("pointercancel", onPointerEnd);

    target.addEventListener("click", function (event) {
      if (primaryPointerId != null) return;
      const c = pointToCoordinate(event.pageX, event.pageY);
      if (!c) return;
      const now = Date.now();
      if (now - state.lastMapTapAt < 180) return;
      state.lastMapTapAt = now;
      debugLog("emit mapTapped(click) lat=" + c.latitude + " lng=" + c.longitude);
      emit({ type: "mapTapped", lat: c.latitude, lng: c.longitude });
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

    state.map.addEventListener("select", function (event) {
      try {
        if (event && event.annotation && event.annotation.data && event.annotation.data.id) {
          debugLog("emit annotationTapped id=" + String(event.annotation.data.id));
          emit({ type: "annotationTapped", id: String(event.annotation.data.id) });
          return;
        }
        if (event && event.overlay && event.overlay.data && event.overlay.data.id) {
          debugLog("emit overlayTapped id=" + String(event.overlay.data.id));
          emit({ type: "overlayTapped", id: String(event.overlay.data.id) });
        }
      } catch (_) {}
    });

    setupLongPressDetection();
  }

  function applyMapKitRegion(region) {
    if (!state.map || !window.mapkit || !region) return;
    const center = new window.mapkit.Coordinate(region.centerLat, region.centerLng);
    const span = new window.mapkit.CoordinateSpan(region.latDelta, region.lngDelta);
    state.map.region = new window.mapkit.CoordinateRegion(center, span);
  }

  function resolveImageSource(source) {
    if (!source || !source.kind) return null;
    if (source.kind === "url") return source.value || null;
    if (source.kind === "base64Png") return "data:image/png;base64," + String(source.value || "");
    if (source.kind === "resourceName") {
      return "file:///android_res/drawable/" + source.value + ".png";
    }
    return null;
  }

  function buildAnnotation(item) {
    const coord = new window.mapkit.Coordinate(item.lat, item.lng);
    const style = item.style || { kind: "default" };
    let annotation = null;
    try {
      if (style.kind === "customImage" && window.mapkit.ImageAnnotation) {
        const imageUrl = resolveImageSource(style.source);
        const h = Number(style.heightDp || 36);
        annotation = new window.mapkit.ImageAnnotation(coord, {
          title: item.title || item.id,
          subtitle: item.subtitle || undefined,
          url: { 1: imageUrl, 2: imageUrl, 3: imageUrl },
          anchorOffset: new DOMPoint(0, -Math.round(h / 2)),
        });
      } else {
        const options = {
          title: item.title || item.id,
          subtitle: item.subtitle || undefined,
          color: style.tintHex || undefined,
          glyphText: style.glyphText || undefined,
        };
        const glyphImageUrl = resolveImageSource(style.glyphImageSource);
        if (glyphImageUrl) {
          try {
            options.glyphImage = { 1: glyphImageUrl, 2: glyphImageUrl, 3: glyphImageUrl };
            options.glyphText = undefined;
          } catch (_) {}
        }
        annotation = new window.mapkit.MarkerAnnotation(coord, options);
      }
    } catch (_) {
      annotation = new window.mapkit.MarkerAnnotation(coord, {
        title: item.title || item.id,
        subtitle: item.subtitle || undefined,
      });
    }
    if (!annotation) return null;
    annotation.data = { id: item.id };
    if (item.isSelected && typeof annotation.selected !== "undefined") {
      annotation.selected = true;
    }
    if (typeof annotation.addEventListener === "function") {
      annotation.addEventListener("select", function () {
        debugLog("emit annotationTapped(annotation.select) id=" + item.id);
        emit({ type: "annotationTapped", id: item.id });
      });
    }
    return annotation;
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
      if (item && item.isVisible === false) return;
      const nextHash = stableHash(item);
      const prevHash = state.annotationHashesById[id];
      if (prevHash === nextHash && state.annotationsById[id]) return;

      if (state.annotationsById[id]) {
        try {
          state.map.removeAnnotation(state.annotationsById[id]);
        } catch (_) {}
      }
      const marker = buildAnnotation(item);
      if (!marker) return;
      state.map.addAnnotation(marker);
      state.annotationsById[id] = marker;
      state.annotationHashesById[id] = nextHash;
    });
  }

  function applyOverlayStyle(item) {
    return new window.mapkit.Style({
      lineWidth: item.strokeWidth || 3,
      strokeColor: item.strokeColor || "#0EA5E9",
      fillColor: item.fillColor || undefined,
    });
  }

  function buildPolyline(item) {
    const points = (item.points || []).map((p) => new window.mapkit.Coordinate(p.lat, p.lng));
    if (!points.length) return null;
    return new window.mapkit.PolylineOverlay(points, {
      style: applyOverlayStyle(item),
    });
  }

  function buildPolygon(item) {
    const points = (item.points || []).map((p) => new window.mapkit.Coordinate(p.lat, p.lng));
    if (points.length < 3) return null;
    return new window.mapkit.PolygonOverlay(points, {
      style: applyOverlayStyle(item),
    });
  }

  function buildCircle(item) {
    if (typeof item.centerLat === "undefined" || typeof item.centerLng === "undefined") return null;
    const center = new window.mapkit.Coordinate(item.centerLat, item.centerLng);
    return new window.mapkit.CircleOverlay(center, item.radiusMeter || 100, {
      style: applyOverlayStyle(item),
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
      } else if (item.type === "MKPolygonOverlay") {
        overlay = buildPolygon(item);
      } else if (item.type === "MKCircleOverlay") {
        overlay = buildCircle(item);
      }
      if (!overlay) return;

      overlay.data = { id: item.id };
      if (typeof overlay.addEventListener === "function") {
        overlay.addEventListener("select", function () {
          debugLog("emit overlayTapped(overlay.select) id=" + item.id);
          emit({ type: "overlayTapped", id: item.id });
        });
      }
      state.map.addOverlay(overlay);
      state.overlaysById[id] = overlay;
      state.overlayHashesById[id] = nextHash;
    });
  }

  function applyMapOptions() {
    if (!state.mapReady || !state.map) return;
    const effectivePoi = !!state.showsPointsOfInterest;

    try {
      const nextMapType = mapTypeFor(state.mapStyle);
      if (nextMapType) {
        state.map.mapType = nextMapType;
      }
    } catch (_) {}

    try {
      const nextScheme = colorSchemeFor(state.appearance);
      if (nextScheme) {
        state.map.colorScheme = nextScheme;
      }
    } catch (_) {}

    try {
      state.map.showsPointsOfInterest = !!effectivePoi;
    } catch (_) {}
    applyPoiFilter(state.poiFilter, effectivePoi);
    applyCameraZoomRange(state.cameraZoomRange);

    try {
      state.map.showsCompass = featureVisibilityFor(!!state.showsCompass);
    } catch (_) {}

    try {
      state.map.showsScale = featureVisibilityFor(!!state.showsScale);
    } catch (_) {}

    try {
      state.map.isRotationEnabled = !!state.isRotateEnabled;
      state.map.isScrollEnabled = !!state.isScrollEnabled;
      state.map.isZoomEnabled = !!state.isZoomEnabled;
      state.map.isPitchEnabled = !!state.isPitchEnabled;
    } catch (_) {}

    try {
      if (typeof state.map.showsMapTypeControl !== "undefined") {
        state.map.showsMapTypeControl = !!state.showsMapTypeControl;
      }
    } catch (_) {}
    try {
      if (typeof state.map.showsZoomControl !== "undefined") {
        state.map.showsZoomControl = !!state.showsZoomControl;
      }
    } catch (_) {}

    debugLog(
      "applyMapOptions style=" + state.mapStyle +
      " poi=" + effectivePoi +
      " compass=" + state.showsCompass +
      " zoomControl=" + state.showsZoomControl +
      " mapType=" + String(state.map.mapType)
    );
  }

  function initializeMapKit() {
    debugLog("initializeMapKit called");
    return loadMapKitScriptIfNeeded().then(() => {
      if (!window.mapkit) throw new Error("mapkit is unavailable");
      if (!state.token || !String(state.token).startsWith("eyJ")) {
        emitBridgeError("MAPKIT_JS_TOKEN does not look like Apple JWT token (expected prefix: eyJ...)");
      }

      const initOptions = {
        authorizationCallback: function (done) {
          debugLog("authorizationCallback called");
          done(state.token);
        },
      };
      if (state.language && state.language !== "auto") {
        initOptions.language = state.language;
      }
      window.mapkit.init(initOptions);

      const mapCanvasId = "mapCanvas";
      return waitForCanvasSizeReady(mapCanvasId, 4000).then(function (size) {
        debugLog("mapCanvas size: " + size.width + "x" + size.height);
        state.map = new window.mapkit.Map(mapCanvasId, {
          isRotationEnabled: true,
          isZoomEnabled: true,
          isScrollEnabled: true,
        });

        state.map.addEventListener("error", function (e) {
          const msg = e && e.message ? e.message : "map error";
          emitBridgeError("MapKit map error: " + msg);
        });

        attachMapEvents();
        state.mapReady = true;
        applyMapOptions();
        debugLog("map instance created");
      });
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
    applyMapOptions();
    reconcileAnnotations(state.annotations || []);
    reconcileOverlays(state.overlays || []);
    applyMapUserLocationConfig();
    applyUserLocationPoint();
    renderStatus();
  }

  window.MKBridge = {
    init: function (token) {
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

      if (payload && payload.mapStyle) state.mapStyle = payload.mapStyle;
      if (payload && payload.appearance) state.appearance = payload.appearance;
      if (payload && payload.language) state.language = payload.language;
      if (payload && typeof payload.showsCompass !== "undefined") state.showsCompass = !!payload.showsCompass;
      if (payload && typeof payload.showsScale !== "undefined") state.showsScale = !!payload.showsScale;
      if (payload && typeof payload.showsZoomControl !== "undefined") {
        state.showsZoomControl = !!payload.showsZoomControl;
      }
      if (payload && typeof payload.showsMapTypeControl !== "undefined") {
        state.showsMapTypeControl = !!payload.showsMapTypeControl;
      }
      if (payload && typeof payload.showsPointsOfInterest !== "undefined") {
        state.showsPointsOfInterest = !!payload.showsPointsOfInterest;
      }
      if (payload && payload.poiFilter) {
        state.poiFilter = payload.poiFilter;
      }
      if (payload && typeof payload.cameraZoomRange !== "undefined") {
        state.cameraZoomRange = payload.cameraZoomRange;
      }
      if (payload && typeof payload.isRotateEnabled !== "undefined") state.isRotateEnabled = !!payload.isRotateEnabled;
      if (payload && typeof payload.isScrollEnabled !== "undefined") state.isScrollEnabled = !!payload.isScrollEnabled;
      if (payload && typeof payload.isZoomEnabled !== "undefined") state.isZoomEnabled = !!payload.isZoomEnabled;
      if (payload && typeof payload.isPitchEnabled !== "undefined") state.isPitchEnabled = !!payload.isPitchEnabled;

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
      if (!state.mapReady) return;
      state.region.centerLat = state.region.centerLat + 0.001;
      state.region.centerLng = state.region.centerLng + 0.001;
      applyMapKitRegion(state.region);
      emit({ type: "regionDidChange", region: state.region, settled: true });
      renderStatus();
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

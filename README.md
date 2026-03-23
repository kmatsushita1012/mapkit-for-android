# MapKit for Android

English | [日本語](README.ja.md)

Kotlin library that bridges MapKit JS on Android `WebView`.
You can control region, annotations, overlays, and map options through Kotlin models and `MKMapView` for Compose.

## Install (JitPack, 0.1.1)

### 1. Add JitPack to `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### 2. Add dependency

Recommended: single aggregate artifact.

```kotlin
dependencies {
    implementation("io.github.kmatsushita1012:mapkit:0.1.1")
}
```

You can also use per-module artifacts:

```kotlin
dependencies {
    implementation("io.github.kmatsushita1012:mapkit-core:0.1.1")
    implementation("io.github.kmatsushita1012:mapkit-webview:0.1.1")
    implementation("io.github.kmatsushita1012:mapkit-compose:0.1.1")
}
```

## Prerequisites

### 1. Prepare Apple MapKit JS token

Generate a MapKit JS JWT and inject it at app startup via `MKMapKit.init(token)`.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MKMapKit.init(BuildConfig.MAPKIT_JS_TOKEN)

        setContent { App() }
    }
}
```

`local.properties` example:

```properties
MAPKIT_JS_TOKEN=YOUR_MAPKIT_JS_JWT
```

`build.gradle.kts` example to load token from `local.properties` with env-var fallback:

```kotlin
import java.util.Properties

android {
    defaultConfig {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use(::load)
        }
        val mapToken = localProps.getProperty("MAPKIT_JS_TOKEN")
            ?: System.getenv("MAPKIT_JS_TOKEN")
            ?: "DUMMY_TOKEN_FOR_DEMO"
        val escapedMapToken = mapToken
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "MAPKIT_JS_TOKEN", "\"$escapedMapToken\"")
    }
}
```

See [docs/setup/mapkitjs-token-env.md](docs/setup/mapkitjs-token-env.md) for details.

### 2. Android permissions / manifest

At minimum:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

If `showsUserLocation` is enabled, runtime location permission is also required.

## Minimal usage (Compose)

```kotlin
@Composable
fun SampleMap() {
    var state by remember {
        mutableStateOf(
            MKMapState(
                region = MKCoordinateRegion.fromCenter(
                    center = MKCoordinate(35.681236, 139.767125),
                    latitudeDelta = 0.05,
                    longitudeDelta = 0.05
                ),
                options = MKMapOptions(
                    mapStyle = MKMapStyle.standard,
                    userLocation = MKUserLocationOptions(isEnabled = false)
                )
            )
        )
    }

    MKMapView(
        state = state,
        onEvent = { event ->
            when (event) {
                is MKMapEvent.RegionDidChange -> {
                    if (event.settled) {
                        state = state.copy(region = event.region)
                    }
                }
                else -> Unit
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

## Modules

- `source/mapkit-for-android`: aggregate artifact for easier adoption
- `source/mapkit-for-android-core`: public models and init API
- `source/mapkit-for-android-webview`: WebView + MapKit JS bridge
- `source/mapkit-for-android-compose`: Compose API (`MKMapView`)
- `example/app`: sample app

# MapKit for Android

[English](/Users/matsushitakazuya/private/MapKitForAndroid/README.md) | 日本語

MapKit JS を Android `WebView` で扱うための Kotlin ライブラリです。  
Compose から `MKMapView` を使って地図を表示し、Annotation / Overlay / Region / Option を Kotlin モデルで制御できます。

## インストール (JitPack, 0.1.0)

### 1. `settings.gradle.kts` に JitPack を追加

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

### 2. 依存を追加

推奨は単一artifactです。

```kotlin
dependencies {
    implementation("io.github.kmatsushita1012:mapkit-android:0.1.0")
}
```

必要に応じて個別モジュールも選択できます。

```kotlin
dependencies {
    implementation("io.github.kmatsushita1012:mapkit-android-core:0.1.0")
    implementation("io.github.kmatsushita1012:mapkit-android-webview:0.1.0")
    implementation("io.github.kmatsushita1012:mapkit-android-compose:0.1.0")
}
```

## 事前準備

### 1. Apple MapKit JS Token を用意

MapKit JS 用 JWT token を発行し、アプリ起動時に `MKMapKit.init(token)` で注入します。

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MKMapKit.init(BuildConfig.MAPKIT_JS_TOKEN)

        setContent { App() }
    }
}
```

`local.properties` 例:

```properties
MAPKIT_JS_TOKEN=YOUR_MAPKIT_JS_JWT
```

`build.gradle.kts` で `local.properties` と環境変数をフォールバックして `BuildConfig` に渡す例:

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

トークン設定の詳細は [docs/setup/mapkitjs-token-env.md](/Users/matsushitakazuya/private/MapKitForAndroid/docs/setup/mapkitjs-token-env.md) を参照してください。

### 2. Android permission / manifest

最低限、以下を宣言してください。

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

`showsUserLocation` を有効化する場合は runtime permission の許可も必要です。

## 最小利用例 (Compose)

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

## モジュール構成

- `source/mapkit-android`: 導入を簡単にする集約モジュール
- `source/mapkit-android-core`: 公開モデル/初期化 API
- `source/mapkit-android-webview`: WebView + MapKit JS bridge
- `source/mapkit-android-compose`: Compose 向け API (`MKMapView`)
- `example/app`: サンプルアプリ

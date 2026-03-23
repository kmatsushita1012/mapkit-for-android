# 02. Map 全体仕様

## 外部仕様(interface)

### 1. 公開 UI/API

```kotlin
package com.studiomk.mapkit.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MKMapView(
    state: MKMapState,
    onEvent: (MKMapEvent) -> Unit,
    modifier: Modifier = Modifier
)

object MKMapKit {
    fun init(token: String)
    fun clear()
    fun isInitialized(): Boolean
}
```

### 2. 公開モデル

```kotlin
package com.studiomk.mapkit.model

data class MKMapState(
    val region: MKCoordinateRegion,
    val annotations: List<MKAnnotation> = emptyList(),
    val overlays: List<MKOverlay> = emptyList(),
    val options: MKMapOptions = MKMapOptions()
)
```

### 3. 公開イベント

```kotlin
package com.studiomk.mapkit.model

sealed interface MKMapEvent {
    data object MapLoaded : MKMapEvent
    data class MapError(val cause: MKMapErrorCause) : MKMapEvent
    data class RegionDidChange(val region: MKCoordinateRegion, val settled: Boolean) : MKMapEvent
    data class AnnotationTapped(val id: String) : MKMapEvent
    data class OverlayTapped(val id: String) : MKMapEvent
    data class UserLocationUpdated(val coordinate: MKCoordinate) : MKMapEvent
}

sealed interface MKMapErrorCause {
    data object NotInitialized : MKMapErrorCause
    data object TokenUnavailable : MKMapErrorCause
    data class BridgeFailure(val message: String) : MKMapErrorCause
}
```

### 4. 初期化コード(利用者が書くコード)

```kotlin
package com.example.app

import androidx.compose.runtime.*
import com.studiomk.mapkit.api.MKMapKit
import com.studiomk.mapkit.api.MKMapView
import com.studiomk.mapkit.model.*

@Composable
fun MapScreen() {
    LaunchedEffect(Unit) {
        if (!MKMapKit.isInitialized()) {
            MKMapKit.init(BuildConfig.MAPKIT_JS_TOKEN)
        }
    }

    var state by remember {
        mutableStateOf(
            MKMapState(
                region = MKCoordinateRegion.fromCenter(
                    center = MKCoordinate(35.681236, 139.767125),
                    latitudeDelta = 0.05,
                    longitudeDelta = 0.05
                )
            )
        )
    }

    MKMapView(
        state = state,
        onEvent = { event ->
            when (event) {
                is MKMapEvent.RegionDidChange -> if (event.settled) state = state.copy(region = event.region)
                is MKMapEvent.AnnotationTapped -> println("annotation tapped: ${event.id}")
                is MKMapEvent.OverlayTapped -> println("overlay tapped: ${event.id}")
                is MKMapEvent.UserLocationUpdated -> println("user location: ${event.coordinate}")
                else -> Unit
            }
        }
    )
}
```

## 内部仕様

### 1. 実装方式の明示
- Kotlin は MapKit JS の外部 URL を直接叩かない。
- WebView はライブラリ同梱の中間 HTML/JS(ブリッジ JS) を読む。
- 中間 JS が MapKit JS を初期化し、Kotlin <-> JS 双方向ブリッジを担当する。

### 2. MapState 監視型反映(MapCommand 廃止)
- Bridge は `MKMapState` の前回値/今回値を比較し、差分のみ JS に適用する。
- 差分対象: region / annotations / overlays / options。
- 利用者は `MKMapState` を更新するだけでよい。

### 3. 公開 `MKXXX` と内部モデルのブリッジ
- 公開インターフェースは `MK` 接頭辞で統一する。
- 内部実装は `MapState`, `CoordinateRegion`, `AnnotationModel` のような非 `MK` 名を使う。
- Bridge 層で `MK* -> Internal*` を変換する。

### 4. 初期化と token 保持
- `MKMapKit.init(token)` はアプリ起動時に 1 回呼ぶ。
- token は内部 static 領域に保持する。
- 未初期化で `MKMapView` が生成された場合は `MKMapEvent.MapError(TokenUnavailable)` を返す。

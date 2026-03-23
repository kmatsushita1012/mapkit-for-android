# 04. Annotation / Overlay 仕様

## 外部仕様(interface)

### 1. Annotation モデル

```kotlin
package com.studiomk.mapkit.model

data class MKAnnotation(
    val id: String,
    val coordinate: MKCoordinate,
    val title: String? = null,
    val subtitle: String? = null,
    val isVisible: Boolean = true,
    val isSelected: Boolean = false,
    val style: MKAnnotationStyle = MKAnnotationStyle.Default()
)

sealed interface MKImageSource {
    data class Url(val value: String) : MKImageSource
    data class Base64Png(val value: String) : MKImageSource
    data class ResourceName(val value: String) : MKImageSource
}

sealed interface MKAnnotationStyle {
    data class Default(
        val tintHex: String? = null,
        val glyphText: String? = null,
        val glyphImageSource: MKImageSource? = null
    ) : MKAnnotationStyle
    data class CustomImage(
        val source: MKImageSource,
        val widthDp: Int,
        val heightDp: Int,
        val anchorX: Double = 0.5,
        val anchorY: Double = 1.0
    ) : MKAnnotationStyle
}
```

### 2. Overlay モデル

```kotlin
package com.studiomk.mapkit.model

sealed interface MKOverlay {
    val id: String
    val isVisible: Boolean
    val zIndex: Int
    val style: MKOverlayStyle
}

data class MKPolylineOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKPolygonOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    val holes: List<List<MKCoordinate>> = emptyList(),
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKCircleOverlay(
    override val id: String,
    val center: MKCoordinate,
    val radiusMeter: Double,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKOverlayStyle(
    val strokeColorHex: String = "#007AFF",
    val strokeWidth: Double = 2.0,
    val fillColorHex: String? = null,
    val lineDashPattern: List<Double>? = null
)
```

### 3. 公開イベント

```kotlin
sealed interface MKAnnotationOverlayEvent : MKMapEvent {
    data class AnnotationTapped(val annotationId: String) : MKAnnotationOverlayEvent
    data class AnnotationSelected(val annotationId: String) : MKAnnotationOverlayEvent
    data class AnnotationDeselected(val annotationId: String) : MKAnnotationOverlayEvent
    data class OverlayTapped(val overlayId: String) : MKAnnotationOverlayEvent
}
```

### 4. クロージャ(タップハンドラ)の扱い
- `MKAnnotation` / `MKOverlay` 自体にクロージャは持たせない。
- タップ処理は `MKMapEvent.AnnotationTapped` / `MKMapEvent.OverlayTapped` を画面側で分岐する。

### 5. 現在地表示

```kotlin
data class MKUserLocationOptions(
    val isEnabled: Boolean = false,
    val followsHeading: Boolean = false,
    val showsAccuracyRing: Boolean = true
)
```

- `MKMapOptions.userLocation` で現在地表示を制御する。
- 現在地更新は `MKMapEvent.UserLocationUpdated` で受け取る。
- 位置情報権限がない場合は `isEnabled=true` でも描画しない。

## 内部仕様

### 1. 差分更新
- `id` を主キーとして `add/update/remove` を算出。
- `update` は `geometry/style/visibility/selected` のいずれか変化時。
- 全消し再描画は禁止。

### 2. 公開 `MKXXX` と内部モデルのブリッジ
- Bridge で `MKAnnotation -> AnnotationModel`、`MKOverlay -> OverlayModel` に変換。
- 内部モデルには `MK` 接頭辞を付けない。

### 3. カスタム画像描画方式
- `MKAnnotationStyle.CustomImage` は以下で描画する。
- `Url`: JS 側 `img.src` に URL を設定して描画。
- `Base64Png`: `data:image/png;base64,...` へ変換して描画。
- `ResourceName`: Android リソースを base64 化して JS へ渡し描画。
- 画像は内部キャッシュ(key: source + size)し、同一画像は再デコードしない。

### 4. ヒットテスト規約
- JS 側で描画オブジェクトと `id` を 1:1 保持。
- タップ時は最高 `zIndex` の 1 件を Kotlin 側へ返す。

### 5. 現在地実装方針
- Android `FusedLocationProviderClient` で位置取得。
- 更新を Bridge 経由で JS に渡し、`showsUserLocation` 相当の表示を同期。
- 権限状態の変化時は内部で追従し、必要なら `MapError` を通知する。

### 6. Polygon/Circle fill alpha の既知制約
- Polygon と Circle の `fillColor` は alpha 指定を渡しても、MapKit JS 側の描画特性により「完全不透明」に見えない場合がある。
- 実装では `rgba(...)` を明示して渡すが、最終描画は WebGL/MapKit JS のレンダラ依存。
- そのため仕様上は「alpha が効く」ことを保証しつつ、見た目の上限は実行環境依存であることを明記する。

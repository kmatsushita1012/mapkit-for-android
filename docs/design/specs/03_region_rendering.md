# 03. CoordinateRegion 等の描画系仕様

## 外部仕様(interface)

### 1. 公開モデル(`MKXXX` 統一)

```kotlin
package com.studiomk.mapkit.model

data class MKCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class MKCoordinateSpan(
    val latitudeDelta: Double,
    val longitudeDelta: Double
)

data class MKEdgePadding(
    val top: Double,
    val left: Double,
    val bottom: Double,
    val right: Double
)

data class MKCoordinateRegion(
    val center: MKCoordinate,
    val span: MKCoordinateSpan
) {
    companion object {
        fun fromCenter(
            center: MKCoordinate,
            latitudeDelta: Double,
            longitudeDelta: Double
        ): MKCoordinateRegion = MKCoordinateRegion(
            center = center,
            span = MKCoordinateSpan(latitudeDelta, longitudeDelta)
        )

        fun fromCoordinates(
            coordinates: List<MKCoordinate>,
            edgePadding: MKEdgePadding = MKEdgePadding(16.0, 16.0, 16.0, 16.0)
        ): MKCoordinateRegion {
            require(coordinates.isNotEmpty()) { "coordinates must not be empty" }
            val lats = coordinates.map { it.latitude }
            val lngs = coordinates.map { it.longitude }
            val minLat = lats.min()
            val maxLat = lats.max()
            val minLng = lngs.min()
            val maxLng = lngs.max()
            val center = MKCoordinate((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
            val latDelta = (maxLat - minLat) + edgePadding.top + edgePadding.bottom
            val lngDelta = (maxLng - minLng) + edgePadding.left + edgePadding.right
            return fromCenter(center, latDelta, lngDelta)
        }
    }
}
```

### 2. 仕様上の契約
- `MKCoordinateRegion` は純粋な表示領域モデルのみ公開する。
- `lastOperation` は公開しない。
- 利用者起因の初期/更新状態は常に system 扱いで内部へ渡る。
- ユーザー操作由来はライブラリ内部でのみ `userInteractive` として扱う。

## 内部仕様

### 1. 内部モデル

```kotlin
package com.studiomk.mapkit.internal.region

enum class InternalRegionOperation {
    system,
    userInteractive
}

data class InternalCoordinateRegion(
    val centerLat: Double,
    val centerLng: Double,
    val latDelta: Double,
    val lngDelta: Double,
    val operation: InternalRegionOperation,
    val revision: Long,
    val settled: Boolean
)
```

### 2. 反映ルール
- Kotlin から入る `MKCoordinateRegion` は `operation=system` で変換。
- JS から入るジェスチャイベントは `operation=userInteractive` で生成。
- `userInteractive` 中は押し戻し適用しない。
- `settled=true` 到達時のみ、利用者側 state へ採用するのを推奨。

### 3. 近似比較
- 緯度経度: `1e-6`
- span: `1e-5`
- 近似一致時は再適用をスキップして反映ループを防ぐ。

# Feature Request: Map設定系の拡張候補 (PlaceDetail除外)

作成日: 2026-03-22
対象: `mapkit-for-android`

## 方針
- 本ファイルは「実装候補の要求仕様」を管理する。
- 実装完了後に、正式仕様は `docs/design/` へ反映する。
- `PlaceDetail` は後回しとし、本書の対象外とする。

## 背景
現状では `traffic` と `NavigationEmphasis` は MapKit JS 側の挙動差や再現性の問題があり、公開APIから削除した。
そのため、再現性が高く、Android WebView + MapKit JS で安定運用しやすい設定を優先的に追加する。

## 候補一覧

### 1. Camera Zoom Range
優先度: High

#### 外部仕様(interface)
- `MKMapOptions` へズーム制限設定を追加する。
- 例:
```kotlin
data class MKCameraZoomRange(
    val minDistanceMeter: Double? = null,
    val maxDistanceMeter: Double? = null
)

// MKMapOptions に追加
val cameraZoomRange: MKCameraZoomRange? = null
```

#### 内部仕様
- Kotlin -> JS bridge payload に `cameraZoomRange` を追加。
- JS で `map.cameraZoomRange` を安全に適用。
- `null` の場合は制限解除。

#### 受け入れ条件
- min/max の更新が map 再初期化なしで反映される。
- min > max の不正値は Kotlin 側で拒否または正規化。

---

### 2. Zoom Control 表示切替
優先度: High

#### 外部仕様(interface)
- `MKMapOptions` にズームコントロール表示設定を追加。
- 例:
```kotlin
val showsZoomControl: Boolean = false
```

#### 内部仕様
- JS で `map.showsZoomControl` を適用。
- Feature availability をチェックし、未対応環境では no-op。

#### 受け入れ条件
- ON/OFF で即時反映される。
- 未対応環境でもクラッシュしない。

---

### 3. POI フィルタの詳細化 (カテゴリ指定)
優先度: Medium

#### 外部仕様(interface)
- 現在の `showsPointsOfInterest: Boolean` に加えてカテゴリ絞り込みを追加。
- 例:
```kotlin
sealed interface MKPoiFilter {
    data object All : MKPoiFilter
    data object None : MKPoiFilter
    data class Include(val categories: List<String>) : MKPoiFilter
    data class Exclude(val categories: List<String>) : MKPoiFilter
}

// MKMapOptions に追加
val poiFilter: MKPoiFilter = MKPoiFilter.All
```

#### 内部仕様
- JS で `map.pointOfInterestFilter` を構築して適用。
- `showsPointsOfInterest` と競合時は `poiFilter` を優先。

#### 受け入れ条件
- All / None / Include / Exclude が UI上で差として確認できる。
- 不正カテゴリは安全に無視または警告ログに退避。

---

### 4. Map 操作 UI の細分化
優先度: Medium

#### 外部仕様(interface)
- 既存 `isRotateEnabled/isScrollEnabled/isZoomEnabled/isPitchEnabled` は維持。
- 以下を追加候補とする:
```kotlin
val showsMapTypeControl: Boolean = false
val showsCompass: Boolean = true // 既存
val showsScale: Boolean = false  // 既存
```

#### 内部仕様
- JS で未対応プロパティは可用性チェックして no-op。
- MapKit JS warning を拾い、Bridge error には昇格しない。

#### 受け入れ条件
- 表示系トグル変更で map が再初期化されない。
- Android Emulator / 実機で差分挙動が安定する。

## 実装順提案
1. Camera Zoom Range
2. Zoom Control 表示切替
3. POIフィルタ詳細化
4. Map操作UI細分化

## 非目標
- `traffic` の再導入
- `NavigationEmphasis` の再導入
- `PlaceDetail` 実装

## 参考ドキュメント
- [MapType | Apple Developer](https://developer.apple.com/documentation/mapkitjs/maptype)
- [MapKit JS Documentation](https://developer.apple.com/documentation/mapkitjs/)
- [Map Constructor Options](https://developer.apple.com/documentation/mapkitjs/mapconstructoroptions)
- [Map | Apple Developer](https://developer.apple.com/documentation/mapkitjs/map)
- [PointOfInterestCategory](https://developer.apple.com/documentation/mapkitjs/pointofinterestcategory)

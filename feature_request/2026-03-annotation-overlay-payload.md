# Feature Request: Annotation Event / Style Refactor

作成日: 2026-03-23
対象: `mapkit-for-android`

## 方針
- 本ファイルは「実装候補の要求仕様」を管理する。
- 先に event の扱いを改善し、その後 annotation 拡張性を高める。
- 本提案では `MKPayloadAnnotation` は作らない。

## 背景
現状は tap event が id 中心であり、利用側で追加の解決ロジックを持ちやすい。
あわせて annotation のスタイル指定が固定的で、利用側が独自 annotation モデルを持ちにくい。

## 提案概要

### 1. Event リファクタ (優先)

#### 外部仕様(interface)
- `MKMapEvent.AnnotationTapped` は `id` だけでなく annotation 本体を返す。
- `MKMapEvent.OverlayTapped` は `id` だけでなく overlay 本体を返す。

```kotlin
sealed interface MKMapEvent {
    data class AnnotationTapped(val annotation: MKAnnotation) : MKMapEvent {
        val id: String get() = annotation.id // 後方互換の暫定導線
    }
    data class OverlayTapped(val overlay: MKOverlay) : MKMapEvent {
        val id: String get() = overlay.id
    }
}
```

#### 内部仕様
- JS からは従来どおり `id` を受ける。
- Bridge で最新 state の `id -> annotation/overlay` index を引いて event を組み立てる。
- 解決不能時は `MapError(BridgeFailure)` を返す。

---

### 2. Annotation API の拡張性改善 (本セクションを今回追加)

## 2.1 公開モデルの再定義

### 外部仕様(interface)

- `MKAnnotationStyle` は残すが命名を変更する。
  - `Default` -> `Marker`
  - `CustomImage` -> `Image`

- `MKAnnotation` は data class ではなく基盤 `interface` とする。
- `MKAnnotation` にレンダリング用メソッドを定義し、利用者が必要時に上書きできるようにする。
- デフォルト実装は `Style.Marker` を返す。

```kotlin
sealed interface MKAnnotationStyle {
    data class Marker(
        val tintHex: String = "#FF3B30", // Red
        val glyphText: String? = null,
        val glyphImage: MKMarkerGlyph = MKMarkerGlyph.Pin
    ) : MKAnnotationStyle

    data class Image(
        val source: MKImageSource,
        val widthDp: Int,
        val heightDp: Int,
        val anchorX: Double = 0.5,
        val anchorY: Double = 1.0
    ) : MKAnnotationStyle
}

enum class MKMarkerGlyph { Pin }

interface MKAnnotation {
    val id: String
    val coordinate: MKCoordinate
    val title: String?
    val subtitle: String?
    val isVisible: Boolean
    val isSelected: Boolean

    // default: MKMarkerAnnotationView 相当
    fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker()
}
```

- 標準実装として `MKMarkerAnnotation` を提供する。

```kotlin
data class MKMarkerAnnotation(
    override val id: String,
    override val coordinate: MKCoordinate,
    override val title: String? = null,
    override val subtitle: String? = null,
    override val isVisible: Boolean = true,
    override val isSelected: Boolean = false,
    val tintHex: String = "#FF3B30",
    val glyphText: String? = null,
    val glyphImage: MKMarkerGlyph = MKMarkerGlyph.Pin
) : MKAnnotation {
    override fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker(
        tintHex = tintHex,
        glyphText = glyphText,
        glyphImage = glyphImage
    )
}
```

- 利用者は独自 data class で `MKAnnotation` を実装できる。
- 難しい場合の補助として `MKCustomAnnotation` (中間 interface) を提供してもよい。

```kotlin
interface MKCustomAnnotation : MKAnnotation
```

### 内部仕様
- 描画時は `annotation.renderingStyle()` を評価して bridge payload を組み立てる。
- `renderingStyle()` が `Marker` / `Image` 以外を返した場合の拡張余地は将来の仕様に委ねる。
- 差分計算は `id + geometry + visibility + selected + renderingStyle()` の変化で update 判定する。

---

## MapKit 比較での機能充足評価

### 充足している点
- `MKAnnotation` を protocol/interface 化する思想は MapKit と整合する。
- 標準 marker + 画像 annotation の 2 系統を提供できる。
- 利用者が独自 annotation data class を作成できるため、業務モデルとの接続がしやすい。

### まだ不足しうる点 (MapKit 完全同等ではない)
- `MKAnnotationView` レベルの view 再利用戦略 (`reuseIdentifier`)。
- クラスタリング、display priority、collision mode など高度機能。
- callout accessory の柔軟なカスタマイズ。
- drag state や selection lifecycle の詳細制御。

### 結論
- 「Android WebView + MapKit JS の安定運用」という本プロジェクトの目的には十分実用的。
- ただし MapKit ネイティブ API と 1:1 同等を目指すなら追加仕様が必要。

## 受け入れ条件
- `AnnotationTapped` / `OverlayTapped` でオブジェクト本体を受け取れる。
- `MKAnnotation` は interface 化され、`renderingStyle()` デフォルト実装を持つ。
- `MKAnnotationStyle` は `Marker` / `Image` 命名で公開される。
- `MKMarkerAnnotation` をそのまま使えば従来同等の利用ができる。
- 独自 annotation data class が `MKAnnotation` 実装で描画できる。

## 実装順提案
1. Event refactor (`id` -> object)
2. `MKAnnotation` interface 化
3. `MKAnnotationStyle` 命名変更 (`Marker` / `Image`)
4. `renderingStyle()` 導入 + bridge 側反映
5. example 更新 + `./gradlew :example:app:assembleDebug`

---

## 3. class ベース案 (data class 代替)

`data class` だと共通プロパティの重複記述が増えるため、`MKAnnotation` を基盤 class にする案を比較検討する。

### 3.1 外部仕様(interface) 案

```kotlin
open class MKAnnotation(
    open val id: String,
    open val coordinate: MKCoordinate,
    open val title: String? = null,
    open val subtitle: String? = null,
    open val isVisible: Boolean = true,
    open val isSelected: Boolean = false
) {
    open fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker()

    protected open fun extraEquals(other: MKAnnotation): Boolean = true
    protected open fun extraHashCode(): Int = 0

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MKAnnotation

        return id == other.id &&
            coordinate == other.coordinate &&
            title == other.title &&
            subtitle == other.subtitle &&
            isVisible == other.isVisible &&
            isSelected == other.isSelected &&
            extraEquals(other)
    }

    final override fun hashCode(): Int = listOf(
        id,
        coordinate,
        title,
        subtitle,
        isVisible,
        isSelected,
        extraHashCode()
    ).hashCode()
}

class MKMarkerAnnotation(
    override val id: String,
    override val coordinate: MKCoordinate,
    override val title: String? = null,
    override val subtitle: String? = null,
    override val isVisible: Boolean = true,
    override val isSelected: Boolean = false,
    val tintHex: String = "#FF3B30",
    val glyphText: String? = null,
    val glyphImage: MKMarkerGlyph = MKMarkerGlyph.Pin
) : MKAnnotation(
    id = id,
    coordinate = coordinate,
    title = title,
    subtitle = subtitle,
    isVisible = isVisible,
    isSelected = isSelected
) {
    override fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Marker(
        tintHex = tintHex,
        glyphText = glyphText,
        glyphImage = glyphImage
    )

    override fun extraEquals(other: MKAnnotation): Boolean {
        other as MKMarkerAnnotation
        return tintHex == other.tintHex &&
            glyphText == other.glyphText &&
            glyphImage == other.glyphImage
    }

    override fun extraHashCode(): Int = listOf(tintHex, glyphText, glyphImage).hashCode()
}
```

利用者カスタム例:

```kotlin
class ShopAnnotation(
    id: String,
    coordinate: MKCoordinate,
    title: String?,
    val shopId: String
) : MKAnnotation(id = id, coordinate = coordinate, title = title) {
    override fun renderingStyle(): MKAnnotationStyle = MKAnnotationStyle.Image(
        source = MKImageSource.Url("https://example.com/shop-pin.png"),
        widthDp = 40,
        heightDp = 40
    )
}
```

### 3.2 利点
- 共通項目は基底 class で持てるため、実装側のボイラーテンプレートを減らしやすい。
- 利用者は必要項目のみ constructor で受ける class を作りやすい。
- `renderingStyle()` override で見た目を明確に差し替えられる。

### 3.3 注意点
- `equals/hashCode` は基底 class の `final` 実装を維持し、拡張点は `extraEquals/extraHashCode` に限定する。
- mutable 実装が混ざると比較が不安定になるため、公開ガイドで immutable 推奨を明記する必要がある。
- Java/Kotlin 混在利用で subclassing ルールを明確化しないと利用者ごとの差が出やすい。

### 3.4 判断
- 「利用者の実装コスト最小化」を優先するなら class ベースは有力。
- 差分更新判定は、基底 class の `equals/hashCode` 契約に合わせる。
- 本プロジェクトの性質上、class ベース採用は実用的で MapKit の思想にもより近い。

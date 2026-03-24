# Feature Request: MapController Command Architecture (No Selection State)

作成日: 2026-03-24  
対象: `mapkit-for-android-compose`, `mapkit-for-android-core`, `mapkit-for-android-webview`

## 結論(この設計で固定)
- 大元 API は `MKMapView(region, controller, options)` を中心にする。
- 「状態変化で表しにくい操作」は state で持たず、callback で受けて `controller` から命令する。
- `isSelected` は公開 API から削除する。
- 後方互換は考慮しない(旧 API 併存しない)。
- 禁止対象は「利用者カスタム拡張を目的とした Annotation/Overlay の基盤 class/interface」。
- Overlay は必要なら共通 interface を保持してよい(禁止しない)。
- `AnnotationSelected` / `AnnotationDeselected` は 1 フレーム遅延で通知する。
- region は「読み取り(state) + 命令(controller)の併用」を採用する。

---

## 外部仕様(interface)

### 1. MapView API

```kotlin
@Composable
fun MKMapView(
    region: MKCoordinateRegion,
    controller: MKMapController,
    options: MKMapOptions = MKMapOptions(),
    modifier: Modifier = Modifier,
    onRegionDidChange: ((MKCoordinateRegion) -> Unit)? = null, // state取り込み入口
    content: MKMapContentScope.() -> Unit
)
```

意図:
- `region` は declarative な表示対象。
- 選択/非選択や一時的操作は `controller` で命令。
- callback は事実通知のみ。状態の真実は利用側アプリで持つ。

### 2. Controller API (命令専用)

```kotlin
interface MKMapController {
    fun selectAnnotation(id: String, animated: Boolean = true)
    fun deselectAnnotation(animated: Boolean = false)

    fun setRegion(region: MKCoordinateRegion, animated: Boolean = true)
    fun fitToCoordinates(
        coordinates: List<MKCoordinate>,
        edgePadding: MKEdgePadding = MKEdgePadding(0.0, 0.0, 0.0, 0.0),
        animated: Boolean = true
    )
}
```

ポイント:
- `deselectAnnotation(id)` ではなく `deselectAnnotation()` とする。
  理由: MapKit JS の選択実体は単一(`selectedAnnotation`)であり、選択中 1 件を解除する命令に揃える。

### 3. Annotation API (state を持たない)

```kotlin
@MKMapDsl
interface MKMapContentScope {
    fun Annotation(
        id: String,
        coordinate: MKCoordinate,
        title: String? = null,
        subtitle: String? = null,
        isVisible: Boolean = true,
        isDraggable: Boolean = false,
        style: MKAnnotationStyle = MKAnnotationStyle.Marker(),
        onSelected: (() -> Unit)? = null,
        onDeselected: (() -> Unit)? = null,
        onDragStart: (() -> Unit)? = null,
        onDrag: ((MKCoordinate) -> Unit)? = null,
        onDragEnd: ((MKCoordinate) -> Unit)? = null
    )
}
```

- `isSelected` は存在しない。
- 選択を維持するか解除するかは callback 内で `controller` を呼ぶ。

利用例:

```kotlin
MKMapView(
    region = region,
    controller = controller,
    options = options
) {
    Annotation(
        id = "shop-1",
        coordinate = shopCoord,
        onSelected = {
            // 必要ならそのまま維持
        },
        onDeselected = {
            // UI更新のみ
        },
        onSelected = {
            // 選択させたくない画面なら即解除命令
            controller.deselectAnnotation(animated = false)
        }
    )
}
```

### 4. Overlay API

Overlay は data class を基本にする。  
ただし共通処理都合で `MKOverlay` のような interface を保持してもよい。

```kotlin
data class MKPolylineOverlay(
    val id: String,
    val points: List<MKCoordinate>,
    val style: MKOverlayStyle = MKOverlayStyle(),
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)

data class MKPolygonOverlay(
    val id: String,
    val points: List<MKCoordinate>,
    val holes: List<List<MKCoordinate>> = emptyList(),
    val style: MKOverlayStyle = MKOverlayStyle(),
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)

data class MKCircleOverlay(
    val id: String,
    val center: MKCoordinate,
    val radiusMeter: Double,
    val style: MKOverlayStyle = MKOverlayStyle(),
    val isVisible: Boolean = true,
    val zIndex: Int = 0
)
```

---

## 内部仕様

### 1. Event -> Callback -> Command の一方向設計

1. JS で event 受信
2. Kotlin `MKMapEvent` に変換
3. Compose callback registry で対象 Annotation callback を実行
4. 必要なら callback 内で `controller.select()/deselect()` を命令

- 選択状態は model に保持しない。
- 選択実体は map 側(`selectedAnnotation`)にのみ存在する。

### 2. Annotation callback registry

```kotlin
internal data class AnnotationCallbacks(
    val onSelected: (() -> Unit)? = null,
    val onDeselected: (() -> Unit)? = null,
    val onDragStart: (() -> Unit)? = null,
    val onDrag: ((MKCoordinate) -> Unit)? = null,
    val onDragEnd: ((MKCoordinate) -> Unit)? = null
)

internal class MKAnnotationCallbackRegistry {
    private var callbacksById: Map<String, AnnotationCallbacks> = emptyMap()

    fun replace(next: Map<String, AnnotationCallbacks>) {
        callbacksById = next
    }

    fun dispatch(event: MKMapEvent) {
        when (event) {
            is MKMapEvent.AnnotationSelected -> callbacksById[event.id]?.onSelected?.invoke()
            is MKMapEvent.AnnotationDeselected -> callbacksById[event.id]?.onDeselected?.invoke()
            is MKMapEvent.AnnotationDragStart -> callbacksById[event.id]?.onDragStart?.invoke()
            is MKMapEvent.AnnotationDragging -> callbacksById[event.id]?.onDrag?.invoke(event.coordinate)
            is MKMapEvent.AnnotationDragEnd -> callbacksById[event.id]?.onDragEnd?.invoke(event.coordinate)
            else -> Unit
        }
    }
}
```

### 2.1 Region 方針(検討結果)

region は「state だけ」でも「命令だけ」でもなく、次の併用を採用する。

1. 読み取り: `onRegionDidChange` で利用側 state に取り込む
2. 書き込み(明示操作): `controller.setRegion(...)`, `controller.fitToCoordinates(...)`

採用理由:
- ジェスチャー由来の region 変化を UI 側で観測できる
- ボタン操作や外部イベントからのジャンプを命令的に実行できる
- Compose でありがちな「再代入競合」を減らせる

### 3. 1フレーム遅延の必須仕様

`select` / `deselect` はアニメーション都合でイベント発火を 1 フレーム遅延させる。

```javascript
state.map.addEventListener("select", function (event) {
  requestAnimationFrame(function () {
    emit({ type: "annotationSelected", id: event.annotation.data.id });
  });
});

state.map.addEventListener("deselect", function (event) {
  requestAnimationFrame(function () {
    emit({ type: "annotationDeselected", id: event.annotation.data.id });
  });
});
```

- この遅延は仕様として固定する(オプション化しない)。

### 4. Controller 実装

`MKMapController` は command channel に積むだけの薄い実装にする。

```kotlin
internal class MKMapControllerImpl(
    private val dispatch: (MKMapCommand) -> Unit
) : MKMapController {
    override fun selectAnnotation(id: String, animated: Boolean) {
        dispatch(MKMapCommand.SelectAnnotation(id, animated))
    }

    override fun deselectAnnotation(animated: Boolean) {
        dispatch(MKMapCommand.DeselectAnnotation(animated))
    }
}
```

- `MKMapCommand.DeselectAnnotation` は id なしの仕様へ変更する。

---

## 破壊的変更(明示)

1. `isSelected` 削除
2. Annotation のカスタム拡張用 基盤 interface/class 削除
3. 旧 `state.annotations/overlays` 前提 API 削除
4. 旧イベント名/イベント形状の見直し

後方互換は取らない。

---

## 受け入れ条件

- `MapView(region, controller, options)` がトップ API として成立している。
- `isSelected` が公開 API に存在しない。
- callback で受けた事象に対して controller 命令で操作できる。
- `AnnotationSelected` / `AnnotationDeselected` が 1 フレーム遅延で発火する。
- `Annotation.onTap` が存在せず、選択契機は `onSelected` に一本化されている。
- region が `onRegionDidChange`(読み取り) + controller(書き込み) で扱える。

---

## 実装順

1. `MKMapController` 導入
2. `MKMapView` シグネチャを `region, controller, options` へ変更
3. Annotation callback registry 実装
4. JS `select/deselect` の 1 フレーム遅延実装
5. `isSelected` 削除
6. Annotation カスタム拡張用の基盤 type を削除
7. example を controller 命令型へ更新

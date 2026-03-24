# Feature Request: Annotation Selection Command API (DebugController 廃止)

## 方針

- `DebugController` は廃止する。
- `MKAnnotation.isSelected` の同期は維持する。
- ただし `isSelected` は public には getter のみ公開し、外部から直接変更させない。
- 外部からの選択制御は `MKMapState` の明示 API (`selectAnnotation` / `deselectAnnotation`) を使う。

---

## 背景

現行の `event.annotation.isSelected = false` は、Compose の `remember` や再compositionの有無に依存して反映可否が変わる。
結果として API 利用者にとって挙動が読みづらく、安定した選択制御が難しい。

「選択状態の真実はライブラリ側で持つ」「外部は明示的コマンドで制御する」に寄せることで、
MapKit ライクな利用感を保ちつつ反映の確実性を上げる。

---

## 提案概要

### 1. `DebugController` 廃止

- `MKMapDebugController` の公開を終了する。
- デバッグ専用の simulate API (`simulateAnnotationTap` など) は公開 API から外す。

注記:
- 破壊的変更のため、必要であれば 1 リリースは `@Deprecated` で移行猶予を設ける。

### 2. `MKAnnotation.isSelected` を public getter のみにする

公開仕様(外部):

```kotlin
open class MKAnnotation(
    open val id: String,
    ...
) {
    open val isSelected: Boolean
        get() = _isSelected

    internal var _isSelected: Boolean = false
        set(value) {
            field = value
        }
}
```

- 外部コードは `annotation.isSelected` の参照のみ可能。
- 選択状態の更新はライブラリ内部のみで行う。

### 3. `MKMapState` から明示コマンドを発行

公開仕様(外部):

```kotlin
class MKMapState(...) {
    fun selectAnnotation(annotationId: String, animated: Boolean = true)
    fun deselectAnnotation(annotationId: String, animated: Boolean = false)
}
```

- 利用者はイベント内で以下のように呼ぶ。

```kotlin
is MKMapEvent.AnnotationTapped -> {
    mapState.deselectAnnotation(event.annotation.id, animated = false)
}
```

---

## 内部仕様

### 1. State command queue

- `MKMapState` 内部に command queue (or command revision) を持つ。
- `selectAnnotation` / `deselectAnnotation` 呼び出し時に command を enqueue。
- `MKMapView` update フェーズで queue を drain し、`MKBridgeWebView` に command を転送する。

### 2. WebView bridge command API

`MKBridgeWebView` に以下を追加する:

```kotlin
fun selectAnnotation(annotationId: String, animated: Boolean = true)
fun deselectAnnotation(annotationId: String, animated: Boolean = false)
```

- JS 側 `window.MKBridge` に対応 command を追加する。
- 対象 annotation を id で引き、`map.selectedAnnotation` を直接更新する。
- `map.selectAnnotation` / `map.deselectAnnotation` は利用しない。
  理由: MapKit JS の公開ドキュメントで安定して参照できる操作は
  `selectedAnnotation` であり、実装差異の影響を避けるため。

### 3. isSelected 同期

- annotation select event 受信時:
- ライブラリ内部で対象 `_isSelected = true`、他は `false` に更新。
- 外部へ `MKMapEvent.AnnotationTapped(annotation)` を通知。

- `deselectAnnotation` command 実行時:
- ライブラリ内部で対象 `_isSelected = false` に更新。
- JS 側も deselect を実行。

---

## API 境界のルール

- lib -> public:
- `MKMapEvent.AnnotationTapped(annotation)` の `annotation.isSelected` は read 可能。

- public -> lib:
- `annotation.isSelected = ...` は不可。
- 選択操作は `mapState.selectAnnotation/deselectAnnotation` のみ。

---

## 受け入れ条件

- `DebugController` なしで map が利用できる。
- `MKAnnotation.isSelected` は外部から代入できない。
- tap で `AnnotationTapped` が発火し、`annotation.isSelected == true` を読める。
- `mapState.deselectAnnotation(id, animated = false)` で即 deselect される。
- `remember` の有無に関わらず、`deselectAnnotation` の反映結果が安定する。

---

## 実装順提案

1. `feature_request` 合意 (本ドキュメント)
2. `MKMapState` に command API と queue 実装
3. `MKBridgeWebView` / JS bridge に select/deselect command 追加
4. `MKAnnotation.isSelected` を getter-only + internal setter 化
5. `DebugController` 廃止 (必要なら deprecate 経由)
6. example 更新 + `./gradlew :example:app:assembleDebug`

---

## 互換性メモ

- `DebugController` を廃止するため、既存デモ・検証コードは移行が必要。
- `isSelected` 直接代入を使っている利用コードはコンパイルエラーになるため、
  `mapState.deselectAnnotation(...)` への置換をガイドする。

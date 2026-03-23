# AGENTS.md

このリポジトリで作業するエージェント向けガイド。

## 1. リポジトリ構成
- `source/mapkit-for-android-core`: 公開モデルと初期化API
- `source/mapkit-for-android-webview`: WebView + MapKit JS ブリッジ
- `source/mapkit-for-android-compose`: Compose向け `MKMapView`
- `source/mapkit-for-android`: 利用者向け集約モジュール
- `example/app`: 動作確認用アプリ
- `docs/design`: 正式仕様
- `feature_request`: 実装前の要求仕様メモ

## 2. 設計原則
- 利用者には Kotlin API のみ公開する。MapKit JS 直接操作を強制しない。
- 公開インターフェースは `MK` prefix を維持する。
- map 反映は再初期化ではなく差分反映を優先する。
- 設定変更も region 変更と同等に反映されるようにする。

## 3. 現時点の既知方針
- `showsTraffic` と `navigationEmphasis` は公開APIから削除済み。
- 理由: MapKit JS + WebView 上で再現性が低く、ライブラリとして安定提供が難しいため。
- 将来再導入する場合は `feature_request` で再定義してから検討する。

## 4. 実装時の必須チェック
- 変更後は最低でも `./gradlew :example:app:assembleDebug` を実行。
- 反映不具合の切り分け時は JS 側の debug log を優先確認。
- map canvas が 0 height で初期化されないよう注意する。

## 5. ドキュメント運用
- 新機能はまず `feature_request` に要求仕様を追加。
- 実装完了後に `docs/design/specification.md` と該当 `docs/design/specs/*.md` へ反映。
- 「外部仕様(interface) -> 内部仕様」の順序を維持する。

## 6. デモアプリ運用
- デモUIは検証しやすさを優先し、イベント表示を維持する。
- レイアウトジャンプ防止のため、可変文言領域は高さ固定を基本とする。

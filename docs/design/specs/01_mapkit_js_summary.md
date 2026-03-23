# 01. MapKit JS ドキュメントサマリ

## 外部仕様(interface)

### 1. 目的
- Android 側利用者が MapKit JS を直接意識せず、Kotlin の型安全な API で地図を扱えるようにする。

### 2. 利用者向け公開インターフェース

```kotlin
package com.studiomk.mapkit.api

object MKMapKit {
    fun init(token: String)
    fun clear()
    fun isInitialized(): Boolean
}

sealed interface MKMapInitError {
    data object TokenUnavailable : MKMapInitError
    data class TokenRejected(val reason: String?) : MKMapInitError
    data class JavaScriptLoadFailed(val message: String?) : MKMapInitError
}
```

### 3. 契約
- 利用者は起動時に `MKMapKit.init(token)` を1回呼ぶ。
- token は環境変数起点で注入し、コード直書きしない。
- 初期化失敗は `MKMapInitError` で扱える。

## 内部仕様

### 1. 初期化フロー
1. WebView 生成後、MapKit JS スクリプトを読み込む。
2. Kotlin Bridge から token コールバックを注入する。
3. `mapkit.init` -> `new mapkit.Map(...)` を実行する。
4. 成功時に `MapLoaded` を発火する。

### 2. 実装責務分離
- `TokenStore`: `init(token)` で受け取った値を static 保持。
- `MapJsBootstrapper`: HTML/JS の初期化。
- `MapBridge`: Kotlin <-> JS メッセージ変換。

### 3. エラーハンドリング
- token 取得失敗: 初期化停止、再試行可能状態へ。
- JS ロード失敗: 失敗理由をログ + Kotlin 側へ通知。
- Bridge 例外: map インスタンス破棄せず、コマンド単位で失敗扱い。

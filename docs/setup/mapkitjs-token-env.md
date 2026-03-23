# MapKit JS トークンと環境変数設定

このドキュメントは Apple の MapKit JS 公式情報を前提に、`mapkit-for-android` で `MAPKIT_JS_TOKEN` を安全に渡す手順をまとめたものです。

## 1. 前提

- 本プロジェクトの `example` は `BuildConfig.MAPKIT_JS_TOKEN` を使って `MKMapKit.init(token)` へ渡します。
- トークンは `example/app/build.gradle.kts` で次の優先順に解決されます。
1. `local.properties` の `MAPKIT_JS_TOKEN`
2. OS 環境変数 `MAPKIT_JS_TOKEN`
3. 未設定時は `"DUMMY_TOKEN_FOR_DEMO"`

## 2. Apple 側でトークンを作る

Apple 公式では、Maps Token を Certificates, Identifiers & Profiles から作成できます。

参考:
- [MapKit JS ドキュメント](https://developer.apple.com/documentation/mapkitjs/)
- [Maps tokens (Apple Developer Account Help)](https://developer.apple.com/help/account/service-configurations/maps-tokens)

公式ヘルプの要点:
1. Certificates, Identifiers & Profiles の Services を開く
2. Maps の Configure Tokens を開く
3. `+` から token を作成する
4. 必要に応じて有効期間や Web ドメインを設定する

注意:
- このプロジェクトは Android `WebView` で MapKit JS を動かすため、Web ドメイン制限付き token だと利用環境によっては失敗する場合があります。
- まずは検証用として最小制約の token で動作確認し、必要に応じて制約を段階的に強めてください。

## 3. プロジェクトへ設定する

### 3.1 `local.properties` を使う

プロジェクトルートの `local.properties` に次を設定:

```properties
MAPKIT_JS_TOKEN=YOUR_APPLE_MAPKIT_JS_TOKEN
```

`local.properties` は `.gitignore` 対象なので、リポジトリには含まれません。

### 3.2 環境変数を使う

`local.properties` に書かない場合は、シェルで環境変数を設定:

```bash
export MAPKIT_JS_TOKEN="YOUR_APPLE_MAPKIT_JS_TOKEN"
```

## 4. BuildConfig への注入箇所

`example/app/build.gradle.kts` で `BuildConfig.MAPKIT_JS_TOKEN` を生成しています。

```kotlin
val mapToken = localProps.getProperty("MAPKIT_JS_TOKEN")
    ?: System.getenv("MAPKIT_JS_TOKEN")
    ?: "DUMMY_TOKEN_FOR_DEMO"
val escapedMapToken = mapToken
    .replace("\\\\", "\\\\\\\\")
    .replace("\"", "\\\\\"")
buildConfigField("String", "MAPKIT_JS_TOKEN", "\"$escapedMapToken\"")
```

## 5. 実行時確認ポイント

1. `BuildConfig.MAPKIT_JS_TOKEN` に Apple 発行 token が入っていること
2. `bridge.js` が `mapkit.js` を読み込めていること
3. `MKMapEvent.MapError` に `BridgeFailure` が来ていないこと

もし地図が出ない場合:
- token の形式・有効期限・制約を再確認
- ネットワークで `https://cdn.apple-mapkit.com/` へ到達できるか確認

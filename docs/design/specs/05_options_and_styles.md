# 05. その他オプション系仕様(交通機関/drive等)

## 外部仕様(interface)

### 1. 公開オプションモデル

```kotlin
package com.studiomk.mapkit.model

enum class MKMapStyle {
    standard,
    mutedStandard,
    satellite,
    hybrid
}

enum class MKNavigationEmphasis {
    none,
    driving,
    walking,
    transit
}

enum class MKAppearanceOption {
    auto,
    light,
    dark
}

data class MKMapOptions(
    val mapStyle: MKMapStyle = MKMapStyle.standard,
    val navigationEmphasis: MKNavigationEmphasis = MKNavigationEmphasis.none,
    val showsTraffic: Boolean = false,
    val showsCompass: Boolean = true,
    val showsScale: Boolean = false,
    val showsPointsOfInterest: Boolean = true,
    val isRotateEnabled: Boolean = true,
    val isScrollEnabled: Boolean = true,
    val isZoomEnabled: Boolean = true,
    val isPitchEnabled: Boolean = true,
    val appearance: MKAppearanceOption = MKAppearanceOption.auto,
    val userLocation: MKUserLocationOptions = MKUserLocationOptions()
)
```

### 2. 利用環境契約
- token など機密値は環境変数起点で設定する。
- `local.properties` はローカル参照用途で、機密値を公開しない。
- 公開形態は Maven Central 前提。
- プロジェクト構成は `source/` と `example/` を分離する。

#### 2.1 推奨の簡便化ルール(優先順)
1. `local.properties` の `MAPKIT_JS_TOKEN`
2. OS 環境変数 `MAPKIT_JS_TOKEN`
3. 未設定時はビルド失敗(明示エラー)

#### 2.2 利用者向け最小設定例

```kotlin
// app/build.gradle.kts
import java.util.Properties

android {
    defaultConfig {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use(::load)
        }
        val localToken = localProps.getProperty("MAPKIT_JS_TOKEN")
            ?: System.getenv("MAPKIT_JS_TOKEN")
            ?: error("MAPKIT_JS_TOKEN is missing. Set local.properties or environment variable.")
        buildConfigField("String", "MAPKIT_JS_TOKEN", "\"$localToken\"")
    }
}
```

```properties
# local.properties (VCSに含めない)
MAPKIT_JS_TOKEN=xxxxx.yyyyy.zzzzz
```

### 3. モジュール向け公開インターフェース

```kotlin
package com.studiomk.mapkit.config

data class MKMapKitConfig(
    val tokenEnvKey: String = "MAPKIT_JS_TOKEN",
    val endpointEnvKey: String? = null,
    val tokenValueOverride: String? = null
)

interface MKMapOptionsApplier {
    fun apply(options: MKMapOptions)
}
```

- `tokenValueOverride` がある場合はそれを最優先で利用する。
- 未指定時は `tokenEnvKey` を使って環境値を探索する。
- これによりアプリ側は「環境変数運用」か「DI で直接注入」を選択できる。

## 内部仕様

### 1. JS 変換テーブル
- `mapStyle` -> `map.mapType`。
- `showsTraffic` -> `map.showsTraffic`。
- `showsCompass/showsScale` -> control 表示設定。
- `userLocation.isEnabled` -> 現在地表示レイヤ表示/非表示。
- `userLocation.followsHeading` -> heading 追従カメラ有効/無効。
- `navigationEmphasis` は内部プリセットで視認性を調整:
- `driving`: 道路/車線情報優先
- `walking`: 徒歩向け POI 優先
- `transit`: 鉄道/バス路線優先

### 2. 差分適用ルール
- 初回: 全オプション適用。
- 2回目以降: `MKMapOptions` 同士のプロパティ差分のみ適用。
- option 変更では map 再初期化しない。

### 3. 配布/公開内部要件

```kotlin
package com.studiomk.mapkit.publish

data class PublishCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String
)

data class ModuleLayout(
    val sourceModules: List<String> = listOf(
        "mapkit-android",
        "mapkit-android-core",
        "mapkit-android-webview",
        "mapkit-android-compose"
    ),
    val exampleModules: List<String> = listOf("app")
)
```

- `maven-publish` と `signing` を有効化。
- release/SNAPSHOT のバージョン運用を分離。
- `example` は公開 artifact に含めない。

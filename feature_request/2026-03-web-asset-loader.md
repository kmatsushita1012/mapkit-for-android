
WebViewAssetLoader でローカル HTML を https://master.d2p6lwu2ojgxks.amplifyapp.com/... に見せる最小例をそのまま出します。

前提はこれです。
	•	Apple 側の Web domains に
master.d2p6lwu2ojgxks.amplifyapp.com
を登録
	•	Android では 実ネットワークには行かず
WebViewAssetLoader がローカル asset を返す
	•	WebView では
https://master.d2p6lwu2ojgxks.amplifyapp.com/assets/index.html
を開く

⸻

Kotlin 側

MainActivity.kt

package com.studiomk.mapkit.demo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MapKitWebView(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapKitWebView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("master.d2p6lwu2ojgxks.amplifyapp.com")
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(context)
                )
                .build()

            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.databaseEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            "WV_CONSOLE",
                            "${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                        )
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        Log.d("WV_PAGE", "started: $url")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        Log.d("WV_PAGE", "finished: $url")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        Log.e(
                            "WV_ERROR",
                            "url=${request.url}, code=${error.errorCode}, desc=${error.description}"
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        Log.e(
                            "WV_HTTP",
                            "url=${request.url}, status=${errorResponse.statusCode}"
                        )
                    }
                }

                loadUrl("https://master.d2p6lwu2ojgxks.amplifyapp.com/assets/index.html")
            }
        }
    )
}


⸻

assets 配置

app/src/main/assets/ の中にこう置きます。

app/src/main/assets/
├─ index.html
├─ app.js
└─ styles.css

ただし今の URL が /assets/index.html なので、実際には asset root に index.html を置くなら AssetsPathHandler の /assets/ に対して index.html が見えるようになります。
つまり app/src/main/assets/index.html で大丈夫です。

⸻

index.html

<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8" />
  <meta
    name="viewport"
    content="width=device-width, initial-scale=1, viewport-fit=cover"
  />
  <title>MapKit JS Demo</title>
  <link rel="stylesheet" href="/assets/styles.css" />
</head>
<body>
  <div id="status">loading...</div>
  <div id="map"></div>

  <script>
    window.onerror = function(message, source, lineno, colno, error) {
      console.log("window.onerror", {
        message, source, lineno, colno, stack: error && error.stack
      });
    };

    window.addEventListener("unhandledrejection", function(event) {
      console.log("unhandledrejection", event.reason);
    });
  </script>

  <script src="https://cdn.apple-mapkit.com/mk/5.x.x/mapkit.core.js"></script>
  <script src="/assets/app.js"></script>
</body>
</html>


⸻

styles.css

html, body {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
}

body {
  overflow: hidden;
  font-family: sans-serif;
}

#status {
  position: absolute;
  z-index: 10;
  left: 12px;
  top: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255,255,255,0.9);
  font-size: 12px;
}

#map {
  width: 100vw;
  height: 100vh;
}


⸻

app.js

ここは トークンを固定で直書きしない で、まずは動作確認用に YOUR_JWT_TOKEN を入れています。
本番は API で返す形に差し替えるのが安全です。

const statusEl = document.getElementById("status");

function setStatus(text) {
  statusEl.textContent = text;
  console.log("[status]", text);
}

function createMap() {
  setStatus("initializing map...");

  mapkit.init({
    authorizationCallback: function(done) {
      // まずは切り分け用に固定トークンで確認
      // 本番ではここで fetch("/token-api") などに差し替える
      const token = "YOUR_JWT_TOKEN";
      done(token);
    }
  });

  const center = new mapkit.Coordinate(35.681236, 139.767125);

  const map = new mapkit.Map("map", {
    center,
    showsCompass: mapkit.FeatureVisibility.Visible,
    showsScale: mapkit.FeatureVisibility.Visible,
    isRotationEnabled: true,
    isZoomEnabled: true
  });

  map.region = new mapkit.CoordinateRegion(
    center,
    new mapkit.CoordinateSpan(0.02, 0.02)
  );

  const annotation = new mapkit.MarkerAnnotation(center, {
    title: "Tokyo Station",
    subtitle: "MapKit JS"
  });

  map.addAnnotation(annotation);

  map.addEventListener("single-tap", (event) => {
    try {
      const coordinate = map.convertPointOnPageToCoordinate(event.pointOnPage);
      console.log("single-tap", coordinate.latitude, coordinate.longitude);
      setStatus(`tap: ${coordinate.latitude.toFixed(6)}, ${coordinate.longitude.toFixed(6)}`);
    } catch (e) {
      console.log("tap conversion failed", e);
    }
  });

  map.addEventListener("region-change-end", () => {
    const c = map.center;
    console.log("region-change-end", c.latitude, c.longitude);
  });

  setStatus("map ready");
}

(function main() {
  try {
    if (!window.mapkit) {
      setStatus("mapkit not found");
      console.log("window.mapkit is undefined");
      return;
    }

    createMap();
  } catch (e) {
    setStatus("failed");
    console.log("createMap error", e);
  }
})();


⸻

まず確認するポイント

これで表示されない場合、見るところはかなり絞れます。

1. Apple 側登録ドメイン

これが一致しているか。

master.d2p6lwu2ojgxks.amplifyapp.com

2. WebView が実際に開いている URL

ログにこれが出るか。

started: https://master.d2p6lwu2ojgxks.amplifyapp.com/assets/index.html

3. Console

WV_CONSOLE に
	•	window.mapkit is undefined
	•	authorization token is invalid
	•	failed to load
	•	createMap error

のどれが出るか。

4. 高さ

#map に高さがあるか。
これが 0 だと真っ白です。

⸻

よくある落とし穴

mapkit.core.js だけでは足りない可能性

環境によっては core ではなく通常の bundle にした方が切り分けしやすいです。
まずは次に変えてみる価値があります。

<script src="https://cdn.apple-mapkit.com/mk/5.x.x/mapkit.js"></script>

トークンの origin 不一致

file:// で開いていたらほぼ噛み合いませんが、今回のように

https://master.d2p6lwu2ojgxks.amplifyapp.com/assets/index.html

で開けていればそこは揃います。

shouldInterceptRequest はローカル asset だけ

cdn.apple-mapkit.com まで intercept しないので、そこは通常のネットワークです。
つまり オフラインでは無理 です。

⸻

次にやるとよいこと

このまま動かして、WV_CONSOLE のログを貼ってくれれば、
次は トークン問題か、script 読み込み問題か、WebView 相性か をかなり切れます。
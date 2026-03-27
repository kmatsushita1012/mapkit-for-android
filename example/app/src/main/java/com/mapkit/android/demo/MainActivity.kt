package com.studiomk.mapkit.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.studiomk.mapkit.api.MKMapKit
import com.studiomk.mapkit.model.MKMapKitConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MKMapKit.isInitialized()) {
            MKMapKit.init(
                token = BuildConfig.MAPKIT_JS_TOKEN,
                config = MKMapKitConfig(
                    webDomain = BuildConfig.MAPKIT_JS_WEB_DOMAIN
                )
            )
        }

        setContent {
            AppScreen()
        }
    }
}

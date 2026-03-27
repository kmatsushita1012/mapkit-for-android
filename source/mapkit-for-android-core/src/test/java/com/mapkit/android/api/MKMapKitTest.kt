package com.studiomk.mapkit.api

import com.studiomk.mapkit.model.MKMapKitConfig
import com.studiomk.mapkit.model.MKWebAssetPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MKMapKitTest {

    @Test
    fun init_withConfig_storesTokenAndConfig() {
        val config = MKMapKitConfig(
            webDomain = "maps.example.com",
            webAssetPaths = listOf(
                MKWebAssetPath.assets("/assets/"),
                MKWebAssetPath.resources("/res/")
            ),
            entryPath = "/assets/mkbridge/index.html"
        )

        MKMapKit.init(token = "token-value", config = config)

        assertEquals("token-value", MKMapKit.currentTokenOrNull())
        assertEquals(config, MKMapKit.currentConfig())
        MKMapKit.clear()
    }

    @Test
    fun clear_resetsTokenAndConfig() {
        val config = MKMapKitConfig(webDomain = "maps.example.com")
        MKMapKit.init(token = "token-value", config = config)

        MKMapKit.clear()

        assertNull(MKMapKit.currentTokenOrNull())
        assertEquals(MKMapKitConfig(), MKMapKit.currentConfig())
    }
}

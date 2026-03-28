package com.studiomk.mapkit.api

import com.studiomk.mapkit.model.MKMapKitConfig
import java.util.concurrent.atomic.AtomicReference

object MKMapKit {
    private val tokenRef = AtomicReference<String?>(null)
    private val configRef = AtomicReference(MKMapKitConfig())

    fun init(token: String) {
        init(token = token, config = MKMapKitConfig())
    }

    fun init(token: String, config: MKMapKitConfig) {
        require(token.isNotBlank()) { "token must not be blank" }
        tokenRef.set(token)
        configRef.set(config)
    }

    fun clear() {
        tokenRef.set(null)
        configRef.set(MKMapKitConfig())
    }

    fun isInitialized(): Boolean = !tokenRef.get().isNullOrBlank()

    fun currentTokenOrNull(): String? = tokenRef.get()

    fun currentConfig(): MKMapKitConfig = configRef.get()
}

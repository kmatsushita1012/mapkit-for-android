package com.mapkit.android.api

import java.util.concurrent.atomic.AtomicReference

object MKMapKit {
    private val tokenRef = AtomicReference<String?>(null)

    fun init(token: String) {
        require(token.isNotBlank()) { "token must not be blank" }
        tokenRef.set(token)
    }

    fun clear() {
        tokenRef.set(null)
    }

    fun isInitialized(): Boolean = !tokenRef.get().isNullOrBlank()

    fun currentTokenOrNull(): String? = tokenRef.get()
}

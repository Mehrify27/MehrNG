package com.v2ray.ang.utils

import android.util.Base64

object CryptoUtils {
    // Encrypted URL in Base64
    private const val ENCRYPTED_URL = "aHR0cHM6Ly9icm9hZC1jYWtlLThkMGMubWVocnNob3AxNi53b3JrZXJzLmRldi9zdWI="

    /**
     * Decrypts the subscription URL from Base64.
     */
    fun getDecryptedSubscriptionUrl(): String {
        return try {
            val decodedBytes = Base64.decode(ENCRYPTED_URL, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback (should not happen in normal conditions)
            ""
        }
    }
}

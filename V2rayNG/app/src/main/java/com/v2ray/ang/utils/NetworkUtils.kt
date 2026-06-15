package com.v2ray.ang.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkUtils {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Performs a synchronous GET request to fetch subscription content.
     * Throws IOException if the request fails.
     */
    @Throws(IOException::class)
    fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "v2rayNG/2.2.3")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }
}

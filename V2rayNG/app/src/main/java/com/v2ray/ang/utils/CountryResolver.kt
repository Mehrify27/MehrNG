package com.v2ray.ang.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object CountryResolver {
    private val mmkv by lazy { MMKV.defaultMMKV() }
    private val pendingRequests = mutableSetOf<String>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getFlagEmoji(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "🌐"
        val code = countryCode.uppercase()
        val firstChar = Character.toChars(code[0].code - 0x41 + 0x1F1E6)
        val secondChar = Character.toChars(code[1].code - 0x41 + 0x1F1E6)
        return String(firstChar) + String(secondChar)
    }

    fun getCachedFlag(address: String): String? {
        val code = mmkv.decodeString("country_code_$address", null)
        return if (code != null) getFlagEmoji(code) else null
    }

    fun getCachedCountryCode(address: String): String? {
        return mmkv.decodeString("country_code_$address", null)
    }

    fun getCachedFlagBitmap(context: Context, countryCode: String): Bitmap? {
        val code = countryCode.uppercase()
        val flagsDir = File(context.cacheDir, "flags")
        val flagFile = File(flagsDir, "$code.png")
        return if (flagFile.exists()) {
            try {
                BitmapFactory.decodeFile(flagFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun getFlagBitmap(context: Context, countryCode: String): Bitmap? = withContext(Dispatchers.IO) {
        val code = countryCode.uppercase()
        val flagsDir = File(context.cacheDir, "flags")
        if (!flagsDir.exists()) {
            flagsDir.mkdirs()
        }
        val flagFile = File(flagsDir, "$code.png")
        if (flagFile.exists()) {
            try {
                return@withContext BitmapFactory.decodeFile(flagFile.absolutePath)
            } catch (e: Exception) {
                flagFile.delete() // Corrupt file, delete and redownload
            }
        }

        try {
            val url = "https://flagcdn.com/w80/${code.lowercase()}.png"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        flagFile.writeBytes(bytes)
                        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun resolveCountryCode(address: String): String? = withContext(Dispatchers.IO) {
        val cached = mmkv.decodeString("country_code_$address", null)
        if (cached != null) return@withContext cached

        if (address.isBlank() || address == "127.0.0.1" || address == "localhost") {
            return@withContext null
        }

        synchronized(pendingRequests) {
            if (pendingRequests.contains(address)) return@withContext null
            pendingRequests.add(address)
        }

        try {
            // Remove port if present
            val cleanAddress = address.substringBefore(":")
            // Resolve domain name to IP address first so freeipapi.com receives a valid IP address
            val resolvedIp = try {
                java.net.InetAddress.getByName(cleanAddress).hostAddress
            } catch (e: Exception) {
                cleanAddress
            }
            val url = "https://freeipapi.com/api/json/$resolvedIp"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        val countryCode = json.optString("countryCode", "")
                        if (countryCode.length == 2) {
                            mmkv.encode("country_code_$address", countryCode)
                            return@withContext countryCode
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            synchronized(pendingRequests) {
                pendingRequests.remove(address)
            }
        }
        null
    }
}

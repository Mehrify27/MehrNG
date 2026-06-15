package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.utils.CryptoUtils
import com.v2ray.ang.utils.NetworkUtils
import java.io.IOException

object SubscriptionManager {
    const val SUB_ID = "mehr_vpn_sub"
    const val SUB_REMARKS = "MehrNetVPN (Telegram)"

    /**
     * Ensures our custom subscription is registered in MMKV and set as the active subscription.
     */
    fun ensureSubscriptionExists() {
        val targetUrl = CryptoUtils.getDecryptedSubscriptionUrl()
        val existing = MmkvManager.decodeSubscription(SUB_ID)
        if (existing == null) {
            val subItem = SubscriptionItem().apply {
                remarks = SUB_REMARKS
                url = targetUrl
                autoUpdate = true
                enabled = true
            }
            MmkvManager.encodeSubscription(SUB_ID, subItem)
        } else if (existing.url != targetUrl) {
            existing.url = targetUrl
            MmkvManager.encodeSubscription(SUB_ID, existing)
        }
        
        // Remove all other subscriptions to enforce subscription-only mode
        MmkvManager.decodeSubsList().toList().forEach { id ->
            if (id != SUB_ID) {
                MmkvManager.removeSubscription(id)
            }
        }

        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, SUB_ID)
    }

    /**
     * Performs a silent subscription refresh.
     * Attempts to fetch subscription from hidden URL with up to 3 retries.
     * If successful, parses and updates local nodes.
     * If failed, falls back to locally cached nodes.
     * Auto-selects the best latency node.
     *
     * @return Pair of (Success status, list of server GUIDs)
     */
    suspend fun refreshSubscription(): Pair<Boolean, List<String>> {
        ensureSubscriptionExists()
        val url = CryptoUtils.getDecryptedSubscriptionUrl()
        if (url.isBlank()) {
            LogUtil.e(AppConfig.TAG, "Subscription URL is empty/invalid")
            return false to getCachedGuids()
        }

        var fetchSuccess = false
        var content = ""
        var attempt = 1
        val maxAttempts = 3

        while (attempt <= maxAttempts) {
            try {
                LogUtil.i(AppConfig.TAG, "Fetching subscription: attempt $attempt")
                content = NetworkUtils.fetchUrl(url)
                if (content.isNotBlank()) {
                    fetchSuccess = true
                    break
                }
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to fetch subscription on attempt $attempt: ${e.message}")
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(30000L) // Wait 30 seconds before retry
                }
            }
            attempt++
        }

        return if (fetchSuccess && content.isNotBlank()) {
            try {
                val profiles = NodeParser.parseSubscription(content, SUB_ID)
                if (profiles.isNotEmpty()) {
                    val guids = NodeManager.saveProfiles(profiles, SUB_ID)
                    
                    // Update last updated time in subscription
                    val subItem = MmkvManager.decodeSubscription(SUB_ID)
                    if (subItem != null) {
                        subItem.lastUpdated = System.currentTimeMillis()
                        MmkvManager.encodeSubscription(SUB_ID, subItem)
                    }

                    // Run latency tests and auto-select best node
                    NodeManager.testAndSelectBestNode(guids)
                    true to guids
                } else {
                    LogUtil.w(AppConfig.TAG, "Parsed 0 valid nodes from subscription content")
                    false to getCachedGuids()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error parsing or saving subscription profiles", e)
                false to getCachedGuids()
            }
        } else {
            LogUtil.i(AppConfig.TAG, "Offline or fetch failed. Falling back to cached nodes.")
            val cached = getCachedGuids()
            if (cached.isNotEmpty()) {
                NodeManager.testAndSelectBestNode(cached)
            }
            false to cached
        }
    }

    /**
     * Gets the list of cached server GUIDs for our subscription.
     */
    fun getCachedGuids(): List<String> {
        return MmkvManager.decodeServerList(SUB_ID)
    }
}

package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object NodeManager {

    /**
     * Batch saves a list of profiles for the given subscription, removing existing ones.
     * Returns a list of generated GUIDs.
     */
    fun saveProfiles(profiles: List<ProfileItem>, subscriptionId: String): List<String> {
        // Clear existing servers under this subscription
        MmkvManager.removeServerViaSubid(subscriptionId)

        val serverList = mutableListOf<String>()
        profiles.forEach { profile ->
            val key = Utils.getUuid()
            // Save profile directly
            MmkvManager.encodeProfileDirect(key, JsonUtil.toJson(profile))
            serverList.add(key)
        }

        // Save server list
        MmkvManager.encodeServerList(serverList, subscriptionId)
        return serverList
    }

    /**
     * Measures TCP latency for a list of server GUIDs in parallel.
     * Updates MmkvManager with delay results and returns the GUID of the best (lowest latency) node.
     */
    suspend fun testAndSelectBestNode(guids: List<String>): String? = coroutineScope {
        if (guids.isEmpty()) return@coroutineScope null

        val jobs = guids.map { guid ->
            async {
                val profile = MmkvManager.decodeServerConfig(guid)
                val server = profile?.server
                val port = profile?.serverPort?.toIntOrNull()
                val delay = if (!server.isNullOrBlank() && port != null) {
                    SpeedtestManager.tcping(server, port)
                } else {
                    -1L
                }
                MmkvManager.encodeServerTestDelayMillis(guid, delay)
                guid to delay
            }
        }

        val results = jobs.awaitAll()

        // Filter valid latencies (>= 0), sort ascending. Fallback to all if none are valid.
        val validResults = results.filter { it.second >= 0 }.sortedBy { it.second }
        val bestGuid = if (validResults.isNotEmpty()) {
            validResults.first().first
        } else {
            // No valid latency test succeeded, fall back to first node
            guids.first()
        }

        MmkvManager.setSelectServer(bestGuid)
        bestGuid
    }
}

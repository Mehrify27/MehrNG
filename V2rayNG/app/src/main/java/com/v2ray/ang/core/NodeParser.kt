package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.Utils

object NodeParser {
    private val parsers = mapOf(
        EConfigType.VMESS.protocolScheme to VmessFmt::parse,
        EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
        EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
        AppConfig.SOCKS4 to SocksFmt::parse,
        AppConfig.SOCKS5 to SocksFmt::parse,
        EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
        EConfigType.VLESS.protocolScheme to VlessFmt::parse,
        EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
        EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
        AppConfig.HY2 to Hysteria2Fmt::parse
    )

    /**
     * Decodes subscription payload and parses lines into list of ProfileItems.
     * All parsed profiles will have their remarks overridden to "MehrNetVPN (Telegram)".
     */
    fun parseSubscription(content: String, subscriptionId: String): List<ProfileItem> {
        val decoded = Utils.decode(content)
        val lines = if (decoded.isNotBlank()) decoded.lines() else content.lines()
        val profiles = mutableListOf<ProfileItem>()

        lines.distinct().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) return@forEach

            val profile = parseLine(trimmedLine)
            if (profile != null) {
                profile.subscriptionId = subscriptionId
                profiles.add(profile)
            }
        }

        // Rename all nodes with a suffix index to distinguish them (e.g. MehrNetVPN (Telegram) #1)
        profiles.forEachIndexed { index, profile ->
            profile.remarks = "MehrNetVPN (Telegram) #${index + 1}"
        }

        return profiles
    }

    private fun parseLine(line: String): ProfileItem? {
        return parsers.firstNotNullOfOrNull { (scheme, parser) ->
            if (line.startsWith(scheme)) {
                try {
                    parser(line)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
}

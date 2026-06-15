package com.v2ray.ang.vpn

import android.content.Context
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager

object VpnController {

    /**
     * Checks if the VPN core service is currently running.
     */
    fun isRunning(): Boolean {
        return CoreServiceManager.isRunning()
    }

    /**
     * Starts the VPN service using the selected configuration.
     * If [guid] is provided, sets it as the selected server before starting.
     */
    fun startVpn(context: Context, guid: String? = null) {
        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }
        CoreServiceManager.startVService(context)
    }

    /**
     * Stops the VPN service.
     */
    fun stopVpn(context: Context) {
        CoreServiceManager.stopVService(context)
    }

    /**
     * Restarts the VPN service.
     */
    fun restartVpn(context: Context) {
        stopVpn(context)
        Thread.sleep(500L)
        startVpn(context)
    }

    /**
     * Gets the currently selected server GUID.
     */
    fun getSelectedServerGuid(): String? {
        return MmkvManager.getSelectServer()
    }

    /**
     * Gets the configuration details of the currently selected server.
     */
    fun getSelectedServer(): ProfileItem? {
        val guid = getSelectedServerGuid() ?: return null
        return MmkvManager.decodeServerConfig(guid)
    }

    /**
     * Sets the active server configuration to the specified GUID.
     */
    fun setSelectedServerGuid(guid: String) {
        MmkvManager.setSelectServer(guid)
    }
}

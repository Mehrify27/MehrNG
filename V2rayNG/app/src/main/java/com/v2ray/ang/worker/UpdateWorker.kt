package com.v2ray.ang.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.SubscriptionManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.vpn.VpnController
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LogUtil.i(AppConfig.TAG, "UpdateWorker background task starting...")
        
        val isRunningBefore = VpnController.isRunning()
        val oldSelectedGuid = VpnController.getSelectedServerGuid()

        val (success, guids) = SubscriptionManager.refreshSubscription()

        if (success && guids.isNotEmpty()) {
            LogUtil.i(AppConfig.TAG, "Subscription updated in background successfully. Nodes count: ${guids.size}")
            
            val newSelectedGuid = VpnController.getSelectedServerGuid()
            if (isRunningBefore && oldSelectedGuid != newSelectedGuid && newSelectedGuid != null) {
                LogUtil.i(AppConfig.TAG, "Active node changed. Reconnecting VPN to the new best node...")
                VpnController.restartVpn(applicationContext)
            }
            return Result.success()
        }

        LogUtil.w(AppConfig.TAG, "Subscription background update failed or returned empty nodes.")
        return Result.retry()
    }

    companion object {
        private const val WORK_NAME = "mehr_vpn_update_work"

        /**
         * Enqueues the periodic update worker to run every 15 minutes.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

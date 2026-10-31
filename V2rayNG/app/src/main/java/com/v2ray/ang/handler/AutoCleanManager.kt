package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.AutoCleanSummary
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.service.RealPingWorkerService
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Hourly real-delay test of every saved server, run on the device itself (so delays reflect
 * the user's own network), followed by removal of servers that failed.
 *
 * Separate from SubscriptionUpdater because it owns a different lifetime: one global
 * periodic task, not one task per subscription.
 */
object AutoCleanManager {

    private const val PERIODIC_TASK = "collector_auto_clean"
    private const val ONE_TIME_TASK = "collector_auto_clean_now"
    private const val INTERVAL_MINUTES = 60L

    /** WorkManager stops a worker after 10 minutes; stay below that. */
    private const val MAX_TEST_MILLIS = 8 * 60 * 1000L

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_COLLECTOR_AUTO_CLEAN, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_COLLECTOR_AUTO_CLEAN, enabled)
        val rw = RemoteWorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<CleanTask>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()
            rw.enqueueUniquePeriodicWork(PERIODIC_TASK, ExistingPeriodicWorkPolicy.REPLACE, request)
        } else {
            rw.cancelUniqueWork(PERIODIC_TASK)
        }
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CleanTask>()
            .setConstraints(constraints())
            .build()
        RemoteWorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_TASK, ExistingWorkPolicy.KEEP, request)
    }

    fun lastSummary(): AutoCleanSummary? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_COLLECTOR_AUTO_CLEAN_LAST)
            ?.let { JsonUtil.fromJsonSafe(it, AutoCleanSummary::class.java) }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Tests all servers and removes the failed ones. Returns the run summary. */
    suspend fun cleanAll(context: Context): AutoCleanSummary {
        CoreNativeManager.initCoreEnv(context)
        val guids = MmkvManager.decodeAllServerList()
        if (guids.isEmpty()) return AutoCleanSummary(time = System.currentTimeMillis())

        val results = ConcurrentHashMap<String, Long>()
        val finished = CompletableDeferred<Unit>()
        val worker = RealPingWorkerService(context, guids) { event ->
            when (event) {
                is RealPingEvent.Result -> results.put(event.guid, event.delayMillis)
                is RealPingEvent.Finish -> finished.complete(Unit)
                is RealPingEvent.Progress -> Unit
            }
            Unit
        }
        worker.start()
        try {
            withTimeout(MAX_TEST_MILLIS) { finished.await() }
        } catch (_: TimeoutCancellationException) {
            LogUtil.w(AppConfig.TAG, "AutoClean: test timed out, using ${results.size} partial results")
            worker.cancel()
        }

        val ok = results.values.count { it > 0 }
        // No server answered at all: almost certainly the phone is offline or the network
        // blocks everything right now. Deleting would wipe the whole list, so skip.
        if (ok == 0) {
            val summary = AutoCleanSummary(System.currentTimeMillis(), results.size, 0, 0, skippedOffline = true)
            saveSummary(summary)
            return summary
        }

        val selected = MmkvManager.getSelectServer()
        var removed = 0
        results.forEach { (guid, delay) ->
            MmkvManager.encodeServerTestDelayMillis(guid, delay)
            if (delay <= 0L && guid != selected) {
                MmkvManager.removeServer(guid)
                removed++
            }
        }
        MmkvManager.decodeSubsList().forEach { AngConfigManager.sortByTestResultsForSub(it) }
        SettingsChangeManager.makeSetupGroupTab()

        val summary = AutoCleanSummary(System.currentTimeMillis(), results.size, ok, removed)
        saveSummary(summary)
        LogUtil.i(AppConfig.TAG, "AutoClean: tested=${results.size} ok=$ok removed=$removed")
        return summary
    }

    private fun saveSummary(summary: AutoCleanSummary) {
        MmkvManager.encodeSettings(AppConfig.PREF_COLLECTOR_AUTO_CLEAN_LAST, JsonUtil.toJson(summary))
    }

    class CleanTask(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                cleanAll(AngApplication.application)
                Result.success()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "AutoClean: worker failed", e)
                Result.success()
            }
        }
    }
}

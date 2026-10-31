package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CollectorData
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

/**
 * Connects the app to the v2-collector project: reads its published catalogue and turns
 * the chosen lists (country / protocol / fastest / all) into auto-updating subscriptions.
 */
object CollectorManager {

    /** Collector subscriptions refresh every 3 hours, matching the collector's own schedule. */
    private const val UPDATE_INTERVAL_MINUTES = 180L

    fun fileUrl(file: String): String = AppConfig.COLLECTOR_BASE_URL + file

    /** Blocking network call; run off the main thread. */
    fun fetchData(): CollectorData? {
        val text = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = fileUrl("data.json") + "?t=" + System.currentTimeMillis(),
                timeout = 20000
            )
        ) ?: return null
        return JsonUtil.fromJsonSafe(text, CollectorData::class.java)
    }

    /** Collector file path (e.g. "country/DE.txt") -> existing subscription id. */
    fun existingSubscriptions(): Map<String, String> =
        MmkvManager.decodeSubscriptions()
            .filter { it.subscription.url.startsWith(AppConfig.COLLECTOR_BASE_URL) }
            .associate { it.subscription.url.removePrefix(AppConfig.COLLECTOR_BASE_URL) to it.guid }

    /**
     * Adds one auto-updating subscription per file that is not already present and starts
     * an immediate update for them. Returns the number of subscriptions added.
     */
    fun addSubscriptions(context: Context, items: List<Pair<String, String>>): Int {
        val existing = existingSubscriptions()
        val added = mutableListOf<String>()
        items.forEach { (file, label) ->
            if (existing.containsKey(file)) return@forEach
            val subId = Utils.getUuid()
            val sub = SubscriptionItem(
                remarks = label,
                url = fileUrl(file),
                autoUpdate = true,
                updateInterval = UPDATE_INTERVAL_MINUTES,
                lastUpdated = System.currentTimeMillis()
            )
            MmkvManager.encodeSubscription(subId, sub)
            SubscriptionUpdater.syncOne(context, subId)
            added.add(subId)
        }
        if (added.isNotEmpty()) {
            MessageHelper.sendMsg2SubscriptionService(
                context,
                SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, true, added)
            )
            SettingsChangeManager.makeSetupGroupTab()
            LogUtil.i(AppConfig.TAG, "Collector: added ${added.size} subscriptions")
        }
        return added.size
    }

    fun removeSubscription(context: Context, file: String): Boolean {
        val subId = existingSubscriptions()[file] ?: return false
        SubscriptionUpdater.cancelOne(context, subId)
        MmkvManager.removeSubscription(subId)
        SettingsChangeManager.makeSetupGroupTab()
        return true
    }
}

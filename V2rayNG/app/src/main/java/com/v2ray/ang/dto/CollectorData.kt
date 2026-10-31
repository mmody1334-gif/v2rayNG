package com.v2ray.ang.dto

/** data.json published by the v2-collector GitHub project (subs branch). */
data class CollectorData(
    val updated: String? = null,
    val stats: CollectorStats? = null,
    val files: Map<String, Int>? = null,
    val countries: List<CollectorCountry>? = null,
    val protocols: List<CollectorProtocol>? = null,
)

data class CollectorStats(
    val alive: Int = 0,
    val countries: Int = 0,
)

data class CollectorCountry(
    val code: String = "",
    val flag: String = "",
    val count: Int = 0,
    val total: Int = 0,
    val best: Int = 0,
    val protocols: List<String>? = null,
)

data class CollectorProtocol(
    val name: String = "",
    val count: Int = 0,
    val total: Int = 0,
    val best: Int = 0,
)

/** Result of the last hourly auto-clean run. */
data class AutoCleanSummary(
    val time: Long = 0L,
    val tested: Int = 0,
    val ok: Int = 0,
    val removed: Int = 0,
    val skippedOffline: Boolean = false,
)

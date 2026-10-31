package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.TimeZone

/** ip-api.com response (only the fields we request). */
data class IpGeoInfo(
    val status: String? = null,
    val query: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val isp: String? = null,
    val org: String? = null,
    val `as`: String? = null,
    val proxy: Boolean? = null,
    val hosting: Boolean? = null,
)

enum class Ipv6Status { NoIpv6, Protected, Leaking, NotConnected }

data class ConnectionCheckResult(
    /** What websites see: through the running core if any, otherwise direct. */
    val visible: IpGeoInfo?,
    /** The phone's own address (direct, outside the tunnel). */
    val real: IpGeoInfo?,
    /** True when [visible] was obtained through the local proxy of a running core. */
    val throughVpn: Boolean,
    val phoneTimezone: String,
    val timezoneMatches: Boolean?,
    val ipv6: Ipv6Status,
    val realIpv6: String?,
)

/**
 * "Is my VPN working and does it look like a VPN?" checks.
 *
 * This app is excluded from its own VPN, so a direct request shows the real address and a
 * request through the core's local HTTP inbound shows what other apps and websites see.
 */
object ConnectionCheckManager {

    private const val GEO_URL =
        "http://ip-api.com/json/?fields=status,query,country,countryCode,regionName,city,lat,lon,timezone,isp,org,as,proxy,hosting"
    private const val IPV6_URL = "https://api6.ipify.org"

    /** Blocking network work; call off the main thread. */
    fun check(): ConnectionCheckResult {
        val viaProxy = fetchGeo(SettingsManager.getHttpPort())
        val direct = fetchGeo(0)
        val throughVpn = viaProxy != null && viaProxy.query != direct?.query
        val visible = if (throughVpn) viaProxy else direct

        val phoneTz = TimeZone.getDefault()
        val tzMatches = visible?.timezone?.let { ipTzId ->
            val ipTz = TimeZone.getTimeZone(ipTzId)
            val now = System.currentTimeMillis()
            ipTzId == phoneTz.id || ipTz.getOffset(now) == phoneTz.getOffset(now)
        }

        val realIpv6 = HttpUtil.getUrlContent(UrlContentRequest(url = IPV6_URL, timeout = 5000))
            ?.trim()?.takeIf { it.contains(':') }
        val hasIpv6 = realIpv6 != null || hasGlobalIpv6Outside()
        val ipv6 = when {
            !hasIpv6 -> Ipv6Status.NoIpv6
            !throughVpn -> Ipv6Status.NotConnected
            SettingsManager.vpnCapturesIpv6() -> Ipv6Status.Protected
            else -> Ipv6Status.Leaking
        }

        return ConnectionCheckResult(
            visible = visible,
            real = direct,
            throughVpn = throughVpn,
            phoneTimezone = phoneTz.id,
            timezoneMatches = tzMatches,
            ipv6 = ipv6,
            realIpv6 = realIpv6,
        )
    }

    private fun fetchGeo(httpPort: Int): IpGeoInfo? {
        if (httpPort < 0) return null
        return try {
            val text = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = GEO_URL,
                    timeout = 8000,
                    httpPort = httpPort,
                    proxyUsername = if (httpPort != 0) SettingsManager.getSocksUsername() else null,
                    proxyPassword = if (httpPort != 0) SettingsManager.getSocksPassword() else null,
                )
            ) ?: return null
            JsonUtil.fromJsonSafe(text, IpGeoInfo::class.java)?.takeIf { it.status == "success" }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ConnectionCheck: geo lookup failed (proxy=${httpPort != 0})", e)
            null
        }
    }

    /** A public IPv6 address on any non-tunnel interface means the network offers IPv6. */
    private fun hasGlobalIpv6Outside(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet6Address>()
            .any { a ->
                !a.isLinkLocalAddress && !a.isSiteLocalAddress && !a.isLoopbackAddress &&
                        (a.address[0].toInt() and 0xFE) != 0xFC // not ULA fc00::/7
            }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "ConnectionCheck: interface scan failed", e)
        false
    }
}

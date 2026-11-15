package com.v2ray.ang.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.Tun2SocksControl
import com.v2ray.ang.contracts.Tun2SocksTarget
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Manages the tun2socks process that handles VPN traffic
 */
class TProxyService(
    private val context: Context,
    private val vpnInterface: ParcelFileDescriptor,
    private val isRunningProvider: () -> Boolean,
    private val restartCallback: () -> Unit
) : Tun2SocksControl {
    companion object {
        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyIsRunning(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyGetStats(): LongArray?

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    /**
     * Starts the tun2socks process with the appropriate parameters.
     */
    fun startTun2Socks(target: Tun2SocksTarget) {
        val configContent = buildConfig(target)
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(configContent)
        }
        LogUtil.d(AppConfig.TAG, "HevSocks5Tunnel Config content:\n$configContent")

        try {
            TProxyStartService(configFile.absolutePath, vpnInterface.fd)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "HevSocks5Tunnel exception: ${e.message}")
        }
    }

    override fun startTun2Socks() {
        startTun2Socks(Tun2SocksTarget.Xray)
    }

    fun buildConfig(target: Tun2SocksTarget = Tun2SocksTarget.Xray): String {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val timeoutSetting = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) ?: AppConfig.HEVTUN_RW_TIMEOUT
        val parts = timeoutSetting.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val tcpTimeout = parts.getOrNull(0)?.toIntOrNull() ?: 300
        val udpTimeout = parts.getOrNull(1)?.toIntOrNull() ?: 60
        val logLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL)

        return when (target) {
            is Tun2SocksTarget.Xray -> {
                val socksPort = SettingsManager.getSocksPort()
                val socksUsername = SettingsManager.getSocksUsername()
                val socksPassword = SettingsManager.getSocksPassword()
                val escapedSocksUsername = socksUsername?.replace("'", "''")
                val escapedSocksPassword = socksPassword?.replace("'", "''")
                buildString {
                    appendLine("tunnel:")
                    appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
                    appendLine("  ipv4: ${vpnConfig.ipv4Client}")

                    if (SettingsManager.vpnCapturesIpv6()) {
                        appendLine("  ipv6: '${vpnConfig.ipv6Client}'")
                    }

                    appendLine("socks5:")
                    appendLine("  port: ${socksPort}")
                    appendLine("  address: ${AppConfig.LOOPBACK}")
                    appendLine("  udp: 'udp'")
                    if (escapedSocksUsername != null && escapedSocksPassword != null) {
                        appendLine("  username: '${escapedSocksUsername}'")
                        appendLine("  password: '${escapedSocksPassword}'")
                    }

                    appendLine("misc:")
                    appendLine("  tcp-read-write-timeout: ${tcpTimeout * 1000}")
                    appendLine("  udp-read-write-timeout: ${udpTimeout * 1000}")
                    appendLine("  log-level: ${logLevel}")
                }
            }
            is Tun2SocksTarget.Shroudly -> {
                val escapedUser = target.user.replace("'", "''")
                val escapedPass = target.pass.replace("'", "''")
                buildString {
                    appendLine("tunnel:")
                    appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
                    appendLine("  ipv4: ${vpnConfig.ipv4Client}")

                    if (SettingsManager.vpnCapturesIpv6()) {
                        appendLine("  ipv6: '${vpnConfig.ipv6Client}'")
                    }

                    appendLine("socks5:")
                    appendLine("  port: ${target.port}")
                    appendLine("  address: ${AppConfig.LOOPBACK}")
                    appendLine("  udp: 'udp'")
                    appendLine("  username: '${escapedUser}'")
                    appendLine("  password: '${escapedPass}'")

                    appendLine("mapdns:")
                    appendLine("  address: ${vpnConfig.ipv4Router}")
                    appendLine("  port: 53")
                    appendLine("  network: 198.19.0.0")
                    appendLine("  netmask: 255.255.192.0")
                    appendLine("  cache-size: 10000")

                    appendLine("misc:")
                    appendLine("  connect-timeout: 10000")
                    appendLine("  tcp-read-write-timeout: ${tcpTimeout * 1000}")
                    appendLine("  udp-read-write-timeout: ${udpTimeout * 1000}")
                    appendLine("  log-level: ${logLevel}")
                }
            }
        }
    }

    /**
     * Stops the tun2socks process
     */
    override fun stopTun2Socks() {
        try {
            LogUtil.i(AppConfig.TAG, "TProxyStopService...")
            TProxyStopService()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to stop hev-socks5-tunnel", e)
        }
    }
}

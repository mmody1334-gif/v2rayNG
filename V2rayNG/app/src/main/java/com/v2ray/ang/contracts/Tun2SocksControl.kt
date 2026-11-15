package com.v2ray.ang.contracts

sealed class Tun2SocksTarget {
    data object Xray : Tun2SocksTarget()
    data class Shroudly(val port: Int, val user: String, val pass: String) : Tun2SocksTarget()
}

/**
 * Interface that defines the control operations for tun2socks implementations.
 */
interface Tun2SocksControl {
    /**
     * Starts the tun2socks process with the appropriate parameters.
     * This initializes the VPN tunnel and connects it to the SOCKS proxy.
     */
    fun startTun2Socks()

    /**
     * Stops the tun2socks process and cleans up resources.
     */
    fun stopTun2Socks()
}

package com.v2ray.ang.shroudly

import me.codeshare.shroudly.EngineNative

object ShroudlyAvailability {
    val isAvailable: Boolean
        get() = EngineNative.loadError == null

    val loadError: String?
        get() = EngineNative.loadError
}

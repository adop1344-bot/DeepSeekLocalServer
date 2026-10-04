package com.rikkahub.deepseeklocal.service

import java.net.Inet4Address
import java.net.NetworkInterface

/** Utility for finding the device's primary LAN IPv4 address. */
object LanAddress {
    /** Returns the first non-loopback IPv4 address, or 127.0.0.1 if none. */
    fun find(): String {
        return try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress ?: "127.0.0.1"
                }
            }
            "127.0.0.1"
        } catch (t: Throwable) {
            "127.0.0.1"
        }
    }
}

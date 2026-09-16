package com.jakspinning.wakemypc.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FritzBox's TR-064 host table reports whether a MAC address currently has
 * link on the network, not whether the PC's OS is actually running. A PC
 * with "Wake on Magic Packet" enabled (required for WOL to work at all)
 * keeps its Ethernet PHY powered while fully shut down, so the FritzBox
 * keeps seeing link and reports the host as connected forever. An ICMP
 * ping instead reflects whether the OS network stack is up, which is what
 * "is the PC on" actually means here.
 *
 * Shells out to the system ping binary rather than InetAddress.isReachable,
 * which needs ICMP capability apps don't have on Android and otherwise
 * silently falls back to a TCP probe on port 7 (rarely open on Windows).
 */
suspend fun pingHost(ipAddress: String, timeoutSeconds: Int = 1): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", timeoutSeconds.toString(), ipAddress)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

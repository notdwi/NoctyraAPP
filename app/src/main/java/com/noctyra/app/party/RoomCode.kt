package com.noctyra.app.party

import java.net.Inet4Address
import java.net.NetworkInterface

object RoomCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 7

    fun encode(ip: String): String {
        var value = ipToLong(ip) ?: return ip
        val chars = CharArray(LENGTH)
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = ALPHABET[(value and 31).toInt()]
            value = value shr 5
        }
        val s = String(chars)
        return "NX-${s.substring(0, 3)}-${s.substring(3)}"
    }

    /** Aceita o código da sala (com ou sem "NX-", traços e minúsculas) ou um IPv4 digitado. */
    fun decode(input: String): String? {
        val raw = input.trim()
        if (raw.count { it == '.' } == 3) return raw.takeIf { ipToLong(it) != null }

        val cleaned = raw.uppercase()
            .removePrefix("NX")
            .filter { it.isLetterOrDigit() }
            .map { c -> when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c } }
            .joinToString("")
        if (cleaned.length != LENGTH) return null

        var value = 0L
        for (c in cleaned) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            value = (value shl 5) or digit.toLong()
        }
        if (value > 0xFFFFFFFFL) return null
        return longToIp(value)
    }

    fun localAddresses(): List<RoomAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic ->
                nic.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .mapNotNull { addr ->
                        val label = labelFor(nic.name) ?: return@mapNotNull null
                        val ip = addr.hostAddress ?: return@mapNotNull null
                        RoomAddress(label, ip, encode(ip))
                    }
            }
            .distinctBy { it.ip }
            .sortedBy { if (it.label.startsWith("ZeroTier")) 0 else 1 }
    }.getOrDefault(emptyList())

    private fun labelFor(name: String): String? = when {
        name.startsWith("wlan") -> "Mesma rede Wi‑Fi"
        name.startsWith("tun") || name.startsWith("zt") || name.startsWith("ppp") -> "ZeroTier / VPN"
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> "Roteador do celular"
        name.startsWith("eth") -> "Cabo / Ethernet"
        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("radio") -> null
        else -> "Rede $name"
    }

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (p in parts) {
            val n = p.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    private fun longToIp(v: Long) = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"
}

package dev.veil.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks a name up at your own network's DNS server (the router), for names that only exist at home, like
 * a self-hosted server. With ad blocking on, the TV asks an outside DNS service for everything, and that
 * service has never heard of such names. This asks the router directly, for that one name only.
 */
internal object LocalDns {
    private val cache = ConcurrentHashMap<String, InetAddress>()

    fun resolve(context: Context, host: String): InetAddress? {
        cache[host]?.let { return it }
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val servers = cm.getLinkProperties(cm.activeNetwork ?: return null)?.dnsServers.orEmpty()
        for (server in servers) {
            val ip = runCatching { query(server, host) }.getOrNull() ?: continue
            cache[host] = ip
            return ip
        }
        return null
    }

    fun forget(host: String) { cache.remove(host) }

    /** One plain DNS question ("A record of host?") over UDP, and the first IPv4 address in the answer. */
    private fun query(server: InetAddress, host: String): InetAddress? {
        val q = java.io.ByteArrayOutputStream()
        val id = (System.nanoTime() and 0xFFFF).toInt()
        q.write(id shr 8); q.write(id and 0xFF)
        q.write(byteArrayOf(0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)) // recursion wanted, 1 question
        for (label in host.trimEnd('.').split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            if (b.isEmpty() || b.size > 63) return null
            q.write(b.size); q.write(b)
        }
        q.write(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01)) // end of name, type A, class IN
        val out = q.toByteArray()
        DatagramSocket().use { socket ->
            socket.soTimeout = 2_000
            socket.send(DatagramPacket(out, out.size, server, 53))
            val buf = ByteArray(1500)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val n = packet.length
            fun u8(i: Int) = buf[i].toInt() and 0xFF
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            if (n < 12 || u16(0) != id) return null
            val answers = u16(6)
            /** Index just past the (possibly compressed) name starting at [start]. */
            fun skipName(start: Int): Int {
                var i = start
                while (i < n) {
                    val len = u8(i)
                    if (len == 0) return i + 1
                    if (len and 0xC0 == 0xC0) return i + 2
                    i += len + 1
                }
                return n
            }
            var p = 12
            repeat(u16(4)) { p = skipName(p) + 4 }
            repeat(answers) {
                p = skipName(p)
                if (p + 10 > n) return null
                val type = u16(p); val len = u16(p + 8)
                p += 10
                if (type == 1 && len == 4 && p + 4 <= n) return InetAddress.getByAddress(buf.copyOfRange(p, p + 4))
                p += len // e.g. a CNAME before the address
            }
        }
        return null
    }
}

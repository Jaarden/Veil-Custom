package dev.veil.launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP helper. No third-party networking or analytics libraries. */
internal object Http {
    fun call(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        /** Shorter connect/read timeout, for trying several addresses of the same server. */
        timeoutMs: Int? = null,
        /**
         * Connect to this address instead of looking the host name up (see [LocalDns]). The certificate is
         * still checked against the real host name, so this is exactly as secure as a normal request.
         */
        address: java.net.InetAddress? = null,
    ): Pair<Int, String> {
        if (address != null && url.startsWith("https://")) return callAt(address, method, URL(url), headers, body)
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs ?: 10_000
            c.readTimeout = timeoutMs ?: 15_000
            c.setRequestProperty("User-Agent", "Veil/1.0")
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    c.setRequestProperty("Content-Type", "application/json")
                }
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /**
     * The same request, sent to [address] over a connection made by hand: the TLS handshake names the real
     * host (so the server picks the right site) and the certificate is checked against that name. The
     * system's own HTTPS client can't do this; it would name the IP address instead.
     */
    private fun callAt(
        address: java.net.InetAddress, method: String, url: URL, headers: Map<String, String>, body: String?,
    ): Pair<Int, String> {
        val host = url.host
        val port = if (url.port == -1) 443 else url.port
        val plain = java.net.Socket()
        plain.connect(java.net.InetSocketAddress(address, port), 10_000)
        plain.soTimeout = 15_000
        val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
        val ssl = factory.createSocket(plain, host, port, true) as javax.net.ssl.SSLSocket
        try {
            ssl.startHandshake()
            if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) {
                throw javax.net.ssl.SSLPeerUnverifiedException("Certificate doesn't match $host")
            }
            val bytes = body?.toByteArray()
            val head = StringBuilder()
            head.append(method).append(' ').append(url.file.ifEmpty { "/" }).append(" HTTP/1.1\r\n")
            head.append("Host: ").append(host).append("\r\n")
            head.append("User-Agent: Veil/1.0\r\nAccept: application/json\r\nConnection: close\r\n")
            headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
            if (bytes != null) {
                if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) head.append("Content-Type: application/json\r\n")
                head.append("Content-Length: ").append(bytes.size).append("\r\n")
            }
            head.append("\r\n")
            val out = ssl.outputStream
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            if (bytes != null) out.write(bytes)
            out.flush()

            val input = java.io.BufferedInputStream(ssl.inputStream)
            fun line(): String {
                val sb = StringBuilder()
                while (true) {
                    val ch = input.read()
                    if (ch == -1 || ch == '\n'.code) break
                    if (ch != '\r'.code) sb.append(ch.toChar())
                }
                return sb.toString()
            }
            fun take(n: Int): ByteArray {
                val buf = ByteArray(n)
                var got = 0
                while (got < n) {
                    val r = input.read(buf, got, n - got)
                    if (r == -1) break
                    got += r
                }
                return if (got == n) buf else buf.copyOf(got)
            }
            val code = line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw java.io.IOException("Bad reply from $host")
            var length = -1
            var chunked = false
            while (true) {
                val h = line()
                if (h.isEmpty()) break
                val name = h.substringBefore(':').trim().lowercase()
                val value = h.substringAfter(':', "").trim()
                if (name == "content-length") length = value.toIntOrNull() ?: -1
                if (name == "transfer-encoding" && value.contains("chunked", ignoreCase = true)) chunked = true
            }
            val data = java.io.ByteArrayOutputStream()
            when {
                chunked -> while (true) {
                    val size = line().substringBefore(';').trim().toIntOrNull(16) ?: break
                    if (size == 0) break
                    data.write(take(size))
                    line() // the line break after each chunk
                }
                length >= 0 -> data.write(take(length))
                else -> data.write(input.readBytes())
            }
            return code to data.toString("UTF-8")
        } finally {
            runCatching { ssl.close() }
        }
    }
}

internal fun JSONObject.str(k: String): String? =
    if (has(k) && !isNull(k)) getString(k).takeIf { it.isNotBlank() } else null

internal fun JSONObject.int(k: String): Int? =
    if (has(k) && !isNull(k)) optInt(k) else null

internal fun JSONObject.obj(k: String): JSONObject? =
    if (has(k) && !isNull(k)) optJSONObject(k) else null

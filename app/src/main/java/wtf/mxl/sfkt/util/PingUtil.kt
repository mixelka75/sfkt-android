package wtf.mxl.sfkt.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wtf.mxl.sfkt.R
import java.net.InetSocketAddress
import java.net.Socket

object PingUtil {

    suspend fun measurePing(host: String, port: Int, timeoutMs: Int = 3000): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                (System.currentTimeMillis() - startTime).toInt()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getPingColor(ping: Int?): Int {
        return when {
            ping == null -> R.color.ping_none
            ping < 100 -> R.color.ping_good
            ping < 200 -> R.color.ping_medium
            else -> R.color.ping_bad
        }
    }

    fun getPingColorValue(ping: Int?): Int {
        return when {
            ping == null -> 0xFF9E9E9E.toInt() // Gray
            ping < 100 -> 0xFF4CAF50.toInt() // Green
            ping < 200 -> 0xFFFF9800.toInt() // Orange
            else -> 0xFFF44336.toInt() // Red
        }
    }

    fun formatPing(ping: Int?): String {
        return ping?.let { "${it}ms" } ?: "..."
    }
}

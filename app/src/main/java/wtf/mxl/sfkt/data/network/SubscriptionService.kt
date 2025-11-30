package wtf.mxl.sfkt.data.network

import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wtf.mxl.sfkt.data.database.Server
import java.net.URI
import java.net.URL
import java.net.URLDecoder

class SubscriptionService {

    private val gson = Gson()

    suspend fun fetchServers(subscriptionUrl: String): Result<List<Server>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = URL(subscriptionUrl).readText()
                val decoded = String(Base64.decode(response, Base64.DEFAULT), Charsets.UTF_8)

                val servers = decoded.lines()
                    .filter { it.startsWith("vless://") }
                    .mapNotNull { parseVlessUrl(it) }

                if (servers.isEmpty()) {
                    Result.failure(Exception("No servers found in subscription"))
                } else {
                    Result.success(servers)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchSubscriptionInfo(subscriptionUrl: String): Result<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                // Extract token from subscription URL
                // URL format: https://sfkt.mxl.wtf/api/v1/subscriptions/{token}
                val token = subscriptionUrl.substringAfterLast("/")
                val infoUrl = "${subscriptionUrl}/info"

                val response = URL(infoUrl).readText()
                val info = gson.fromJson(response, SubscriptionInfo::class.java)

                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseVlessUrl(url: String): Server? {
        return try {
            // vless://UUID@HOST:PORT?params#Name
            val uri = URI(url)
            if (uri.scheme != "vless") return null

            val uuid = uri.userInfo ?: return null
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443
            val name = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "Server"

            Server(
                name = name,
                host = host,
                port = port,
                uuid = uuid,
                originalUrl = url
            )
        } catch (e: Exception) {
            null
        }
    }
}

package wtf.mxl.sfkt.util

import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import wtf.mxl.sfkt.Settings
import java.net.URLDecoder

class LinkHelper(
    private val settings: Settings,
    private val link: String
) {
    private var parsed: VlessConfig? = null

    init {
        parsed = parseVlessUrl(link)
    }

    data class VlessConfig(
        val uuid: String,
        val address: String,
        val port: Int,
        val type: String,         // xhttp, tcp, ws, etc.
        val security: String,     // reality, tls, none
        val sni: String,
        val fingerprint: String,
        val publicKey: String,
        val shortId: String,
        val path: String,
        val remark: String
    )

    companion object {
        private const val TAG = "LinkHelper"

        fun parseVlessUrl(url: String): VlessConfig? {
            try {
                if (!url.startsWith("vless://")) {
                    Log.e(TAG, "Not a VLESS URL")
                    return null
                }

                // vless://uuid@host:port?params#remark
                val withoutScheme = url.removePrefix("vless://")

                // Split by # to get remark
                val parts = withoutScheme.split("#", limit = 2)
                val mainPart = parts[0]
                val remark = if (parts.size > 1) {
                    URLDecoder.decode(parts[1], "UTF-8")
                } else "Server"

                // Split by ? to get params
                val queryParts = mainPart.split("?", limit = 2)
                val userHostPort = queryParts[0]
                val queryString = if (queryParts.size > 1) queryParts[1] else ""

                // Parse uuid@host:port
                val atIndex = userHostPort.indexOf("@")
                if (atIndex == -1) {
                    Log.e(TAG, "Invalid VLESS URL format: no @")
                    return null
                }

                val uuid = userHostPort.substring(0, atIndex)
                val hostPort = userHostPort.substring(atIndex + 1)

                // Parse host:port
                val colonIndex = hostPort.lastIndexOf(":")
                if (colonIndex == -1) {
                    Log.e(TAG, "Invalid VLESS URL format: no port")
                    return null
                }

                val address = hostPort.substring(0, colonIndex)
                val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 443

                // Parse query params
                val params = mutableMapOf<String, String>()
                if (queryString.isNotEmpty()) {
                    queryString.split("&").forEach { param ->
                        val kv = param.split("=", limit = 2)
                        if (kv.size == 2) {
                            params[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
                        }
                    }
                }

                return VlessConfig(
                    uuid = uuid,
                    address = address,
                    port = port,
                    type = params["type"] ?: "tcp",
                    security = params["security"] ?: "none",
                    sni = params["sni"] ?: "",
                    fingerprint = params["fp"] ?: "chrome",
                    publicKey = params["pbk"] ?: "",
                    shortId = params["sid"] ?: "",
                    path = params["path"] ?: "/",
                    remark = remark
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse VLESS URL", e)
                return null
            }
        }
    }

    fun isValid(): Boolean {
        return parsed != null
    }

    fun json(): String {
        return config().toString(2) + "\n"
    }

    fun remark(): String {
        return parsed?.remark ?: "Server"
    }

    private fun log(): JSONObject {
        return JSONObject().apply {
            put("loglevel", "warning")
        }
    }

    private fun dns(): JSONObject {
        return JSONObject().apply {
            put("servers", JSONArray().apply {
                put(settings.primaryDns)
                put(settings.secondaryDns)
            })
        }
    }

    private fun inbounds(): JSONArray {
        val sniffing = JSONObject().apply {
            put("enabled", true)
            put("destOverride", JSONArray().apply {
                put("http")
                put("tls")
                put("quic")
            })
        }

        val socks = JSONObject().apply {
            put("listen", settings.socksAddress)
            put("port", settings.socksPort)
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("udp", true)
            })
            put("sniffing", sniffing)
            put("tag", "socks")
        }

        return JSONArray().apply { put(socks) }
    }

    private fun outbounds(): JSONArray {
        val cfg = parsed!!

        // VLESS user
        val user = JSONObject().apply {
            put("id", cfg.uuid)
            put("encryption", "none")
            put("flow", "")
        }

        // VLESS vnext
        val vnext = JSONObject().apply {
            put("address", cfg.address)
            put("port", cfg.port)
            put("users", JSONArray().apply { put(user) })
        }

        // Stream settings
        val streamSettings = JSONObject().apply {
            put("network", cfg.type)

            // Transport settings based on type
            when (cfg.type) {
                "xhttp" -> {
                    put("xhttpSettings", JSONObject().apply {
                        put("path", cfg.path)
                    })
                }
                "ws" -> {
                    put("wsSettings", JSONObject().apply {
                        put("path", cfg.path)
                    })
                }
                "tcp" -> {
                    // No additional settings needed
                }
            }

            // Security settings
            put("security", cfg.security)

            when (cfg.security) {
                "reality" -> {
                    put("realitySettings", JSONObject().apply {
                        put("serverName", cfg.sni)
                        put("fingerprint", cfg.fingerprint)
                        put("publicKey", cfg.publicKey)
                        put("shortId", cfg.shortId)
                        put("spiderX", "")
                    })
                }
                "tls" -> {
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", cfg.sni)
                        put("fingerprint", cfg.fingerprint)
                    })
                }
            }
        }

        // Proxy outbound
        val proxy = JSONObject().apply {
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply { put(vnext) })
            })
            put("streamSettings", streamSettings)
            put("tag", "proxy")
        }

        // Direct outbound
        val direct = JSONObject().apply {
            put("protocol", "freedom")
            put("tag", "direct")
        }

        // Block outbound
        val block = JSONObject().apply {
            put("protocol", "blackhole")
            put("tag", "block")
        }

        return JSONArray().apply {
            put(proxy)
            put(direct)
            put(block)
        }
    }

    private fun routing(): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // DNS through proxy
                put(JSONObject().apply {
                    put("ip", JSONArray().apply {
                        put(settings.primaryDns)
                        put(settings.secondaryDns)
                    })
                    put("port", 53)
                    put("outboundTag", "proxy")
                })
                // Private IPs direct
                put(JSONObject().apply {
                    put("ip", JSONArray().apply {
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                        put("127.0.0.0/8")
                        put("169.254.0.0/16")
                    })
                    put("outboundTag", "direct")
                })
            })
        }
    }

    private fun config(): JSONObject {
        return JSONObject().apply {
            put("log", log())
            put("dns", dns())
            put("inbounds", inbounds())
            put("outbounds", outbounds())
            put("routing", routing())
        }
    }
}

package wtf.mxl.sfkt

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class Settings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sfkt_settings", Context.MODE_PRIVATE)
    private val filesDir: File = context.filesDir

    // Subscription
    var subscriptionUrl: String
        get() = prefs.getString("subscription_url", "") ?: ""
        set(value) = prefs.edit().putString("subscription_url", value).apply()

    var selectedServerId: Long
        get() = prefs.getLong("selected_server_id", -1L)
        set(value) = prefs.edit().putLong("selected_server_id", value).apply()

    // Last connected server (for Quick Settings Tile)
    var lastServerUrl: String
        get() = prefs.getString("last_server_url", "") ?: ""
        set(value) = prefs.edit().putString("last_server_url", value).apply()

    var lastServerName: String
        get() = prefs.getString("last_server_name", "") ?: ""
        set(value) = prefs.edit().putString("last_server_name", value).apply()

    var lastSubscriptionUpdate: Long
        get() = prefs.getLong("last_subscription_update", 0L)
        set(value) = prefs.edit().putLong("last_subscription_update", value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(value) = prefs.edit().putBoolean("is_first_launch", value).apply()

    // Split Tunneling
    var excludedApps: Set<String>
        get() = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("excluded_apps", value).apply()

    // VPN Settings
    val socksAddress: String get() = "127.0.0.1"
    val socksPort: Int get() = 10808
    val primaryDns: String get() = "1.1.1.1"
    val secondaryDns: String get() = "1.0.0.1"
    val tunMtu: Int get() = 8500
    val tunAddress: String get() = "10.10.10.10"
    val tunPrefix: Int get() = 32

    // Files
    fun xrayConfig(): File = File(filesDir, "config.json")
    fun tun2socksConfig(): File = File(filesDir, "tun2socks.yml")
}

package wtf.mxl.sfkt.service

import XrayCore.XrayCore
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.ui.main.MainActivity
import wtf.mxl.sfkt.util.FileHelper
import wtf.mxl.sfkt.util.LinkHelper

@SuppressLint("VpnServicePolicy")
class SfktVpnService : VpnService() {

    companion object {
        const val PKG_NAME = "wtf.mxl.sfkt"
        const val STATUS_VPN_SERVICE_ACTION_NAME = "$PKG_NAME.VpnStatus"
        const val STOP_VPN_SERVICE_ACTION_NAME = "$PKG_NAME.VpnStop"
        const val START_VPN_SERVICE_ACTION_NAME = "$PKG_NAME.VpnStart"
        private const val VPN_SERVICE_NOTIFICATION_ID = 1
        private const val OPEN_MAIN_ACTIVITY_ACTION_ID = 2
        private const val STOP_VPN_SERVICE_ACTION_ID = 3

        private const val EXTRA_SERVER_URL = "server_url"
        private const val EXTRA_SERVER_NAME = "server_name"

        fun status(context: Context) {
            Intent(context, SfktVpnService::class.java).also {
                it.action = STATUS_VPN_SERVICE_ACTION_NAME
                context.startService(it)
            }
        }

        fun stop(context: Context) {
            Intent(context, SfktVpnService::class.java).also {
                it.action = STOP_VPN_SERVICE_ACTION_NAME
                context.startService(it)
            }
        }

        fun start(context: Context, server: Server) {
            if (prepare(context) != null) {
                Log.e("SfktVpnService", "Can't start: VpnService#prepare(): needs user permission")
                return
            }
            Intent(context, SfktVpnService::class.java).also {
                it.action = START_VPN_SERVICE_ACTION_NAME
                it.putExtra(EXTRA_SERVER_URL, server.originalUrl)
                it.putExtra(EXTRA_SERVER_NAME, server.name)
                context.startForegroundService(it)
            }
        }
    }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val settings by lazy { Settings(applicationContext) }

    private var isRunning: Boolean = false
    private var tunDevice: ParcelFileDescriptor? = null
    private var toast: Toast? = null
    private var connectionStartTime: Long = 0
    private var currentServerName: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_VPN_SERVICE_ACTION_NAME -> {
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: return START_NOT_STICKY
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Server"
                start(serverUrl, serverName)
            }
            STOP_VPN_SERVICE_ACTION_NAME -> stopVPN()
            STATUS_VPN_SERVICE_ACTION_NAME -> broadcastStatus()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVPN()
    }

    override fun onDestroy() {
        toast = null
        super.onDestroy()
    }

    private fun start(serverUrl: String, serverName: String) {
        val config = generateConfig(serverUrl)
        if (config == null) {
            showToast("Invalid server configuration")
            // Must call startForeground before stopping when started via startForegroundService
            startForeground(VPN_SERVICE_NOTIFICATION_ID, createErrorNotification())
            broadcastStop()
            stopSelf()
            return
        }

        // Save last server for Quick Settings Tile
        settings.lastServerUrl = serverUrl
        settings.lastServerName = serverName
        currentServerName = serverName

        startXray(config)
        startTun(serverName)
    }

    private fun generateConfig(serverUrl: String): String? {
        val linkHelper = LinkHelper(settings, serverUrl)
        if (!linkHelper.isValid()) {
            return null
        }

        val configJson = linkHelper.json()
        val configFile = settings.xrayConfig()
        FileHelper.createOrUpdate(configFile, configJson)

        // Test config
        val error = XrayCore.test(filesDir.absolutePath, configFile.absolutePath)
        if (error.isNotEmpty()) {
            Log.e("SfktVpnService", "Config test failed: $error")
            showToast("Config error: $error")
            return null
        }

        return configFile.absolutePath
    }

    private fun startXray(configPath: String) {
        val error = XrayCore.start(filesDir.absolutePath, configPath)
        if (error.isNotEmpty()) {
            Log.e("SfktVpnService", "XrayCore start failed: $error")
            showToast("Start error: $error")
        }
    }

    private fun startTun(serverName: String) {
        // Load native library
        if (!TProxyService.loadLibrary()) {
            showToast("Failed to load tun2socks library")
            XrayCore.stop()
            startForeground(VPN_SERVICE_NOTIFICATION_ID, createErrorNotification())
            broadcastStop()
            stopSelf()
            return
        }

        val tun = Builder()

        // Basic config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tun.setMetered(false)
        }
        tun.setMtu(settings.tunMtu)
        tun.setSession(getString(R.string.app_name))

        // IPv4
        tun.addAddress(settings.tunAddress, settings.tunPrefix)
        tun.addDnsServer(settings.primaryDns)
        tun.addDnsServer(settings.secondaryDns)
        tun.addRoute("0.0.0.0", 0)

        // Exclude self
        tun.addDisallowedApplication(packageName)

        // Split Tunneling - exclude selected apps
        settings.excludedApps.forEach { packageName ->
            try {
                tun.addDisallowedApplication(packageName)
            } catch (_: Exception) {}
        }

        // Build TUN device
        tunDevice = tun.establish()
        if (tunDevice == null) {
            Log.e("SfktVpnService", "tun#establish failed")
            XrayCore.stop()
            return
        }

        // Create tun2socks config
        val tun2socksConfig = """
            tunnel:
              name: ${getString(R.string.app_name)}
              mtu: ${settings.tunMtu}
            socks5:
              address: ${settings.socksAddress}
              port: ${settings.socksPort}
              udp: udp
        """.trimIndent()

        FileHelper.createOrUpdate(settings.tun2socksConfig(), tun2socksConfig)

        // Start tun2socks
        TProxyService.TProxyStartService(settings.tun2socksConfig().absolutePath, tunDevice!!.fd)

        // Show notification
        connectionStartTime = System.currentTimeMillis()
        startForeground(VPN_SERVICE_NOTIFICATION_ID, createNotification(serverName))

        // Broadcast start
        isRunning = true
        showToast("VPN Connected")
        broadcastStart(serverName)
    }

    fun getConnectionDuration(): Long {
        return if (isRunning && connectionStartTime > 0) {
            System.currentTimeMillis() - connectionStartTime
        } else 0
    }

    fun isVpnRunning(): Boolean = isRunning

    private fun stopVPN() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { tunDevice?.close() }
        tunDevice = null

        XrayCore.stop()

        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false
        showToast("VPN Disconnected")
        broadcastStop()
        stopSelf()
    }

    private fun broadcastStart(serverName: String) {
        Intent(START_VPN_SERVICE_ACTION_NAME).also {
            it.`package` = PKG_NAME
            it.putExtra("server", serverName)
            sendBroadcast(it)
        }
        VpnTileService.requestUpdate(this)
    }

    private fun broadcastStop() {
        Intent(STOP_VPN_SERVICE_ACTION_NAME).also {
            it.`package` = PKG_NAME
            sendBroadcast(it)
        }
        VpnTileService.requestUpdate(this)
    }

    private fun broadcastStatus() {
        Intent(STATUS_VPN_SERVICE_ACTION_NAME).also {
            it.`package` = PKG_NAME
            it.putExtra("isRunning", isRunning)
            sendBroadcast(it)
        }
    }

    private fun createNotification(serverName: String): Notification {
        val pendingActivity = PendingIntent.getActivity(
            applicationContext,
            OPEN_MAIN_ACTIVITY_ACTION_ID,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val pendingStop = PendingIntent.getService(
            applicationContext,
            STOP_VPN_SERVICE_ACTION_ID,
            Intent(applicationContext, SfktVpnService::class.java).also {
                it.action = STOP_VPN_SERVICE_ACTION_NAME
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat
            .Builder(applicationContext, createNotificationChannel())
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(serverName)
            .setSubText(getString(R.string.vpn_notification_tap_to_open))
            .setContentIntent(pendingActivity)
            .addAction(
                R.drawable.ic_power,
                getString(R.string.vpn_stop),
                pendingStop
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$serverName\n${getString(R.string.vpn_notification_protected)}"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(connectionStartTime)
            .setUsesChronometer(true)
            .build()
    }

    private fun createErrorNotification(): Notification {
        return NotificationCompat
            .Builder(applicationContext, createNotificationChannel())
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.error_connection))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel(): String {
        val id = "sfkt_vpn_channel"
        val name = "VPN Service"
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
        return id
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            toast?.cancel()
            toast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).also {
                it.show()
            }
        }
    }
}

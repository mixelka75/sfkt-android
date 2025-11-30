package wtf.mxl.sfkt.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.data.database.Server

class VpnTileService : TileService() {

    companion object {
        private const val TAG = "VpnTileService"

        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, VpnTileService::class.java))
        }
    }

    private val settings by lazy { Settings(applicationContext) }
    private var isVpnRunning = false
    private var receiverRegistered = false

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast: ${intent?.action}")
            when (intent?.action) {
                SfktVpnService.START_VPN_SERVICE_ACTION_NAME -> {
                    isVpnRunning = true
                    updateTile()
                }
                SfktVpnService.STOP_VPN_SERVICE_ACTION_NAME -> {
                    isVpnRunning = false
                    updateTile()
                }
                SfktVpnService.STATUS_VPN_SERVICE_ACTION_NAME -> {
                    isVpnRunning = intent.getBooleanExtra("isRunning", false)
                    updateTile()
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        registerReceiver()
        // Check current VPN status
        isVpnRunning = isVpnActive()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
        unregisterReceiverSafe()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick, isVpnRunning=$isVpnRunning")

        if (isVpnRunning) {
            // Stop VPN
            SfktVpnService.stop(this)
            isVpnRunning = false
            updateTile()
        } else {
            // Start VPN with last server
            val serverUrl = settings.lastServerUrl
            val serverName = settings.lastServerName

            if (serverUrl.isBlank()) {
                showToast(getString(R.string.tile_no_server))
                return
            }

            // Check VPN permission
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Need to request permission - open app
                showToast(getString(R.string.tile_no_server))
                return
            }

            // Create server object and start
            val server = Server(
                id = 0,
                name = serverName,
                host = "",
                port = 0,
                uuid = "",
                originalUrl = serverUrl
            )
            SfktVpnService.start(this, server)
            isVpnRunning = true
            updateTile()
        }
    }

    private fun isVpnActive(): Boolean {
        // Check if VPN interface exists
        return try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.any {
                it.name.startsWith("tun") || it.name.startsWith("ppp")
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(SfktVpnService.START_VPN_SERVICE_ACTION_NAME)
            addAction(SfktVpnService.STOP_VPN_SERVICE_ACTION_NAME)
            addAction(SfktVpnService.STATUS_VPN_SERVICE_ACTION_NAME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(vpnReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterReceiverSafe() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(vpnReceiver)
            receiverRegistered = false
        } catch (_: Exception) {}
    }

    private fun updateTile() {
        val tile = qsTile ?: return

        if (isVpnRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = settings.lastServerName.ifBlank { getString(R.string.tile_vpn_name) }
            tile.subtitle = getString(R.string.connected)
            tile.icon = Icon.createWithResource(this, R.drawable.baseline_vpn_lock)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_vpn_name)
            tile.subtitle = getString(R.string.disconnected)
            tile.icon = Icon.createWithResource(this, R.drawable.baseline_vpn_key)
        }

        tile.updateTile()
        Log.d(TAG, "Tile updated: state=${tile.state}, label=${tile.label}")
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}

package wtf.mxl.sfkt.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import wtf.mxl.sfkt.Settings
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionMonitor(
    private val context: Context,
    private val onConnectionLost: () -> Unit,
    private val onAllAttemptsFailed: () -> Unit
) {
    companion object {
        private const val TAG = "ConnectionMonitor"
        private const val CHECK_HOST = "1.1.1.1"
        private const val CHECK_PORT = 53
        private const val SOCKET_TIMEOUT = 5000
    }

    private val settings = Settings(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var monitorJob: Job? = null
    private var consecutiveFailures = 0
    private var isMonitoring = false

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    enum class ConnectionState {
        CONNECTED,
        CHECKING,
        RECONNECTING,
        NO_INTERNET
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        consecutiveFailures = 0
        _connectionState.value = ConnectionState.CONNECTED

        Log.d(TAG, "Starting connection monitoring every ${settings.failoverCheckIntervalSeconds}s")

        monitorJob = scope.launch {
            while (isActive) {
                delay(settings.failoverCheckIntervalSeconds * 1000L)

                if (!settings.autoFailoverEnabled) continue

                _connectionState.value = ConnectionState.CHECKING
                val hasInternet = checkInternetConnectivity()

                if (!hasInternet) {
                    consecutiveFailures++
                    Log.w(TAG, "Internet check failed. Consecutive failures: $consecutiveFailures/${settings.maxFailoverAttempts}")

                    if (consecutiveFailures >= settings.maxFailoverAttempts) {
                        _connectionState.value = ConnectionState.NO_INTERNET
                        Log.e(TAG, "Max failover attempts reached, notifying user")
                        withContext(Dispatchers.Main) {
                            onAllAttemptsFailed()
                        }
                        consecutiveFailures = 0
                    } else {
                        _connectionState.value = ConnectionState.RECONNECTING
                        Log.i(TAG, "Attempting failover to preferred server")
                        withContext(Dispatchers.Main) {
                            onConnectionLost()
                        }
                    }
                } else {
                    if (consecutiveFailures > 0) {
                        Log.d(TAG, "Connection restored after $consecutiveFailures failures")
                    }
                    consecutiveFailures = 0
                    _connectionState.value = ConnectionState.CONNECTED
                }
            }
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping connection monitoring")
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        consecutiveFailures = 0
    }

    fun resetFailureCount() {
        consecutiveFailures = 0
        _connectionState.value = ConnectionState.CONNECTED
    }

    private fun checkInternetConnectivity(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(CHECK_HOST, CHECK_PORT), SOCKET_TIMEOUT)
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Socket check failed: ${e.message}")
            checkWithConnectivityManager()
        }
    }

    private fun checkWithConnectivityManager(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}

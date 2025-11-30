package wtf.mxl.sfkt.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.SfktApp
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.data.network.SubscriptionInfo
import wtf.mxl.sfkt.data.network.SubscriptionService
import wtf.mxl.sfkt.util.PingUtil

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)
    private val serverRepository = (application as SfktApp).serverRepository
    private val subscriptionService = SubscriptionService()

    val servers = serverRepository.allServers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState

    private val _selectedServerId = MutableStateFlow(settings.selectedServerId)
    val selectedServerId: StateFlow<Long> = _selectedServerId

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _subscriptionInfo = MutableStateFlow<SubscriptionInfo?>(null)
    val subscriptionInfo: StateFlow<SubscriptionInfo?> = _subscriptionInfo

    fun refreshSubscriptionInfo() {
        if (settings.subscriptionUrl.isBlank()) return

        viewModelScope.launch {
            subscriptionService.fetchSubscriptionInfo(settings.subscriptionUrl)
                .onSuccess { info ->
                    _subscriptionInfo.value = info
                }
                .onFailure { e ->
                    // Silently fail, subscription info is optional
                }
        }
    }

    fun refreshSubscription() {
        if (settings.subscriptionUrl.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true

            subscriptionService.fetchServers(settings.subscriptionUrl)
                .onSuccess { newServers ->
                    serverRepository.replaceAll(newServers)
                    settings.lastSubscriptionUpdate = System.currentTimeMillis()
                    measureAllPings()
                }
                .onFailure { e ->
                    _error.value = e.message
                }

            _isLoading.value = false

            // Also refresh subscription info
            refreshSubscriptionInfo()
        }
    }

    fun selectServer(server: Server) {
        settings.selectedServerId = server.id
        _selectedServerId.value = server.id
    }

    fun getSelectedServer(): Server? {
        return viewModelScope.let {
            var result: Server? = null
            viewModelScope.launch {
                result = serverRepository.getById(settings.selectedServerId)
            }
            result
        }
    }

    suspend fun getSelectedServerBlocking(): Server? {
        return serverRepository.getById(settings.selectedServerId)
            ?: servers.first().firstOrNull()
    }

    fun setVpnState(state: VpnState) {
        _vpnState.value = state
    }

    fun clearError() {
        _error.value = null
    }

    fun measureAllPings() {
        viewModelScope.launch {
            servers.first().forEach { server ->
                launch {
                    val ping = PingUtil.measurePing(server.host, server.port)
                    serverRepository.updatePing(server.id, ping)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settings.subscriptionUrl = ""
            settings.selectedServerId = -1
            settings.isFirstLaunch = true
            serverRepository.deleteAll()
        }
    }

    enum class VpnState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}

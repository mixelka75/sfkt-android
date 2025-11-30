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

    private val _preferredServerIds = MutableStateFlow(settings.preferredServerIds)
    val preferredServerIds: StateFlow<Set<Long>> = _preferredServerIds

    private val _failoverEvent = MutableStateFlow<FailoverEvent?>(null)
    val failoverEvent: StateFlow<FailoverEvent?> = _failoverEvent

    private var currentFailoverIndex = 0

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

    // Preferred Servers Management
    fun togglePreferredServer(serverId: Long) {
        val current = settings.preferredServerIds.toMutableSet()
        if (current.contains(serverId)) {
            current.remove(serverId)
        } else {
            current.add(serverId)
        }
        settings.preferredServerIds = current
        _preferredServerIds.value = current
    }

    fun setPreferredServers(serverIds: Set<Long>) {
        settings.preferredServerIds = serverIds
        _preferredServerIds.value = serverIds
    }

    fun isServerPreferred(serverId: Long): Boolean {
        return _preferredServerIds.value.contains(serverId)
    }

    suspend fun getPreferredServers(): List<Server> {
        val allServers = servers.first()
        val preferredIds = _preferredServerIds.value
        return if (preferredIds.isEmpty()) {
            allServers
        } else {
            allServers.filter { it.id in preferredIds }
        }
    }

    // Failover Logic
    fun getNextFailoverServer(): Server? {
        var result: Server? = null
        viewModelScope.launch {
            result = getNextFailoverServerBlocking()
        }
        return result
    }

    suspend fun getNextFailoverServerBlocking(): Server? {
        val preferredServers = getPreferredServers()
        if (preferredServers.isEmpty()) return null

        val currentServerId = _selectedServerId.value
        val currentIndex = preferredServers.indexOfFirst { it.id == currentServerId }

        // Get next server in the list (circular)
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + 1) % preferredServers.size
        }

        val nextServer = preferredServers.getOrNull(nextIndex)

        // If we've cycled through all servers, return null (all failed)
        if (nextServer?.id == currentServerId && preferredServers.size > 1) {
            currentFailoverIndex++
            if (currentFailoverIndex >= preferredServers.size) {
                currentFailoverIndex = 0
                return null
            }
        }

        return nextServer
    }

    fun resetFailoverIndex() {
        currentFailoverIndex = 0
    }

    fun emitFailoverEvent(event: FailoverEvent) {
        _failoverEvent.value = event
    }

    fun clearFailoverEvent() {
        _failoverEvent.value = null
    }

    enum class VpnState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    sealed class FailoverEvent {
        data class SwitchingServer(val serverName: String) : FailoverEvent()
        object NoInternetWarning : FailoverEvent()
        data class FailoverSuccess(val serverName: String) : FailoverEvent()
    }
}

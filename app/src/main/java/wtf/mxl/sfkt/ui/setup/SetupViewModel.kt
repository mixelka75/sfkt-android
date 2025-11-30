package wtf.mxl.sfkt.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.data.network.SubscriptionService
import wtf.mxl.sfkt.data.repository.ServerRepository

class SetupViewModel(
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val subscriptionService = SubscriptionService()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success

    fun fetchServers(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            subscriptionService.fetchServers(url)
                .onSuccess { servers ->
                    serverRepository.replaceAll(servers)
                    _success.value = true
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Unknown error"
                }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(private val serverRepository: ServerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(serverRepository) as T
        }
    }
}

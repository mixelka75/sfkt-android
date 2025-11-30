package wtf.mxl.sfkt.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.databinding.FragmentServersBinding
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.service.SfktVpnService

class ServersFragment : Fragment() {

    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settings: Settings
    private lateinit var serverAdapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())

        setupUI()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        serverAdapter = ServerAdapter { server ->
            val wasConnected = viewModel.vpnState.value == MainViewModel.VpnState.CONNECTED
            val previousServerId = viewModel.selectedServerId.value

            viewModel.selectServer(server)
            serverAdapter.setSelectedServer(server.id)

            // Auto-reconnect if connected and different server selected
            if (wasConnected && previousServerId != server.id) {
                reconnectToServer(server)
            }
        }

        binding.rvServers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serverAdapter
            isNestedScrollingEnabled = false
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshSubscription()
        }

        binding.btnRefreshServers.setOnClickListener {
            viewModel.refreshSubscription()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.servers.collect { servers ->
                serverAdapter.submitList(servers) {
                    // Run layout animation after list is updated
                    binding.rvServers.scheduleLayoutAnimation()
                }

                if (servers.isNotEmpty()) {
                    // If VPN is connected, select the active server by lastServerUrl
                    if (viewModel.vpnState.value == MainViewModel.VpnState.CONNECTED) {
                        val activeServer = servers.find { it.originalUrl == settings.lastServerUrl }
                        if (activeServer != null) {
                            viewModel.selectServer(activeServer)
                        }
                    }
                    // Auto-select first server if none selected
                    else if (viewModel.selectedServerId.value == -1L) {
                        viewModel.selectServer(servers.first())
                    }
                    // Validate that selected server still exists
                    else {
                        val selectedExists = servers.any { it.id == viewModel.selectedServerId.value }
                        if (!selectedExists) {
                            viewModel.selectServer(servers.first())
                        }
                    }
                }

                serverAdapter.setSelectedServer(viewModel.selectedServerId.value)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedServerId.collect { serverId ->
                serverAdapter.setSelectedServer(serverId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun reconnectToServer(server: Server) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setVpnState(MainViewModel.VpnState.CONNECTING)

            // Stop current connection
            SfktVpnService.stop(requireContext())

            // Small delay to ensure clean disconnect
            kotlinx.coroutines.delay(500)

            // Start new connection
            SfktVpnService.start(requireContext(), server)
        }
    }
}

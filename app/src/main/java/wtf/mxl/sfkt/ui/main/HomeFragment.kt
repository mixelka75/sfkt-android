package wtf.mxl.sfkt.ui.main

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.databinding.FragmentHomeBinding
import wtf.mxl.sfkt.service.ConnectionMonitor
import wtf.mxl.sfkt.service.SfktVpnService
import wtf.mxl.sfkt.ui.settings.AppsRoutingActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settings: Settings

    private var connectionMonitor: ConnectionMonitor? = null
    private var isFailoverInProgress = false

    companion object {
        private const val FAILOVER_NOTIFICATION_CHANNEL_ID = "sfkt_failover_channel"
        private const val FAILOVER_NOTIFICATION_ID = 100
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpn()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled, continue regardless
    }

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SfktVpnService.START_VPN_SERVICE_ACTION_NAME -> {
                    viewModel.setVpnState(MainViewModel.VpnState.CONNECTED)
                    updateUI(MainViewModel.VpnState.CONNECTED)
                    syncSelectedServerWithActive()
                    startConnectionMonitoring()

                    if (isFailoverInProgress) {
                        isFailoverInProgress = false
                        viewModel.resetFailoverIndex()
                        connectionMonitor?.resetFailureCount()
                        val serverName = intent.getStringExtra("server") ?: ""
                        showSnackbar(getString(R.string.failover_success, serverName))
                    }
                }
                SfktVpnService.STOP_VPN_SERVICE_ACTION_NAME -> {
                    viewModel.setVpnState(MainViewModel.VpnState.DISCONNECTED)
                    updateUI(MainViewModel.VpnState.DISCONNECTED)
                    stopConnectionMonitoring()
                }
                SfktVpnService.STATUS_VPN_SERVICE_ACTION_NAME -> {
                    val isRunning = intent.getBooleanExtra("isRunning", false)
                    val state = if (isRunning) MainViewModel.VpnState.CONNECTED else MainViewModel.VpnState.DISCONNECTED
                    viewModel.setVpnState(state)
                    updateUI(state)
                    if (isRunning) {
                        syncSelectedServerWithActive()
                        startConnectionMonitoring()
                    } else {
                        stopConnectionMonitoring()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())

        setupUI()
        observeViewModel()
        registerVpnReceiver()
        animateEntrance()
        requestNotificationPermission()

        // Request VPN status
        SfktVpnService.status(requireContext())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopConnectionMonitoring()
        try {
            requireContext().unregisterReceiver(vpnReceiver)
        } catch (_: Exception) {}
        _binding = null
    }

    private fun setupUI() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshSubscription()
        }

        binding.btnConnect.setOnClickListener {
            animateConnectButton()
            when (viewModel.vpnState.value) {
                MainViewModel.VpnState.DISCONNECTED -> requestVpnPermission()
                MainViewModel.VpnState.CONNECTED -> stopVpn()
                MainViewModel.VpnState.CONNECTING -> {} // Do nothing
            }
        }

        binding.btnSplitTunneling.setOnClickListener {
            startActivity(Intent(requireContext(), AppsRoutingActivity::class.java))
        }

        binding.btnManageSubscription.setOnClickListener {
            openTelegramBot()
        }

        binding.btnPreferredServers.setOnClickListener {
            showPreferredServersBottomSheet()
        }
    }

    private fun showPreferredServersBottomSheet() {
        PreferredServersBottomSheet.newInstance()
            .show(childFragmentManager, PreferredServersBottomSheet.TAG)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.subscriptionInfo.collect { info ->
                info?.let {
                    updateSubscriptionInfo(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.servers.collect { servers ->
                if (servers.isNotEmpty()) {
                    // Update current server card
                    val selectedServer = servers.find { it.id == viewModel.selectedServerId.value }
                    updateCurrentServerCard(selectedServer)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedServerId.collect { serverId ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val servers = viewModel.servers.first()
                    val selectedServer = servers.find { it.id == serverId }
                    updateCurrentServerCard(selectedServer)
                }
            }
        }
    }

    private fun registerVpnReceiver() {
        val filter = IntentFilter().apply {
            addAction(SfktVpnService.START_VPN_SERVICE_ACTION_NAME)
            addAction(SfktVpnService.STOP_VPN_SERVICE_ACTION_NAME)
            addAction(SfktVpnService.STATUS_VPN_SERVICE_ACTION_NAME)
        }
        ContextCompat.registerReceiver(
            requireContext(),
            vpnReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun syncSelectedServerWithActive() {
        viewLifecycleOwner.lifecycleScope.launch {
            val lastUrl = settings.lastServerUrl
            if (lastUrl.isNotBlank()) {
                viewModel.servers.first().find { it.originalUrl == lastUrl }?.let { server ->
                    viewModel.selectServer(server)
                    updateCurrentServerCard(server)
                }
            }
        }
    }

    private fun updateCurrentServerCard(server: wtf.mxl.sfkt.data.database.Server?) {
        if (server != null) {
            binding.tvCurrentServer.text = server.name
            binding.currentServerCard.visibility = View.VISIBLE
        } else {
            binding.currentServerCard.visibility = View.GONE
        }
    }

    private fun updateSubscriptionInfo(info: wtf.mxl.sfkt.data.network.SubscriptionInfo) {
        binding.subscriptionCard.visibility = View.VISIBLE
        binding.tvSubscriptionDays.text = info.getDaysLeftText()
        binding.premiumBadge.visibility = if (info.isPremium) View.VISIBLE else View.GONE

        if (info.isPremium) {
            binding.trafficSection.visibility = View.GONE
        } else {
            binding.trafficSection.visibility = View.VISIBLE
            binding.tvTrafficUsed.text = info.getUsedTrafficFormatted()
            binding.tvTrafficLimit.text = "/ ${info.getLimitTrafficFormatted()}"
            binding.progressTraffic.progress = info.getTrafficPercentage()
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        viewLifecycleOwner.lifecycleScope.launch {
            val server = viewModel.getSelectedServerBlocking()
            if (server == null) {
                Toast.makeText(requireContext(), R.string.error_no_servers, Toast.LENGTH_SHORT).show()
                return@launch
            }

            viewModel.setVpnState(MainViewModel.VpnState.CONNECTING)
            updateUI(MainViewModel.VpnState.CONNECTING)

            SfktVpnService.start(requireContext(), server)
        }
    }

    private fun stopVpn() {
        SfktVpnService.stop(requireContext())
    }

    private fun updateUI(state: MainViewModel.VpnState) {
        when (state) {
            MainViewModel.VpnState.DISCONNECTED -> {
                binding.tvStatus.text = getString(R.string.disconnected)
                binding.tvStatus.setTextColor(
                    MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0)
                )
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0)
                )
                binding.btnConnect.setImageResource(R.drawable.ic_power)
                binding.btnConnect.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, 0)
                )
                binding.btnConnect.isEnabled = true
                binding.tvConnectionTime.visibility = View.GONE
                updateRingColor(ContextCompat.getColor(requireContext(), R.color.ring_disconnected))
                stopPulseAnimation()
            }
            MainViewModel.VpnState.CONNECTING -> {
                binding.tvStatus.text = getString(R.string.connecting)
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connecting))
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.connect_button_connecting)
                )
                binding.btnConnect.isEnabled = false
                updateRingColor(ContextCompat.getColor(requireContext(), R.color.ring_connecting))
                startPulseAnimation()
            }
            MainViewModel.VpnState.CONNECTED -> {
                binding.tvStatus.text = getString(R.string.connected)
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connected))
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.connect_button_connected)
                )
                binding.btnConnect.setImageResource(R.drawable.ic_power)
                binding.btnConnect.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                binding.btnConnect.isEnabled = true
                binding.tvConnectionTime.visibility = View.VISIBLE
                updateRingColor(ContextCompat.getColor(requireContext(), R.color.ring_connected))
                stopPulseAnimation()
            }
        }
    }

    private fun updateRingColor(color: Int) {
        binding.connectRing.backgroundTintList = ColorStateList.valueOf(color)
        binding.connectRingOuter.backgroundTintList = ColorStateList.valueOf(color)
    }

    private var pulseAnimator: ObjectAnimator? = null
    private var pulseAnimatorOuter: ObjectAnimator? = null

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimatorOuter?.cancel()

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f, 1f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.connectRing, scaleX, scaleY).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val scaleXOuter = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f)
        val scaleYOuter = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)

        pulseAnimatorOuter = ObjectAnimator.ofPropertyValuesHolder(binding.connectRingOuter, scaleXOuter, scaleYOuter).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 100
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimatorOuter?.cancel()
        binding.connectRing.scaleX = 1f
        binding.connectRing.scaleY = 1f
        binding.connectRingOuter.scaleX = 1f
        binding.connectRingOuter.scaleY = 1f
    }

    private fun openTelegramBot() {
        try {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("tg://resolve?domain=safekittyvpn_bot&start=premium")
            }
            startActivity(deepLinkIntent)
        } catch (e: Exception) {
            try {
                val webLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://t.me/sfkt_vpn_bot?start=premium")
                }
                startActivity(webLinkIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animateEntrance() {
        // Prepare views for animation
        binding.btnConnect.alpha = 0f
        binding.btnConnect.scaleX = 0.5f
        binding.btnConnect.scaleY = 0.5f
        binding.connectRing.alpha = 0f
        binding.connectRing.scaleX = 0.3f
        binding.connectRing.scaleY = 0.3f
        binding.connectRingOuter.alpha = 0f
        binding.connectRingOuter.scaleX = 0.2f
        binding.connectRingOuter.scaleY = 0.2f
        binding.tvStatus.alpha = 0f
        binding.tvStatus.translationY = 30f
        binding.subscriptionCard.alpha = 0f
        binding.subscriptionCard.translationY = 50f
        binding.btnPreferredServers.alpha = 0f
        binding.btnPreferredServers.translationY = 50f
        binding.btnSplitTunneling.alpha = 0f
        binding.btnSplitTunneling.translationY = 50f

        // Animate outer ring
        binding.connectRingOuter.animate()
            .alpha(0.3f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.5f))
            .setStartDelay(100)
            .start()

        // Animate inner ring
        binding.connectRing.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.5f))
            .setStartDelay(150)
            .start()

        // Animate connect button with bounce
        binding.btnConnect.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2f))
            .setStartDelay(200)
            .start()

        // Animate status text
        binding.tvStatus.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(350)
            .start()

        // Animate subscription card
        binding.subscriptionCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(450)
            .start()

        // Animate preferred servers button
        binding.btnPreferredServers.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(500)
            .start()

        // Animate split tunneling button
        binding.btnSplitTunneling.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(550)
            .start()
    }

    private fun animateConnectButton() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f, 1.05f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1.05f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(binding.btnConnect, scaleX, scaleY).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // Connection Monitoring & Failover
    private fun startConnectionMonitoring() {
        if (!settings.autoFailoverEnabled) return
        if (connectionMonitor != null) return

        connectionMonitor = ConnectionMonitor(
            context = requireContext(),
            onConnectionLost = { performFailover() },
            onAllAttemptsFailed = { showNoInternetNotification() }
        )
        connectionMonitor?.startMonitoring()
    }

    private fun stopConnectionMonitoring() {
        connectionMonitor?.stopMonitoring()
        connectionMonitor?.destroy()
        connectionMonitor = null
    }

    private fun performFailover() {
        if (isFailoverInProgress) return
        isFailoverInProgress = true

        // Stop monitoring during failover
        connectionMonitor?.stopMonitoring()

        viewLifecycleOwner.lifecycleScope.launch {
            val nextServer = viewModel.getNextFailoverServerBlocking()
            if (nextServer == null) {
                isFailoverInProgress = false
                connectionMonitor?.startMonitoring()
                return@launch
            }

            showSnackbar(getString(R.string.switching_to_server, nextServer.name))

            // Set connecting state
            viewModel.setVpnState(MainViewModel.VpnState.CONNECTING)
            updateUI(MainViewModel.VpnState.CONNECTING)

            // Select new server
            viewModel.selectServer(nextServer)

            // Stop current connection
            SfktVpnService.stop(requireContext())

            // Wait for clean disconnect
            kotlinx.coroutines.delay(800)

            // Start new connection
            SfktVpnService.start(requireContext(), nextServer)
        }
    }

    private fun showNoInternetNotification() {
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        val channel = NotificationChannel(
            FAILOVER_NOTIFICATION_CHANNEL_ID,
            getString(R.string.auto_failover),
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(requireContext(), FAILOVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.no_internet_warning))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(FAILOVER_NOTIFICATION_ID, notification)

        showSnackbar(getString(R.string.no_internet_warning))
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}

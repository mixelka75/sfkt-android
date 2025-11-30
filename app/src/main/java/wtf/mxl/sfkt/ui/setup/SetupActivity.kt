package wtf.mxl.sfkt.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.SfktApp
import wtf.mxl.sfkt.databinding.ActivitySetupBinding
import wtf.mxl.sfkt.ui.main.MainActivity

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var settings: Settings

    private val viewModel: SetupViewModel by viewModels {
        SetupViewModel.Factory((application as SfktApp).serverRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)

        // Check if already configured
        if (!settings.isFirstLaunch && settings.subscriptionUrl.isNotBlank()) {
            navigateToMain()
            return
        }

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnGetCredentials.setOnClickListener {
            openTelegramBot()
        }

        binding.btnGetServers.setOnClickListener {
            val url = binding.etSubscription.text?.toString()?.trim() ?: ""
            if (url.isBlank()) {
                binding.subscriptionLayout.error = getString(wtf.mxl.sfkt.R.string.error_invalid_url)
                return@setOnClickListener
            }
            binding.subscriptionLayout.error = null
            viewModel.fetchServers(url)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnGetServers.isEnabled = !isLoading
                binding.etSubscription.isEnabled = !isLoading
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@SetupActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.success.collect { success ->
                if (success) {
                    settings.subscriptionUrl = binding.etSubscription.text?.toString()?.trim() ?: ""
                    settings.isFirstLaunch = false
                    navigateToMain()
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openTelegramBot() {
        // Try deeplink first (opens Telegram app directly)
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=safekittyvpn_bot&start=fromsub"))
        try {
            if (deepLinkIntent.resolveActivity(packageManager) != null) {
                startActivity(deepLinkIntent)
            } else {
                // Fallback to https if Telegram is not installed
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/safekittyvpn_bot?start=fromsub"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            // Fallback to https on any error
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/safekittyvpn_bot?start=fromsub"))
            startActivity(webIntent)
        }
    }
}

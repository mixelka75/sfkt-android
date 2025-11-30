package wtf.mxl.sfkt.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.databinding.ActivityMainBinding
import wtf.mxl.sfkt.ui.setup.SetupActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val viewModel: MainViewModel by viewModels()

    private val homeFragment = HomeFragment()
    private val serversFragment = ServersFragment()
    private var activeFragment: Fragment = homeFragment
    private var currentFragmentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)

        // Check if setup is needed
        if (settings.isFirstLaunch || settings.subscriptionUrl.isBlank()) {
            navigateToSetup()
            return
        }

        setupToolbar()
        setupFragments()
        setupBottomNavigation()

        // Fix bottom navigation padding
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        // Refresh servers if not connected
        if (viewModel.vpnState.value == MainViewModel.VpnState.DISCONNECTED) {
            viewModel.refreshSubscription()
        }

        // Always load subscription info on start
        viewModel.refreshSubscriptionInfo()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_logout -> {
                    viewModel.logout()
                    navigateToSetup()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.navHostFragment, homeFragment, "home")
            add(R.id.navHostFragment, serversFragment, "servers")
            hide(serversFragment)
        }.commit()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    showFragment(homeFragment, 0)
                    true
                }
                R.id.navigation_servers -> {
                    showFragment(serversFragment, 1)
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment, newIndex: Int) {
        if (fragment != activeFragment) {
            supportFragmentManager.beginTransaction().apply {
                setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                hide(activeFragment)
                show(fragment)
            }.commit()

            activeFragment = fragment
            currentFragmentIndex = newIndex
        }
    }

    private fun navigateToSetup() {
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }
}

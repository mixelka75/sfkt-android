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
import wtf.mxl.sfkt.service.SfktVpnService
import wtf.mxl.sfkt.ui.setup.SetupActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val viewModel: MainViewModel by viewModels()

    private lateinit var homeFragment: HomeFragment
    private lateinit var serversFragment: ServersFragment
    private lateinit var activeFragment: Fragment
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
        setupFragments(savedInstanceState)
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
                    // Stop VPN if connected
                    if (viewModel.vpnState.value == MainViewModel.VpnState.CONNECTED) {
                        SfktVpnService.stop(this)
                    }
                    viewModel.logout()
                    navigateToSetup()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // Restore existing fragments
            homeFragment = supportFragmentManager.findFragmentByTag("home") as? HomeFragment ?: HomeFragment()
            serversFragment = supportFragmentManager.findFragmentByTag("servers") as? ServersFragment ?: ServersFragment()

            // Restore active fragment state
            currentFragmentIndex = savedInstanceState.getInt("currentFragmentIndex", 0)
            activeFragment = if (currentFragmentIndex == 0) homeFragment else serversFragment

            // Ensure correct visibility
            supportFragmentManager.beginTransaction().apply {
                if (currentFragmentIndex == 0) {
                    show(homeFragment)
                    hide(serversFragment)
                } else {
                    hide(homeFragment)
                    show(serversFragment)
                }
            }.commitNowAllowingStateLoss()
        } else {
            // Create new fragments
            homeFragment = HomeFragment()
            serversFragment = ServersFragment()
            activeFragment = homeFragment

            supportFragmentManager.beginTransaction().apply {
                add(R.id.navHostFragment, homeFragment, "home")
                add(R.id.navHostFragment, serversFragment, "servers")
                hide(serversFragment)
            }.commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentFragmentIndex", currentFragmentIndex)
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

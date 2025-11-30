package wtf.mxl.sfkt.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.databinding.ActivityAppsRoutingBinding

class AppsRoutingActivity : AppCompatActivity() {

    private val settings by lazy { Settings(applicationContext) }
    private lateinit var binding: ActivityAppsRoutingBinding
    private lateinit var adapter: AppsRoutingAdapter
    private lateinit var apps: ArrayList<AppInfo>
    private lateinit var filtered: MutableList<AppInfo>
    private lateinit var excludedApps: MutableSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityAppsRoutingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSearch()
        setupFab()
        loadApps()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                search(s?.toString())
            }
        })
    }

    private fun setupFab() {
        binding.fabSave.setOnClickListener {
            saveAndFinish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveAndFinish()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun search(query: String?) {
        val keyword = query?.trim()?.lowercase() ?: ""
        if (keyword.isEmpty()) {
            if (apps.size > filtered.size) {
                filtered.clear()
                filtered.addAll(apps.toMutableList())
                adapter.notifyDataSetChanged()
            }
            return
        }
        val list = ArrayList<AppInfo>()
        apps.forEach {
            if (it.appName.lowercase().contains(keyword) || it.packageName.contains(keyword)) {
                list.add(it)
            }
        }
        filtered.clear()
        filtered.addAll(list.toMutableList())
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        binding.loadingOverlay.visibility = android.view.View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val selected = ArrayList<AppInfo>()
            val unselected = ArrayList<AppInfo>()

            excludedApps = settings.excludedApps.toMutableSet()

            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).forEach {
                val permissions = it.requestedPermissions
                if (permissions == null || !permissions.contains(Manifest.permission.INTERNET)) {
                    return@forEach
                }

                val appIcon = it.applicationInfo!!.loadIcon(packageManager)
                val appName = it.applicationInfo!!.loadLabel(packageManager).toString()
                val packageName = it.packageName

                // Skip our own app
                if (packageName == this@AppsRoutingActivity.packageName) {
                    return@forEach
                }

                val app = AppInfo(appIcon, appName, packageName)
                if (excludedApps.contains(packageName)) {
                    selected.add(app)
                } else {
                    unselected.add(app)
                }
            }

            withContext(Dispatchers.Main) {
                apps = ArrayList(selected + unselected)
                filtered = apps.toMutableList()

                adapter = AppsRoutingAdapter(filtered, excludedApps) { packageName, isExcluded ->
                    if (isExcluded) {
                        excludedApps.add(packageName)
                    } else {
                        excludedApps.remove(packageName)
                    }
                }

                binding.appsList.adapter = adapter
                binding.appsList.layoutManager = LinearLayoutManager(applicationContext)

                binding.loadingOverlay.visibility = android.view.View.GONE
            }
        }
    }

    private fun saveAndFinish() {
        settings.excludedApps = excludedApps
        finish()
    }
}

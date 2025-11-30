package wtf.mxl.sfkt.ui.settings

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import wtf.mxl.sfkt.databinding.ItemAppBinding

data class AppInfo(
    val appIcon: Drawable,
    val appName: String,
    val packageName: String
)

class AppsRoutingAdapter(
    private val apps: List<AppInfo>,
    private val excludedApps: MutableSet<String>,
    private val onAppToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppsRoutingAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.appIcon.setImageDrawable(app.appIcon)
            binding.appName.text = app.appName
            binding.appPackage.text = app.packageName
            binding.checkbox.isChecked = excludedApps.contains(app.packageName)

            binding.root.setOnClickListener {
                binding.checkbox.isChecked = !binding.checkbox.isChecked
                onAppToggle(app.packageName, binding.checkbox.isChecked)
            }

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onAppToggle(app.packageName, isChecked)
            }
        }
    }
}

package wtf.mxl.sfkt.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.databinding.ItemPreferredServerBinding
import wtf.mxl.sfkt.util.PingUtil

class PreferredServerAdapter(
    private val onServerToggled: (Server, Boolean) -> Unit
) : ListAdapter<PreferredServerAdapter.ServerItem, PreferredServerAdapter.ViewHolder>(DiffCallback()) {

    data class ServerItem(
        val server: Server,
        val isPreferred: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPreferredServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPreferredServerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServerItem) {
            val server = item.server
            val context = binding.root.context

            binding.tvServerName.text = server.name
            binding.tvServerHost.text = "${server.host}:${server.port}"
            binding.checkboxServer.isChecked = item.isPreferred

            // Ping chip
            if (server.ping != null) {
                binding.chipPing.text = "${server.ping}ms"
                binding.chipPing.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, PingUtil.getPingColor(server.ping))
                )
            } else {
                binding.chipPing.text = "-"
                binding.chipPing.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.ping_none)
                )
            }
            // Set dark text color for better contrast on colored backgrounds
            binding.chipPing.setTextColor(ContextCompat.getColor(context, R.color.ping_text))

            // Card styling based on selection
            if (item.isPreferred) {
                binding.cardServer.strokeColor = ContextCompat.getColor(context, R.color.preferred_server_stroke)
                binding.cardServer.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.preferred_server_background)
                )
            } else {
                binding.cardServer.strokeColor = ContextCompat.getColor(context, R.color.server_stroke_default)
                binding.cardServer.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.server_background_default)
                )
            }

            // Click handler
            binding.cardServer.setOnClickListener {
                val newState = !binding.checkboxServer.isChecked
                binding.checkboxServer.isChecked = newState
                onServerToggled(server, newState)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ServerItem>() {
        override fun areItemsTheSame(oldItem: ServerItem, newItem: ServerItem): Boolean {
            return oldItem.server.id == newItem.server.id
        }

        override fun areContentsTheSame(oldItem: ServerItem, newItem: ServerItem): Boolean {
            return oldItem == newItem
        }
    }
}

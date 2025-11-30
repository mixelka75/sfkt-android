package wtf.mxl.sfkt.ui.main

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.databinding.ItemServerBinding
import wtf.mxl.sfkt.util.PingUtil

class ServerAdapter(
    private val onServerClick: (Server) -> Unit
) : ListAdapter<Server, ServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    private var selectedServerId: Long = -1

    fun setSelectedServer(serverId: Long) {
        val oldSelectedId = selectedServerId
        selectedServerId = serverId

        // Update old and new selected items
        currentList.forEachIndexed { index, server ->
            if (server.id == oldSelectedId || server.id == serverId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(
        private val binding: ItemServerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(server: Server) {
            val context = binding.root.context
            val isSelected = server.id == selectedServerId

            // Server info
            binding.tvServerName.text = server.name
            binding.tvServerHost.text = "${server.host}:${server.port}"

            // Selected state styling
            if (isSelected) {
                // Selected state
                binding.serverCard.isChecked = true
                binding.serverCard.strokeColor = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorPrimary,
                    0
                )
                binding.serverCard.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_selected)
                binding.serverCard.setCardBackgroundColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorPrimaryContainer,
                        0
                    )
                )
                binding.serverIconBg.backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorPrimary,
                        0
                    )
                )
                binding.serverIcon.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnPrimary,
                        0
                    )
                )
                binding.ivSelected.visibility = View.VISIBLE
            } else {
                // Unselected state
                binding.serverCard.isChecked = false
                binding.serverCard.strokeColor = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOutlineVariant,
                    0
                )
                binding.serverCard.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_default)
                binding.serverCard.setCardBackgroundColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )
                binding.serverIconBg.backgroundTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurfaceContainerHighest,
                        0
                    )
                )
                binding.serverIcon.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0
                    )
                )
                binding.ivSelected.visibility = View.GONE
            }

            // Ping chip
            val pingText = PingUtil.formatPing(server.ping)
            val pingColor = PingUtil.getPingColor(server.ping)
            binding.chipPing.text = pingText
            binding.chipPing.chipBackgroundColor = ColorStateList.valueOf(pingColor)
            binding.chipPing.setTextColor(
                if (server.ping in 1..150) {
                    ContextCompat.getColor(context, R.color.white)
                } else if (server.ping in 151..300) {
                    ContextCompat.getColor(context, R.color.black)
                } else {
                    ContextCompat.getColor(context, R.color.white)
                }
            )

            binding.root.setOnClickListener {
                animateClick(binding.serverCard)
                onServerClick(server)
            }
        }

        private fun animateClick(view: View) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.95f, 1.02f, 1f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.95f, 1.02f, 1f)
            ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
                duration = 250
                interpolator = OvershootInterpolator(2f)
                start()
            }
        }
    }

    class ServerDiffCallback : DiffUtil.ItemCallback<Server>() {
        override fun areItemsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem == newItem
        }
    }
}

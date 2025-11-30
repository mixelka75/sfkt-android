package wtf.mxl.sfkt.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wtf.mxl.sfkt.R
import wtf.mxl.sfkt.Settings
import wtf.mxl.sfkt.databinding.BottomSheetPreferredServersBinding

class PreferredServersBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPreferredServersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settings: Settings
    private lateinit var adapter: PreferredServerAdapter

    private val selectedServerIds = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPreferredServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())

        setupRecyclerView()
        setupUI()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = PreferredServerAdapter { server, isSelected ->
            if (isSelected) {
                selectedServerIds.add(server.id)
            } else {
                selectedServerIds.remove(server.id)
            }
            updateSaveButtonState()
        }

        binding.recyclerServers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PreferredServersBottomSheet.adapter
            itemAnimator = null
        }
    }

    private fun setupUI() {
        binding.switchAutoFailover.isChecked = settings.autoFailoverEnabled
        binding.switchAutoFailover.setOnCheckedChangeListener { _, isChecked ->
            settings.autoFailoverEnabled = isChecked
        }

        binding.btnSave.setOnClickListener {
            savePreferredServers()
        }
    }

    private fun loadData() {
        selectedServerIds.clear()
        selectedServerIds.addAll(viewModel.preferredServerIds.value)

        viewLifecycleOwner.lifecycleScope.launch {
            val servers = viewModel.servers.first()
            val items = servers.map { server ->
                PreferredServerAdapter.ServerItem(
                    server = server,
                    isPreferred = selectedServerIds.contains(server.id)
                )
            }
            adapter.submitList(items)
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val hasSelection = selectedServerIds.isNotEmpty()
        binding.btnSave.text = if (hasSelection) {
            getString(R.string.save_with_count, selectedServerIds.size)
        } else {
            getString(R.string.save)
        }
    }

    private fun savePreferredServers() {
        viewModel.setPreferredServers(selectedServerIds.toSet())

        val message = if (selectedServerIds.isEmpty()) {
            getString(R.string.preferred_servers_cleared)
        } else {
            getString(R.string.preferred_servers_saved, selectedServerIds.size)
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PreferredServersBottomSheet"

        fun newInstance(): PreferredServersBottomSheet {
            return PreferredServersBottomSheet()
        }
    }
}

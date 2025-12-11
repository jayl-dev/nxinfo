package com.jl.nxinfo

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.jl.nxinfo.databinding.FragmentSecondBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private val cheatPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleCheatFile(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSelectCheat.setOnClickListener {
            cheatPickerLauncher.launch("application/zip")
        }

        updateCheatStatus()
    }

    private fun handleCheatFile(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val cheatManager = CheatFileManager.getInstance(requireContext())
                cheatManager.extractCheatFile(uri)
            }

            when (result) {
                is CheatResult.Success -> {
                    val cheatInfo = result.cheatInfo
                    displayCheatInfo(cheatInfo)
                    updateCheatStatus()
                    Snackbar.make(binding.root, "Cheat file loaded successfully", Snackbar.LENGTH_SHORT).show()
                }
                is CheatResult.NotFoundInZip -> {
                    Snackbar.make(binding.root, "Cheat file not found in zip. Expected: xxxx/contents/{titleId}/cheats/{buildId}.txt", Snackbar.LENGTH_LONG).show()
                }
                is CheatResult.InvalidStructure -> {
                    Snackbar.make(binding.root, "Invalid zip structure", Snackbar.LENGTH_LONG).show()
                }
                is CheatResult.Error -> {
                    Snackbar.make(binding.root, "Error: ${result.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayCheatInfo(cheatInfo: CheatInfo) {
        binding.apply {
            cardCheatInfo.visibility = View.VISIBLE
            textviewCheatTitleId.text = cheatInfo.titleId
            textviewCheatBuildId.text = cheatInfo.buildId
            textviewCheatLines.text = cheatInfo.cheats.size.toString()

            val cheatDisplay = cheatInfo.cheats.joinToString("\n\n") { cheat ->
                val header = "[${cheat.name}]"
                val codes = cheat.codes.joinToString("\n")
                if (codes.isNotEmpty()) {
                    "$header\n$codes"
                } else {
                    header
                }
            }
            textviewCheatContent.text = cheatDisplay
        }
    }

    private fun updateCheatStatus() {
        val cheatManager = CheatFileManager.getInstance(requireContext())
        binding.textviewCheatStatus.text = cheatManager.getCheatSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
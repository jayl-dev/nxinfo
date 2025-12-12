package com.jl.nxinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jl.nxinfo.databinding.FragmentSecondBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CheatViewModel by activityViewModels()
    
    private var selectedBuildForExport: BuildCheats? = null

    private val cheatPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleCheatFile(it)
        }
    }
    
    private val exportDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            handleExport(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ):
 View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fab.setOnClickListener {
            cheatPickerLauncher.launch("*/*")
        }

        binding.recyclerviewBuilds.layoutManager = LinearLayoutManager(requireContext())

        binding.textviewGameName.setOnClickListener {
            copyToClipboard("Game Name", binding.textviewGameName.text.toString())
        }

        binding.textviewCheatTitleId.setOnClickListener {
            copyToClipboard("Title ID", binding.textviewCheatTitleId.text.toString())
        }

        binding.textviewFilePath.setOnClickListener {
            copyToClipboard("File Path", binding.textviewFilePath.text.toString())
        }

        viewModel.cheatInfo.observe(viewLifecycleOwner) { cheatInfo ->
            if (cheatInfo != null) {
                displayCheatInfo(cheatInfo)
            } else {
                binding.layoutCheatInfo.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun handleCheatFile(uri: Uri) {
        viewModel.setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val cheatManager = CheatFileManager.getInstance(requireContext())
                cheatManager.extractCheatFile(uri)
            }

            viewModel.setLoading(false)

            when (result) {
                is CheatResult.Success -> {
                    val cheatInfo = result.cheatInfo
                    viewModel.setCheatInfo(cheatInfo)
                }
                is CheatResult.NotFoundInZip -> {
                    Snackbar.make(binding.root, "No valid cheats found.", Snackbar.LENGTH_LONG).show()
                }
                is CheatResult.InvalidStructure -> {
                    Snackbar.make(binding.root, "Invalid file structure", Snackbar.LENGTH_LONG).show()
                }
                is CheatResult.Error -> {
                    Snackbar.make(binding.root, "Error: ${result.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun handleExport(treeUri: Uri) {
        val build = selectedBuildForExport ?: return
        val gameName = viewModel.cheatInfo.value?.gameName ?: viewModel.cheatInfo.value?.titleId ?: "Unknown_Game"
        
        viewModel.setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
             val success = withContext(Dispatchers.IO) {
                 exportCheats(treeUri, gameName, build)
             }
             viewModel.setLoading(false)
             if (success) {
                 Snackbar.make(binding.root, "Export successful", Snackbar.LENGTH_SHORT).show()
             } else {
                 Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
             }
        }
    }
    
    private fun exportCheats(treeUri: Uri, gameName: String, build: BuildCheats): Boolean {
        try {
            val rootDir = DocumentFile.fromTreeUri(requireContext(), treeUri) ?: return false
            val safeGameName = sanitizeFileName(gameName)
            val gameDir = rootDir.findFile(safeGameName) ?: rootDir.createDirectory(safeGameName) ?: return false
            
            for (cheat in build.cheats) {
                val safeCheatName = sanitizeFileName(cheat.name)
                val cheatDir = gameDir.findFile(safeCheatName) ?: gameDir.createDirectory(safeCheatName) ?: continue
                val cheatsSubDir = cheatDir.findFile("cheats") ?: cheatDir.createDirectory("cheats") ?: continue
                
                val fileName = "${build.buildId.uppercase()}.txt"
                // If file exists, we probably want to overwrite or append? 
                // Assuming overwrite for export, or check if exists.
                // DocumentFile doesn't strictly support "overwrite" easily without delete, but openOutputStream("w") or "wt" might truncate.
                // Actually contentResolver.openOutputStream(uri, "wt") truncates.
                // But finding if file exists:
                val existingFile = cheatsSubDir.findFile(fileName)
                val file = existingFile ?: cheatsSubDir.createFile("text/plain", fileName) ?: continue
                
                requireContext().contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                     val content = "${cheat.name}\n${cheat.codes.joinToString("\n")}"
                     output.write(content.toByteArray())
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun displayCheatInfo(cheatInfo: CheatInfo) {
        binding.apply {
            layoutCheatInfo.visibility = View.VISIBLE
            textviewGameName.text = cheatInfo.gameName ?: "Unknown Game"
            textviewCheatTitleId.text = cheatInfo.titleId
            textviewFilePath.text = cheatInfo.filePath
            
            val adapter = BuildAdapter(cheatInfo.builds, { cheat ->
                showCheatDialog(cheat)
            }, { build ->
                selectedBuildForExport = build
                exportDirLauncher.launch(null)
            }, {
                showExportInfoDialog()
            })
            recyclerviewBuilds.adapter = adapter
        }
    }

    private fun showExportInfoDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Info")
            .setMessage("This function export this cheat/mods to a folder structure that the Citron/Eden emulator can load.\n\nAfter exporting, go to Citron/Eden emulator (android), long press a game, Add-ons -> Install -> Mods and cheats to install: \n\nFor example, you will find an exported folder like [Infinite HP]/cheats/12838123812.txt , select the [Infinite HP] folder to install the mods/cheat \n\n**Make sure you check the Build ID, otherwise the mods/cheat will not work**")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showCheatDialog(cheat: Cheat) {
        val cheatContent = if (cheat.codes.isNotEmpty()) {
            cheat.codes.joinToString("\n")
        } else {
            "No codes available"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(cheat.name)
            .setMessage(cheatContent)
            .setPositiveButton("Copy") { dialog, _ ->
                copyCheatToClipboard(cheat)
                dialog.dismiss()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun copyCheatToClipboard(cheat: Cheat) {
        val cheatBlock = if (cheat.codes.isNotEmpty()) {
            "${cheat.name}\n${cheat.codes.joinToString("\n")}"
        } else {
            cheat.name
        }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(cheat.name, cheatBlock)
        clipboard.setPrimaryClip(clip)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

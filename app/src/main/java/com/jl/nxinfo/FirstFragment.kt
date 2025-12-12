package com.jl.nxinfo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jl.nxinfo.databinding.FragmentFirstBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    private val binding get() = _binding!!

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedFile(it)
        }
    }

    private val viewModel: RomInfoViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkProdKeysStatus()

        binding.buttonSelectKeys.setOnClickListener {
            (activity as? MainActivity)?.openKeysFilePicker()
        }

        binding.fab.setOnClickListener {
            val keysManager = ProdKeysManager.getInstance(requireContext())
            if (!keysManager.isKeysLoaded()) {
                Snackbar.make(binding.root, "Please import prod.keys file first (use menu button)", Snackbar.LENGTH_LONG)
                    .setAction("Select Keys") {
                        (activity as? MainActivity)?.openKeysFilePicker()
                    }
                    .show()
                return@setOnClickListener
            }

            filePickerLauncher.launch("*/*")
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoading()
            } else {
                hideLoading()
            }
        }

        viewModel.romInfo.observe(viewLifecycleOwner) { romInfo ->
            romInfo?.let {
                displayRomInfo(it)
            }
        }

        // Set click listeners for copying to clipboard
        binding.textviewTitle.setOnClickListener {
            copyToClipboard("Game Title", binding.textviewTitle.text.toString())
        }
        binding.textviewTitleId.setOnClickListener {
            copyToClipboard("Title ID", binding.textviewTitleId.text.toString())
        }
        binding.textviewVersion.setOnClickListener {
            copyToClipboard("Version", binding.textviewVersion.text.toString())
        }
        binding.textviewSdkVersion.setOnClickListener {
            copyToClipboard("SDK Version", binding.textviewSdkVersion.text.toString())
        }
        binding.textviewBuildId.setOnClickListener {
            copyToClipboard("Build ID", binding.textviewBuildId.text.toString())
        }

        // Set click listener for opening Tinfoil URL
        binding.imageviewIcon.setOnClickListener {
            val titleId = binding.textviewTitleId.text.toString()
            if (titleId.isNotBlank() && titleId != "-") {
                openTinfoilUrl(titleId)
            } else {
                Toast.makeText(requireContext(), "Title ID not available", Toast.LENGTH_SHORT).show()
            }
        }

        // Set click listeners for cheat search buttons
        binding.buttonFindCheatslips.setOnClickListener {
            val title = binding.textviewTitle.text.toString()
            if (title.isNotBlank() && title != "-") {
                searchCheatSlips(title)
            } else {
                Toast.makeText(requireContext(), "Title not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonFindCheatsDb.setOnClickListener {
            val titleId = binding.textviewTitleId.text.toString()
            if (titleId.isNotBlank() && titleId != "-") {
                findCheatsFromDb(titleId)
            } else {
                Toast.makeText(requireContext(), "Title ID not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkProdKeysStatus()
    }

    private fun checkProdKeysStatus() {
        val keysManager = ProdKeysManager.getInstance(requireContext())
        val keysLoaded = keysManager.isKeysLoaded()

        binding.cardKeysPrompt.visibility = if (keysLoaded) View.GONE else View.VISIBLE
    }

    fun refreshKeysStatus() {
        checkProdKeysStatus()
    }

    private fun showLoading() {
        binding.apply {
            cardRomInfo.visibility = View.VISIBLE
            loadingContainer.visibility = View.VISIBLE
            contentContainer.visibility = View.GONE
        }
    }

    private fun hideLoading() {
        binding.apply {
            loadingContainer.visibility = View.GONE
            contentContainer.visibility = View.VISIBLE
        }
    }

    fun displayRomInfo(romInfo: SwitchRomInfo) {
        binding.apply {
            cardRomInfo.visibility = View.VISIBLE
            textviewTitle.text = romInfo.title
            textviewTitleId.text = romInfo.titleId
            textviewVersion.text = romInfo.version
            textviewSdkVersion.text = romInfo.sdkVersion
            textviewBuildId.text = romInfo.buildId
            textviewFileType.text = romInfo.fileType

            if (romInfo.iconData != null && romInfo.iconData.isNotEmpty()) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(romInfo.iconData, 0, romInfo.iconData.size)
                    if (bitmap != null) {
                        imageviewIcon.setImageBitmap(bitmap)
                        imageviewIcon.visibility = View.VISIBLE
                    } else {
                        imageviewIcon.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    imageviewIcon.visibility = View.GONE
                }
            } else {
                imageviewIcon.visibility = View.GONE
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun openTinfoilUrl(titleId: String) {
        val url = "https://tinfoil.media/Title/$titleId"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "Unknown"
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension !in listOf("xci", "nsp", "nsz", "xcz")) {
            Snackbar.make(binding.root, "Please select a valid Switch ROM file (.xci, .nsp, .nsz, .xcz)", Snackbar.LENGTH_LONG)
                .show()
            return
        }

        viewModel.setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            val romInfo = withContext(Dispatchers.IO) {
                val parser = SwitchRomParser(requireContext())
                parser.parseRom(uri)
            }

            viewModel.setRomInfo(romInfo)
            viewModel.setLoading(false)
        }
    }

    private fun searchCheatSlips(title: String) {
        val encodedTitle = Uri.encode(title)
        val url = "https://www.cheatslips.com/games/search/?terms=$encodedTitle"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun findCheatsFromDb(titleId: String) {
        val url = "https://raw.githubusercontent.com/HamletDuFromage/switch-cheats-db/refs/heads/master/cheats/$titleId.json"

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    checkAndDownloadJson(url)
                }

                if (jsonContent != null) {
                    showDownloadDialog(titleId, jsonContent)
                } else {
                    Toast.makeText(requireContext(), "No cheats found in database for this title", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error checking cheats database: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndDownloadJson(url: String): String? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showDownloadDialog(titleId: String, jsonContent: String) {
        val gameTitle = viewModel.romInfo.value?.title ?: "Unknown"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cheats Found")
            .setMessage("Cheat file found for title ID: $titleId\n\nDo you want to download it to your Downloads folder?")
            .setPositiveButton("Yes") { dialog, _ ->
                downloadCheatFile(titleId, gameTitle, jsonContent)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun downloadCheatFile(titleId: String, gameTitle: String, jsonContent: String) {
        try {
            val sanitizedTitle = gameTitle.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            val fileName = "[$sanitizedTitle]-$titleId.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        output.write(jsonContent.toByteArray())
                    }

                    Toast.makeText(
                        requireContext(),
                        "Cheat file downloaded to Downloads folder: $fileName",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: run {
                    Toast.makeText(
                        requireContext(),
                        "Failed to create download file",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                FileOutputStream(file).use { output ->
                    output.write(jsonContent.toByteArray())
                }

                Toast.makeText(
                    requireContext(),
                    "Cheat file downloaded to: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error downloading file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
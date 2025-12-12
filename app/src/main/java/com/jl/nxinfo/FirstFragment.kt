package com.jl.nxinfo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
            CheatSearchHelper.searchOnCheatSlips(requireContext(), title)
        }

        binding.buttonFindCheatsDb.setOnClickListener {
            val titleId = binding.textviewTitleId.text.toString()
            val gameTitle = binding.textviewTitle.text.toString()
            CheatSearchHelper.findInCheatDatabase(this, titleId, gameTitle)
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
        CheatSearchHelper.openUrl(requireContext(), url)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.jl.nxinfo

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.jl.nxinfo.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    private val binding get() = _binding!!

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
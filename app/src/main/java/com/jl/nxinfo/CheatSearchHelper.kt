package com.jl.nxinfo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for searching and downloading Switch game cheats
 * Shared across FirstFragment and SearchFragment to avoid code duplication
 */
object CheatSearchHelper {

    /**
     * Open CheatSlips.com search with the game title
     */
    fun searchOnCheatSlips(context: Context, gameTitle: String) {
        if (gameTitle.isBlank()) {
            Toast.makeText(context, "Game title not available", Toast.LENGTH_SHORT).show()
            return
        }

        val encodedTitle = Uri.encode(gameTitle)
        val url = "https://www.cheatslips.com/games/search/?terms=$encodedTitle"
        openUrl(context, url)
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    /**
     * Search for cheats in Switch Cheats DB and offer to download
     */
    fun findInCheatDatabase(fragment: Fragment, titleId: String, gameTitle: String) {
        if (titleId.isBlank()) {
            Toast.makeText(fragment.requireContext(), "Title ID not available", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "https://raw.githubusercontent.com/HamletDuFromage/switch-cheats-db/refs/heads/master/cheats/$titleId.json"

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    downloadCheatJson(url)
                }

                if (jsonContent != null) {
                    showDownloadDialog(fragment, titleId, gameTitle, jsonContent)
                } else {
                    Toast.makeText(
                        fragment.requireContext(),
                        "No cheats found in database for this title",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    fragment.requireContext(),
                    "Error checking cheats database: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun downloadCheatJson(url: String): String? {
        return DownloadHelper.downloadToString(url)
    }

    private fun showDownloadDialog(
        fragment: Fragment,
        titleId: String,
        gameTitle: String,
        jsonContent: String
    ) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Cheats Found")
            .setMessage("Cheat file found for title ID: $titleId\n\nDo you want to download it to your Downloads folder?")
            .setPositiveButton("Yes") { dialog, _ ->
                downloadCheatFile(fragment.requireContext(), titleId, gameTitle, jsonContent)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun downloadCheatFile(
        context: Context,
        titleId: String,
        gameTitle: String,
        jsonContent: String
    ) {
        try {
            val sanitizedTitle = gameTitle.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            val fileName = "[$sanitizedTitle]-$titleId.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        output.write(jsonContent.toByteArray())
                    }

                    Toast.makeText(
                        context,
                        "Cheat file downloaded to Downloads folder: $fileName",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: run {
                    Toast.makeText(
                        context,
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
                    context,
                    "Cheat file downloaded to: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Error downloading file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

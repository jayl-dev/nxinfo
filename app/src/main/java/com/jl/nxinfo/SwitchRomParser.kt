package com.jl.nxinfo

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileInputStream

class SwitchRomParser(private val context: Context) {

    companion object {
        private const val TAG = "SwitchRomParser"
        private const val MAX_FILE_SIZE_BYTES = 32L * 1024 * 1024 * 1024
    }

    external fun parseRomNative(fd: Int, fileName: String, keysFile: String): SwitchRomInfo

    fun parseRom(uri: Uri): SwitchRomInfo {
        val fileName = getFileName(uri) ?: return SwitchRomInfo()
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension !in listOf("xci", "nsp", "nsz", "xcz")) {
            return SwitchRomInfo()
        }

        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fileSize = pfd.statSize

                if (fileSize > MAX_FILE_SIZE_BYTES) {
                    Log.w(TAG, "File size ($fileSize bytes) exceeds maximum supported size ($MAX_FILE_SIZE_BYTES bytes)")
                    return SwitchRomInfo(title = getFileNameWithoutExtension(fileName), fileType = extension.uppercase())
                }

                Log.d(TAG, "Processing ROM file: $fileName (${fileSize / (1024 * 1024)} MB)")

                val fd = pfd.fd
                val keysManager = ProdKeysManager.getInstance(context)
                val keysFile = if (keysManager.isKeysLoaded()) keysManager.getKeysFilePath() ?: "" else ""

                parseRomNative(fd, fileName, keysFile)
            } ?: SwitchRomInfo(title = getFileNameWithoutExtension(fileName), fileType = extension.uppercase())
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while parsing ROM - file may be too large", e)
            SwitchRomInfo(title = getFileNameWithoutExtension(fileName), fileType = extension.uppercase())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ROM", e)
            SwitchRomInfo(title = getFileNameWithoutExtension(fileName), fileType = extension.uppercase())
        }
    }

    private fun getFileName(uri: Uri): String? {
        return uri.lastPathSegment
    }

    private fun getFileNameWithoutExtension(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }
}
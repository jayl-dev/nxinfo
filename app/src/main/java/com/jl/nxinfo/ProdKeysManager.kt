package com.jl.nxinfo

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

sealed class KeysResult {
    object Success : KeysResult()
    object NotFoundInZip : KeysResult()
    object InvalidFile : KeysResult()
    data class Error(val message: String) : KeysResult()
}

class ProdKeysManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProdKeysManager"
        private const val INTERNAL_KEYS_FILENAME = "prod.keys"

        @Volatile
        private var instance: ProdKeysManager? = null

        fun getInstance(context: Context): ProdKeysManager {
            return instance ?: synchronized(this) {
                instance ?: ProdKeysManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveProdKeysFile(uri: Uri): KeysResult {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return KeysResult.InvalidFile

            val outputFile = File(context.filesDir, INTERNAL_KEYS_FILENAME)

            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (e: Exception) {
                null
            }

            val isZipFile = fileName?.endsWith(".zip", ignoreCase = true) == true

            if (isZipFile) {
                return extractProdKeysFromZip(inputStream, outputFile)
            } else {
                val content = inputStream.use { it.readBytes() }
                if (!isValidKeysFile(content)) {
                    return KeysResult.InvalidFile
                }

                FileOutputStream(outputFile).use { output ->
                    output.write(content)
                }
                Log.d(TAG, "prod.keys file saved to: ${outputFile.absolutePath}")
                return KeysResult.Success
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving prod.keys file", e)
            return KeysResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun isValidKeysFile(content: ByteArray): Boolean {
        val text = String(content)
        return text.contains("header_key") ||
               text.contains("aes_kek_generation_source") ||
               text.contains("key_area_key_application")
    }

    private fun extractProdKeysFromZip(inputStream: java.io.InputStream, outputFile: File): KeysResult {
        try {
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith("prod.keys", ignoreCase = true) && !entry.isDirectory) {
                        val content = zipStream.readBytes()

                        if (!isValidKeysFile(content)) {
                            return KeysResult.InvalidFile
                        }

                        FileOutputStream(outputFile).use { output ->
                            output.write(content)
                        }
                        Log.d(TAG, "prod.keys extracted from zip and saved to: ${outputFile.absolutePath}")
                        return KeysResult.Success
                    }
                    entry = zipStream.nextEntry
                }
                Log.e(TAG, "prod.keys not found in zip file")
                return KeysResult.NotFoundInZip
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting prod.keys from zip", e)
            return KeysResult.Error(e.message ?: "Unknown error")
        }
    }

    fun isKeysLoaded(): Boolean {
        val file = File(context.filesDir, INTERNAL_KEYS_FILENAME)
        return file.exists()
    }

    fun getKeysFilePath(): String? {
        val file = File(context.filesDir, INTERNAL_KEYS_FILENAME)
        return if (file.exists()) file.absolutePath else null
    }

    fun getKeysSummary(): String {
        return if (isKeysLoaded()) {
            "Keys file loaded."
        } else {
            "No keys file loaded"
        }
    }
}
package com.jl.nxinfo

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.zip.ZipInputStream

data class Cheat(
    val name: String,
    val codes: List<String>
)

data class CheatInfo(
    val titleId: String,
    val buildId: String,
    val cheats: List<Cheat>,
    val rawLines: List<String>
)

sealed class CheatResult {
    data class Success(val cheatInfo: CheatInfo) : CheatResult()
    object NotFoundInZip : CheatResult()
    object InvalidStructure : CheatResult()
    data class Error(val message: String) : CheatResult()
}

class CheatFileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CheatFileManager"
        private const val CONTENTS_DIR = "contents"
        private const val CHEATS_DIR = "cheats"

        @Volatile
        private var instance: CheatFileManager? = null

        fun getInstance(context: Context): CheatFileManager {
            return instance ?: synchronized(this) {
                instance ?: CheatFileManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var lastCheatInfo: CheatInfo? = null

    fun extractCheatFile(uri: Uri): CheatResult {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return CheatResult.Error("Cannot open file")

            return extractCheatFromZip(inputStream)

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cheat file", e)
            return CheatResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun extractCheatFromZip(inputStream: java.io.InputStream): CheatResult {
        try {
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    // Pattern: xxxx/contents/{title id}/cheats/{build id}.txt
                    if (!entry.isDirectory && entryName.contains("/$CONTENTS_DIR/") &&
                        entryName.contains("/$CHEATS_DIR/") && entryName.endsWith(".txt")) {

                        val pathResult = parsePath(entryName)
                        if (pathResult != null) {
                            val (titleId, buildId) = pathResult

                            val rawLines = zipStream.bufferedReader().use { reader ->
                                reader.readLines()
                            }

                            val cheats = parseCheatFile(rawLines)

                            val cheatInfo = CheatInfo(
                                titleId = titleId,
                                buildId = buildId,
                                cheats = cheats,
                                rawLines = rawLines
                            )

                            lastCheatInfo = cheatInfo

                            Log.d(TAG, "Cheat file extracted - Title ID: $titleId, Build ID: $buildId, Cheats: ${cheats.size}, Lines: ${rawLines.size}")
                            return CheatResult.Success(cheatInfo)
                        }
                    }
                    entry = zipStream.nextEntry
                }

                Log.e(TAG, "Cheat file not found in zip with expected structure")
                return CheatResult.NotFoundInZip
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cheat from zip", e)
            return CheatResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun parsePath(path: String): Pair<String, String>? {
        try {
            val parts = path.split("/")

            val contentsIndex = parts.indexOf(CONTENTS_DIR)
            val cheatsIndex = parts.indexOf(CHEATS_DIR)

            if (contentsIndex == -1 || cheatsIndex == -1 || contentsIndex >= cheatsIndex) {
                return null
            }

            if (contentsIndex + 1 >= parts.size) {
                return null
            }
            val titleId = parts[contentsIndex + 1]

            val filename = parts.lastOrNull() ?: return null
            if (!filename.endsWith(".txt", ignoreCase = true)) {
                return null
            }
            val buildId = filename.substringBeforeLast(".txt")

            if (titleId.isBlank() || buildId.isBlank()) {
                return null
            }

            Log.d(TAG, "Parsed path - Title ID: $titleId, Build ID: $buildId")
            return Pair(titleId, buildId)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing path: $path", e)
            return null
        }
    }

    fun getLastCheatInfo(): CheatInfo? {
        return lastCheatInfo
    }

    fun hasCheatInfo(): Boolean {
        return lastCheatInfo != null
    }

    fun clearCheatInfo() {
        lastCheatInfo = null
        Log.d(TAG, "Cheat info cleared")
    }

    fun getCheatSummary(): String {
        return if (hasCheatInfo()) {
            val info = lastCheatInfo!!
            "Title ID: ${info.titleId}, Build ID: ${info.buildId}, Cheats: ${info.cheats.size}"
        } else {
            "No cheat file loaded"
        }
    }

    private fun parseCheatFile(lines: List<String>): List<Cheat> {
        val cheats = mutableListOf<Cheat>()
        var currentName: String? = null
        val currentCodes = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                if (currentName != null) {
                    cheats.add(Cheat(currentName, currentCodes.toList()))
                    currentName = null
                    currentCodes.clear()
                }
                continue
            }

            if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("{") && trimmed.endsWith("}"))) {

                if (currentName != null) {
                    cheats.add(Cheat(currentName, currentCodes.toList()))
                    currentCodes.clear()
                }

                currentName = when {
                    trimmed.startsWith("[") && trimmed.endsWith("]") ->
                        trimmed.substring(1, trimmed.length - 1)
                    trimmed.startsWith("{") && trimmed.endsWith("}") ->
                        trimmed.substring(1, trimmed.length - 1)
                    else -> trimmed
                }
            } else {
                if (currentName != null) {
                    currentCodes.add(trimmed)
                }
            }
        }

        if (currentName != null) {
            cheats.add(Cheat(currentName, currentCodes.toList()))
        }

        Log.d(TAG, "Parsed ${cheats.size} cheats from file")
        return cheats
    }
}

package com.jl.nxinfo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.util.zip.ZipInputStream

data class Cheat(
    val name: String,
    val codes: List<String>
)

data class BuildCheats(
    val buildId: String,
    val cheats: List<Cheat>
)

data class CheatInfo(
    val titleId: String,
    val gameName: String?,
    val filePath: String,
    val builds: List<BuildCheats>
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

            val fileName = getFileName(uri) ?: "unknown"
            val filePath = uri.path ?: uri.toString()

            return if (fileName.endsWith(".json", ignoreCase = true)) {
                extractCheatFromJson(inputStream, fileName, filePath)
            } else if (fileName.endsWith(".zip", ignoreCase = true)) {
                extractCheatFromZip(inputStream, filePath)
            } else {
                CheatResult.Error("Unsupported file type. Please select a .json or .zip file.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cheat file", e)
            return CheatResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query file name: $e")
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun extractCheatFromJson(inputStream: java.io.InputStream, fileName: String, filePath: String): CheatResult {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            var extractedTitleId = fileName.substringBeforeLast(".json")
            var gameName: String? = null
            val regex = Regex("\\[(.*?)\\]-(.*)")
            val match = regex.matchEntire(extractedTitleId)
            if (match != null) {
                gameName = match.groupValues[1]
                extractedTitleId = match.groupValues[2]
            } else {
                gameName = extractedTitleId
            }

            val builds = mutableListOf<BuildCheats>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val buildId = keys.next()
                if (buildId == "attribution") continue

                val buildCheatsObj = jsonObject.optJSONObject(buildId) ?: continue
                val cheats = mutableListOf<Cheat>()

                val cheatKeys = buildCheatsObj.keys()
                while (cheatKeys.hasNext()) {
                    val cheatName = cheatKeys.next()
                    val cheatContent = buildCheatsObj.getString(cheatName)
                    val cheatLines = cheatContent.lines()
                    cheats.addAll(parseCheatFile(cheatLines))
                }

                if (cheats.isNotEmpty()) {
                    builds.add(BuildCheats(buildId, cheats))
                }
            }

            if (builds.isEmpty()) {
                return CheatResult.Error("No cheats found in JSON file")
            }

            val cheatInfo = CheatInfo(
                titleId = extractedTitleId,
                gameName = gameName,
                filePath = filePath,
                builds = builds
            )

            lastCheatInfo = cheatInfo
            Log.d(TAG, "Cheat file extracted from JSON - Title ID: $extractedTitleId, Game Name: $gameName, Builds: ${builds.size}")

            return CheatResult.Success(cheatInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cheat from JSON", e)
            return CheatResult.Error(e.message ?: "Error parsing JSON")
        }
    }

    private fun extractCheatFromZip(inputStream: java.io.InputStream, filePath: String): CheatResult {
        try {
            val builds = mutableListOf<BuildCheats>()
            var foundTitleId: String? = null
            var gameName: String? = null
            // Regex to match: [XXXX vxxxx TID=XXX BID=XXX]
            // We want to capture XXXX (Game Name).
            val gameNameRegex = Regex("^\\[(.*?)\\s+TID.*?\\]")

            ZipInputStream(inputStream).use {
                var entry = it.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    // Pattern: xxxx/contents/{title id}/cheats/{build id}.txt
                    if (!entry.isDirectory && entryName.contains("/$CONTENTS_DIR/") &&
                        entryName.contains("/$CHEATS_DIR/") && entryName.endsWith(".txt")) {

                        val pathResult = parsePath(entryName)
                        if (pathResult != null) {
                            val (titleId, buildId) = pathResult

                            // We only support one title ID per session for now.
                            // If we haven't found one, set it. If we have, only process matches.
                            if (foundTitleId == null) {
                                foundTitleId = titleId
                            }

                            if (foundTitleId == titleId) {
                                val lines = String(it.readBytes(), Charsets.UTF_8).lines()
                                val cheats = parseCheatFile(lines)
                                if (cheats.isNotEmpty()) {
                                    builds.add(BuildCheats(buildId, cheats))
                                    
                                    // Look for game name if we haven't found it yet
                                    if (gameName == null) {
                                        for (cheat in cheats) {
                                            val match = gameNameRegex.find(cheat.name)
                                            if (match != null) {
                                                gameName = match.groupValues[1]
                                                Log.d(TAG, "Found game name in cheat: $gameName")
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    entry = it.nextEntry
                }
            }

            if (foundTitleId == null || builds.isEmpty()) {
                return CheatResult.NotFoundInZip
            }

            val cheatInfo = CheatInfo(
                titleId = foundTitleId!!,
                gameName = gameName ?: foundTitleId,
                filePath = filePath,
                builds = builds
            )

            lastCheatInfo = cheatInfo
            Log.d(TAG, "Cheat file extracted from ZIP - Title ID: $foundTitleId, Game Name: $gameName, Builds: ${builds.size}")
            return CheatResult.Success(cheatInfo)

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

                currentName = trimmed
            } else {
                if (currentName != null) {
                    currentCodes.add(trimmed)
                }
            }
        }

        if (currentName != null) {
            cheats.add(Cheat(currentName, currentCodes.toList()))
        }

        return cheats
    }
}

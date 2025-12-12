package com.jl.nxinfo

import com.google.gson.annotations.SerializedName

data class GameInfo(
    @SerializedName("title_id")
    val titleId: String,

    @SerializedName("name_en")
    val nameEn: String,

    @SerializedName("name_zh")
    val nameZh: String,

    @SerializedName("icon_url")
    val iconUrl: String
) {
    // Calculate fuzzy match score for search
    fun matchScore(query: String): Int {
        if (query.isBlank()) return 0

        val normalizedQuery = query.lowercase()
        val normalizedNameEn = nameEn.lowercase()
        val normalizedNameZh = nameZh.lowercase()
        val normalizedTitleId = titleId.lowercase()

        // Get all Chinese variants of the query (Simplified, Traditional)
        val queryVariants = ChineseConverter.getBothVariants(query).map { it.lowercase() }

        // Check exact match for all query variants
        for (variant in queryVariants) {
            if (normalizedNameEn == variant || normalizedNameZh == variant || normalizedTitleId == variant) {
                return 1000
            }
        }

        // Check starts with for all query variants
        for (variant in queryVariants) {
            if (normalizedNameEn.startsWith(variant) || normalizedNameZh.startsWith(variant)) {
                return 500
            }
        }

        // Check contains for all query variants
        for (variant in queryVariants) {
            if (normalizedNameEn.contains(variant) || normalizedNameZh.contains(variant)) {
                return 100
            }
        }

        // Fuzzy match for all query variants
        var maxFuzzyScore = 0
        for (variant in queryVariants) {
            val fuzzyScore = fuzzyMatch(normalizedNameEn, variant) + fuzzyMatch(normalizedNameZh, variant)
            if (fuzzyScore > maxFuzzyScore) {
                maxFuzzyScore = fuzzyScore
            }
        }
        if (maxFuzzyScore > 0) {
            return maxFuzzyScore
        }

        // Title ID match
        if (normalizedTitleId.contains(normalizedQuery)) {
            return 50
        }

        return 0
    }

    private fun fuzzyMatch(text: String, query: String): Int {
        var textIndex = 0
        var queryIndex = 0
        var score = 0

        while (textIndex < text.length && queryIndex < query.length) {
            if (text[textIndex] == query[queryIndex]) {
                score += 1
                queryIndex++
            }
            textIndex++
        }

        // All query characters found in order
        return if (queryIndex == query.length) score else 0
    }
}

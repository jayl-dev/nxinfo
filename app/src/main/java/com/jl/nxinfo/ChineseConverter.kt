package com.jl.nxinfo

import com.github.houbb.opencc4j.util.ZhConverterUtil

object ChineseConverter {

    /**
     * Check if a string contains Chinese characters
     */
    fun containsChinese(text: String): Boolean {
        return text.any { char ->
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        }
    }

    /**
     * Convert Simplified Chinese to Traditional Chinese
     */
    fun toTraditional(text: String): String {
        return try {
            ZhConverterUtil.toTraditional(text)
        } catch (e: Exception) {
            text // Return original if conversion fails
        }
    }

    /**
     * Convert Traditional Chinese to Simplified Chinese
     */
    fun toSimplified(text: String): String {
        return try {
            ZhConverterUtil.toSimple(text)
        } catch (e: Exception) {
            text // Return original if conversion fails
        }
    }

    /**
     * Get both Simplified and Traditional variants of a Chinese text
     * Returns a set containing both variants (may be same if no conversion needed)
     */
    fun getBothVariants(text: String): Set<String> {
        if (!containsChinese(text)) {
            return setOf(text)
        }

        val variants = mutableSetOf<String>()
        variants.add(text) // Original
        variants.add(toSimplified(text)) // Simplified variant
        variants.add(toTraditional(text)) // Traditional variant

        return variants
    }
}

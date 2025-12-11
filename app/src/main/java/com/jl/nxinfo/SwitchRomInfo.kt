package com.jl.nxinfo


data class SwitchRomInfo(
    val title: String = "Unknown",
    val version: String = "Unknown",
    val titleId: String = "Unknown",
    val sdkVersion: String = "Unknown",
    val buildId: String = "Unknown",
    val fileType: String = "Unknown",
    val iconData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SwitchRomInfo

        if (title != other.title) return false
        if (version != other.version) return false
        if (titleId != other.titleId) return false
        if (sdkVersion != other.sdkVersion) return false
        if (buildId != other.buildId) return false
        if (fileType != other.fileType) return false
        if (iconData != null) {
            if (other.iconData == null) return false
            if (!iconData.contentEquals(other.iconData)) return false
        } else if (other.iconData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + titleId.hashCode()
        result = 31 * result + sdkVersion.hashCode()
        result = 31 * result + buildId.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + (iconData?.contentHashCode() ?: 0)
        return result
    }
}

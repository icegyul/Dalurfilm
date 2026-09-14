package com.dalur.film.pro

import android.media.MediaCodecInfo
import android.media.MediaCodecList

object CodecSelector {
    data class Selection(val mime: String, val label: String, val profile: Int? = null)

    fun selectVideoEncoder(preferHevc: Boolean, prefer10Bit: Boolean): Selection {
        val encoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { it.isEncoder }
        fun has(mime: String, profile: Int? = null): Boolean = encoders.any { info ->
            info.supportedTypes.any { it.equals(mime, true) } &&
                (profile == null || runCatching {
                    info.getCapabilitiesForType(mime).profileLevels.any { it.profile == profile }
                }.getOrDefault(false))
        }
        val hevc10 = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        if (preferHevc && prefer10Bit && has("video/hevc", hevc10)) {
            return Selection("video/hevc", "HEVC 10-bit", hevc10)
        }
        if (preferHevc && has("video/hevc")) return Selection("video/hevc", "HEVC")
        return Selection("video/avc", "H.264")
    }
}

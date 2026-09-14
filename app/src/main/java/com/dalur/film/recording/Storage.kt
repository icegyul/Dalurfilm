package com.dalur.film.recording

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.dalur.film.shared.UsbVolume
import java.io.File

/** Storage targets. USB SSD recording and monitor/recorder output stay separate. */
sealed interface RecordTarget {
    data object Internal : RecordTarget
    data class Usb(val volume: UsbVolume) : RecordTarget
}

object StoragePaths {
    fun internalCaptures(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: context.filesDir, "DALUR").also { it.mkdirs() }

    fun usbCaptures(volume: UsbVolume): File? = runCatching {
        File(File(volume.rootPath), "DALUR").also { it.mkdirs() }.takeIf { it.canWrite() }
    }.getOrNull()

    fun resolve(context: Context, target: RecordTarget): File = when (target) {
        is RecordTarget.Internal -> internalCaptures(context)
        is RecordTarget.Usb -> usbCaptures(target.volume) ?: internalCaptures(context)
    }
}

object MediaVerifier {
    data class Result(val ok: Boolean, val reason: String? = null)
    fun verify(file: File, minBytes: Long = 64 * 1024): Result {
        if (!file.exists()) return Result(false, "file missing after finalize")
        if (file.length() < minBytes) return Result(false, "file too small (${file.length()} bytes)")
        if (!file.canRead()) return Result(false, "file not readable")
        return Result(true)
    }
}

package com.dalur.film.journey

import android.content.Context
import android.graphics.*
import android.media.*
import com.dalur.film.shared.Journey
import java.io.File

/**
 * Real on-device MP4 renderer: draws journey cards with Canvas and encodes with
 * MediaCodec + MediaMuxer. H.264 baseline for universal playback; HEVC when the
 * journey preset requests it AND the device encodes it (verified, never faked).
 */
object JourneyMp4Renderer {
    fun render(ctx: Context, journey: Journey, out: File) {
        val wantHevc = journey.exportPreset.contains("HEVC")
        val hevcOk = runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos.any {
                it.isEncoder && it.supportedTypes.any { t -> t.equals("video/hevc", true) }
            }
        }.getOrDefault(false)
        val mime = if (wantHevc && hevcOk) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        val (w, h) = when {
            journey.exportPreset.startsWith("SQUARE") -> 720 to 720
            journey.exportPreset.startsWith("SIXTEEN") -> 1280 to 720
            else -> 720 to 1280 // 9:16
        }
        val fps = 30
        val bitrate = 6_000_000
        val framesPerCard = fps * 2 // 2s per stop
        val totalCards = (journey.routePoints.size.coerceAtLeast(1) + 1) // + overview
        val totalFrames = framesPerCard * totalCards

        val format = MediaFormat.createVideoFormat(mime, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxStarted = false
        val info = MediaCodec.BufferInfo()

        fun drawCard(index: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.parseColor("#0B0B0D"))
            val p = Paint().apply { color = Color.parseColor("#E8DCC8"); textSize = 54f; isAntiAlias = true }
            val small = Paint().apply { color = Color.parseColor("#9A958A"); textSize = 34f; isAntiAlias = true }
            c.drawText("DALUR film", 60f, 120f, small)
            if (index < journey.routePoints.size) {
                val rp = journey.routePoints[index]
                c.drawText(journey.title, 60f, 220f, p)
                c.drawText("${index + 1} / ${journey.routePoints.size}", 60f, 300f, small)
                c.drawText(String.format("%.5f, %.5f", rp.gps.latitude, rp.gps.longitude), 60f, 360f, small)
                // Route dot pulse (static frame approximation of the signature animation)
                val dot = Paint().apply { color = Color.parseColor("#E8DCC8"); isAntiAlias = true }
                c.drawCircle(w / 2f, h / 2f, 26f, dot)
                val card = Paint().apply { color = Color.parseColor("#141417") }
                c.drawRoundRect(60f, h - 420f, (w - 60).toFloat(), (h - 120).toFloat(), 24f, 24f, card)
                c.drawText(rp.mediaId, 100f, h - 320f, p)
                c.drawText(journey.stylePreset, 100f, h - 250f, small)
            } else {
                c.drawText("Full route", 60f, 220f, p)
                c.drawText("${journey.routePoints.size} moments", 60f, 300f, small)
            }
            return bmp
        }

        try {
            var frame = 0
            var cardIndex = 0
            var cardFrame = 0
            var cardBmp = drawCard(0)
            while (frame < totalFrames) {
                if (cardFrame >= framesPerCard) {
                    cardFrame = 0; cardIndex++
                    if (!cardBmp.isRecycled) cardBmp.recycle()
                    cardBmp = drawCard(cardIndex.coerceAtMost(totalCards - 1))
                }
                // Draw bitmap into encoder surface.
                val canvas: Canvas = surface.lockCanvas(null)
                try {
                    canvas.drawBitmap(cardBmp, null,
                        Rect(0, 0, w, h), Paint().apply { isFilterBitmap = true })
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                // Drain encoder.
                var outIndex = codec.dequeueOutputBuffer(info, 10_000)
                while (outIndex >= 0) {
                    val buf = codec.getOutputBuffer(outIndex)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        if (!muxStarted) {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start(); muxStarted = true
                        }
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(info, 0)
                }
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxStarted) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start(); muxStarted = true
                }
                frame++; cardFrame++
            }
            // EOS
            codec.signalEndOfInputStream()
            var eos = false
            while (!eos) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                if (idx >= 0) {
                    val buf = codec.getOutputBuffer(idx)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        if (!muxStarted) {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start(); muxStarted = true
                        }
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                    codec.releaseOutputBuffer(idx, false)
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxStarted) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start(); muxStarted = true
                }
            }
            if (!cardBmp.isRecycled) cardBmp.recycle()
        } finally {
            runCatching { codec.stop() }; runCatching { codec.release() }
            runCatching { surface.release() }
            runCatching { if (muxStarted) muxer.stop() }; runCatching { muxer.release() }
        }
    }
}

package com.example.sesoynatici

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object WaveformExtractor {

    fun extract(context: Context, uri: Uri, buckets: Int = 600): FloatArray {
        val peaks = FloatArray(buckets)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return fallback(buckets)
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return fallback(buckets)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (durationUs <= 0) return fallback(buckets)

            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(outIdx)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var m = 0
                        while (shorts.hasRemaining()) m = max(m, abs(shorts.get().toInt()))
                        val b = (info.presentationTimeUs * buckets / durationUs).toInt()
                            .coerceIn(0, buckets - 1)
                        peaks[b] = max(peaks[b], m / 32768f)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } catch (e: Exception) {
            return fallback(buckets)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
        val top = peaks.max()
        if (top <= 0f) return fallback(buckets)
        for (i in peaks.indices) peaks[i] = sqrt(peaks[i] / top)
        return peaks
    }

    private fun fallback(n: Int) = FloatArray(n) { 0.2f }
}

package org.studiomobile.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File
import java.util.Collections

/**
 * Bounded, background-safe thumbnail cache for canvas rendering.
 *
 * Why this exists: the original ProjectCanvasView.onDraw() called
 * BitmapFactory.decodeFile() directly, at full source resolution, on every
 * single frame redrawn (i.e. on every touch-move event, for every item).
 * For a normal photo this was wasteful; for a 12000x16000 photo it could
 * require ~768 MB for a single decode, repeated dozens of times per second,
 * which is consistent with the crashes/freezes reported on device.
 *
 * Rule enforced by this file: nothing in onDraw() ever touches a file or
 * calls BitmapFactory directly again. onDraw() only reads from this cache;
 * generation always happens on a background thread, once per item, at a
 * fixed small target size - never at source resolution.
 */
object ThumbnailCache {
    /** Longest side of a generated thumbnail, in pixels. */
    const val TARGET_SIZE = 512

    // Cap the cache at an eighth of the app's available heap, measured in KB
    // (matches the standard Android LruCache-for-bitmaps pattern).
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxMemoryKb / 8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // Items currently being generated in the background, so we never queue
    // the same item twice while it's already in progress.
    private val inProgress: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun get(itemId: String): Bitmap? = cache.get(itemId)

    fun isPending(itemId: String): Boolean = inProgress.contains(itemId)

    /**
     * Must be called from a background thread. Decodes a safe, capped-size
     * thumbnail regardless of the source file's actual resolution, and
     * stores it in the cache. Returns the thumbnail, or null on failure
     * (e.g. unsupported/corrupt file) - callers should keep showing the
     * existing label fallback in that case, not crash.
     */
    fun generate(itemId: String, file: File, kind: String): Bitmap? {
        if (!inProgress.add(itemId)) return null
        try {
            val bitmap = when (kind) {
                "image" -> decodeSampledImage(file)
                "video" -> extractVideoFrame(file)
                else -> null
            }
            if (bitmap != null) cache.put(itemId, bitmap)
            return bitmap
        } finally {
            inProgress.remove(itemId)
        }
    }

    fun remove(itemId: String) { cache.remove(itemId) }
    fun clear() { cache.evictAll(); inProgress.clear() }

    // --- Image path -----------------------------------------------------
    // Classic two-pass Android decode: first read only the bounds (no pixel
    // data allocated at all), compute a power-of-two sample size, then
    // decode once directly at that reduced size. A 12000x16000 source never
    // causes a full-resolution allocation at any point.
    private fun decodeSampledImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= TARGET_SIZE &&
               bounds.outHeight / (sampleSize * 2) >= TARGET_SIZE) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: OutOfMemoryError) {
            null // extreme edge case even after sampling; fail soft, never crash the app
        }
    }

    // --- Video path -------------------------------------------------------
    // MediaMetadataRetriever is the native Android API for pulling a single
    // representative frame out of a video file without playing it - the
    // direct equivalent of the "poster frame" approach used in the earlier
    // web prototype, and the reason video items had zero preview until now.
    private fun extractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            downscaleIfNeeded(frame)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= TARGET_SIZE) return bitmap
        val scale = TARGET_SIZE.toFloat() / maxDim
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

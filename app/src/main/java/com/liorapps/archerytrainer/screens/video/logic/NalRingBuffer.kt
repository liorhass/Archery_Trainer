package com.liorapps.archerytrainer.screens.video.logic

import java.nio.ByteBuffer

/**
 * RingBuffer — pre-allocated, GC-free circular buffer for H.264 NAL units.
 *
 * All compressed video bytes are stored in a single [DirectByteBuffer] in native
 * (off-JVM-heap) memory, paired with five parallel primitive arrays that hold per-NAL
 * metadata. No JVM objects are allocated on the hot write path.
 *
 * Thread safety model (matches §11 of the architecture plan):
 *  - [metaHead], [metaTail], [metaCount], [writeHead] are marked @Volatile.
 *  - The encoder coroutine completes all writes to the metadata arrays at index [metaHead]
 *    *before* it increments [metaHead], so the decoder coroutine always sees a complete
 *    entry once it observes the new [metaHead] value.
 *  - The [dataBuffer] itself is partitioned by the ring pointers; encoder and decoder never
 *    touch the same byte region simultaneously under normal operation.
 *  - If the jump sequence creates a race between an in-progress write and a flush, promote
 *    access to a Mutex as noted in §11.
 *
 * @param bufferSizeBytes  Size of the native data buffer in bytes (default 90 MB).
 * @param maxFrames        Maximum number of NAL unit metadata slots (default 1200).
 */
class NalRingBuffer(
    private val bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
) {

    // -------------------------------------------------------------------------
    // Native data buffer
    // -------------------------------------------------------------------------

    /** Pre-allocated native memory for all H.264 NAL unit bytes. Never replaced. */
    private val dataBuffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSizeBytes)

    // -------------------------------------------------------------------------
    // Metadata parallel arrays  (§3.2)
    // -------------------------------------------------------------------------

    /** Presentation timestamp of each NAL unit, in microseconds (µs). */
    private val metaPTS: LongArray = LongArray(maxFrames)

    /** Byte offset of each NAL unit's first byte inside [dataBuffer]. */
    private val metaOffset: LongArray = LongArray(maxFrames)

    /** Size in bytes of each NAL unit. */
    private val metaSize: IntArray = IntArray(maxFrames)

    /** True if the NAL unit at this slot is an IDR (I-frame). */
    private val metaIsKey: BooleanArray = BooleanArray(maxFrames)

    // -------------------------------------------------------------------------
    // Ring pointers  — all @Volatile so cross-thread visibility is guaranteed
    // -------------------------------------------------------------------------

    /** Next write index into the metadata arrays (encoder side). Wraps mod [maxFrames]. */
    @Volatile private var metaHead: Int = 0

    /** Index of the oldest valid metadata entry (eviction pointer). Wraps mod [maxFrames]. */
    @Volatile private var metaTail: Int = 0

    /** Number of valid entries currently stored. */
    @Volatile private var metaCount: Int = 0

    /** Byte offset of the next NAL write position in [dataBuffer]. */
    @Volatile private var writeHead: Long = 0L

    // -------------------------------------------------------------------------
    // Eviction / overflow counters (diagnostic)
    // -------------------------------------------------------------------------

    /** Total number of times the oldest frame was evicted due to a full buffer. */
    @Volatile var evictionCount: Long = 0L
        private set

    // -------------------------------------------------------------------------
    // Write  (called by the encoder coroutine)
    // -------------------------------------------------------------------------

    /**
     * Writes one H.264 NAL unit into the ring buffer.
     *
     * The NAL bytes are appended at [writeHead] in [dataBuffer], wrapping to offset 0
     * if the remaining capacity at the tail of the buffer is insufficient. Metadata is
     * recorded at [metaHead] *before* [metaHead] is incremented, ensuring the decoder
     * never observes a partially-written entry (§6, §11).
     *
     * If the metadata ring is full ([metaCount] == [maxFrames]), the oldest entry is
     * evicted by advancing [metaTail] (§7.2).
     *
     * @param pts      Presentation timestamp in µs (`System.nanoTime() / 1000`).
     * @param nalBytes Compressed H.264 NAL unit bytes produced by MediaCodec.
     * @param isKey    True if this NAL unit is an IDR frame.
     * @throws IllegalArgumentException if [nalBytes] is larger than the entire data buffer.
     */
    fun writeNal(pts: Long, nalBytes: ByteBuffer, isKey: Boolean) {
//        Timber.d("#######NRB writeNal() pts=$pts buf_len=${nalBytes.remaining()} isKey=$isKey")
//        require(nalBytes.size <= bufferSizeBytes) {
//            "NAL unit size ${nalBytes.size} exceeds buffer capacity $bufferSizeBytes"
//        }
        val nalNBytes = nalBytes.remaining()
        require(nalNBytes <= bufferSizeBytes) {
            "NAL unit size $nalNBytes exceeds buffer capacity $bufferSizeBytes"
        }
//        if (writeHead + nalBytes.size > bufferSizeBytes) {
        if (writeHead + nalNBytes > bufferSizeBytes) {
            // Not enough room at the end — wrap around to the start of the native buffer.
            writeHead = 0L
        }

        // --- 1. Place NAL bytes into the data buffer, wrapping if necessary ---
//        val nalOffset: Long = writeHead
        dataBuffer.position(writeHead.toInt())
        dataBuffer.put(nalBytes)
        // writeHead is updated AFTER the put — metadata is recorded below before pointer advances.

        // --- 2. Record metadata for this NAL unit ---
        // All four array writes happen before metaHead is incremented (§6 / §11).
        metaPTS[metaHead]    = pts
//        metaOffset[metaHead] = nalOffset
        metaOffset[metaHead] = writeHead
//        metaSize[metaHead]   = nalBytes.size
        metaSize[metaHead]   = nalNBytes
        metaIsKey[metaHead]  = isKey

        // --- 3. Advance the data write pointer ---
//        writeHead += nalBytes.size
        writeHead += nalNBytes

        // --- 4. Manage the metadata ring (evict the oldest if full) ---
        if (metaCount == maxFrames) {
            // Ring is full: overwrite the oldest slot, advance tail.
            metaTail = (metaTail + 1) % maxFrames
            evictionCount++
        } else {
            metaCount++
        }

        // Incrementing metaHead last is the store-release that makes the new entry
        // visible to the decoder coroutine (§11).
        metaHead = (metaHead + 1) % maxFrames
    }

    // -------------------------------------------------------------------------
    // Read  (called by the decoder coroutine)
    // -------------------------------------------------------------------------

    /**
     * Copies the NAL unit at [index] directly into [dest].
     *
     * [dest] must have at least [getSize](index) bytes of remaining capacity.
     * The caller is responsible for setting [dest].position() before calling
     * and reading [dest].position() after to know how many bytes were written.
     *
     * @return  The number of bytes written, or -1 if [index] is not valid.
     */
    fun readNal(index: Int, dest: ByteBuffer): Int {
        if (!isValidIndex(index)) return -1

        val offset = metaOffset[index].toInt()
        val size   = metaSize[index]

        require(dest.remaining() >= size) {
            "Destination buffer has ${dest.remaining()} bytes remaining, need $size"
        }

        val slice = dataBuffer.duplicate()  // does not allocate native memory - just a view
        slice.position(offset).limit(offset + size)

        dest.put(slice)                     // single native-to-native copy

        return size
    }

    /** Returns the size in bytes of the NAL unit at [index], or -1 if invalid. */
    fun getNalSize(index: Int): Int = if (isValidIndex(index)) metaSize[index] else -1

    /**
     * Returns the presentation timestamp (µs) of the NAL unit at [index],
     * or [Long.MIN_VALUE] if [index] is not valid.
     */
    fun getNalPts(index: Int): Long =
        if (isValidIndex(index)) metaPTS[index] else Long.MIN_VALUE

    /**
     * Returns true if the NAL unit at [index] is an IDR (I-frame), false otherwise.
     * Returns false for invalid indices.
     */
    fun isKeyFrame(index: Int): Boolean =
        isValidIndex(index) && metaIsKey[index]

    // -------------------------------------------------------------------------
    // Seek  (called by the decoder coroutine on a "jump")
    // -------------------------------------------------------------------------

    /**
     * Walks backward from the newest entry to find the most-recent IDR frame whose
     * PTS is ≤ [targetPTS] (§7.3).
     *
     * @param targetPTS  The playback target timestamp in µs.
     * @return           The metadata slot index of the nearest preceding keyframe,
     *                   or -1 if no suitable keyframe exists in the current buffer
     *                   (e.g. buffer is empty or all keyframes are newer than [targetPTS]).
     */
    fun findNearestKeyframeBefore(targetPTS: Long): Int {
        val count = metaCount  // snapshot: metaCount is @Volatile
        if (count == 0) return -1

        val head = metaHead    // snapshot
        var i = (head - 1 + maxFrames) % maxFrames
        var nChecked = 0

        while (nChecked < count) {
            if (metaIsKey[i] && metaPTS[i] <= targetPTS) {
                return i
            }
            i = (i - 1 + maxFrames) % maxFrames
            nChecked++
        }

        // No qualifying keyframe found — caller should fall back to oldest keyframe.
        return -1
    }

    /**
     * Walks forward from [startIndex] to find the oldest keyframe available in the buffer.
     * Useful as a fallback when [findNearestKeyframeBefore] returns -1.
     *
     * @return  The metadata slot index of the oldest keyframe, or -1 if none exist.
     */
    fun findOldestKeyframe(): Int {
        val count = metaCount
        if (count == 0) return -1

        var i = metaTail
        var checked = 0
        while (checked < count) {
            if (metaIsKey[i]) return i
            i = (i + 1) % maxFrames
            checked++
        }
        return -1
    }

    // -------------------------------------------------------------------------
    // State queries  (safe for any thread)
    // -------------------------------------------------------------------------

    /** Returns the number of valid NAL units currently held in the buffer. */
    val count: Int
        get() = metaCount

    /** Returns true if the buffer contains at least one entry. */
    val isEmpty: Boolean
        get() = metaCount == 0

    /** Returns the metadata slot index of the oldest (tail) entry, or -1 if empty. */
    val tailIndex: Int
        get() = if (metaCount == 0) -1 else metaTail

    /** Returns the metadata slot index of the most-recently-written entry, or -1 if empty. */
    val newestIndex: Int
        get() = if (metaCount == 0) -1 else (metaHead - 1 + maxFrames) % maxFrames

    /** Returns the PTS of the most-recently-written NAL unit in µs, or [Long.MIN_VALUE] if empty. */
    val newestPts: Long
        get() = if (metaCount == 0) Long.MIN_VALUE else metaPTS[(metaHead - 1 + maxFrames) % maxFrames]

    /** Returns the PTS of the oldest NAL unit in µs, or [Long.MIN_VALUE] if empty. */
    val oldestPts: Long
        get() = if (metaCount == 0) Long.MIN_VALUE else metaPTS[metaTail]

    /**
     * Returns the metadata slot that immediately follows [index] in ring order.
     * Use this to advance a decoder read cursor through consecutive entries.
     */
    fun nextIndex(index: Int): Int = (index + 1) % maxFrames

    // -------------------------------------------------------------------------
    // Reset  (e.g. pipeline restart — call from a single coordinating thread)
    // -------------------------------------------------------------------------

    /**
     * Clears all ring pointers and resets the buffer to an empty state.
     * The [dataBuffer] is NOT zeroed — its contents are simply treated as invalid.
     * Must be called from a context where neither the encoder nor decoder coroutine
     * is actively reading or writing (e.g. after both coroutines have been canceled).
     */
    fun reset() {
        metaHead  = 0
        metaTail  = 0
        metaCount = 0
        writeHead = 0L
        evictionCount = 0L
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if [index] refers to a currently-valid metadata slot.
     * A slot is valid when it lies within [metaTail]..[metaHead) in ring order
     * and [metaCount] > 0.
     */
    private fun isValidIndex(index: Int): Boolean {
        val count = metaCount
        if (count == 0) return false
        val tail = metaTail
        val head = metaHead  // exclusive upper bound
        return if (tail < head) {
            index in tail until head
        } else {
            // Ring has wrapped: valid range is [tail, maxFrames) ∪ [0, head)
            index >= tail || index < head
        }
    }

    inner class AsLinearBuffer {
        /** Number of frames currently in the buffer */
        val count: Int
            get() = metaCount

        /** Get the PTS of a frame in uSec */
        fun getFramePts(index: Int): Long = metaPTS[linearIndexToMetaIndex(index)]

        /**
         * Get the data of a frame as a ByteBuffer
         */
        fun getFrameData(index: Int): ByteBuffer {
            val offset   = metaOffset[linearIndexToMetaIndex(index)].toInt()
            val size     = metaSize[linearIndexToMetaIndex(index)]
            val frameBuf = dataBuffer.duplicate()  // does not allocate native memory - just a view
            frameBuf.position(offset).limit(offset + size)
            return frameBuf
        }

        /** Check if a frame is a keyframe */
        fun isKeyFrame(index: Int): Boolean = metaIsKey[linearIndexToMetaIndex(index)]

        /**
         * Walks backward from the passed index to find the most-recent IDR frame
         *
         * @param index The index of the NAL for which we're looking a keyframe
         * @return      The index of the nearest preceding keyframe,
         *              or -1 if no suitable keyframe exists in the current buffer
         *              (e.g. buffer is empty)
         */
        fun findNearestKeyframeBefore(index: Int): Int {
            if (metaCount == 0) return -1

            var i = (index - 1 + maxFrames) % maxFrames
            var nChecked = 0

            while (i != metaHead  &&  nChecked <= metaCount) {
                if (metaIsKey[i]) {
                    return i
                }
                i = (i - 1 + maxFrames) % maxFrames
                nChecked++
            }

            // No qualifying keyframe found
            return -1
        }

        private fun linearIndexToMetaIndex(linearIndex: Int): Int {
            return (metaTail + linearIndex) % maxFrames
        }
    }

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        /** Default native buffer size: 30 s × 2.5 MB/s × 1.2 safety margin ≈ 90 MB. */
        const val DEFAULT_BUFFER_SIZE_BYTES: Int = 90 * 1024 * 1024

        /**
         * Default metadata capacity: 30 s × 30 fps × 1.33 headroom ≈ 1200 slots.
         * Matches [com.liorapps.archerytrainer.ArcheryTrainerDefaults.MAX_FRAMES].
         */
        const val DEFAULT_MAX_FRAMES: Int = 1200
    }
}

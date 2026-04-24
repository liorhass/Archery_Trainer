package com.liorapps.videotrainer

import kotlinx.serialization.Serializable

/**
 * Global constants
 */
object VideoTrainerDefaults {

    // -------------------------------------------------------------------------
    // Video format
    // -------------------------------------------------------------------------

    @Serializable
    sealed class VideoResolution(val width: Int, val height: Int) {
        @Serializable class SD_640x480(): VideoResolution(640, 480)
        @Serializable class HD_1024x720(): VideoResolution(1024, 720)
        @Serializable class FHD_1920x1080(): VideoResolution(1920, 1080)
        @Serializable class QHD_2560x1440(): VideoResolution(2560, 1440)
        @Serializable class UHD_3840x2160(): VideoResolution(3840, 2160)

        override fun toString(): String { return "${width}x${height}" }

    }
    val VIDEO_RESOLUTION = VideoResolution.HD_1024x720()  // Default resolution is 720p

//    /** Frame width in pixels. Switch to 1920 for 1080p. */
//    const val VIDEO_WIDTH: Int = 1280
//
//    /** Frame height in pixels. Switch to 1080 for 1080p. */
//    const val VIDEO_HEIGHT: Int = 720

    /** Target frame rate in frames per second. */
    const val FRAME_RATE: Int = 30

    /**
     * Encoder bitrate in bits per second (15 Mbps CBR).
     *
     * CBR is required so the ring buffer can be sized with confidence (§4.2).
     * At this rate, 30 seconds of H.264 ≈ 56 MB, well within [BUFFER_SIZE_BYTES].
     */
    const val BIT_RATE: Int = 15_000_000
    const val MAX_BIT_RATE: Int = 20_000_000

    /**
     * I-frame interval in seconds.
     *
     * A 1-second interval caps the catch-up decoding distance after a "jump"
     * to at most 1 second of frames (§4.3).
     */
    const val I_FRAME_INTERVAL: Int = 1

    // -------------------------------------------------------------------------
    // Delay range
    // -------------------------------------------------------------------------

    /** Maximum user-selectable delay in seconds. Drives buffer sizing. */
    const val MAX_DELAY_SEC: Int = 30

    /** Minimum user-selectable delay in seconds. */
    const val MIN_DELAY_SEC: Int = 0

    /** Default (initial) delay in seconds. */
    const val DEFAULT_DELAY_SEC  = 5

    // -------------------------------------------------------------------------
    // Ring buffer sizing  (§3.1, §3.2)
    // -------------------------------------------------------------------------

    /**
     * Native (DirectByteBuffer) data buffer size in bytes
     * Rational: Should have been .../8. We divide by 6 in order to have a 33% safety margin
     * E.g.: 30s × 20MBit/S / 6 ≈ 100MB.
     */
    const val BUFFER_SIZE_BYTES: Int = MAX_DELAY_SEC * MAX_BIT_RATE / 6

    /**
     * Metadata ring buffer capacity in number of NAL unit slots.
     *
     * Derivation: 30 s × 30 fps × 1.33 headroom ≈ 1200 slots.
     */
    const val MAX_FRAMES: Int = 1200
}

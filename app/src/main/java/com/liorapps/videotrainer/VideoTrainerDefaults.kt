package com.liorapps.videotrainer

/**
 * Global constants for the TimeShift pipeline.
 *
 * All sizing decisions and their rationale are documented in §14 and §3 of the
 * architecture plan. Change values here only — never hardcode them elsewhere.
 */
object VideoTrainerDefaults {

    // -------------------------------------------------------------------------
    // Video format
    // -------------------------------------------------------------------------

    /** Frame width in pixels. Switch to 1920 for 1080p. */
    const val VIDEO_WIDTH: Int = 1280

    /** Frame height in pixels. Switch to 1080 for 1080p. */
    const val VIDEO_HEIGHT: Int = 720

    /** Target frame rate in frames per second. */
    const val FRAME_RATE: Int = 30

    /**
     * Encoder bitrate in bits per second (15 Mbps CBR).
     *
     * CBR is required so the ring buffer can be sized with confidence (§4.2).
     * At this rate, 30 seconds of H.264 ≈ 56 MB, well within [BUFFER_SIZE_BYTES].
     */
    const val BIT_RATE: Int = 15_000_000

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
     * Native (DirectByteBuffer) data buffer size in bytes.
     *
     * Derivation: 30 s × 2.5 MB/s × 1.2 safety margin ≈ 90 MB.
     */
    const val BUFFER_SIZE_BYTES: Int = 90 * 1024 * 1024

    /**
     * Metadata ring buffer capacity in number of NAL unit slots.
     *
     * Derivation: 30 s × 30 fps × 1.33 headroom ≈ 1200 slots.
     */
    const val MAX_FRAMES: Int = 1200
}

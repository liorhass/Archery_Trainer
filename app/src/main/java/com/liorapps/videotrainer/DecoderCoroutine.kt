package com.liorapps.videotrainer

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * DecoderCoroutine — the sole consumer of the shared [RingBuffer].
 *
 * Configures and operates a MediaCodec AVC decoder, managing a three-state machine
 * (PLAYING / FROZEN / CATCHING_UP) to render delayed camera footage onto [outputSurface].
 *
 * Responsibilities:
 *   - Own the full lifecycle of the MediaCodec decoder (configure → start → loop → release).
 *   - Feed SPS/PPS codec config to the decoder on startup and after every flush.
 *   - Compute [targetPTS] each iteration and submit NAL units whose PTS ≤ [targetPTS].
 *   - Drain decoder output: render to surface in PLAYING; drop silently in CATCHING_UP.
 *   - Execute the jump sequence (flush → re-seek → CATCHING_UP) when signaled via [jumpChannel].
 *   - Detect and recover from ring-buffer eviction of the read cursor.
 *
 * Does NOT:
 *   - Write to the ring buffer (encoder-only responsibility).
 *   - Interact with Camera2 or the encoder in any way.
 *   - Call [RingBuffer.reset] (ViewModel-only, after both coroutines are canceled).
 *   - Manage [delaySec] history or decide when a jump is needed (ViewModel's responsibility).
 *
 * Threading: must be launched on [kotlinx.coroutines.Dispatchers.IO].
 *
 * Notable Implementation Decisions
 * A few choices worth calling out explicitly:
 * trySubmitFrame returns Boolean so the outer loop only advances readIndex on a confirmed
 *    successful queue. If dequeueInputBuffer times out (decoder input queue full), we retry
 *    the same frame next iteration without losing our place.
 * Eviction recovery is separate from the FROZEN path. FROZEN means "the buffer doesn't have
 *    data at targetPTS yet." Eviction means "we had a valid cursor but the encoder lapped us."
 *    Conflating the two would make the re-seek logic harder to reason about.
 * bufferInfo is allocated once outside the loop (line ~97) — a small but deliberate choice,
 *    since this allocation would otherwise run at 30 fps for the entire pipeline lifetime.
 */
class DecoderCoroutine(
    private val nalRingBuffer: NalRingBuffer,

    /**
     * Single-element array written exclusively by [EncoderCoroutine] on its first output.
     * Index 0 holds the raw SPS+PPS bytes once the encoder starts.
     *
     * The ViewModel's backing field must be annotated [@Volatile] for cross-thread visibility.
     */
    private val codecConfigDataHolder: Array<ByteArray?>,

    /**
     * Hardware output surface provided by AndroidExternalSurface.
     * The decoder renders directly here — no CPU pixel copies.
     */
    private val outputSurface: Surface,

    /**
     * Returns the current user-selected delay in seconds.
     * Reads Compose [androidx.compose.runtime.mutableStateOf] (backed by @Volatile),
     * so cross-thread reads from Dispatchers.IO are safe.
     */
    private val delaySecProvider: () -> Int,

    /**
     * Receives a [Unit] whenever the ViewModel detects that the user reduced [delaySec].
     * **Must** be a [Channel.CONFLATED] channel: rapid slider movements collapse into a
     * single seek to the latest [targetPTS], discarding stale intermediate positions.
     */
    private val jumpChannel: Channel<Unit>,

    /**
     * Invoked on fatal, non-cancellation errors (e.g. [MediaCodec.CodecException]).
     * Does not rethrow — the coroutine returns normally after invoking this callback.
     */
    private val onError: (Throwable) -> Unit = {},
) {

    // Three operating states for the decode loop.
    private enum class State {
        /** Normal playback: submitting and rendering frames at [targetPTS]. */
        PLAYING,

        /**
         * The ring buffer has no data at or past [targetPTS].
         * Occurs at pipeline startup and if the encoder falls behind (rare).
         * The surface naturally persists the last rendered frame with no extra logic.
         */
        FROZEN,

        /**
         * After a jump: decoding frames without rendering until [targetPTS] is reached.
         * Maximises catch-up speed while avoiding visual glitches from half-decoded frames.
         */
        CATCHING_UP,
    }

    companion object {
        /**
         * Maximum time (µs) to block waiting for a free decoder input buffer.
         * Matches the encoder drain timeout — keeps the loop responsive to cancellation
         * without busy-spinning. At 30 fps, one frame arrives every ~33 ms, so 10 ms
         * is always fast enough to keep up with the encoder.
         */
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        /** How long to sleep (ms) per iteration while FROZEN or while waiting for a keyframe. */
        private const val FROZEN_SLEEP_MS = 5L

        /** How frequently (ms) to poll [codecConfigDataHolder] at startup. */
        private const val CODEC_CONFIG_POLL_MS = 10L

        private const val TAG = "DecoderCoroutine"
    }

    // -----------------------------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------------------------

    /**
     * Initialises the MediaCodec decoder, runs the decode loop until cancelled, then releases
     * all resources.
     *
     * Suspend contract:
     *   - On cancellation: teardown completes in the `finally` block;
     *     [CancellationException] is rethrown (standard coroutine behaviour).
     *   - On any other exception: [onError] is invoked and the function returns normally.
     *
     * Must be called from a coroutine running on [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun run() {
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            setupDecoder(decoder)
            Timber.e("After setupDecoder()")
            runDecodeLoop(decoder)
            Timber.e("After runDecodeLoop()")
        } catch (e: CancellationException) {
            throw e  // Let the coroutine framework handle cancellation normally.
        } catch (e: Exception) {
            Timber.e(e, "Fatal decoder error")
            onError(e)
        } finally {
            // Always release — even on cancellation or error.
            // Each step is wrapped independently so one failure cannot prevent subsequent releases.
            runCatching { decoder.stop() }
                .onFailure { Timber.e(it, "decoder.stop() failed") }
            runCatching { decoder.release() }
                .onFailure { Timber.e(it, "decoder.release() failed") }
        }
    }

    private suspend fun setupDecoder(decoder: MediaCodec) {
        // The encoder emits SPS/PPS very shortly after startup (usually within 1–2 frames).
        // Block here until those bytes are available; we cannot configure the decoder without them.
        waitForCodecConfig()

        // Minimal format: width/height are sufficient for surface-output decoding.
        // The decoder learns everything else (profile, level, colour space) from the SPS/PPS.
        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            VideoTrainerDefaults.VideoResolution.HD_1024x720().width, // todo from settings
            VideoTrainerDefaults.VideoResolution.HD_1024x720().height, // todo from settings
        )
        decoder.configure(videoFormat, outputSurface, /* crypto = */ null, /* flags = */ 0)
        decoder.start()

        // Feed SPS/PPS before the first NAL unit so the decoder has stream parameters.
        feedCodecConfig(decoder)
    }

    // -----------------------------------------------------------------------------------------
    // Core decode loop
    // -----------------------------------------------------------------------------------------

    private suspend fun runDecodeLoop(decoder: MediaCodec) {
        // Start FROZEN: the ring buffer is empty at pipeline launch. The state machine will
        // transition to CATCHING_UP → PLAYING once the encoder has produced enough data.
        var state = State.FROZEN

        // Ring buffer metadata slot index of the next frame to feed to the decoder input.
        // Initialised to -1 (unset); seekToKeyframe() assigns a valid slot before first use.
        var readIndex = -1

        while (currentCoroutineContext().isActive) {
            // ── Jump detection ────────────────────────────────────────────────────────────────
            // The ViewModel sends Unit here whenever the user reduces delaySec.
            // tryReceive() is non-blocking. The CONFLATED channel ensures only the latest jump
            // intent survives; stale intermediate positions are automatically discarded.
            if (jumpChannel.tryReceive().isSuccess) {
                val targetPTS = computeTargetPts()
                Timber.d("#######D Jump signalled → targetPTS=$targetPTS")
                readIndex = executeJump(decoder, targetPTS)
                state = State.CATCHING_UP
                continue
            }

            val targetPTS = computeTargetPts()
//            Timber.d("#######D Loop iteration state=$state targetPTS=$targetPTS readIndex=$readIndex")

            // ── Encoder Stall check (rare condition) ──────────────────────────────────────────
            // If the newest frame in the buffer is still older than what we want to display,
            // there is nothing to decode. Sleep and try again.
            // Primary triggers: (1) pipeline startup — buffer is empty; (2) encoder stall (rare).
            if (nalRingBuffer.isEmpty() || nalRingBuffer.newestPts() < targetPTS) {
                if (state != State.FROZEN) {
                    Timber.d("#######D Entering FROZEN (newestPts=${nalRingBuffer.newestPts()} < targetPTS=$targetPTS)")
                    state = State.FROZEN
                }
                delay(FROZEN_SLEEP_MS)
                continue
            }

            // ── Exit FROZEN  and  Cursor initialization (safety net) ──────────────────────────
            // Data has arrived. Seek to the right I-frame and begin catch-up decode.
            if (state == State.FROZEN  ||  readIndex < 0) {
                readIndex = seekToKeyframe(targetPTS)
                if (readIndex < 0) {
                    // Buffer has data but no keyframes yet — retry after a short wait.
                    delay(FROZEN_SLEEP_MS)
                    continue
                }
                Timber.d("#######D Exiting FROZEN → CATCHING_UP at readIndex=$readIndex")
                state = State.PLAYING // State.CATCHING_UP
                // Fall through: start submitting frames this iteration.
            }

//todo 2brm            // ── Cursor initialisation (safety net) ────────────────────────────────────────────
//            if (readIndex < 0) {
//                readIndex = seekToKeyframe(targetPTS)
//                if (readIndex < 0) {
//                    delay(FROZEN_SLEEP_MS)
//                    continue
//                }
//                state = State.CATCHING_UP
//            }

            // ── Eviction detection ────────────────────────────────────────────────────────────
            // If the encoder's ring buffer has lapped us and evicted the slot our cursor points
            // to, getPtsOfNal() returns Long.MIN_VALUE. Re-seek to a valid I-frame and resume.
            // In normal operation. This should never occur
            if (nalRingBuffer.getPtsOfNal(readIndex) == Long.MIN_VALUE) {
                Timber.w("#######D readIndex=$readIndex evicted by encoder — re-seeking")
                readIndex = seekToKeyframe(targetPTS)
                if (readIndex < 0) {
                    delay(FROZEN_SLEEP_MS)
                    continue
                }
                state = State.CATCHING_UP
            }

            // ── Input submission ──────────────────────────────────────────────────────────────
            // Submit the frame at readIndex only if its PTS is at or before targetPTS.
            // If the frame is too new (framePts > targetPTS), we skip input this iteration;
            // this happens when the user has increased delaySec, and we have "lapped" the
            // target - we simply wait for wall-clock time to advance until targetPTS catches up
            val framePts = nalRingBuffer.getPtsOfNal(readIndex)
            if (framePts != Long.MIN_VALUE  &&  framePts <= targetPTS) {
                Timber.d("#######D Submitting frame state=$state targetPTS=$targetPTS framePts=$framePts readIndex=$readIndex")
                val submitted = trySubmitFrame(decoder, readIndex, framePts)
                if (submitted) {
                    // Advance the cursor. nextIndex() wraps in ring order.
                    // On the next iteration, Long.MIN_VALUE at the new slot means
                    // the encoder hasn't written there yet — handled above.
                    readIndex = nalRingBuffer.nextIndex(readIndex)
                }
                // If not submitted (no input buffer available this iteration), readIndex stays
                // put, and we retry submission on the next loop pass.
            }

            // ── Output drain ──────────────────────────────────────────────────────────────────
            // Drain one output buffer per iteration. Non-blocking (timeout = 0): if the decoder
            // has nothing ready, we just continue the outer loop rather than blocking here.
            // This keeps the state machine responsive and avoids starving the input path.
//            state = dequeueAndRenderOneDecoderBuffer(decoder, state, targetPTS)
            val bufDequeued = dequeueAndRenderOneDecoderBuffer(decoder)
            if (! bufDequeued) {
                delay(FROZEN_SLEEP_MS)
            }
        }
        Timber.i("#######D Loop terminated")
    }

    // -----------------------------------------------------------------------------------------
    // Output draining
    // -----------------------------------------------------------------------------------------

    // Pre-allocated once to avoid per-iteration object allocation on dequeueAndRenderOneDecoderBuffer()
    val bufferInfo = MediaCodec.BufferInfo()

    /** todo: fix comment (func was changed)
     * Dequeues one output buffer from the decoder (non-blocking) and render it to the
     * preconfigured decoder Surface.
     *
     * - In [State.PLAYING]: calls `releaseOutputBuffer(index, true)` to render the frame
     *   to [outputSurface].
     * - In [State.CATCHING_UP]: calls `releaseOutputBuffer(index, false)` to drop the frame.
     *   Once the decoded PTS reaches [targetPTS], transitions to [State.PLAYING].
     * - Handles [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED] as required by the MediaCodec spec.
     *
     * Returns the (possibly updated) [State].
     * Returns an indication whether a buffer was dequeue or not
     */
    private fun dequeueAndRenderOneDecoderBuffer(
        decoder: MediaCodec,
//        state: State,
//        targetPTS: Long,
    ): Boolean {
//        var currentState = state

        // Timeout = 0: return immediately if no output buffer is ready.
        val outputIndex = decoder.dequeueOutputBuffer(bufferInfo /*[out]*/, /* timeoutUs = */ 0)

        when {
            outputIndex >= 0 -> {
                val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                // Render iff we are in PLAYING and this is not an internal codec-config buffer.
                // todo zzzzzzz remove the CATCHING_UP state (only when playing)
//                val render = (currentState == State.PLAYING) && !isCodecConfig
                val render = !isCodecConfig
                decoder.releaseOutputBuffer(outputIndex, render)

                Timber.d("#######D dequeueAndRenderOneDecoderBuffer() outputIndex=$outputIndex")
                // CATCHING_UP → PLAYING transition: the decoded PTS has reached our target.
                // Any subsequent frames will be rendered to the surface normally.
//                if (currentState == State.CATCHING_UP && bufferInfo.presentationTimeUs >= targetPTS) {
//                    Timber.d("Catch-up complete at decodedPts=${bufferInfo.presentationTimeUs} → PLAYING")
//                    currentState = State.PLAYING
//                }
            }

//            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {  // -1
//                // No output ready this iteration — normal.
//                delay(FROZEN_SLEEP_MS)
//            }

            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {  // -2
                // The MediaCodec spec requires getOutputFormat() to be called here.
                // For surface-output decoding the format change is handled by the hardware path;
                // there is no further action needed on our side.
                val newFormat = decoder.outputFormat
                Timber.d("Output format changed: $newFormat")
            }

            // INFO_OUTPUT_BUFFERS_CHANGED (-3): deprecated since API 21; safe to ignore.
        }

//        return currentState
        return outputIndex >= 0
    }

    // -----------------------------------------------------------------------------------------
    // Input submission
    // -----------------------------------------------------------------------------------------

    /**
     * Attempts to feed the NAL unit at [index] into the next available decoder input buffer.
     *
     * Blocks up to [DEQUEUE_TIMEOUT_US] waiting for a free slot. If the decoder's input queue
     * is full (all slots occupied by frames being decoded), returns `false` so the outer loop
     * retries the same [index] on the next iteration without advancing the cursor.
     *
     * Returns `true` if the frame was successfully queued (caller should advance [readIndex]).
     * Returns `false` if no input slot was available, or if the slot was evicted between the
     * caller's [RingBuffer.getPts] check and this [RingBuffer.readNal] call.
     */
    private fun trySubmitFrame(decoder: MediaCodec, index: Int, framePts: Long): Boolean {
        val decoderBufIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (decoderBufIndex < 0) {
            // Decoder input queue is full; retry next iteration
            Timber.w("#######D decoder input queue full")
            return false
        }

        val destBuf = decoder.getInputBuffer(decoderBufIndex)
            ?: run {
                // Should not happen after a successful dequeue, but guard defensively.
                // Queue an empty buffer to return the slot to the codec.
                decoder.queueInputBuffer(decoderBufIndex, 0, 0, framePts, 0)
                Timber.w("#######D decoder input buffer is null (Shouldn't happen!!!)")
                return false
            }

        destBuf.clear() // todo: this is not needed because getInputBuffer() returns a cleared buf
        // Zero-copy read: dest is the codec's own native ByteBuffer, so no intermediate allocation
        val nalSize = nalRingBuffer.readNal(index, destBuf)

        if (nalSize <= 0) {
            // The slot was evicted by the encoder between our getPts() check and now.
            // Queue an empty buffer to return the slot; the outer loop will re-seek.
            Timber.w("readNal returned $nalSize for index=$index — slot was evicted")
            decoder.queueInputBuffer(decoderBufIndex, 0, 0, framePts, 0)
            return false
        }

        val flags = if (nalRingBuffer.isKeyFrame(index)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        decoder.queueInputBuffer(
            decoderBufIndex,
            /* offset */ 0,
            /* size */ nalSize,
            framePts,
            flags)
        Timber.d("#######D data submitted to decoder. index=$index pts=$framePts bytesWritten=$nalSize")
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Jump sequence
    // -----------------------------------------------------------------------------------------

    /**
     * Executes the full jump sequence when the user reduces [delaySec]:
     *
     * 1. `decoder.flush()` — clears all pending input/output buffers and resets codec state.
     * 2. Re-feed SPS/PPS — mandatory after flush; without it, the next IDR frame is rejected.
     * 3. Seek to the nearest I-frame at or before [targetPTS].
     *
     * Returns the metadata slot index from which decoding should resume.
     * The caller is responsible for setting [State.CATCHING_UP].
     */
    private fun executeJump(decoder: MediaCodec, targetPTS: Long): Int {
        decoder.flush()
        feedCodecConfig(decoder)
        return seekToKeyframe(targetPTS)
    }

    // -----------------------------------------------------------------------------------------
    // Keyframe seek
    // -----------------------------------------------------------------------------------------

    /**
     * Returns the metadata slot of the best I-frame to begin decoding from for [targetPTS].
     *
     * Preferred: the most recent IDR frame whose PTS ≤ [targetPTS] (via [RingBuffer.findNearestKeyframeBefore]).
     * Fallback:  the oldest available IDR frame (via [RingBuffer.findOldestKeyframe]).
     *            Used when the buffer only contains frames newer than [targetPTS] — e.g. at
     *            startup, or if the user sets an extremely long delay that exceeds the buffer's
     *            oldest entry. This may produce a brief visual glitch.
     *
     * Returns -1 only if the buffer contains no keyframes at all (caller must retry).
     */
    private fun seekToKeyframe(targetPTS: Long): Int {
        val index = nalRingBuffer.findNearestKeyframeBefore(targetPTS)
        if (index >= 0) return index

        // Primary seek failed: fall back to the oldest keyframe available.
        val fallback = nalRingBuffer.findOldestKeyframe()
        if (fallback < 0) {
            Timber.w("#######D seekToKeyframe: no keyframes in buffer — will retry")
        } else {
            Timber.w("#######D seekToKeyframe: no keyframe ≤ targetPTS=$targetPTS, using oldest at index=$fallback")
        }
        return fallback
    }

    // -----------------------------------------------------------------------------------------
    // Codec configuration (SPS/PPS)
    // -----------------------------------------------------------------------------------------

    /**
     * Feeds the SPS/PPS bytes from [codecConfigDataHolder][0] to the decoder as a
     * [MediaCodec.BUFFER_FLAG_CODEC_CONFIG] buffer.
     *
     * Must be called:
     *   (a) Once immediately after [MediaCodec.start] — before any NAL unit is submitted.
     *   (b) Again after every [MediaCodec.flush] — flush discards the decoder's internal
     *       codec configuration state; submitting an IDR without preceding SPS/PPS will
     *       produce corrupted output or an exception on many devices.
     */
    private fun feedCodecConfig(decoder: MediaCodec) {
        val configData = requireNotNull(codecConfigDataHolder[0]) {
            // Guaranteed non-null here because run() calls waitForCodecConfig() before
            // configuring the decoder, and executeJump() is only reachable after run() starts.
            "feedCodecConfig: SPS/PPS not available — waitForCodecConfig() must be called first"
        }

        val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        check(inputIndex >= 0) { "feedCodecConfig: no input buffer available for SPS/PPS" }

        val dest = decoder.getInputBuffer(inputIndex)!!
        //todo 2brm dest.clear() //todo: not needed because getInputBuffer() returns a cleared buf.
        dest.put(configData)

        decoder.queueInputBuffer(
            /* index  = */ inputIndex,
            /* offset = */ 0,
            /* size   = */ configData.size,
            /* presentationTimeUs = */ 0L,
            MediaCodec.BUFFER_FLAG_CODEC_CONFIG,
        )
    }

    /**
     * Suspends until [codecConfigDataHolder][0] is non-null, polling every [CODEC_CONFIG_POLL_MS].
     *
     * The encoder emits SPS/PPS within a few frames of starting (typically < 100 ms).
     * We must not configure or start the decoder before this data is available.
     */
    private suspend fun waitForCodecConfig() {
        while (codecConfigDataHolder[0] == null && currentCoroutineContext().isActive) {
            delay(CODEC_CONFIG_POLL_MS)
        }
        Timber.d("SPS/PPS received (${codecConfigDataHolder[0]?.size ?: 0} bytes)")
    }

    // -----------------------------------------------------------------------------------------
    // PTS helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Computes the target presentation timestamp for this iteration:
     * the wall-clock PTS we want to display right now, which is [delaySec] seconds behind live.
     *
     * Uses [System.nanoTime], exactly matching the PTS stamped by [EncoderCoroutine] at
     * drain time — this is what makes the delay arithmetic correct across both coroutines.
     */
    private fun computeTargetPts(): Long =
        (System.nanoTime() / 1000L) - (delaySecProvider() * 1_000_000L)
}

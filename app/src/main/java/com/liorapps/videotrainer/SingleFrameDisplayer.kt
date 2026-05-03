package com.liorapps.videotrainer

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.math.roundToInt

/*
  H.264 is not randomly accessible - to display frame N, you must decode all frames from the
   nearest preceding keyframe up to N. The decoder is also a stateful pipeline: it buffers
   several frames internally before producing output (codec latency). The architecture must
   manage this efficiently, especially for the common "next frame" case.

  Components
  ┌─────────────────────────────────────────────────────┐
  │              VideoFrameNavigator                     │  ← Public API
  │  displayFrameByRelativeLocation() / Next / Prev     │
  └──────────────────────────┬──────────────────────────┘
                             │ calls
  ┌──────────────────────────▼──────────────────────────┐
  │                  FrameDecoder                        │  ← Decode session manager
  │  Owns MediaCodec. Tracks decoder state.             │
  │  seekToFrame(targetIndex)                           │
  └──────────────┬───────────────────────┬──────────────┘
                 │                       │
  ┌──────────────▼──────┐   ┌────────────▼─────────────┐
  │   FrameBuffer       │   │   Surface (from View)     │
  │ (existing, given)   │   │  decoded frames drawn here│
  └─────────────────────┘   └──────────────────────────┘
*/

class SingleFrameDisplayer(
    private val cameraAndCodecConfig: CameraAndCodecConfig, // Always represents the current config (has @Volatile var members)
    private val nalBuffer: NalRingBuffer.AsLinearBuffer,
) {
    private var decoder: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    // --- Decoder session state ---
    // The keyframe index used to initialize the current codec session.
    // -1 means no active session.
    private var sessionKeyframeIndex: Int = -1

    // The last frame index fed into the codec's input queue.
    private var lastFedFrameIndex: Int = -1

    // The last frame index successfully rendered to the surface.
    var currentFrameIndex: Int = -1
        private set

    // The first frame in the buffer that is actually displayable
    // (i.e., has a preceding-or-equal keyframe).
    var firstDisplayableIndex: Int = -1
//    val firstDisplayableIndex: Int by lazy { computeFirstDisplayable() }
    var lastFrameIndex: Int = -1
//    val lastFrameIndex: Int get() = nalBuffer.count - 1

    companion object {
        private const val CODEC_LATENCY = 4      // extra frames fed past target to prime pipeline
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val OUTPUT_TIMEOUT_US = 10_000L
    }

    // A simple protection against a situation where multiple calls to seekToFrame() are done
    // in quick succession, and the decoder throws an exception of illegal state
    val decoderMutex = Mutex()

    /**
     * Initializes and starts the MediaCodec decoder using the provided [surface].
     *
     * Configures the decoder for H.264 (AVC) playback using SPS/PPS data and rotation
     * from [cameraAndCodecConfig]. If the [cameraAndCodecConfig] configuration is not yet
     * valid, it performs a brief retry before giving up.
     *
     * @param surface The output surface for rendered frames.
     * @return True if the decoder is ready, false if configuration failed.
     */
    suspend fun initialize(surface: Surface): Boolean {
        if (! cameraAndCodecConfig.isConfigurationValid()) {
            delay(50)
            if (! cameraAndCodecConfig.isConfigurationValid()) {
                Timber.e("#######S ! cameraAndCodecConfig.isConfigurationValid()")
                return false
            }
        }

        firstDisplayableIndex = computeFirstDisplayable()
        lastFrameIndex = nalBuffer.count - 1
        Timber.d("#######S initialize(): firstDisplayableIndex=$firstDisplayableIndex lastFrameIndex=$lastFrameIndex")

        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            VideoTrainerDefaults.VideoResolution.HD_1280x720().width, // todo from settings
            VideoTrainerDefaults.VideoResolution.HD_1280x720().height, // todo from settings
        )
        // SPS (NAL type 7) and PPS (NAL type 8) are set here once. The codec will
        // automatically re-apply them after each flush() - no need to re-feed them manually
        videoFormat.setByteBuffer("csd-0", cameraAndCodecConfig.sps)   // SPS NAL
        videoFormat.setByteBuffer("csd-1", cameraAndCodecConfig.pps)   // PPS NAL
        val rotation = cameraAndCodecConfig.computeRelativeDecoderRotation()
        videoFormat.setInteger(MediaFormat.KEY_ROTATION, rotation)

        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        if (!surface.isValid) {
            Timber.e("#######S invalid surface")
            return false
        }
        Timber.d("#######S calling decoder.configure() surface.isValid=${surface.isValid} sps-size=${cameraAndCodecConfig.sps?.capacity()}")
        decoder?.configure(videoFormat, surface, null, 0)
        decoder?.start()
        return true
    }

    /** stop(), release() and set decoder to null */
    fun release() {
        Timber.d("#######S releaseDecoder()")
        runCatching { decoder?.stop() }
            .onFailure { Timber.e(it, "#######S decoder.stop() failed") }
        runCatching { decoder?.release() }
            .onFailure { Timber.e(it, "#######S decoder.release() failed") }
        decoder = null
    }

    /*
    The Core: seekToFrame(targetIndex)
    This is the engine everything else calls into. There are two paths:

    Flush path: needed when seeking backward, or crossing into a different keyframe group.
      Wipes codec state and re-decodes from the nearest keyframe.
    Incremental path: when advancing forward within the same keyframe group — just keep
      feeding; no flush.
    */
    /*
    Why interleave? The codec's internal pipeline needs input before it can produce output.
    If you feed everything first then drain, you may deadlock if the input queue fills up
    before the codec has processed earlier frames.
    */
    /*
    Flush Condition Visualized
    Keyframe groups (K = keyframe, . = P-frame):

    Index:   0   1   2   3   4  ... 29  30  31  32  33  34 ... 59  60
    Frame:   K   .   .   .   .  ...  .   K   .   .   .   .  ...  .   K
    ├── Group A ──────────────┤├── Group B ──────────────┤├──...

    currentFrameIndex = 34 (in Group B, session keyframe = 30)

    seekToFrame(35)  → same group, forward  → NO FLUSH, feed 35 onwards ✓
    seekToFrame(33)  → same group, backward → FLUSH, re-feed from 30..33 ✓
    seekToFrame(55)  → same group, forward  → NO FLUSH, feed 35..55 ✓
    seekToFrame(12)  → different group      → FLUSH, re-feed from 0..12 ✓
    seekToFrame(62)  → different group      → FLUSH, re-feed from 60..62 ✓
    */
    suspend fun seekToFrame(targetIndex: Int) {
        if (decoder == null) {
            // We simply return since OK to call seekToFrame before initialization. This happens
            // when the app starts. The first screen (before any video capture) is for PAUSED
            // state, which displays a SingleFrameDisplayer but without any content to show
//            throw(IllegalStateException("decoder not initialized"))
            Timber.d("#######S decoder is null")
            return
        }
        val keyframeIndex = findKeyframeForFrame(targetIndex)
        require(keyframeIndex >= 0) { "No Keyframe for frame $targetIndex so it cannot be displayed" }
//        Timber.d("#######S seekToFrame() targetIndex=$targetIndex  keyframeIndex=$keyframeIndex")

        val needsFlush = (keyframeIndex != sessionKeyframeIndex) || // different keyframe group
                         (targetIndex   <= currentFrameIndex)     // going backward

        decoderMutex.withLock {
            if (needsFlush) {
//                Timber.d("#######S needsFlush")
                decoder?.flush()
                sessionKeyframeIndex = keyframeIndex
                lastFedFrameIndex = keyframeIndex - 1   // feed will start AT the keyframe
            }

            // Feed and drain in a single interleaved loop.
            // We feed up to targetIndex + CODEC_LATENCY to prime the pipeline.
            // We exit as soon as the target frame is rendered to the surface.
            val targetPTS = nalBuffer.getFramePts(targetIndex)
            var rendered = false
            var safetyLimit = (targetIndex - lastFedFrameIndex + CODEC_LATENCY + 10)
                .coerceAtLeast(CODEC_LATENCY + 10)  // loop guard

//            Timber.d("#######S <<<LOOP>>> targetIndex=$targetIndex targetPTS=$targetPTS lastFrameIndex=$lastFrameIndex safetyLimit=$safetyLimit")
            while (!rendered && safetyLimit-- > 0) {

                // ── Feed input ──────────────────────────────────────────────────────
                val nextToFeed = lastFedFrameIndex + 1
                val lastToFeed = (targetIndex + CODEC_LATENCY).coerceAtMost(lastFrameIndex)

                if (nextToFeed <= lastToFeed) {
                    val inputIndex = decoder?.dequeueInputBuffer(INPUT_TIMEOUT_US) ?: -1
                    if (inputIndex >= 0) {
                        val nalData = nalBuffer.getFrameData(nextToFeed)
                        val inputBuf = decoder?.getInputBuffer(inputIndex)!!
                        inputBuf.clear()
                        inputBuf.put(nalData)

                        // todo: flags can be a val with a bit more sophisticated initializer
                        var flags = if (nalBuffer.isKeyFrame(nextToFeed))
                            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        if (nextToFeed == lastToFeed) flags =
                            flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM

                        // Output frames are identified by their PTS
                        decoder?.queueInputBuffer(
                            inputIndex, 0, inputBuf.position(),
                            nalBuffer.getFramePts(nextToFeed), flags
                        )
                        lastFedFrameIndex = nextToFeed
                    } else {
                        Timber.e("#######S decoder.dequeueInputBuffer() returned $inputIndex")
                    }
                }

                // ── Drain output ─────────────────────────────────────────────────────
                val outputIndex = decoder?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
//                Timber.d("#######S outputIndex=$outputIndex")
                when {
                    outputIndex >= 0 -> {
//                        Timber.d("#######S bufferInfo.presentationTimeUs=${bufferInfo.presentationTimeUs}  targetPts=$targetPTS")
                        val isTarget = bufferInfo.presentationTimeUs == targetPTS
                        decoder?.releaseOutputBuffer(outputIndex, isTarget)
                        if (isTarget) rendered = true
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {  // -1
                        // todo: instead of using a timeout in dequeueOutputBuffer above, we should delay() here
                        // No output ready this iteration — normal.
                        delay(10)
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {  // -2
                        // The MediaCodec spec requires getOutputFormat() to be called here.
                        // For surface-output decoding the format change is handled by the hardware path;
                        // there is no further action needed on our side.
                        val newFormat = decoder?.outputFormat
                        Timber.d("#######S Output format changed: $newFormat")
                    }
                }
            }
//            Timber.d("#######S rendered=$rendered targetIndex=$targetIndex")

            if (rendered) currentFrameIndex = targetIndex
        } // mutex.withLock
    }


//    // Public API — VideoFrameNavigator
//    class VideoFrameNavigator(private val decoder: SingleFrameDisplayer) {

    /**
     * percentage in [0.0, 1.0]. Maps across the displayable frame range only,
     * so 0.0 = first displayable frame, 1.0 = last frame.
     */
    suspend fun displayFrameByRelativeLocation(percentage: Float) {
        val first = firstDisplayableIndex
        val last = lastFrameIndex
        val displayableCount = last - first + 1
        if (displayableCount <= 0) return

        val target = first + (percentage.coerceIn(0f, 1f) * displayableCount).roundToInt()
        seekToFrame(target)
    }

    /** Display the frame we displayed last. Used when the surface is destroyed and then becomes
     * available again (e.g. app goes to the background and back) */
    suspend fun redisplayLastDisplayedFrame() {
        val current = currentFrameIndex
        val frameIndex = if (current < 0) firstDisplayableIndex else current
        if (frameIndex <= lastFrameIndex) {
            seekToFrame(frameIndex)
        }
    }

    suspend fun displayNextFrame() {
        val current = currentFrameIndex
        val next = if (current < 0) firstDisplayableIndex else current + 1
        if (next <= lastFrameIndex) {
            seekToFrame(next)
        }
    }

    suspend fun displayPreviousFrame() {
//        Timber.d("#######S displayPreviousFrame() current=$currentFrameIndex  firstDisplayableIndex=$firstDisplayableIndex")
        val current = currentFrameIndex
        if (current > firstDisplayableIndex) {
            seekToFrame(current - 1)
        }
    }

    suspend fun seekToLastFrame() {
//        Timber.d("#######S seekToLastFrame() lastFrameIndex=$lastFrameIndex")
        seekToFrame(lastFrameIndex)
    }
//    }

    // Helper: Finding the First Displayable Frame
    private fun computeFirstDisplayable(): Int {
        for (i in 0..lastFrameIndex) { //todo can be much more efficient just look for the 1st keyframe in the buffer
            if (findKeyframeForFrame(i) >= 0) return i
        }
        return lastFrameIndex + 1 // sentinel: nothing displayable
    }

    // findNearestKeyframeBefore() is "strictly before", so handle the
    // case where the frame itself is a keyframe:
    private fun findKeyframeForFrame(index: Int): Int {
        if (nalBuffer.isKeyFrame(index)) return index
        return nalBuffer.findNearestKeyframeBefore(index)
    }

}

    /*
    Threading
    MediaCodec is not thread-safe. All calls to seekToFrame and everything in FrameDecoder must run on a single dedicated background thread. The public VideoFrameNavigator methods should dispatch to it:
    kotlinprivate val decoderThread = HandlerThread("frame-decoder").also { it.start() }
    private val handler = Handler(decoderThread.looper)

    fun displayNextFrame() {
        handler.post { /* call decoder.seekToFrame(...) */ }
    }
    For the scrollbar (displayFrameByRelativeLocation), debounce rapid scroll events — cancel any pending decode job before posting the new one:
    kotlinfun displayFrameByRelativeLocation(pct: Float) {
        handler.removeCallbacksAndMessages(null) // cancel pending seek
        handler.post { /* seekToFrame(...) */ }
    }
    */

    /*
    Performance Characteristics
            OperationFrames decodedTypical time (hardware decoder)displayNextFrame() (same group)1 + latency already primed~2–5 msdisplayPreviousFrame()Up to 30 (re-decode from keyframe)~10–30 msScrollbar jump (same group, forward)delta + CODEC_LATENCY~5–15 msScrollbar jump (cross keyframe)up to 30 + CODEC_LATENCY~15–40 ms
    The CODEC_LATENCY = 4 constant can be tuned: start at 0 and increment it if seekToFrame ever hits the safety limit, which indicates the pipeline needs more priming.
    */

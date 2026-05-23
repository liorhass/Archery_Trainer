package com.liorapps.archerytrainer.screens.video.logic

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class LoopPlayer(
    private val coroutineScope: CoroutineScope,
    private val cameraAndCodecConfig: CameraAndCodecConfig, // Always represents the current config (has @Volatile var members)
    private val nalBuffer: NalRingBuffer.AsLinearBuffer,
    private val speed: Float
) {
    private var gDecoder: MediaCodec? = null
    private var isLoopRunning = false

    fun startPlaybackLoop(surface: Surface): Job {
        isLoopRunning = true
        val job = coroutineScope.launch (Dispatchers.Default) {
            initialize(surface)
            start()
        }
        return job
    }

    /** stop(), release() and set decoder to null */
    fun release() {
        Timber.d("####LP release()")
        isLoopRunning = false
        runCatching { gDecoder?.stop() }
            .onFailure { Timber.e(it, "####LP decoder.stop() failed") }
        runCatching { gDecoder?.release() }
            .onFailure { Timber.e(it, "####LP decoder.release() failed") }
        gDecoder = null
    }

    /**
     * Initializes and starts the MediaCodec decoder using the provided [surface]
     *
     * Configures the decoder for H.264 (AVC) playback using SPS/PPS data and rotation
     * from [cameraAndCodecConfig]. If the [cameraAndCodecConfig] configuration is not yet
     * valid, it performs a brief retry before giving up
     *
     * @param surface The output surface for rendered frames
     * @return True if the decoder is ready, false if configuration failed
     */
    private suspend fun initialize(surface: Surface): Boolean {
        Timber.d("####LP initialize():")
        if (!surface.isValid) {
            Timber.e("####LP invalid surface")
            return false
        }
        if (! cameraAndCodecConfig.isConfigurationValid()) {
            delay(20)
            if (! cameraAndCodecConfig.isConfigurationValid()) {
                // This is OK on the app's startup. Since video is PAUSED, the VM tries to display
                // a single frame, but since no video was ever captured, there is no camera and
                // codec config yet
                Timber.d("####LP ! cameraAndCodecConfig.isConfigurationValid()")
                return false
            }
        }

        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            ArcheryTrainerDefaults.VideoResolution.HD_1280x720.width, // todo from settings
            ArcheryTrainerDefaults.VideoResolution.HD_1280x720.height, // todo from settings
        )
        // SPS (NAL type 7) and PPS (NAL type 8) are set here once. The codec will
        // automatically re-apply them after each flush() - no need to re-feed them manually
        videoFormat.setByteBuffer("csd-0", cameraAndCodecConfig.sps)   // SPS NAL
        videoFormat.setByteBuffer("csd-1", cameraAndCodecConfig.pps)   // PPS NAL
        val rotation = cameraAndCodecConfig.computeRelativeDecoderRotation()
        videoFormat.setInteger(MediaFormat.KEY_ROTATION, rotation)

        gDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        Timber.d("####LP calling decoder.configure() surface.isValid=${surface.isValid} sps-size=${cameraAndCodecConfig.sps?.capacity()}")
        gDecoder?.configure(videoFormat, surface, null, 0)
        gDecoder?.start()

        Timber.d("#######S initialize(): returning")
        return true
    }


    /**
     * Starts the video looping playback. Runs until the coroutine context is canceled.
     * This function handles scheduling directly through the system Surface via [MediaCodec].
     */
    private suspend fun start() {
        val decoder = gDecoder
        if (decoder == null) {
            Timber.e("####LP No decoder")
            return
        }

        while (isLoopRunning && currentCoroutineContext().isActive) {
            val firstIFrameIndex = findFirstIFrameIndex()
            if (firstIFrameIndex == -1) {
                // No valid I-frame found in the entire buffer; back off and retry
                delay(100)
                continue
            }

            val totalFramesToPlay = nalBuffer.count - firstIFrameIndex
            val framesSubmitted = AtomicInteger(0)
            val framesDrained = AtomicInteger(0)

            // Coordinate the concurrent pipeline within this loop iteration
            coroutineScope {
                // 1. Feeder Loop (Input)
                launch {
                    var currentIdx = firstIFrameIndex
                    while (currentIdx < nalBuffer.count && isActive && isLoopRunning) {

                        // Throttling to prevent over-filling the decoder pipelines
                        if (framesSubmitted.get() - framesDrained.get() > 8) {
                            delay(10)
                            continue
                        }

                        if (! isLoopRunning) continue
                        val inputIndex = decoder.dequeueInputBuffer(0)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val frameData = nalBuffer.getFrameData(currentIdx)
                                val size = frameData.remaining()
                                inputBuffer.put(frameData)

                                val ptsUs = nalBuffer.getFramePts(currentIdx)
                                val flags = if (nalBuffer.isKeyFrame(currentIdx)) {
                                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                                } else 0

                                if (! isLoopRunning) continue
                                decoder.queueInputBuffer(inputIndex, 0, size, ptsUs, flags)
                                framesSubmitted.incrementAndGet()
                                currentIdx++
                            }
                        } else {
                            // No input buffer available; yield context briefly to prevent hot looping
                            delay(5)
                        }
                    }
                }

                // 2. Drainer Loop (Output)
                launch {
                    var baseSystemTimeNs = 0L
                    var baseVideoPtsUs = 0L
                    val info = MediaCodec.BufferInfo()

                    while (framesDrained.get() < totalFramesToPlay && isActive && isLoopRunning) {
                        when (val outputIndex = decoder.dequeueOutputBuffer(info, 0)) {
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                delay(5) // Prevent high CPU usage when waiting for hardware
                            }
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // Handled implicitly by hardware configuration setup
                                // The MediaCodec spec requires getOutputFormat() to be called here.
                                // For surface-output decoding the format change is handled by the hardware path;
                                // there is no further action needed on our side.
                                val newFormat = decoder.outputFormat
                                Timber.d("#######LP Output format changed: $newFormat")
                            }
                            else -> {
                                if (outputIndex >= 0) {
                                    // Anchor the timeline on the very first decoded output frame
                                    if (framesDrained.get() == 0) {
                                        baseSystemTimeNs = System.nanoTime()
                                        baseVideoPtsUs = info.presentationTimeUs
                                    }

                                    val framePtsUs = info.presentationTimeUs

                                    // Calculate system time based on original PTS delta and playback speed
                                    val elapsedVideoNs = ((framePtsUs - baseVideoPtsUs) * 1000 / speed).toLong()
                                    val targetRenderTimeNs = baseSystemTimeNs + elapsedVideoNs

                                    // Hand off timing mechanics to the Android system compositor
                                    if (! isLoopRunning) continue
                                    decoder.releaseOutputBuffer(outputIndex, targetRenderTimeNs)
                                    framesDrained.incrementAndGet()
                                }
                            }
                        }
                    }
                }
            }

            // Flush the hardware decoder pipeline smoothly before starting the next iteration loop
            runCatching { decoder.flush() }
        }
    }

    /**
     * Scans forward from the beginning of the buffer to find the index of the first valid I-Frame.
     */
    private fun findFirstIFrameIndex(): Int {
        val total = nalBuffer.count
        for (i in 0 until total) {
            if (nalBuffer.isKeyFrame(i)) return i
        }
        return -1
    }
}

package com.liorapps.archerytrainer.screens.video.logic

import android.Manifest
import android.hardware.camera2.CameraDevice
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class DelayedVideoPlayer(
    private val cameraAndCodecConfig: CameraAndCodecConfig, // Always represents the current config (has @Volatile var members)
    private val ringBuffer: NalRingBuffer,
    private val scope: CoroutineScope,
) {
    /** Coroutine job for [EncoderCoroutine.run]. Null when the pipeline is stopped. */
    private var encoderJob: Job? = null

    /** Coroutine job for [DecoderCoroutine.run]. Null when stopped or surface is unavailable. */
    private var decoderJob: Job? = null

    /**
     * Starts the encoder and decoder pipeline:
     *
     * 1. Resets shared state so the new session starts clean:
     *    • [ringBuffer] is reset (stale frames from any prior session are discarded).
     *    • [codecConfigDataHolder][0] is cleared so the decoder waits for fresh SPS/PPS.
     * 2. Sets [playbackState] to PLAYING.
     * 3. Launches the encoder job.
     * 4. Launches the decoder job if a surface is already available; otherwise the decoder
     *    will be launched from [onSurfaceReady] when the surface arrives.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun start(
        surface: Surface,
        cameraDevice: CameraDevice?,
        cameraHandler: Handler,
        cameraThread: HandlerThread,
        delaySec: Int
    ) {
        Timber.d("####DV startPipeline()")
        // Discard any stale ring-buffer data and codec config from a prior session.
        ringBuffer.reset()
        cameraAndCodecConfig.invalidateCodecConfig()

        launchEncoderJob(cameraDevice, cameraHandler, cameraThread)

        // Launch decoder only if we already have a surface. If not, onSurfaceReady()
        // will launch it once AndroidExternalSurface delivers the surface.
        Timber.d("####DV startPipeline() currentSurface=$surface")
        launchDecoderJob(surface, delaySec)
    }

    /**
     * Stops the pipeline, cancelling both coroutine jobs:
     *
     * • Encoder job is canceled first, which triggers its `finally` block:
     *   close session → close camera → stop+release encoder.
     * • Decoder job is canceled next.
     * • [playbackState] is set to PAUSED.
     * • The ring buffer is **not** reset here; that happens at the start of the
     *   next [startPipeline] call (or in [onCleared]).
     */
    suspend fun stop() {
        Timber.d(Throwable(), "####DV stopPipeline(): Current call stack trace")
        // Cancellation is cooperative: each coroutine's finally block handles its own teardown.
        encoderJob?.cancel()
        encoderJob?.join()
        encoderJob = null

        decoderJob?.cancel()
        decoderJob?.join()
        decoderJob = null
    }

    /** Cancel the decoder job only (encoder job keeps running) */
    fun stopDecoderJob() {
        decoderJob?.cancel()
        decoderJob = null
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun launchEncoderJob(cameraDevice: CameraDevice?, cameraHandler: Handler, cameraThread: HandlerThread) {
        encoderJob = scope.launch(Dispatchers.IO + CoroutineName("ATEncoderCoroutine")) {
            try {
                EncoderCoroutine(
                    cameraDevice = cameraDevice,
                    cameraThread = cameraThread,
                    cameraHandler = cameraHandler,
                    ringBuffer = ringBuffer,
                    cameraAndCodecConfig = cameraAndCodecConfig,
//                    codecConfigDataHolder = codecConfigDataHolder,
//                    onError               = ::handleEncoderError,
                ).run()
            } catch (e: CancellationException) {
                Timber.d(e, "####DV encoder CancellationException")
            } catch (e: Exception) {
                Timber.e(e, "####DV EncoderCoroutine failed")
//                handleEncoderError(e)
                encoderJob?.cancel()
                encoderJob = null
            }
        }
    }

    fun launchDecoderJob(surface: Surface, delaySec: Int) {
        if (decoderJob?.isActive == true) {
            Timber.e("####DV launchDecoderJob(): decoderJob already active???")
            return
        }

        decoderJob = scope.launch(Dispatchers.IO + CoroutineName("ATDecoderCoroutine")) {
            try {
                Timber.d("####DV launchDecoderJob()")
                DecoderCoroutine(
//                    decoder               = decoder,
                    nalRingBuffer = ringBuffer,
                    cameraAndCodecConfig = cameraAndCodecConfig,
                    outputSurface = surface,
                    delaySecProvider = { delaySec },
//                    jumpChannel           = jumpChannel,
//                    onError               = ::handleDecoderError,
                ).run()
            } catch (e: CancellationException) {
                // No need to stop and release the decoder here because DecoderCoroutine.run()
                // has a finally block that takes care of that before we get here
                Timber.d(e, "####DV decoder CancellationException")
            } catch (e: Exception) {
                Timber.e(e, "####DV DecoderCoroutine failed")
//                handleDecoderError(e)
                decoderJob?.cancel()
                decoderJob = null
            }
        }
    }
}
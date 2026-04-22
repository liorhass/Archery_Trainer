package com.liorapps.videotrainer

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * By Claude
 * A few design decisions worth highlighting:
 *
 * codecConfigDataHolder: Array<ByteArray?> — Rather than the coroutine writing directly
 * to a var property on the ViewModel, the holder array is passed in.
 * This keeps the class self-contained and testable, while the caller marks the backing
 * field @Volatile as required by §11.
 *
 * PTS stamping — bufferInfo.presentationTimeUs is deliberately ignored. The PTS is
 * stamped at drain time with System.nanoTime() / 1000. This is a slight approximation
 * (a few µs after the frame was actually encoded), but it's on the same clock as the
 * decoder's targetPTS calculation, which is what matters for correct delay
 * arithmetic (§4.4).
 *
 * nalBytes copy in the drain loop — We do allocate a ByteArray here to pass into
 * ringBuffer.writeNal(). This is unavoidable: the encoder's output buffer must be released
 * back to the codec promptly, so we can't hold a reference to it. The allocation happens
 * at 30 fps on the IO thread and the array immediately moves into the DirectByteBuffer, so it
 * becomes short-lived garbage — acceptable, unlike the decoder side where we fixed readNal to
 * avoid per-frame allocation.
 *
 * Shutdown order — captureSession is stopped and closed before cameraDevice, which is closed
 * before encoder.stop(). This ensures no new frames arrive at the encoder input surface while
 * the codec is being torn down (§10.2). Each step is wrapped in runCatching so a failure in
 * one step doesn't prevent the rest from completing.
 */

/**
 * Encoder coroutine - Camera2 producer side of the TimeShift pipeline.
 *
 * Lifecycle: call [run] inside a coroutine scoped to the pipeline lifetime
 * (e.g. launched from the ViewModel on [Dispatchers.IO]).  Cancelling the
 * coroutine triggers a clean shutdown of the capture session, camera device,
 * and MediaCodec encoder in the correct order (§10.2).
 *
 * Outputs:
 *  - NAL units written into [ringBuffer] via [NalRingBuffer.writeNal].
 *  - SPS/PPS bytes written to [codecConfigDataHolder] once, on startup.
 *
 * @param context              Application context (for [CameraManager]).
 * @param ringBuffer           Shared ring buffer written by this coroutine.
 * @param codecConfigDataHolder Single-element array; [0] is set to the
 *                             SPS/PPS [ByteArray] when the encoder emits
 *                             BUFFER_FLAG_CODEC_CONFIG.  Marked @Volatile
 *                             by the caller (§11).
 * @param onError              Invoked on the IO thread if a fatal error occurs.
 *                             The coroutine cancels itself after calling this.
 */
class EncoderCoroutine(
    private val context: Context,
    private val ringBuffer: NalRingBuffer,
    private val codecConfigDataHolder: Array<ByteArray?>,   // index 0 = codecConfigData
    private val onError: (Throwable) -> Unit = {},
) {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Opens the camera, starts the encoder, and drains the encoder output queue
     * until the coroutine is cancelled.
     *
     * Must be called from a coroutine running on [Dispatchers.IO].
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    suspend fun run(): Unit = withContext(Dispatchers.IO) {

        // Camera2 callbacks require a looper - use a dedicated HandlerThread.
        val cameraThread = HandlerThread("VideoTrainer-Camera").also { it.start() }
        val cameraHandler = Handler(cameraThread.looper)

        var encoder: MediaCodec? = null
        var encoderInputSurface: Surface? = null
        var captureSession: CameraCaptureSession? = null
        var cameraDevice: CameraDevice? = null

        Timber.d("####### run()")
        try {
            // ------------------------------------------------------------------
            // 1. Configure and start the MediaCodec encoder  (§5.1)
            // ------------------------------------------------------------------
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                VideoTrainerDefaults.VIDEO_WIDTH,
                VideoTrainerDefaults.VIDEO_HEIGHT,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, VideoTrainerDefaults.BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VideoTrainerDefaults.FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoTrainerDefaults.I_FRAME_INTERVAL)
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
                )
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // Set up the Surface to receive raw video data for encoding
            // createInputSurface() must be called before encoder.start()  (§5.1)
            encoderInputSurface = encoder.createInputSurface()

            Timber.d("####### calling encoder.start()")
            encoder.start()
            Timber.d("####### back from encoder.start()")

            // ------------------------------------------------------------------
            // 2. Open the camera  (§4.1)
            // ------------------------------------------------------------------
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // TODO: LH cameraId should be a parameter passed into this function
            val cameraId = selectBackCamera(cameraManager)
            Timber.d("####### back from selectBackCamera()")

//            Timber.d("####### vvvvvvvvvvvvvvvvvv calling openCamera()")
            cameraDevice = openCamera(cameraManager, cameraId, cameraHandler)
//            Timber.d("####### ^^^^^^^^^^^^^^^^^^ back from openCamera()")

            // ------------------------------------------------------------------
            // 3. Create a CaptureSession targeting the encoder input surface
            // ------------------------------------------------------------------
            captureSession = createCaptureSession(cameraDevice, encoderInputSurface, cameraHandler)
            Timber.d("####### back from createCaptureSession()")

            // ------------------------------------------------------------------
            // 4. Submit a repeating capture request
            // ------------------------------------------------------------------
            val captureRequest =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(encoderInputSurface)
                }.build()

            captureSession.setRepeatingRequest(captureRequest, null, cameraHandler)
            Timber.d("####### back from setRepeatingRequest()")


            // ------------------------------------------------------------------
            // 5. Drain the encoder output queue
            // ------------------------------------------------------------------
            drainEncoder(encoder) // Loop until cancelled
            Timber.d("####### back from drainEncoder()")

        } catch (e: CancellationException) {
            Timber.d("####### Encoder coroutine cancelled")
            // Normal coroutine cancellation - fall through to finally block.
            throw e
        } catch (e: Exception) {
            Timber.d("####### Encoder coroutine failed")
            onError(e)
        } finally {
            Timber.d("####### Encoder coroutine exiting")
            // ------------------------------------------------------------------
            // Shutdown - order matters (§10.2):
            // Camera first, then encoder, to avoid frames arriving on a stopped codec.
            //
            // Note: We close the camera device directly. This automatically and 
            // gracefully closes any active capture session. Explicitly closing 
            // the session before the device can trigger a CAMERA_ERROR (3) on 
            // some devices (e.g. "Function not implemented") if the device is 
            // already closing or disconnected.
            // ------------------------------------------------------------------
            runCatching { cameraDevice?.close(); cameraDevice = null }
            captureSession = null
            runCatching { encoder?.stop() }
            runCatching { encoder?.release(); encoder = null }
            runCatching { encoderInputSurface?.release(); encoderInputSurface = null }
            cameraThread.quitSafely()
            cameraThread.join()
        }
    }

    // -------------------------------------------------------------------------
    // Drain loop
    // -------------------------------------------------------------------------

    /**
     * Continuously dequeues output buffers from [encoder] and dispatches each one.
     * Exits cleanly when the coroutine is canceled ([isActive] becomes false).
     */
    private suspend fun drainEncoder(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var bufferCount = 0

        while (currentCoroutineContext().isActive) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            when (outputIndex) {

                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet - we yield and retry
//                    Timber.d("####### drainEncoder(): INFO_TRY_AGAIN_LATER")
                    delay(5) // Prevent busy-spinning
                }

                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Timber.d("####### drainEncoder(): INFO_OUTPUT_FORMAT_CHANGED")
                    // Must call getOutputFormat() to satisfy the codec state machine;
                    // no further action needed for surface-to-surface output (§5.3).
                    val newFormat = encoder.outputFormat
                    Timber.w("Output format changed (wasn't expected): $newFormat")
                }

                else -> {
                    bufferCount++
                    Timber.d("####### drainEncoder(): bufferCount=$bufferCount outputIndex=$outputIndex buffer_flags=0x${
                        bufferInfo.flags.toString(16)}")
                    if (outputIndex < 0) {
                        // Unexpected negative value - skip.
                        continue
                    }

                    processEncoderBuffer(encoder, outputIndex, bufferInfo)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    /**
     * Processes a single encoder output buffer identified by [outputIndex].
     *
     * - CODEC_CONFIG buffers (SPS/PPS) are saved to [codecConfigDataHolder] and
     *   NOT written to the ring buffer (§3.3, §6).
     * - Normal NAL units are stamped with [System.nanoTime] / 1000 and written
     *   to [ringBuffer] (§4.4, §7.1).
     *
     * The output buffer is always released with `render = false` - the encoder
     * never renders to a display surface.
     */
    private fun processEncoderBuffer(
        encoder: MediaCodec,
        outputIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        Timber.d("####### processEncoderBuffer() buffer_size=${bufferInfo.size} buffer_offset=${bufferInfo.offset}")
        try {
            // Skip empty buffers (e.g. EOS marker with no payload).
            if (bufferInfo.size == 0) return

//            Timber.d("####### processEncoderBuffer() calling encoder.getOutputBuffer($outputIndex)")
            val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: return

            // Constrain the buffer view to the valid data region.
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                Timber.d("####### processEncoderBuffer() CODEC_CONFIG size=${bufferInfo.size}")
                // ---- SPS / PPS ----
                // Copy to a dedicated ByteArray; do NOT write into the ring buffer (§3.3).
                val configBytes = ByteArray(bufferInfo.size)
                outputBuffer.get(configBytes)
                // Visible to the decoder coroutine via @Volatile on the holder field (§11).
                codecConfigDataHolder[0] = configBytes

            } else {
                // ---- Normal NAL unit ----
                // PTS is derived from System.nanoTime(), not bufferInfo.presentationTimeUs,
                // to keep encoder and decoder on the same clock (§4.4).
                val pts = System.nanoTime() / 1000L
                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                Timber.d("####### processEncoderBuffer() normal NAL isKey=$isKey")

//                val nalBytes = ByteArray(bufferInfo.size)
//                outputBuffer.get(nalBytes)
//                ringBuffer.writeNal(pts, nalBytes, isKey)
                ringBuffer.writeNal(pts, outputBuffer, isKey)
//                Timber.d("####### processEncoderBuffer() back from ringBuffer.writeNal()")
            }

        } finally {
            // Always release - render=false because the encoder outputs to the ring buffer,
            // not to any display surface.
            encoder.releaseOutputBuffer(outputIndex, false)
//            Timber.d("####### processEncoderBuffer() back from encoder.releaseOutputBuffer()")
        }
    }

    // -------------------------------------------------------------------------
    // Camera2 helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the ID of the first back-facing camera, or the first available
     * camera if no back-facing camera is found.
     */
    private fun selectBackCamera(manager: CameraManager): String {
        val ids = manager.cameraIdList
        Timber.d("####### selectBackCamera() available camera IDs=$ids")
        for (id in ids) {
            val characteristics = manager.getCameraCharacteristics(id)
            Timber.d("####### selectBackCamera() camera ID=$id")
            Timber.d("#######   selectBackCamera() camera LENS_FACING=${characteristics.get(CameraCharacteristics.LENS_FACING)}")
        }

        val cameraId = ids.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: ids.first()
        Timber.d("####### selectBackCamera() cameraId=$cameraId")
        return cameraId
    }

    /**
     * Opens the camera with [cameraId] and suspends until [CameraDevice.StateCallback.onOpened]
     * fires. Cancellation closes the device if it was already opened.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler,
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        Timber.d("####### openCamera()")
        manager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (cont.isActive) {
                        Timber.d("####### openCamera() camera=$device")
                        cont.resume(device)
                    } else {
                        device.close()
                    }
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    Timber.w("####### ####### openCamera() onDisconnected isActive=${cont.isActive}")
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Camera $cameraId disconnected during open")
                        )
                    } else {
                        // Camera disconnected after it was already opened.
                        // Notify the onError callback so the pipeline stops cleanly.
                        onError(IllegalStateException("Camera $cameraId disconnected"))
                    }
                }

                override fun onError(device: CameraDevice, error: Int) {
                    Timber.w("####### ####### openCamera() onError isActive=${cont.isActive} error=$error")
                    device.close()
                    if (cont.isActive) {
                        cont.resumeWithException(
                            RuntimeException("Camera $cameraId open error: $error")
                        )
                    } else {
                        onError(RuntimeException("Camera $cameraId error: $error"))
                    }
                }
            },
            handler
        )

        cont.invokeOnCancellation { /* device closed in finally block of run() if open */
            Timber.w("####### ####### openCamera() cont.invokeOnCancellation")
        }
    }

    /**
     * Creates a [CameraCaptureSession] targeting [surface] and suspends until
     * [CameraCaptureSession.StateCallback.onConfigured] fires.
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        surface: Surface,
        handler: Handler,
    ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        Timber.d("####### createCaptureSession()")
        // TODO: LH this is obsolete. see: https://developer.android.com/reference/android/hardware/camera2/CameraDevice#createCaptureSession(java.util.List%3Candroid.view.Surface%3E,%20android.hardware.camera2.CameraCaptureSession.StateCallback,%20android.os.Handler)
        device.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cont.isActive) {
                        Timber.d("####### createCaptureSession() got session=$session")
                        cont.resume(session)
                    } else {
                        session.close()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            RuntimeException("CaptureSession configuration failed")
                        )
                    }
                }
            },
            handler,
        )
    }
}

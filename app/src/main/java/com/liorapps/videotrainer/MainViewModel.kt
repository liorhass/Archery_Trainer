package com.liorapps.videotrainer

import android.app.Application
import android.view.Surface
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liorapps.videotrainer.navigation.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * PlaybackState — the only two states visible to the UI.
 *
 * The decoder's internal PLAYING / FROZEN / CATCHING_UP state machine is an
 * implementation detail of [DecoderCoroutine] and is intentionally not
 * surfaced here.
 */
enum class PlaybackState { PAUSED, PLAYING }

enum class CameraPermissionState { CHECKING, GRANTED, DENIED }

/**
 * MainViewModel — the single point of coordination between the UI and the
 * encoder/decoder pipeline.
 *
 * Responsibilities
 * ─────────────────
 * • Expose observable Compose state ([playbackState], [delaySec], [errorMessage]).
 * • Own the shared pipeline resources ([ringBuffer], [codecConfigDataHolder], [jumpChannel]).
 * • Launch and cancel the encoder and decoder coroutine jobs at the right times.
 * • Detect delay reductions and signal the decoder via [jumpChannel].
 * • Handle surface lifecycle events from [AndroidExternalSurface].
 *
 * Does NOT
 * ─────────
 * • Touch [MediaCodec] directly — all codec operations stay inside the coroutine classes.
 * • Hold strong references to [EncoderCoroutine] or [DecoderCoroutine] beyond their [Job]
 *   handles; they are fire-and-cancel objects.
 * • Manage the decoder's internal state machine.
 *
 * Threading
 * ─────────
 * All public methods are called from the main thread (Compose / DisposableEffect).
 * The coroutines run on [Dispatchers.IO].  Cross-thread shared state is synchronized
 * exactly as specified in §11 of the architecture plan.
 *
 * @param application  Application context forwarded to [EncoderCoroutine] for [CameraManager].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // ─────────────────────────────────────────────────────────────────────────
    // UI-observable Compose state  (main-thread reads + writes only)
    // ─────────────────────────────────────────────────────────────────────────
    // TODO LH: convert all of these to StateFlow (like cameraPermissionState)

    val backStack = mutableStateListOf<NavKey>(NavKey.Main)

    // Camera Selector State
    var selectedCamera by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        private set

    /** Whether the pipeline is currently running. Drives the Play/Pause button label. */
    var playbackState: PlaybackState by mutableStateOf(PlaybackState.PAUSED)
        private set

    /**
     * User-selected delay in seconds. Readable from [Dispatchers.IO] via the lambda passed
     * to [DecoderCoroutine] — [mutableStateOf] is backed by @Volatile, so cross-thread
     * reads are safe (§11).
     */
    var delaySec: Int by mutableStateOf(VideoTrainerConfig.DEFAULT_DELAY_SEC)
        private set

    /** Non-null when a fatal pipeline error has occurred. The UI should surface this message. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    private val _cameraPermissionStateFlow = MutableStateFlow<CameraPermissionState>(CameraPermissionState.CHECKING)
    val cameraPermissionStateFlow = _cameraPermissionStateFlow.asStateFlow()
    fun onCameraPermissionGranted() { _cameraPermissionStateFlow.value = CameraPermissionState.GRANTED }
    fun onCameraPermissionDenied() { _cameraPermissionStateFlow.value = CameraPermissionState.DENIED }

    // ─────────────────────────────────────────────────────────────────────────
    // Pipeline-internal shared state
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The single ring buffer shared by the encoder (writer) and decoder (reader).
     * Allocated once; reset at the start of each new playback session.
     */
    private val ringBuffer = NalRingBuffer()

    /**
     * Single-element array holding the SPS/PPS bytes emitted by the encoder.
     *
     * The @Volatile annotation on the *reference* (this field) is the synchronization
     * primitive mandated by §11.  The element [codecConfigDataHolder][0] is written
     * exactly once per session by the encoder coroutine, before the decoder coroutine
     * reads it; [DecoderCoroutine.waitForCodecConfig] polls with coroutine [delay] calls,
     * whose suspension points provide the necessary visibility across threads.
     */
    @Volatile
    private var codecConfigDataHolder: Array<ByteArray?> = arrayOfNulls(1)

    /**
     * Signals the decoder to execute the jump sequence (§9) when the user reduces [delaySec].
     *
     * [Channel.CONFLATED]: rapid slider movements collapse into a single seek to the
     * latest target PTS, discarding stale intermediate positions.
     */
    private val jumpChannel = Channel<Unit>(Channel.CONFLATED)

    // ─────────────────────────────────────────────────────────────────────────
    // Surface and coroutine job handles  (main-thread only)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The hardware output surface provided by [AndroidExternalSurface].
     * Nullable because the surface may arrive before or after [startPipeline] is called,
     * and is destroyed on configuration changes (§10.1).
     */
    private var currentSurface: Surface? = null

    /** Coroutine job for [EncoderCoroutine.run]. Null when the pipeline is stopped. */
    private var encoderJob: Job? = null

    /** Coroutine job for [DecoderCoroutine.run]. Null when stopped or surface is unavailable. */
    private var decoderJob: Job? = null

    // Expose video dimensions as soon as they are known (e.g. from MediaFormat after
    // MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, or set statically if dimensions are fixed).
    // Set this once you know the dimensions, e.g. in your MediaCodec callback:
    //   _videoSize.value = VideoSize(format.getInteger(MediaFormat.KEY_WIDTH),
    //                                format.getInteger(MediaFormat.KEY_HEIGHT))
    data class VideoSize(val width: Int, val height: Int)
    private val _videoSize = MutableStateFlow<VideoSize>(VideoSize(VideoTrainerConfig.VIDEO_WIDTH, VideoTrainerConfig.VIDEO_HEIGHT))
    val videoSize: StateFlow<VideoSize> = _videoSize.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — called from the UI / Compose lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Toggles between PLAYING and PAUSED:
     * • PAUSED → starts the encoder + decoder pipeline.
     * • PLAYING → stops capture; the ring buffer is preserved.
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun togglePlayback() {
        Timber.d("#######VM togglePlayback() currentPlaybackState=$playbackState")
        when (playbackState) {
            PlaybackState.PAUSED  -> startPipeline()
            PlaybackState.PLAYING -> stopPipeline()
        }
    }

    /**
     * Updates the playback delay and, if the delay was *reduced*, signals the decoder to
     * execute the jump sequence (§9).  Increasing the delay requires no action — the decoder
     * will naturally fall behind or enter FROZEN state.
     *
     * Safe to call while the pipeline is running or stopped.
     *
     * @param newDelaySec  New delay in seconds; must be in [MIN_DELAY_SEC, MAX_DELAY_SEC].
     */
    fun onDelayChanged(newDelaySec: Int) {
        val wasReduced = newDelaySec < delaySec
        delaySec = newDelaySec
        if (wasReduced && playbackState == PlaybackState.PLAYING) {
            // A jump is needed only when delay decreases (targetPTS moves forward in time).
            // Increasing delay requires no action — the decoder naturally falls behind.
            // trySend on a CONFLATED channel never blocks and never fails due to capacity.
            jumpChannel.trySend(Unit)
        }
    }

    /**
     * Called by the Compose UI when [AndroidExternalSurface] provides a ready hardware surface.
     *
     * If the pipeline is already PLAYING (i.e. [startPipeline] was called before the surface
     * arrived) and the decoder is not yet running, the decoder job is launched immediately.
     *
     * @param surface  The hardware surface that [DecoderCoroutine] will render into.
     */
    fun onSurfaceReady(surface: Surface) {
        Timber.d("#######VM onSurfaceReady()")
        currentSurface = surface
        if (playbackState == PlaybackState.PLAYING && decoderJob?.isActive != true) {
            launchDecoderJob(surface)
        }
    }

    /**
     * Called by the Compose UI when [AndroidExternalSurface] destroys its surface
     * (e.g. on a configuration change such as screen rotation).
     *
     * The decoder is torn down because its output surface is now invalid (§10.2).
     * The encoder keeps running — the camera capture continues uninterrupted so the
     * ring buffer stays current during the rotation.  When the new surface is provided
     * via [onSurfaceReady], a fresh decoder job is launched automatically.
     */
    fun onSurfaceDestroyed() {
        decoderJob?.cancel()
        decoderJob = null
        currentSurface = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pipeline lifecycle  (private)
    // ─────────────────────────────────────────────────────────────────────────

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
    @RequiresPermission(android.Manifest.permission.CAMERA)
    private fun startPipeline() {
        Timber.d("#######VM startPipeline()")
        if (playbackState == PlaybackState.PLAYING) return

        // Discard any stale ring-buffer data and codec config from a prior session.
        ringBuffer.reset()
        codecConfigDataHolder[0] = null
        errorMessage = null

        playbackState = PlaybackState.PLAYING

        launchEncoderJob()

        // Launch decoder only if we already have a surface. If not, onSurfaceReady()
        // will launch it once AndroidExternalSurface delivers the surface.
        Timber.d("#######VM startPipeline() currentSurface=$currentSurface")
        currentSurface?.let { surface -> launchDecoderJob(surface) }
    }

    /**
     * Stops the pipeline, cancelling both coroutine jobs:
     *
     * • Encoder job is canceled first, which triggers its `finally` block:
     *   close session → close camera → stop/release encoder (§10.2).
     * • Decoder job is canceled next, which triggers its `finally` block:
     *   stop/release decoder.
     * • [playbackState] is set to PAUSED.
     * • The ring buffer is **not** reset here; that happens at the start of the
     *   next [startPipeline] call (or in [onCleared]).
     */
    private fun stopPipeline() {
        Timber.d("#######VM stopPipeline()")
        // Cancellation is cooperative: each coroutine's finally block handles its own teardown.
        encoderJob?.cancel()
        encoderJob = null

        decoderJob?.cancel()
        decoderJob = null

        playbackState = PlaybackState.PAUSED
    }
//    fun stopPipeline() {
//        // Architecture §10.2 teardown order:
//        encoderJob?.cancel()   // 1. Stops camera + encoder
//        decoderJob?.cancel()   // 2. Stops decoder
//        viewModelScope.launch {
//            encoderJob?.join()
//            decoderJob?.join()
//            ringBuffer.reset() // Only after BOTH coroutines have fully stopped
//        }
//    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job launchers
    // ─────────────────────────────────────────────────────────────────────────

    @RequiresPermission(android.Manifest.permission.CAMERA)
    private fun launchEncoderJob() {
        encoderJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                EncoderCoroutine(
                    context               = getApplication(),
                    ringBuffer            = ringBuffer,
                    codecConfigDataHolder = codecConfigDataHolder,
                    onError               = ::handlePipelineError,
                ).run()
            } catch (e: Exception) {
                Timber.e(e, "EncoderCoroutine failed")
                handlePipelineError(e)
            }
        }
    }

    private fun launchDecoderJob(surface: Surface) {
        decoderJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("launchDecoderJob()")
                DecoderCoroutine(
                    nalRingBuffer            = ringBuffer,
                    codecConfigDataHolder = codecConfigDataHolder,
                    outputSurface         = surface,
                    delaySecProvider      = { delaySec },   // reads @Volatile-backed Compose state
                    jumpChannel           = jumpChannel,
                    onError               = ::handlePipelineError,
                ).run()
            } catch (e: Exception) {
                Timber.e(e, "DecoderCoroutine failed")
                handlePipelineError(e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invoked by either coroutine on a fatal, non-cancellation error.
     *
     * Switches to the main thread (via [viewModelScope]) to safely update Compose state,
     * then stops the entire pipeline so neither coroutine is left running in a broken state.
     *
     * [onError] callbacks from [EncoderCoroutine] and [DecoderCoroutine] are called on
     * [Dispatchers.IO], so the [viewModelScope.launch] dispatch to main is required.
     */
    private fun handlePipelineError(e: Throwable) {
        Timber.e(e, "Pipeline error")
        viewModelScope.launch {                          // no dispatcher = main thread
            errorMessage = e.message ?: "Unknown pipeline error"
            stopPipeline()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by the framework when this ViewModel is permanently destroyed
     * (process death, NavBackStack pop, etc.).
     *
     * [stopPipeline] cancels both jobs; cancellation is cooperative, so the coroutines'
     * finally blocks will still run their codec/camera teardown sequences. The ring buffer
     * is reset afterward to free the 90 MB native allocation cleanly (though the GC would
     * eventually collect it; this makes the timing explicit).
     *
     * Note: we do not join/await the jobs before resetting the ring buffer. In the
     * [onCleared] context this is acceptable: the process is either being destroyed or
     * the ViewModel scope is being cancelled, and any in-flight coroutine work is also
     * being torn down by the framework.
     */
    override fun onCleared() {
        super.onCleared()
        stopPipeline()
        ringBuffer.reset()
    }



    fun switchCamera() {
        selectedCamera = if (selectedCamera == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun updateDelay(value: Int) {
        delaySec = value.coerceIn(VideoTrainerConfig.MIN_DELAY_SEC, VideoTrainerConfig.MAX_DELAY_SEC)
    }

    fun navigateTo(key: NavKey) {
        backStack.add(key)
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }

    //todo lh: 2brm
    // Called from the UI when the camera permission result is received
//    fun onPermissionResult(isGranted: Boolean) {
//        _isCameraPermissionGranted.value = isGranted
//        if (isGranted) {
//            Log.i("MainViewModel", "Camera permission granted")
//        } else {
//            Log.i("MainViewModel", "Camera permission denied")
//        }
//    }

    /* todo lh: 2brm  *********************************************************************************
    Step 5: Usage Example
    Here is how the ViewModel wires DecoderCoroutine together with the already-implemented
    components. This is the minimal integration needed for the full pipeline.

    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun startPipeline(outputSurface: Surface) {
        encoderJob = viewModelScope.launch(Dispatchers.IO) {
            EncoderCoroutine(
                context        = application,
                ringBuffer     = ringBuffer,
                codecConfigDataHolder = codecConfigDataHolder,
                onError        = { e -> /* emit to UI StateFlow */ },
            ).run()
        }

        decoderJob = viewModelScope.launch(Dispatchers.IO) {
            DecoderCoroutine(
                ringBuffer            = ringBuffer,
                codecConfigDataHolder = codecConfigDataHolder,
                outputSurface         = outputSurface,
                delaySecProvider      = { delaySec },   // lambda captures @Volatile-backed state
                jumpChannel           = jumpChannel,
                onError               = { e -> /* emit to UI StateFlow */ },
            ).run()
        }
    }
     ***********************************************************************************/


    /**********************************************************************************
    And in the Compose UI:
    ***********************************************************************************/
    /*
    @Composable
    fun TimeShiftScreen(viewModel: TimeShiftViewModel) {
        Column {
            AndroidExternalSurface(modifier = Modifier.weight(1f)) {
                onSurface { surface, _, _ ->
                    viewModel.onSurfaceReady(surface)
                    surface.onDestroyed { viewModel.onSurfaceDestroyed() }
                }
            }
            Slider(
                value       = viewModel.delaySec.toFloat(),
                onValueChange = { viewModel.onDelayChanged(it.roundToInt()) },
                valueRange  = TimeShiftConfig.MIN_DELAY_SEC.toFloat()..
                        TimeShiftConfig.MAX_DELAY_SEC.toFloat(),
            )
        }
    }
    */
}


//vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
//vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
//vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
//vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
//vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
//
// TimeShiftViewModel — highlights worth noting:
//
// Ring buffer reset on startPipeline(), not on stopPipeline(). This means the buffer
//   persists through the pause (as you originally described), but is cleared the moment the
//   user hits Play again so the decoder never sees stale frames from a prior session.
// codecConfigDataHolder[0] = null on startPipeline(). The encoder will re-emit fresh
//   SPS/PPS within a few frames. Clearing the slot here forces
//   DecoderCoroutine.waitForCodecConfig() to block until that happens, preventing the
//   decoder from accidentally using the previous session's codec config.
// Encoder survives surface destruction, decoder does not. When onSurfaceDestroyed() is
//   called (e.g. screen rotation), only decoderJob is cancelled. The encoder keeps the
//   camera and ring buffer running. When onSurfaceReady() fires with the new surface,
//   launchDecoderJob() is called again and the decoder picks up from wherever the ring buffer is.
// Surface/pipeline ordering is race-free because both startPipeline() and onSurfaceReady()
//   are called on the main thread, so they're always serialized.
// handlePipelineError dispatches back to the main thread via an undecorated
//   viewModelScope.launch (which defaults to Dispatchers.Main) before touching any
//   Compose state, since the error callbacks fire on Dispatchers.IO.


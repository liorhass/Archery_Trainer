package com.liorapps.archerytrainer.screens.video.logic

import android.Manifest
import android.app.Application
import android.view.Surface
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * • Handle surface lifecycle events from [androidx.compose.foundation.AndroidExternalSurface].
 *
 * Does NOT
 * ─────────
 * • Touch [android.media.MediaCodec] directly — all codec operations stay inside the coroutine classes.
 * • Hold strong references to [EncoderCoroutine] or [DecoderCoroutine] beyond their [kotlinx.coroutines.Job]
 *   handles; they are fire-and-cancel objects.
 * • Manage the decoder's internal state machine.
 *
 * Threading
 * ─────────
 * All public methods are called from the main thread (Compose / DisposableEffect).
 * The coroutines run on [kotlinx.coroutines.Dispatchers.IO].  Cross-thread shared state is synchronized
 * exactly as specified in §11 of the architecture plan.
 *
 * @param application  Application context forwarded to [EncoderCoroutine] for [CameraManager].
 */
class DelayedVideoViewModel(application: Application, val settingsRepo: SettingsRepository) : AndroidViewModel(application) {
    // ─────────────────────────────────────────────────────────────────────────
    // UI-observable Compose state  (main-thread reads + writes only)
    // ─────────────────────────────────────────────────────────────────────────
    // TODO LH: convert all of these to StateFlow (like cameraPermissionState)

    // Camera Selector State
    var selectedCamera by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        private set

    /** Whether the pipeline is currently running. Drives the Play/Pause button label. */
    var playbackState: PlaybackState by mutableStateOf(PlaybackState.PAUSED)
        private set

    /** Whether a "Buffering" countdown indicator should be displayed, and for how long (0 - don't display) */
    private val _isBuffering = MutableStateFlow(0)
    val bufferingTime = _isBuffering.asStateFlow()

//    /**
//     * User-selected delay in seconds. Readable from [Dispatchers.IO] via the lambda passed
//     * to [DecoderCoroutine] — [mutableStateOf] is backed by @Volatile, so cross-thread
//     * reads are safe (§11).
//     */
//    var delaySec: Int by mutableStateOf(VideoTrainerConfig.DEFAULT_DELAY_SEC)
//        private set
//    val delaySec: StateFlow<Int> = settingsRepo.delaySec
//        .stateIn(viewModelScope, SharingStarted.Eagerly, VideoTrainerDefaults.DEFAULT_DELAY_SEC)
    val settingsFlow: StateFlow<SettingsRepository.Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsRepository.Settings())

    /** Non-null when a fatal pipeline error has occurred. The UI should surface this message. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    var horizontalDragSensitivity: Float by mutableFloatStateOf(60f) //todo from settings
        private set

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen = _isFullScreen.asStateFlow()

    private val _cameraPermissionStateFlow =
        MutableStateFlow<CameraPermissionState>(CameraPermissionState.CHECKING)
    val cameraPermissionStateFlow = _cameraPermissionStateFlow.asStateFlow()
    fun onCameraPermissionGranted() { _cameraPermissionStateFlow.update { CameraPermissionState.GRANTED } }
    fun onCameraPermissionDenied() { _cameraPermissionStateFlow.update { CameraPermissionState.DENIED } }


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
     * reads it; [DecoderCoroutine.waitForCodecConfig] polls with coroutine [kotlinx.coroutines.delay] calls,
     * whose suspension points provide the necessary visibility across threads.
     */
    private val cameraAndCodecConfig = CameraAndCodecConfig()

    val singleFrameDisplayer =
        SingleFrameDisplayer(cameraAndCodecConfig, ringBuffer.AsLinearBuffer())

    /**
     * Signals the decoder to execute the jump sequence (§9) when the user reduces [delaySec].
     *
     * [Channel.CONFLATED]: rapid slider movements collapse into a single seek to the
     * latest target PTS, discarding stale intermediate positions.
     */
//todo 2brm    private val jumpChannel = Channel<Unit>(Channel.CONFLATED)

    // ─────────────────────────────────────────────────────────────────────────
    // Surface and coroutine job handles  (main-thread only)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The hardware output surface provided by [androidx.compose.foundation.AndroidExternalSurface].
     * Nullable because the surface may arrive before or after [startPipeline] is called,
     * and is destroyed on configuration changes (§10.1).
     */
    private var currentSurface: Surface? = null

    /** Coroutine job for [EncoderCoroutine.run]. Null when the pipeline is stopped. */
    private var encoderJob: Job? = null

    /** Coroutine job for [DecoderCoroutine.run]. Null when stopped or surface is unavailable. */
    private var decoderJob: Job? = null

    private val _videoResolution = MutableStateFlow<ArcheryTrainerDefaults.VideoResolution>(
        ArcheryTrainerDefaults.VideoResolution.HD_1280x720
    )
    val videoResolution: StateFlow<ArcheryTrainerDefaults.VideoResolution> = _videoResolution.asStateFlow()

    private val _singleFrameSliderPositionFlow = MutableStateFlow(0f)
    @OptIn(ExperimentalCoroutinesApi::class)
    val singleFrameSliderPositionFlow: StateFlow<Float> = _singleFrameSliderPositionFlow
        .filterNotNull()
//        .drop(1) explain me
        .flatMapLatest { value ->
            flow {
                val result = withContext(Dispatchers.IO) {
                    Timber.d("#######VM singleFrameScrollbarPosition changed. position=${_singleFrameSliderPositionFlow.value}")
                    singleFrameDisplayer.displayFrameByRelativeLocation(value)
                    _singleFrameSliderPositionFlow.value
                }
                emit(result)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
//            started = SharingStarted.Lazily, // Eagerly,
            initialValue = 0f
        )
//    val singleFrameScrollbarPosition: StateFlow<Float> = _singleFrameSliderPosition.asStateFlow()
//    init {
//        viewModelScope.launch {
//            // collectLatest handles the "conflation" (disregard old values when a new one arrives)
//            _singleFrameSliderPosition.collectLatest { position ->
//                Timber.d("#######VM singleFrameScrollbarPosition changed. position=$position")
////                processNewPosition(position)
//            }
//        }
//    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — called from the UI / Compose lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Toggles between PLAYING and PAUSED:
     * • PAUSED → starts the encoder + decoder pipeline.
     * • PLAYING → stops capture; the ring buffer is preserved.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun onTogglePlayback() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("#######VM togglePlayback() currentPlaybackState=$playbackState")
            when (playbackState) {

                PlaybackState.PAUSED -> {
                    playbackState = PlaybackState.PLAYING
                    singleFrameDisplayer.release()
                    ringBuffer.reset()
                    startPipeline()
                    _isBuffering.update { settingsFlow.value.delaySec } // Start the on screen "buffering" countdown
                }

                PlaybackState.PLAYING -> {
                    playbackState = PlaybackState.PAUSED
                    stopPipeline()
                    if (currentSurface != null) {
                        if (singleFrameDisplayer.initialize(currentSurface!!)) {
                            singleFrameDisplayer.seekToLastFrame()  // When we pause, we display the last captured frame
                            Timber.d("#######VM singleFrameDisplayer _singleFrameSliderPositionFlow.value = 1.0f")
                            _singleFrameSliderPositionFlow.update { 1.0f } // When we pause we display the last frame captured
//                            singleFrameDisplayer.displayFrameByRelativeLocation(1.0f)
                        } else {
                            Timber.w("#######VM singleFrameDisplayer.initialize() failed")
                        }
                    } else {
                        Timber.e("#######VM currentSurface=null")
                    }
                }
            }
        }
    }

    fun setIsFullScreen(newIsFullScreen: Boolean) {
        _isFullScreen.update { newIsFullScreen }
    }
    fun toggleIsFullScreen() {
        _isFullScreen.update { !it }
    }

    /**
     * Called by the Compose UI when [androidx.compose.foundation.AndroidExternalSurface] provides a ready hardware surface.
     *
     * If the pipeline is already PLAYING (i.e. [startPipeline] was called before the surface
     * arrived) and the decoder is not yet running, the decoder job is launched immediately.
     *
     * @param surface  The hardware surface that [DecoderCoroutine] will render into.
     */
    fun onSurfaceReady(surface: Surface) {
        Timber.d("#######VM onSurfaceReady() playbackState=$playbackState")
        currentSurface = surface
        viewModelScope.launch {
            // Sometimes the surface gets destroyed and then a new one becomes ready in quick
            // succession. This happens in scenarios such as:
            //   - The phone is rotated and the screen gets recreated
            //   - The user moves from normal-screen to full-screen or vice versa
            // When the surface is destroyed, the decoder is torn down and then recreated. Without
            // this 200mSec delay it is possible for the call to onSurfaceReady() to happen before
            // the tearing down of the decoder completes, and this causes a mess.
            // This is a simple Hack to prevent this.
            delay(200)
            if (playbackState == PlaybackState.PLAYING) {
               if (decoderJob?.isActive != true) {
                   launchDecoderJob(surface)
               }
            } else { // Playback is paused. The screen displays one frame with singleFrameDisplayer
                withContext(Dispatchers.IO) {
                    if (singleFrameDisplayer.initialize(surface)) {
                        singleFrameDisplayer.redisplayLastDisplayedFrame()
                    } else {
                        Timber.w("#######VM singleFrameDisplayer.setupDecoder() failed")
                    }
                }
            }
        }
    }

    /**
     * Called by the Compose UI when [androidx.compose.foundation.AndroidExternalSurface] destroys its surface
     * (e.g. on a configuration change such as screen rotation).
     *
     * The decoder is torn down because its output surface is now invalid.
     * The encoder keeps running - the camera capture continues uninterrupted so the
     * ring buffer stays current during the rotation.  When the new surface is provided
     * via [onSurfaceReady], a fresh decoder job is launched automatically.
     */
    fun onSurfaceDestroyed() {
        Timber.d("#######VM onSurfaceDestroyed()")
        decoderJob?.cancel()
        decoderJob = null
        singleFrameDisplayer.release()
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
    @RequiresPermission(Manifest.permission.CAMERA)
    private fun startPipeline() {
        Timber.d("#######VM startPipeline()")
        // Discard any stale ring-buffer data and codec config from a prior session.
        ringBuffer.reset()
        cameraAndCodecConfig.invalidateCodecConfig()
        errorMessage = null

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
     *   close session → close camera → stop+release encoder.
     * • Decoder job is canceled next.
     * • [playbackState] is set to PAUSED.
     * • The ring buffer is **not** reset here; that happens at the start of the
     *   next [startPipeline] call (or in [onCleared]).
     */
    private suspend fun stopPipeline() {
        Timber.d(Throwable(), "#######VM stopPipeline(): Current call stack trace")
        // Cancellation is cooperative: each coroutine's finally block handles its own teardown.
        encoderJob?.cancel()
        encoderJob?.join()
        encoderJob = null

        decoderJob?.cancel()
        decoderJob?.join()
        decoderJob = null

//        playbackState = PlaybackState.PAUSED
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

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun launchEncoderJob() {
        encoderJob = viewModelScope.launch(Dispatchers.IO + CoroutineName("ATEncoderCoroutine")) {
            try {
                EncoderCoroutine(
                    context = getApplication(),
                    ringBuffer = ringBuffer,
                    cameraAndCodecConfig = cameraAndCodecConfig,
//                    codecConfigDataHolder = codecConfigDataHolder,
//                    onError               = ::handleEncoderError,
                ).run()
            } catch (e: CancellationException) {
                Timber.d(e, "#######VM encoder CancellationException")
            } catch (e: Exception) {
                Timber.e(e, "#######VM EncoderCoroutine failed")
//                handleEncoderError(e)
                encoderJob?.cancel()
                encoderJob = null
            }
        }
    }

//    val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private fun launchDecoderJob(surface: Surface) {
        decoderJob = viewModelScope.launch(Dispatchers.IO + CoroutineName("ATDecoderCoroutine")) {
            try {
                Timber.d("#######VM launchDecoderJob()")
                DecoderCoroutine(
//                    decoder               = decoder,
                    nalRingBuffer = ringBuffer,
                    cameraAndCodecConfig = cameraAndCodecConfig,
                    outputSurface = surface,
                    delaySecProvider = { settingsFlow.value.delaySec },
//                    jumpChannel           = jumpChannel,
//                    onError               = ::handleDecoderError,
                ).run()
            } catch (e: CancellationException) {
                // No need to stop and release the decoder here because DecoderCoroutine.run()
                // has a finally block that takes care of that before we get here
                Timber.d(e, "#######VM decoder CancellationException")
            } catch (e: Exception) {
                Timber.e(e, "#######VM DecoderCoroutine failed")
//                handleDecoderError(e)
                decoderJob?.cancel()
                decoderJob = null
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
//    private fun handleEncoderError(e: Throwable) {
//        viewModelScope.launch {                          // no dispatcher = main thread
//            Timber.e(e, "#######VM handleEncoderError() Pipeline error")
//            errorMessage = e.message ?: "Unknown pipeline error"
//            stopPipeline()
//        }
//    }
//    private fun handleDecoderError(e: Throwable) {
//        Timber.e(e, "#######VM handleDecoderError() Pipeline error")
//        viewModelScope.launch {                          // no dispatcher = main thread
//            errorMessage = e.message ?: "Unknown pipeline error"
//            Timber.e("#######VM handleDecoderError() Got error: $errorMessage" +
//                    "")
//            stopPipeline()
//        }
//    }

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
     * the ViewModel scope is being canceled, and any in-flight coroutine work is also
     * being torn down by the framework.
     */
    override fun onCleared() {
        super.onCleared()
        Timber.d("#######VM onCleared()")
        singleFrameDisplayer.release()
        viewModelScope.launch(Dispatchers.IO) {
            stopPipeline()
            ringBuffer.reset()
        }
    }

    fun switchCamera() {
        selectedCamera = if (selectedCamera == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    /** todo: 2brm
     * Updates the playback delay and, if the delay was *reduced*, signals the decoder to
     * execute the jump sequence (§9).  Increasing the delay requires no action — the decoder
     * will naturally fall behind or enter FROZEN state.
     *
     * Safe to call while the pipeline is running or stopped.
     *
     * @param newDelaySec  New delay in seconds; must be in [MIN_DELAY_SEC, MAX_DELAY_SEC].
     */
//    fun onDelayChanged(newDelaySec: Int) {
//        val wasReduced = newDelaySec < settingsFlow.value.delaySec
//        viewModelScope.launch { settingsRepo.setDelaySec(newDelaySec) }
//        if (wasReduced && playbackState == PlaybackState.PLAYING) {
//            // A jump is needed only when delay decreases (targetPTS moves forward in time).
//            // Increasing delay requires no action — the decoder naturally falls behind.
//            // trySend on a CONFLATED channel never blocks and never fails due to capacity.
//            jumpChannel.trySend(Unit)
//        }
//    }

    fun onDelayChange(value: Int) {
        val newDelaySec = value.coerceIn(ArcheryTrainerDefaults.MIN_DELAY_SEC, ArcheryTrainerDefaults.MAX_DELAY_SEC)
        viewModelScope.launch { settingsRepo.setDelaySec(newDelaySec) }
    }

    fun onSetSingleFrameLocation(value: Float) {
        Timber.d("#######VM onSetSingleFrameLocation() value=$value")
        _singleFrameSliderPositionFlow.update { value }
    }
    fun onSingleFrameForward() {
        viewModelScope.launch(Dispatchers.IO) {
//            Timber.d("#######VM onSingleFrameForward()")
            singleFrameDisplayer.displayNextFrame()
        }
    }
    fun onSingleFrameBackward() {
        viewModelScope.launch(Dispatchers.IO) {
//            Timber.d("#######VM onSingleFrameBackward()")
            singleFrameDisplayer.displayPreviousFrame()
        }
    }

    /** Called from the UI (via a launchedEffect) every time the screen rotation changes */
    fun onScreenRotationUpdate(newScreenRotationDegrees: Int) {
        Timber.d("#######VM onScreenRotationUpdate() orientation=$newScreenRotationDegrees")
        cameraAndCodecConfig.screenOrientation = newScreenRotationDegrees
    }

    var xWhenTouched: Float = 0f
    var frameIndexWhenTouched: Int = -1
    fun onVideoSurfaceTouched(x: Float) {
        Timber.d("#######VM onVideoSurfaceTouched() x=$x")
        xWhenTouched = x
        frameIndexWhenTouched = singleFrameDisplayer.currentFrameIndex
    }
//    var horizontalDragJob: Job? = null
    /**
     * Called by the composable whenever a drag event occurs
     */
    fun onHorizontalDragOverVideo(currentX: Float) {
        if (ringBuffer.count == 0) return
        if (playbackState != PlaybackState.PAUSED) {
            Timber.e("#######VM onHorizontalDragOverVideo(): but not PAUSED")
            return
        }

        val deltaX = currentX - xWhenTouched
        Timber.d("#######VM onHorizontalDragOverVideo() deltaX=${deltaX}")
        if (abs(deltaX) >= horizontalDragSensitivity) {
            val frameIndexDelta = (deltaX / horizontalDragSensitivity).roundToInt()
            val targetFrameIndex = frameIndexWhenTouched + frameIndexDelta
            _singleFrameSliderPositionFlow.update {
                targetFrameIndex.toFloat() / ringBuffer.count.toFloat()
            }
        }
    }

    fun onBufferingCountdownTerminated() {
        _isBuffering.update { 0 }
    }

    class Factory(
        private val application: Application,
        private val repo: SettingsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DelayedVideoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DelayedVideoViewModel(application, repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    /**
     * PlaybackState — the only two states visible to the UI.
     *
     * The decoder's internal PLAYING / FROZEN / CATCHING_UP state machine is an
     * implementation detail of [DecoderCoroutine] and is intentionally not
     * surfaced here.
     */
    enum class PlaybackState { PAUSED, PLAYING }

    enum class CameraPermissionState { CHECKING, GRANTED, DENIED }
}


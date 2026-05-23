package com.liorapps.archerytrainer.screens.video.logic

import android.Manifest
import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
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
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    // Camera Selector State   todo: implement this
    var selectedCamera by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        private set

    /** Whether the pipeline is currently running. Drives the Play/Pause button label. */
    var playbackState: PlaybackState by mutableStateOf(PlaybackState.PAUSED)
        private set

    /** Whether a "Buffering" countdown indicator should be displayed, and for how long (0 - don't display) */
    private val _isBuffering = MutableStateFlow(0)
    val isBuffering = _isBuffering.asStateFlow()

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
//    var errorMessage: String? by mutableStateOf(null)
//        private set

    var horizontalDragSensitivity: Float by mutableFloatStateOf(60f) //todo from settings
        private set

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen = _isFullScreen.asStateFlow()

    private val _cameraPermissionStateFlow =
        MutableStateFlow<CameraPermissionState>(CameraPermissionState.CHECKING)
    val cameraPermissionStateFlow = _cameraPermissionStateFlow.asStateFlow()

    /** Called from the Compose UI on first composition (if permission was granted), and
     *  whenever the permission is granted afterward */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun onCameraPermissionGranted() {
        if (_cameraPermissionStateFlow.value == CameraPermissionState.GRANTED) return
        _cameraPermissionStateFlow.update { CameraPermissionState.GRANTED }
        viewModelScope.launch {
            gCameraDevice = selectAndOpenCameraDevice()
        }
    }
    fun onCameraPermissionDenied() { _cameraPermissionStateFlow.update { CameraPermissionState.DENIED } }

    var gCameraDevice: CameraDevice? = null

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

    val delayedVideoPlayer =
        DelayedVideoPlayer(cameraAndCodecConfig, ringBuffer, viewModelScope)

    val singleFrameDisplayer =
        SingleFrameDisplayer(cameraAndCodecConfig, ringBuffer.AsLinearBuffer())

    val loopPlayer =
        LoopPlayer(viewModelScope, cameraAndCodecConfig,
            ringBuffer.AsLinearBuffer(), speed = 1.0f)

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
            Timber.d("####VM togglePlayback() currentPlaybackState=$playbackState")
            val surface = currentSurface
            if (surface == null) {
                Timber.e("####VM onTogglePlayback(): currentSurface=null")
            } else {
                when (playbackState) {
                    PlaybackState.PLAYING -> { // Click on "Pause" while playing
                        playbackState = PlaybackState.PAUSED
                        _isBuffering.update { 0 } // Stop the on screen "buffering" countdown in case it's still on
                        delayedVideoPlayer.stop()
                        startSingleFrameDisplay(surface)
                    }
                    PlaybackState.PAUSED -> { // Click on "Play" while paused
                        singleFrameDisplayer.release()
                        playbackState = PlaybackState.PLAYING
                        startDelayedVideoCapturing(surface)
                    }
                    PlaybackState.LOOP_REPLAYING -> {
                    }
                }
            }
        }
    }

    var loopPlayerJob: Job? = null
    fun onToggleLoopPlayback() {
        Timber.d("####LP onToggleLoopPlayback() state=${playbackState}")
        val surface = currentSurface
        if (surface == null) {
            Timber.e("####VM onTogglePlayback(): currentSurface=null")
        } else {
            when (playbackState) {
                PlaybackState.PAUSED -> {
                    playbackState = PlaybackState.LOOP_REPLAYING
                    singleFrameDisplayer.release()
                    loopPlayerJob = loopPlayer.startPlaybackLoop(surface)
                }
                PlaybackState.LOOP_REPLAYING -> {
                    playbackState = PlaybackState.PAUSED
                    loopPlayerJob?.cancel(); loopPlayerJob = null
                    loopPlayer.release()
                    viewModelScope.launch(Dispatchers.IO) {
                        startSingleFrameDisplay(surface)
                    }
                }
                PlaybackState.PLAYING -> {
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private suspend fun startDelayedVideoCapturing(surface: Surface) {
        ringBuffer.reset()
        if (gCameraDevice == null) {
            Timber.d("####VM startDelayedVideoCapturing(): gCameraDevice=null - reopening")
            gCameraDevice = selectAndOpenCameraDevice()
        }
        delayedVideoPlayer.start(surface, gCameraDevice, cameraHandler, cameraThread, settingsFlow.value.delaySec)
        _isBuffering.update { settingsFlow.value.delaySec } // Start the on screen "buffering" countdown
    }
    private suspend fun startSingleFrameDisplay(surface: Surface) {
        if (singleFrameDisplayer.initialize(surface)) {
            singleFrameDisplayer.displayLastFrame()  // When we pause, we display the last captured frame
            Timber.d("####VM singleFrameDisplayer _singleFrameSliderPositionFlow.value = 1.0f")
            _singleFrameSliderPositionFlow.update { 1.0f } // When we pause we display the last frame captured
        } else {
            Timber.w("####VM singleFrameDisplayer.initialize() failed")
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
        viewModelScope.launch(Dispatchers.IO) {
            // Sometimes the surface gets destroyed and then a new one becomes ready in quick
            // succession. This happens in scenarios such as:
            //   - The phone is rotated and the screen gets recreated
            //   - The user moves from normal-screen to full-screen or vice versa
            // When the surface is destroyed, the decoder is torn down and then recreated. Without
            // this 200mSec delay it is possible for the call to onSurfaceReady() to happen before
            // the tearing down of the decoder completes, and this causes a mess.
            // This is a simple Hack to prevent this.
            delay(200)
            when (playbackState) {
                PlaybackState.PLAYING -> {
                    delayedVideoPlayer.launchDecoderJob(surface, settingsFlow.value.delaySec)
                }
                PlaybackState.PAUSED -> {
                    if (singleFrameDisplayer.initialize(surface)) {
                        singleFrameDisplayer.redisplayLastDisplayedFrame()
                    } else {
                        // This is fine if no video has been captured yet (we don't have camera and codec config yet)
                        Timber.d("#######VM singleFrameDisplayer.setupDecoder() failed")
                    }
                }
                PlaybackState.LOOP_REPLAYING -> {
                    loopPlayer.startPlaybackLoop(surface)
                }
            }
        }
    }

    /**
     * Called by the Compose UI when its surface is destroyed (e.g. on a configuration change
     * such as screen rotation)
     *
     * The decoder is torn down because its output surface is now invalid.
     * The encoder keeps running - the camera capture continues uninterrupted so the
     * ring buffer stays current during the rotation.  When the new surface is provided
     * via [onSurfaceReady], a fresh decoder job is launched automatically
     */
    fun onSurfaceDestroyed() {
        Timber.d("####VM onSurfaceDestroyed()")
        currentSurface = null
        when (playbackState) {
            PlaybackState.PLAYING -> {
                delayedVideoPlayer.stopDecoderJob()
            }
            PlaybackState.PAUSED -> {
                singleFrameDisplayer.release()
            }
            PlaybackState.LOOP_REPLAYING -> {
                loopPlayer.release()
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
            delayedVideoPlayer.stop()
            loopPlayer.release()
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

    fun onLoopReplayButtonClicked() {
        Timber.d("#### onLoopReplayButtonClicked")
    }

    // Camera2 callbacks require a looper - use a dedicated HandlerThread
    val cameraThread = HandlerThread("ArcheryTrainer-Camera").also { it.start() }
    val cameraHandler = Handler(cameraThread.looper)
    /**
     * Open the first front-facing camera, or the first available
     * camera if no front-facing camera is found.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    private suspend fun selectAndOpenCameraDevice(): CameraDevice {
        val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Timber.d("####VM Device has ${cameraManager.cameraIdList.size} cameras")

        val ids = cameraManager.cameraIdList
        for (cameraId in ids) {
            val chars: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            Timber.d("####VM camera ID=$cameraId  Characteristics: $chars")
            logCameraCharacteristics(chars)
        }

        // TODO: LH cameraId should be a parameter passed into this function
        val cameraId = ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: ids.first()
        val chars: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        Timber.d("####VM selectCamera() selected cameraId: $cameraId  sensorOrientation: $sensorOrientation")

        val cameraDevice = openCamera(cameraManager, cameraId, cameraHandler)
        return cameraDevice
    }

    private fun logCameraCharacteristics(cameraChars: CameraCharacteristics) {
        Timber.d("####VM camera LENS_FACING=${cameraChars.get(CameraCharacteristics.LENS_FACING)}")
        val map = cameraChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val videoSizes: Array<Size>? = map?.getOutputSizes(SurfaceTexture::class.java)
        videoSizes?.forEach { size ->
//            Timber.d("####VM Supported Resolution: ${size.width} x ${size.height}")
        }

        // Retrieve the list of supported FPS ranges
        val fpsRanges: Array<Range<Int>>? = cameraChars.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        )
        fpsRanges?.forEach { range ->
            // Example: [15, 30] means the camera can vary between 15 and 30 fps
            // Example: [30, 30] means a fixed frame rate of 30 fps
//            Timber.d("####VM Supported FPS range: ${range.lower} - ${range.upper}")
        }
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
        Timber.d("####VM openCamera()")
        manager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(cameraDevice: CameraDevice) {
                    if (cont.isActive) {
                        Timber.d("####VM openCamera() camera=$cameraDevice")
                        cont.resume(cameraDevice)
                    } else {
                        cameraDevice.close()
                        cont.resumeWithException(IllegalStateException("openCamera() continuation is not active"))
                    }
                }

                override fun onClosed(cameraDevice: CameraDevice) {
                    super.onClosed(cameraDevice)
                    Timber.d("####VM Camera closed")
                    gCameraDevice = null
                }

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    cameraDevice.close()
                    Timber.w("####### ####VM openCamera() onDisconnected cont.isActive=${cont.isActive}")
//                    if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("Camera disconnected during open. cameraId=$cameraId")
                    )
//                    } else {
//                        // Camera disconnected after it was already opened.
//                        // Notify the onError callback so the pipeline stops cleanly.
//                        onError(IllegalStateException("Camera $cameraId disconnected"))
//                    }
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    // This may be called when the phone's screen is locked (error code
                    // is 3 - ERROR_CAMERA_DISABLED). We don't try to clean up here because
                    // it's likely that the ViewModel is going to be destroyed and its onClear()
                    // function will take care of everything
                    Timber.w("####VM cameraCallback.onError() cont.isActive=${cont.isActive} error=$error")
                    cameraDevice.close()
//                    if (cont.isActive) {
//                    cont.resumeWithException(
//                        RuntimeException("Camera open error:  cameraId=$cameraId error=$error")
//                    )
//                    } else {
//                        onError(RuntimeException("Camera $cameraId error: $error"))
//                    }
                }
            },
            handler
        )

        /* device closed in finally block of run() if open */
        cont.invokeOnCancellation { throwable ->
            Timber.w("####### ####### openCamera() cont.invokeOnCancellation")
            Timber.w("####### ####### cont.invokeOnCancellation err: ${if (throwable != null) throwable.message else "null"}")
        }
    }

    init {
        viewModelScope.launch {
            settingsFlow.collect {
                // This is called every time the settings change
                Timber.d("####VM Settings changed")
            }
        }
    }

    enum class PlaybackState { PAUSED, PLAYING, LOOP_REPLAYING }

    enum class CameraPermissionState { CHECKING, GRANTED, DENIED }

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
}


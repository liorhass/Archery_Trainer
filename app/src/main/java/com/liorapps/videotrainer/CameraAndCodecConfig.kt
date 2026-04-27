package com.liorapps.videotrainer

class CameraAndCodecConfig {
    @Volatile
    var codecConfigDataHolder: Array<ByteArray?> = arrayOfNulls(1)

    @Volatile
    var cameraSensorOrientation: Int = 270 // 0, 90, 180, 270

    @Volatile
    var cameraIsFacingFront: Boolean = true

    @Volatile
    var screenOrientation: Int? = null // 0, 90, 180, 270

    fun invalidateConfig() {
        codecConfigDataHolder[0] = null
        screenOrientation = null
    }
    fun invalidateCodecConfig() {
        codecConfigDataHolder[0] = null
    }
    fun invalidateScreenConfig() {
        screenOrientation = null
    }

    fun isConfigurationValid(): Boolean = codecConfigDataHolder[0] != null  &&  screenOrientation != null

    fun computeRelativeDecoderRotation(): Int {
        val so = screenOrientation ?: 0
        return if (cameraIsFacingFront) {
            (cameraSensorOrientation + so) % 360
        } else {
            (cameraSensorOrientation - so + 360) % 360
        }
    }
}
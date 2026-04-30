package com.liorapps.videotrainer

import java.nio.ByteBuffer

class CameraAndCodecConfig {
//    @Volatile
//    var codecConfigDataHolder: Array<ByteArray?> = arrayOfNulls(1)
    @Volatile
    var sps: ByteBuffer? = null
    var pps: ByteBuffer? = null

    @Volatile
    var cameraSensorOrientation: Int = 270 // 0, 90, 180, 270

    @Volatile
    var cameraIsFacingFront: Boolean = true

    @Volatile
    var screenOrientation: Int? = null // 0, 90, 180, 270

    fun invalidateConfig() {
        invalidateCodecConfig()
        invalidateScreenConfig()
    }
    fun invalidateCodecConfig() {
//        codecConfigDataHolder[0] = null
        sps = null
        pps = null
    }
    fun invalidateScreenConfig() {
        screenOrientation = null
    }

//    fun isConfigurationValid(): Boolean = codecConfigDataHolder[0] != null  &&  screenOrientation != null
    fun isConfigurationValid(): Boolean = sps != null  &&  pps != null  &&  screenOrientation != null

    fun computeRelativeDecoderRotation(): Int {
        val so = screenOrientation ?: 0
        return if (cameraIsFacingFront) {
            (cameraSensorOrientation + so) % 360
        } else {
            (cameraSensorOrientation - so + 360) % 360
        }
    }
}
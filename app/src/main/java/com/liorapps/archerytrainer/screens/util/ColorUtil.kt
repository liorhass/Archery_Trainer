package com.liorapps.archerytrainer.screens.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.liorapps.archerytrainer.ui.theme.AppTheme

@Composable
fun getPreferredTextColorForBackground(bgColor: Color): Color {
    // standard (Rec. 709) for digital screens uses weighted coefficients to reflect this:
    // Brightness = (0.2126 * R  +  0.7152 * G  +  0.0722 * B)
    val bgBrightness = bgColor.red*0.2126f + bgColor.green*0.7152f + bgColor.blue*0.0722f
    return if (bgBrightness > 0.5f) {
        AppTheme.colors.textOnLightBg
    } else {
        AppTheme.colors.textOnDarkBg
    }
}
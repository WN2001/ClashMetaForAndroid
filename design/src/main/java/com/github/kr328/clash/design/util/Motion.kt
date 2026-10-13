package com.github.kr328.clash.design.util

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

fun Context.animationsEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    ValueAnimator.areAnimatorsEnabled()
} else {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}

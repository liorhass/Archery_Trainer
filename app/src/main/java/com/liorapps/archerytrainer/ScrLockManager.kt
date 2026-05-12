package com.liorapps.archerytrainer

import android.app.Activity
import android.content.Context

interface ScrLockManager {
    fun lockScreen(): Boolean
    fun requestAdminIfNeeded(activity: Activity)
    fun isAdminActive(context: Context): Boolean
}
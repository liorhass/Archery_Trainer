package com.liorapps.archerytrainer

import android.app.Activity
import android.content.Context

interface ScrLockManager {
    @Suppress("SameReturnValue")
    fun lockScreen(context: Context): Boolean
    fun requestAdminPrivilegeIfNeeded(activity: Activity, requestCode: Int)
    fun isAdminActive(context: Context): Boolean
}
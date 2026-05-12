package com.liorapps.archerytrainer

import android.app.Activity
import android.content.Context

class ScrLockManagerImpl : ScrLockManager {
    override fun isAdminActive(context: Context) = false
    override fun requestAdminIfNeeded(activity: Activity) { /* no-op */ }
    override fun lockScreen() = false
}
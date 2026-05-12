package com.liorapps.archerytrainer

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class ATDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Called when the user grants device admin privileges
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Called when the user revokes device admin privileges
    }
}
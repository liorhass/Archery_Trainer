package com.liorapps.archerytrainer

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

fun requestDeviceAdminPrivilege(activity: Activity, requestCode: Int) {
    val devicePolicyManager =
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adminComponent = ComponentName(activity, ATDeviceAdminReceiver::class.java)

    if (!devicePolicyManager.isAdminActive(adminComponent)) {
        // Send the user to the system screen to grant admin rights
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Grant admin access so this app can lock the screen."
            )
        }
        activity.startActivityForResult(intent, requestCode)
    }
}

const val REQUEST_CODE_ADMIN: Int = 777
fun lockScreen(context: Context) {
    val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adminComponent = ComponentName(context, ATDeviceAdminReceiver::class.java)

    if (devicePolicyManager.isAdminActive(adminComponent)) {
        devicePolicyManager.lockNow()   // Locks immediately + turns off the screen
    } else {
        // Admin not granted — prompt user
        requestDeviceAdminPrivilege(context as Activity, REQUEST_CODE_ADMIN)
    }
}
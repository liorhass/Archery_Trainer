package com.liorapps.archerytrainer

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object ScrLockManagerImpl : ScrLockManager {

    const val REQUEST_CODE_ADMIN: Int = 777

    override fun isAdminActive(context: Context): Boolean {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ATDeviceAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    override fun requestAdminPrivilegeIfNeeded(activity: Activity, requestCode: Int) {
        val devicePolicyManager =
            activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val adminComponent = ComponentName(activity, ATDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            // Send the user to the system screen to grant admin rights
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Needed to be able to lock the screen.")
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }

    override fun lockScreen(context: Context): Boolean {
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val adminComponent = ComponentName(context, ATDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow()   // Locks immediately + turns off the screen
        } else {
            // Admin not granted — prompt user
            requestAdminPrivilegeIfNeeded(context as Activity, REQUEST_CODE_ADMIN)
        }
        return true
    }
}
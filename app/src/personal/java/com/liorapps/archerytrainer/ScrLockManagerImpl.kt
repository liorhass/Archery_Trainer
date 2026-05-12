package com.liorapps.archerytrainer

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ScrLockManagerImpl : ScrLockManager {

    private fun getAdmin(context: Context) =
        ComponentName(context, ATDeviceAdminReceiver::class.java)

    override fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(getAdmin(context))
    }

    override fun requestAdminIfNeeded(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isAdminActive(getAdmin(activity))) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdmin(activity))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed to lock the screen.")
            }
            activity.startActivityForResult(intent, 1001)
        }
    }

    override fun lockScreen(): Boolean {
        // actual locking logic here (as shown in the previous answer)
        return true
    }
}
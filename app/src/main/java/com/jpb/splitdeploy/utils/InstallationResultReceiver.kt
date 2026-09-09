package com.jpb.splitdeploy.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallationResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System UI confirmation prompt required
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmationIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmationIntent)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d("Installer", "Successfully installed package: $packageName")
                // Notify UI or ViewModel of success
            }
            else -> {
                Log.e("Installer", "Installation failed ($status): $message")
                // Notify UI or ViewModel of failure
            }
        }
    }
}
package com.jpb.splitdeploy.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.os.Build
import com.jpb.splitdeploy.UIInstallerState

class InstallationResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // User is being prompted to confirm installation
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                confirmationIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Installation completed successfully
                InstallerStateNotifier.postState(UIInstallerState.Success(PackageInstaller.EXTRA_PACKAGE_NAME))
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // User tapped "Cancel" on the system prompt
                InstallerStateNotifier.postState(UIInstallerState.Error("Installation was cancelled by the user."))
            }

            else -> {
                // Installation failed with specific error message
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Installation failed with status code: $status"
                InstallerStateNotifier.postState(UIInstallerState.Error(message))
            }
        }
    }
}
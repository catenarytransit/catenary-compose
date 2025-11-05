package com.catenarymaps.catenary

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

@Composable
fun CheckForAppUpdate(
    // Pass a snackbar host state or a lambda to show UI
    // when a flexible update is downloaded.
    onFlexibleUpdateDownloaded: () -> Unit
) {
    val context = LocalContext.current
    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }

    // 1. Handle the result of the update flow (this is correct)
    val updateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("InAppUpdate", "Update flow finished with RESULT_OK")
        } else {
            Log.d("InAppUpdate", "Update flow finished with code: ${result.resultCode}")
            // Handle update failure or cancellation
        }
    }

    // 2. Listener for FLEXIBLE update download state (this is correct)
    val listener = remember {
        InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                // Flexible update is downloaded and ready to install
                onFlexibleUpdateDownloaded()
            }
        }
    }

    // Register and unregister the listener safely (this is correct)
    DisposableEffect(appUpdateManager, listener) {
        appUpdateManager.registerListener(listener)
        onDispose {
            appUpdateManager.unregisterListener(listener)
        }
    }

    // 3. Trigger the update check (This is the corrected part)
    LaunchedEffect(Unit) { // Runs only once
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val updateAvailable =
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isFlexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            val isImmediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            Log.d(
                "InAppUpdate",
                "Update check success. Available: $updateAvailable, Flexible: $isFlexibleAllowed, Immediate: $isImmediateAllowed"
            )

            if (updateAvailable) {
                // Determine which update type to use
                val updateType = if (isFlexibleAllowed) {
                    AppUpdateType.FLEXIBLE
                } else if (isImmediateAllowed) {
                    AppUpdateType.IMMEDIATE
                } else {
                    null // No allowed update type
                }

                if (updateType != null) {
                    // Create the options object
                    val appUpdateOptions = AppUpdateOptions.newBuilder(updateType).build()

                    // **THE CORRECTED LINE**
                    // Call the modern method, passing the launcher directly.
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        appUpdateOptions
                    )
                    Log.d("InAppUpdate", "Launching $updateType update flow.")
                }
            } else {
                Log.d("InAppUpdate", "No update available.")
            }
        }.addOnFailureListener { e ->
            Log.e("InAppUpdate", "Failed to check for update.", e)
        }
    }
}
/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.safetylabel

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_PACKAGE_ADDED
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v34.LightInstallSourceInfoLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel as SafetyLabelForPersistence
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Listens for package installs and updates, and updates the [AppsSafetyLabelHistoryPersistence] if
 * the app safety label has changed.
 */
// TODO(b/264884476): Remove excess logging from this class once feature is stable.
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetyLabelChangedBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled(context)) {
            return
        }

        val packageChangeEvent = getPackageChangeEvent(intent)
        if (
            !(packageChangeEvent == PackageChangeEvent.NEW_INSTALL ||
                packageChangeEvent == PackageChangeEvent.UPDATE)
        ) {
            return
        }

        val packageName = intent.data?.schemeSpecificPart
        if (packageName == null) {
            Log.w(TAG, "Received broadcast without package name")
            return
        }

        val currentUser = Process.myUserHandle()
        if (DEBUG) {
            Log.i(
                TAG,
                "received broadcast packageName: $packageName, current user: $currentUser," +
                    " packageChangeEvent: $packageChangeEvent, intent user:" +
                    " ${intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
                                    ?: currentUser}"
            )
        }
        val userManager = Utils.getSystemServiceSafe(context, UserManager::class.java)
        if (userManager.isProfile) {
            forwardBroadcastToParentUser(context, userManager, intent)
            return
        }

        val user: UserHandle =
            intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java) ?: currentUser

        val pendingResult: PendingResult = goAsync()

        GlobalScope.launch(Dispatchers.Main) {
            processPackageChange(context, packageName, user)
            pendingResult.finish()
        }
    }

    /** Processes the package change for the given package and user. */
    private suspend fun processPackageChange(
        context: Context,
        packageName: String,
        user: UserHandle,
    ) {
        val lightPackageInfo =
            LightPackageInfoLiveData[Pair(packageName, user)].getInitializedValue() ?: return
        if (!isAppRequestingLocationPermission(lightPackageInfo)) {
            return
        }

        if (!isSafetyLabelSupported(Pair(packageName, user))) {
            return
        }
        writeSafetyLabel(context, lightPackageInfo, user)
    }

    /**
     * Retrieves and writes the safety label for a package to the safety labels store.
     *
     * As I/O operations are invoked, we run this method on the main thread.
     */
    @MainThread
    private fun writeSafetyLabel(
        context: Context,
        lightPackageInfo: LightPackageInfo,
        user: UserHandle,
    ) {
        val packageName = lightPackageInfo.packageName
        if (DEBUG) {
            Log.i(
                TAG,
                "writeSafetyLabel called for packageName: $packageName, currentUser:" +
                    " ${Process.myUserHandle()}"
            )
        }

        // Get the context for the user in which the app is installed.
        val userContext =
            if (user == Process.myUserHandle()) {
                context
            } else {
                context.createContextAsUser(user, 0)
            }
        val appMetadataBundle =
            try {
                userContext.packageManager.getAppMetadata(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package $packageName not found while retrieving app metadata")
                return
            }

        if (DEBUG) {
            Log.i(TAG, "appMetadataBundle $appMetadataBundle")
        }
        val safetyLabel: AppMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(appMetadataBundle) ?: return

        val receivedAtMs: Long = lightPackageInfo.lastUpdateTime

        val safetyLabelForPersistence: SafetyLabelForPersistence =
            AppsSafetyLabelHistory.SafetyLabel.extractLocationSharingSafetyLabel(
                packageName,
                Instant.ofEpochMilli(receivedAtMs),
                safetyLabel
            )
        val historyFile = AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(context)

        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(safetyLabelForPersistence, historyFile)
    }

    private fun getPackageChangeEvent(intent: Intent): PackageChangeEvent {
        val action = intent.action
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        return if (isPackageAddedBroadcast(action) && replacing) {
            PackageChangeEvent.UPDATE
        } else if (isPackageAddedBroadcast(action)) {
            PackageChangeEvent.NEW_INSTALL
        } else {
            PackageChangeEvent.INSIGNIFICANT
        }
    }

    /** Companion object for [SafetyLabelChangedBroadcastReceiver]. */
    companion object {
        private val TAG = SafetyLabelChangedBroadcastReceiver::class.simpleName
        private const val ACTION_PACKAGE_ADDED_PERMISSIONCONTROLLER_FORWARDED =
            "com.android.permissioncontroller.action.PACKAGE_ADDED_PERMISSIONCONTROLLER_FORWARDED"
        private const val DEBUG = true
        private val LOCATION_PERMISSIONS =
            PermissionMapping.getPlatformPermissionNamesOfGroup(Manifest.permission_group.LOCATION)

        private fun isAppRequestingLocationPermission(lightPackageInfo: LightPackageInfo): Boolean {
            return lightPackageInfo.requestedPermissions.any { LOCATION_PERMISSIONS.contains(it) }
        }

        private suspend fun isSafetyLabelSupported(packageUser: Pair<String, UserHandle>): Boolean {
            val lightInstallSourceInfo =
                LightInstallSourceInfoLiveData[packageUser].getInitializedValue()
            return lightInstallSourceInfo.supportsSafetyLabel
        }

        private fun isPackageAddedBroadcast(intentAction: String?) =
            intentAction == ACTION_PACKAGE_ADDED ||
                intentAction == ACTION_PACKAGE_ADDED_PERMISSIONCONTROLLER_FORWARDED

        private fun forwardBroadcastToParentUser(
            context: Context,
            userManager: UserManager,
            intent: Intent
        ) {
            val currentUser = Process.myUserHandle()
            val profileParent = userManager.getProfileParent(currentUser)
            if (profileParent == null) {
                Log.w(TAG, "Could not find profile parent for $currentUser")
                return
            }

            Log.i(
                TAG,
                "Forwarding intent from current user: $currentUser to profile parent" +
                    " $profileParent"
            )
            context.sendBroadcastAsUser(
                Intent(intent)
                    .setAction(ACTION_PACKAGE_ADDED_PERMISSIONCONTROLLER_FORWARDED)
                    .putExtra(Intent.EXTRA_USER, currentUser),
                profileParent
            )
        }

        /** Types of package change events. */
        enum class PackageChangeEvent {
            INSIGNIFICANT,
            NEW_INSTALL,
            UPDATE,
        }
    }
}

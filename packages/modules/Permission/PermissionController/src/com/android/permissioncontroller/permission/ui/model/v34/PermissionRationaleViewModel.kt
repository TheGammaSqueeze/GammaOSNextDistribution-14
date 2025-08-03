/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model.v34

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__HELP_CENTER
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__INSTALL_SOURCE
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__PERMISSION_SETTINGS
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_VIEWED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v34.SafetyLabelInfoLiveData
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleActivity
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getAppStoreIntent
import com.android.permissioncontroller.permission.utils.v34.SafetyLabelUtils
import com.android.settingslib.HelpUtils

/**
 * [ViewModel] for the [PermissionRationaleActivity]. Gets all information required safety label and
 * links required to inform user of data sharing usages by the app when granting this permission
 *
 * @param app: The current application
 * @param packageName: The packageName permissions are being requested for
 * @param permissionGroupName: The permission group requested
 * @param sessionId: A long to identify this session
 * @param storedState: Previous state, if this activity was stopped and is being recreated
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PermissionRationaleViewModel(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    private val sessionId: Long,
    private val storedState: Bundle?
) : ViewModel() {
    private val user = Process.myUserHandle()
    private val safetyLabelInfoLiveData = SafetyLabelInfoLiveData[packageName, user]

    /** Interface for forwarding onActivityResult to this view model */
    interface ActivityResultCallback {
        /**
         * Should be invoked by base activity when a valid onActivityResult is received
         *
         * @param data [Intent] which may contain result data from a started Activity (various data
         *   can be attached to Intent "extras")
         * @return {@code true} if Activity should finish after processing this result
         */
        fun shouldFinishActivityForResult(data: Intent?): Boolean
    }
    var activityResultCallback: ActivityResultCallback? = null

    /**
     * A class which represents a permission rationale for permission group, and messages which
     * should be shown with it.
     */
    data class PermissionRationaleInfo(
        val groupName: String,
        val isPreloadedApp: Boolean,
        val installSourcePackageName: String?,
        val installSourceLabel: String?,
        val purposeSet: Set<Int>
    )

    /** A [LiveData] which holds the currently pending PermissionRationaleInfo */
    val permissionRationaleInfoLiveData =
        object : SmartUpdateMediatorLiveData<PermissionRationaleInfo>() {

            init {
                addSource(safetyLabelInfoLiveData) { onUpdate() }

                // Load package state, if available
                onUpdate()
            }

            override fun onUpdate() {
                if (safetyLabelInfoLiveData.isStale) {
                    return
                }

                val safetyLabelInfo = safetyLabelInfoLiveData.value

                if (safetyLabelInfo?.safetyLabel == null) {
                    Log.e(LOG_TAG, "Safety label for $packageName not found")
                    value = null
                    return
                }

                val installSourcePackageName =
                    safetyLabelInfo.installSourceInfo.initiatingPackageName
                val installSourceLabel: String? =
                    installSourcePackageName?.let {
                        KotlinUtils.getPackageLabel(app, it, Process.myUserHandle())
                    }

                val purposes =
                    SafetyLabelUtils.getSafetyLabelSharingPurposesForGroup(
                        safetyLabelInfo.safetyLabel,
                        permissionGroupName
                    )
                if (value == null) {
                    logPermissionRationaleDialogViewed(purposes)
                }
                value =
                    PermissionRationaleInfo(
                        permissionGroupName,
                        safetyLabelInfo.installSourceInfo.isPreloadedApp,
                        installSourcePackageName,
                        installSourceLabel,
                        purposes
                    )
            }
        }

    fun canLinkToAppStore(context: Context, installSourcePackageName: String): Boolean {
        return getAppStoreIntent(context, installSourcePackageName, packageName) != null
    }

    fun sendToAppStore(context: Context, installSourcePackageName: String) {
        logPermissionRationaleDialogActionReported(
            PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__INSTALL_SOURCE
        )
        val storeIntent = getAppStoreIntent(context, installSourcePackageName, packageName)
        context.startActivity(storeIntent)
    }

    /**
     * Send the user to the AppPermissionFragment
     *
     * @param activity The current activity
     * @param groupName The name of the permission group whose fragment should be opened
     */
    fun sendToSettingsForPermissionGroup(activity: Activity, groupName: String) {
        if (activityResultCallback != null) {
            return
        }
        activityResultCallback =
            object : ActivityResultCallback {
                override fun shouldFinishActivityForResult(data: Intent?): Boolean {
                    val returnGroupName = data?.getStringExtra(EXTRA_RESULT_PERMISSION_INTERACTED)
                    return (returnGroupName != null) &&
                        data.hasExtra(EXTRA_RESULT_PERMISSION_RESULT)
                }
            }
        logPermissionRationaleDialogActionReported(
            PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__PERMISSION_SETTINGS
        )
        startAppPermissionFragment(activity, groupName)
    }

    /** Returns whether UI can provide link to help center */
    fun canLinkToHelpCenter(context: Context): Boolean {
        return !getHelpCenterUrlString(context).isNullOrEmpty()
    }

    /**
     * Send the user to the Safety Label Android Help Center
     *
     * @param activity The current activity
     */
    fun sendToLearnMore(activity: Activity) {
        if (!canLinkToHelpCenter(activity)) {
            Log.w(LOG_TAG, "Unable to open help center, no url provided.")
            return
        }

        // Add in some extra locale query parameters
        val fullUri =
            HelpUtils.uriWithAddedParameters(activity, Uri.parse(getHelpCenterUrlString(activity)))
        val intent =
            Intent(Intent.ACTION_VIEW, fullUri).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        logPermissionRationaleDialogActionReported(
            PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__HELP_CENTER
        )
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // TODO(b/266755891): show snackbar when help center intent unable to be opened
            Log.w(LOG_TAG, "Unable to open help center URL.", e)
        }
    }

    private fun startAppPermissionFragment(activity: Activity, groupName: String) {
        val intent =
            Intent(Intent.ACTION_MANAGE_APP_PERMISSION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                .putExtra(Intent.EXTRA_USER, user)
                .putExtra(
                    ManagePermissionsActivity.EXTRA_CALLER_NAME,
                    PermissionRationaleActivity::class.java.name
                )
                .putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivityForResult(intent, APP_PERMISSION_REQUEST_CODE)
    }

    private fun logPermissionRationaleDialogViewed(purposes: Set<Int>) {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        var purposesPresented = 0
        // Create bitmask for purposes presented, bit numbers are in accordance with PURPOSE_
        // constants in [DataPurposeConstants]
        purposes.forEach { purposeInt ->
            purposesPresented = purposesPresented or 1.shl(purposeInt)
        }
        PermissionControllerStatsLog.write(
            PERMISSION_RATIONALE_DIALOG_VIEWED,
            sessionId,
            uid,
            permissionGroupName,
            purposesPresented
        )
    }

    fun logPermissionRationaleDialogActionReported(buttonPressed: Int) {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        PermissionControllerStatsLog.write(
            PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED,
            sessionId,
            uid,
            permissionGroupName,
            buttonPressed
        )
    }

    private fun getHelpCenterUrlString(context: Context): String? {
        return context.getString(R.string.data_sharing_help_center_link)
    }

    companion object {
        private val LOG_TAG = PermissionRationaleViewModel::class.java.simpleName

        const val APP_PERMISSION_REQUEST_CODE = 1
    }
}

/** Factory for a [PermissionRationaleViewModel] */
class PermissionRationaleViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    private val sessionId: Long,
    private val savedState: Bundle?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PermissionRationaleViewModel(
            app,
            packageName,
            permissionGroupName,
            sessionId,
            savedState
        )
            as T
    }
}

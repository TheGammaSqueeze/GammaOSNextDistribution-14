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

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MANAGE_APP_PERMISSION
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.Intent.EXTRA_PERMISSION_GROUP_NAME
import android.content.Intent.EXTRA_USER
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.v34.AppDataSharingUpdatesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_ALWAYS
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType
import com.android.settingslib.HelpUtils
import kotlinx.coroutines.Job

/** View model for data sharing updates UI. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesViewModel(app: Application) {

    private val appDataSharingUpdatesLiveData = AppDataSharingUpdatesLiveData(app)
    private val locationPermGroupPackagesUiInfoLiveData =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]

    /** Returns whether UI can provide link to help center */
    fun canLinkToHelpCenter(context: Context): Boolean {
        return !getHelpCenterUrlString(context).isNullOrEmpty()
    }

    /** Opens the Safety Label Help Center web page. */
    fun openSafetyLabelsHelpCenterPage(activity: Activity) {
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
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // TODO(b/266755891): show snackbar when help center intent unable to be opened
            Log.w(LOG_TAG, "Unable to open help center URL.", e)
        }
    }

    /**
     * Starts the App Permission fragment for location permission for the provided packageName and
     * userHandle.
     */
    fun startAppLocationPermissionPage(
        activity: Activity,
        sessionId: Long,
        packageName: String,
        userHandle: UserHandle
    ) {
        activity.startActivity(
            Intent(ACTION_MANAGE_APP_PERMISSION).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_PERMISSION_GROUP_NAME, Manifest.permission_group.LOCATION)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_USER, userHandle)
            }
        )
    }

    /**
     * Builds a list of [AppLocationDataSharingUpdateUiInfo], containing all the information
     * required to render the app data sharing updates.
     */
    private fun buildAppLocationDataSharingUpdateUiInfoList():
        List<AppLocationDataSharingUpdateUiInfo> {
        val appDataSharingUpdates = appDataSharingUpdatesLiveData.value ?: return listOf()

        return appDataSharingUpdates
            .map { appDataSharingUpdate ->
                val locationDataSharingUpdate =
                    appDataSharingUpdate.categorySharingUpdates[CATEGORY_LOCATION]

                if (locationDataSharingUpdate == null) {
                    emptyList()
                } else {
                    val users =
                        locationPermGroupPackagesUiInfoLiveData.getUsersWithPermGrantedForApp(
                            appDataSharingUpdate.packageName
                        )
                    users.map { user ->
                        // For each user profile under the current user, display one entry.
                        AppLocationDataSharingUpdateUiInfo(
                            appDataSharingUpdate.packageName,
                            user,
                            locationDataSharingUpdate
                        )
                    }
                }
            }
            .flatten()
    }

    private fun getHelpCenterUrlString(context: Context): String? {
        return context.getString(R.string.data_sharing_help_center_link)
    }

    private fun SinglePermGroupPackagesUiInfoLiveData.getUsersWithPermGrantedForApp(
        packageName: String
    ): List<UserHandle> {
        return value
            ?.filter {
                packageToPermInfoEntry: Map.Entry<Pair<String, UserHandle>, AppPermGroupUiInfo> ->
                val appPermGroupUiInfo = packageToPermInfoEntry.value

                appPermGroupUiInfo.isPermissionGranted()
            }
            ?.keys
            ?.filter { packageUser: Pair<String, UserHandle> -> packageUser.first == packageName }
            ?.map { packageUser: Pair<String, UserHandle> -> packageUser.second }
            ?: listOf()
    }

    private fun AppPermGroupUiInfo.isPermissionGranted() =
        permGrantState == PERMS_ALLOWED_ALWAYS || permGrantState == PERMS_ALLOWED_FOREGROUND_ONLY

    /** All the information necessary to display an app's data sharing update in the UI. */
    data class AppLocationDataSharingUpdateUiInfo(
        val packageName: String,
        val userHandle: UserHandle,
        val dataSharingUpdateType: DataSharingUpdateType
    )

    /** LiveData for all data sharing updates to be displayed in the UI. */
    val appLocationDataSharingUpdateUiInfoLiveData =
        object : SmartAsyncMediatorLiveData<List<AppLocationDataSharingUpdateUiInfo>>() {

            init {
                addSource(appDataSharingUpdatesLiveData) { onUpdate() }
                addSource(locationPermGroupPackagesUiInfoLiveData) { onUpdate() }
            }

            override suspend fun loadDataAndPostValue(job: Job) {
                if (locationPermGroupPackagesUiInfoLiveData.isStale) {
                    return
                }

                if (appDataSharingUpdatesLiveData.isStale) {
                    return
                }

                postValue(buildAppLocationDataSharingUpdateUiInfoList())
            }
        }

    /** Companion object for [AppDataSharingUpdatesViewModel]. */
    companion object {
        private val LOG_TAG = AppDataSharingUpdatesViewModel::class.java.simpleName
    }
}

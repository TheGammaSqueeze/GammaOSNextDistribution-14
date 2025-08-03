/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui.model

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission_group.READ_MEDIA_VISUAL
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_ERRORED
import android.app.AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PERMISSION_RATIONALE
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_VIEWED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData.FullStoragePackageState
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v34.SafetyLabelInfoLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.service.PermissionChangeStorageImpl
import com.android.permissioncontroller.permission.service.v33.PermissionDecisionStorageImpl
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.ALLOW
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.ALLOW_ALWAYS
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.ALLOW_FOREGROUND
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.ASK
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.ASK_ONCE
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.DENY
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.DENY_FOREGROUND
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.LOCATION_ACCURACY
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType.SELECT_PHOTOS
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleActivity
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleActivity.EXTRA_SHOULD_SHOW_SETTINGS_SECTION
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.isLocationAccuracyEnabled
import com.android.permissioncontroller.permission.utils.KotlinUtils.isPhotoPickerPromptEnabled
import com.android.permissioncontroller.permission.utils.KotlinUtils.openPhotoPickerForApp
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.PermissionMapping.getPartialStorageGrantPermissionsForGroup
import com.android.permissioncontroller.permission.utils.SafetyNetLogger
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.navigateSafe
import com.android.permissioncontroller.permission.utils.v34.SafetyLabelUtils
import com.android.settingslib.RestrictedLockUtils
import java.util.Random
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * ViewModel for the AppPermissionFragment. Determines button state and detail text strings, logs
 * permission change information, and makes permission changes.
 *
 * @param app The current application
 * @param packageName The name of the package this ViewModel represents
 * @param permGroupName The name of the permission group this ViewModel represents
 * @param user The user of the package
 * @param sessionId A session ID used in logs to identify this particular session
 */
class AppPermissionViewModel(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModel() {

    companion object {
        private val LOG_TAG = AppPermissionViewModel::class.java.simpleName
        private const val DEVICE_PROFILE_ROLE_PREFIX = "android.app.role"
    }

    interface ConfirmDialogShowingFragment {
        fun showConfirmDialog(
            changeRequest: ChangeRequest,
            @StringRes messageId: Int,
            buttonPressed: Int,
            oneTime: Boolean
        )

        fun showAdvancedConfirmDialog(args: AdvancedConfirmDialogArgs)
    }

    enum class ChangeRequest(val value: Int) {
        GRANT_FOREGROUND(1 shl 0),
        REVOKE_FOREGROUND(1 shl 1),
        GRANT_BACKGROUND(1 shl 2),
        REVOKE_BACKGROUND(1 shl 3),
        GRANT_BOTH(GRANT_FOREGROUND.value or GRANT_BACKGROUND.value),
        REVOKE_BOTH(REVOKE_FOREGROUND.value or REVOKE_BACKGROUND.value),
        GRANT_FOREGROUND_ONLY(GRANT_FOREGROUND.value or REVOKE_BACKGROUND.value),
        GRANT_ALL_FILE_ACCESS(1 shl 4),
        GRANT_FINE_LOCATION(1 shl 5),
        REVOKE_FINE_LOCATION(1 shl 6),
        GRANT_STORAGE_SUPERGROUP(1 shl 7),
        REVOKE_STORAGE_SUPERGROUP(1 shl 8),
        GRANT_STORAGE_SUPERGROUP_CONFIRMED(
            GRANT_STORAGE_SUPERGROUP.value or GRANT_FOREGROUND.value
        ),
        REVOKE_STORAGE_SUPERGROUP_CONFIRMED(REVOKE_STORAGE_SUPERGROUP.value or REVOKE_BOTH.value),
        PHOTOS_SELECTED(1 shl 9);

        infix fun andValue(other: ChangeRequest): Int {
            return value and other.value
        }
    }

    enum class ButtonType(val type: Int) {
        ALLOW(0),
        ALLOW_ALWAYS(1),
        ALLOW_FOREGROUND(2),
        ASK_ONCE(3),
        ASK(4),
        DENY(5),
        DENY_FOREGROUND(6),
        LOCATION_ACCURACY(7),
        SELECT_PHOTOS(8)
    }

    private val isStorageAndLessThanT =
        permGroupName == Manifest.permission_group.STORAGE && !SdkLevel.isAtLeastT()
    private var hasConfirmedRevoke = false
    private var lightAppPermGroup: LightAppPermGroup? = null

    private val mediaStorageSupergroupPermGroups = mutableMapOf<String, LightAppPermGroup>()

    /* Whether the current ViewModel is Location permission with both Coarse and Fine */
    private var shouldShowLocationAccuracy: Boolean? = null

    /** A livedata which determines which detail string, if any, should be shown */
    val detailResIdLiveData = MutableLiveData<Pair<Int, Int?>>()
    /** A livedata which stores the device admin, if there is one */
    val showAdminSupportLiveData = MutableLiveData<RestrictedLockUtils.EnforcedAdmin>()

    /** A livedata for determining the display state of safety label information */
    val showPermissionRationaleLiveData =
        object : SmartUpdateMediatorLiveData<Boolean>() {
            private val safetyLabelInfoLiveData =
                if (SdkLevel.isAtLeastU()) {
                    SafetyLabelInfoLiveData[packageName, user]
                } else {
                    null
                }

            init {
                if (
                    safetyLabelInfoLiveData != null &&
                        PermissionMapping.isSafetyLabelAwarePermissionGroup(permGroupName)
                ) {
                    addSource(safetyLabelInfoLiveData) { update() }
                } else {
                    value = false
                }
            }

            override fun onUpdate() {
                if (safetyLabelInfoLiveData != null && safetyLabelInfoLiveData.isStale) {
                    return
                }

                val safetyLabel = safetyLabelInfoLiveData?.value?.safetyLabel
                if (safetyLabel == null) {
                    value = false
                    return
                }

                value =
                    SafetyLabelUtils.getSafetyLabelSharingPurposesForGroup(
                            safetyLabel,
                            permGroupName
                        )
                        .any()
            }
        }

    /** A livedata which determines which detail string, if any, should be shown */
    val fullStorageStateLiveData =
        object : SmartUpdateMediatorLiveData<FullStoragePackageState>() {
            init {
                if (isStorageAndLessThanT) {
                    addSource(FullStoragePermissionAppsLiveData) { update() }
                } else {
                    value = null
                }
            }
            override fun onUpdate() {
                for (state in FullStoragePermissionAppsLiveData.value ?: return) {
                    if (state.packageName == packageName && state.user == user) {
                        value = state
                        return
                    }
                }
                value = null
                return
            }
        }

    data class ButtonState(
        var isChecked: Boolean,
        var isEnabled: Boolean,
        var isShown: Boolean,
        var customRequest: ChangeRequest?
    ) {
        constructor() : this(false, true, false, null)
    }

    /** A livedata which computes the state of the radio buttons */
    val buttonStateLiveData =
        object : SmartUpdateMediatorLiveData<@JvmSuppressWildcards Map<ButtonType, ButtonState>>() {

            private val appPermGroupLiveData =
                LightAppPermGroupLiveData[packageName, permGroupName, user]
            private val mediaStorageSupergroupLiveData =
                mutableMapOf<String, LightAppPermGroupLiveData>()

            init {
                addSource(appPermGroupLiveData) { appPermGroup ->
                    lightAppPermGroup = appPermGroup
                    if (permGroupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS) {
                        onMediaPermGroupUpdate(permGroupName, appPermGroup)
                    }
                    if (appPermGroupLiveData.isInitialized && appPermGroup == null) {
                        value = null
                    } else if (appPermGroup != null) {
                        if (isStorageAndLessThanT && !fullStorageStateLiveData.isInitialized) {
                            return@addSource
                        }
                        update()
                    }
                }

                if (isStorageAndLessThanT) {
                    addSource(fullStorageStateLiveData) { update() }
                }

                if (permGroupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS) {
                    for (permGroupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS) {
                        val liveData = LightAppPermGroupLiveData[packageName, permGroupName, user]
                        mediaStorageSupergroupLiveData[permGroupName] = liveData
                    }
                    for (permGroupName in mediaStorageSupergroupLiveData.keys) {
                        val liveData = mediaStorageSupergroupLiveData[permGroupName]!!
                        addSource(liveData) { permGroup ->
                            onMediaPermGroupUpdate(permGroupName, permGroup)
                        }
                    }
                }

                addSource(showPermissionRationaleLiveData) { update() }
            }

            private fun onMediaPermGroupUpdate(
                permGroupName: String,
                permGroup: LightAppPermGroup?
            ) {
                if (permGroup == null) {
                    mediaStorageSupergroupPermGroups.remove(permGroupName)
                    value = null
                } else {
                    mediaStorageSupergroupPermGroups[permGroupName] = permGroup
                    update()
                }
            }

            override fun onUpdate() {
                val group = appPermGroupLiveData.value ?: return
                for (mediaGroupLiveData in mediaStorageSupergroupLiveData.values) {
                    if (!mediaGroupLiveData.isInitialized) {
                        return
                    }
                }

                if (!showPermissionRationaleLiveData.isInitialized) {
                    return
                }

                val admin = RestrictedLockUtils.getProfileOrDeviceOwner(app, user)

                val allowedState = ButtonState()
                val allowedAlwaysState = ButtonState()
                val allowedForegroundState = ButtonState()
                val askOneTimeState = ButtonState()
                val askState = ButtonState()
                val deniedState = ButtonState()
                val deniedForegroundState = ButtonState()
                val selectState = ButtonState()

                askOneTimeState.isShown = group.foreground.isGranted && group.isOneTime
                askState.isShown =
                    PermissionMapping.supportsOneTimeGrant(permGroupName) &&
                        !(group.foreground.isGranted && group.isOneTime)
                deniedState.isShown = true

                if (group.hasPermWithBackgroundMode) {
                    // Background / Foreground / Deny case
                    allowedForegroundState.isShown = true
                    if (group.hasBackgroundGroup) {
                        allowedAlwaysState.isShown = true
                    }

                    allowedAlwaysState.isChecked =
                        group.background.isGranted &&
                            group.foreground.isGranted &&
                            !group.background.isOneTime
                    allowedForegroundState.isChecked =
                        group.foreground.isGranted &&
                            (!group.background.isGranted || group.background.isOneTime) &&
                            !group.foreground.isOneTime
                    askState.isChecked = !group.foreground.isGranted && group.isOneTime
                    askOneTimeState.isChecked = group.foreground.isGranted && group.isOneTime
                    askOneTimeState.isShown = askOneTimeState.isChecked
                    deniedState.isChecked = !group.foreground.isGranted && !group.isOneTime
                    if (
                        applyFixToForegroundBackground(
                            group,
                            group.foreground.isSystemFixed,
                            group.background.isSystemFixed,
                            allowedAlwaysState,
                            allowedForegroundState,
                            askState,
                            deniedState,
                            deniedForegroundState
                        ) ||
                            applyFixToForegroundBackground(
                                group,
                                group.foreground.isPolicyFixed,
                                group.background.isPolicyFixed,
                                allowedAlwaysState,
                                allowedForegroundState,
                                askState,
                                deniedState,
                                deniedForegroundState
                            )
                    ) {
                        showAdminSupportLiveData.value = admin
                        val detailId =
                            getDetailResIdForFixedByPolicyPermissionGroup(group, admin != null)
                        if (detailId != 0) {
                            detailResIdLiveData.value = detailId to null
                        }
                    } else if (
                        Utils.areGroupPermissionsIndividuallyControlled(app, permGroupName)
                    ) {
                        val detailId = getIndividualPermissionDetailResId(group)
                        detailResIdLiveData.value = detailId.first to detailId.second
                    }
                } else if (
                    shouldShowPhotoPickerPromptForApp(group) &&
                        group.permGroupName == READ_MEDIA_VISUAL
                ) {
                    // Allow / Select Photos / Deny case
                    allowedState.isShown = true
                    deniedState.isShown = true
                    selectState.isShown = true

                    deniedState.isChecked = !group.isGranted
                    selectState.isChecked = isPartialStorageGrant(group)
                    allowedState.isChecked = group.isGranted && !isPartialStorageGrant(group)
                    if (group.foreground.isPolicyFixed || group.foreground.isSystemFixed) {
                        allowedState.isEnabled = false
                        selectState.isEnabled = false
                        deniedState.isEnabled = false
                        showAdminSupportLiveData.value = admin
                        val detailId =
                            getDetailResIdForFixedByPolicyPermissionGroup(group, admin != null)
                        if (detailId != 0) {
                            detailResIdLiveData.value = detailId to null
                        }
                    }
                } else {
                    // Allow / Deny case
                    allowedState.isShown = true

                    allowedState.isChecked =
                        group.foreground.isGranted && !group.foreground.isOneTime
                    askState.isChecked = !group.foreground.isGranted && group.isOneTime
                    askOneTimeState.isChecked = group.foreground.isGranted && group.isOneTime
                    askOneTimeState.isShown = askOneTimeState.isChecked
                    deniedState.isChecked = !group.foreground.isGranted && !group.isOneTime

                    if (group.foreground.isPolicyFixed || group.foreground.isSystemFixed) {
                        allowedState.isEnabled = false
                        askState.isEnabled = false
                        deniedState.isEnabled = false
                        showAdminSupportLiveData.value = admin
                        val detailId =
                            getDetailResIdForFixedByPolicyPermissionGroup(group, admin != null)
                        if (detailId != 0) {
                            detailResIdLiveData.value = detailId to null
                        }
                    }
                    if (isForegroundGroupSpecialCase(permGroupName)) {
                        allowedForegroundState.isShown = true
                        allowedState.isShown = false
                        allowedForegroundState.isChecked = allowedState.isChecked
                        allowedForegroundState.isEnabled = allowedState.isEnabled
                    }
                }
                if (group.packageInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                    // Pre-M app's can't ask for runtime permissions
                    askState.isShown = false
                    deniedState.isChecked = askState.isChecked || deniedState.isChecked
                    deniedForegroundState.isChecked =
                        askState.isChecked || deniedForegroundState.isChecked
                }

                val storageState = fullStorageStateLiveData.value
                if (isStorageAndLessThanT && storageState?.isLegacy != true) {
                    val allowedAllFilesState = allowedAlwaysState
                    val allowedMediaOnlyState = allowedForegroundState
                    if (storageState != null) {
                        // Set up the tri state permission for storage
                        allowedAllFilesState.isEnabled = allowedState.isEnabled
                        allowedAllFilesState.isShown = true
                        if (storageState.isGranted) {
                            allowedAllFilesState.isChecked = true
                            deniedState.isChecked = false
                        }
                    } else {
                        allowedAllFilesState.isEnabled = false
                        allowedAllFilesState.isShown = false
                    }
                    allowedMediaOnlyState.isShown = true
                    allowedMediaOnlyState.isEnabled = allowedState.isEnabled
                    allowedMediaOnlyState.isChecked =
                        allowedState.isChecked && storageState?.isGranted != true
                    allowedState.isChecked = false
                    allowedState.isShown = false
                }

                if (shouldShowLocationAccuracy == null) {
                    shouldShowLocationAccuracy =
                        isLocationAccuracyAvailableForApp(group) &&
                            group.permissions.containsKey(ACCESS_FINE_LOCATION)
                }
                val locationAccuracyState =
                    ButtonState(isFineLocationChecked(group), true, false, null)
                if (shouldShowLocationAccuracy == true && !deniedState.isChecked) {
                    locationAccuracyState.isShown = true
                }
                if (group.foreground.isSystemFixed || group.foreground.isPolicyFixed) {
                    locationAccuracyState.isEnabled = false
                }

                if (value == null) {
                    logAppPermissionFragmentViewed()
                }

                value =
                    mapOf(
                        ALLOW to allowedState,
                        ALLOW_ALWAYS to allowedAlwaysState,
                        ALLOW_FOREGROUND to allowedForegroundState,
                        ASK_ONCE to askOneTimeState,
                        ASK to askState,
                        DENY to deniedState,
                        DENY_FOREGROUND to deniedForegroundState,
                        LOCATION_ACCURACY to locationAccuracyState,
                        SELECT_PHOTOS to selectState
                    )
            }
        }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    private fun shouldShowPhotoPickerPromptForApp(group: LightAppPermGroup): Boolean {
        if (
            !isPhotoPickerPromptEnabled() ||
                group.packageInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU
        ) {
            return false
        }
        if (group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val userSelectedPerm = group.permissions[READ_MEDIA_VISUAL_USER_SELECTED] ?: return false
        return !userSelectedPerm.isImplicit
    }

    private fun isLocationAccuracyAvailableForApp(group: LightAppPermGroup): Boolean {
        return isLocationAccuracyEnabled() &&
            group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.S
    }

    private fun isFineLocationChecked(group: LightAppPermGroup): Boolean {
        if (shouldShowLocationAccuracy == true) {
            val coarseLocation = group.permissions[ACCESS_COARSE_LOCATION]!!
            val fineLocation = group.permissions[ACCESS_FINE_LOCATION]!!
            // Steps to decide location accuracy toggle state
            // 1. If FINE or COARSE are granted, then return true if FINE is granted.
            // 2. Else if FINE or COARSE have the isSelectedLocationAccuracy flag set, then return
            //    true if FINE isSelectedLocationAccuracy is set.
            // 3. Else, return default precision from device config.
            return if (fineLocation.isGranted || coarseLocation.isGranted) {
                fineLocation.isGranted
            } else if (
                fineLocation.isSelectedLocationAccuracy || coarseLocation.isSelectedLocationAccuracy
            ) {
                fineLocation.isSelectedLocationAccuracy
            } else {
                // default location precision is true, indicates FINE
                true
            }
        }
        return false
    }

    // TODO evanseverson: Actually change mic/camera to be a foreground only permission
    private fun isForegroundGroupSpecialCase(permissionGroupName: String): Boolean {
        return permissionGroupName.equals(Manifest.permission_group.CAMERA) ||
            permissionGroupName.equals(Manifest.permission_group.MICROPHONE)
    }

    /**
     * Modifies the radio buttons to reflect the current policy fixing state
     *
     * @return if anything was changed
     */
    private fun applyFixToForegroundBackground(
        group: LightAppPermGroup,
        isForegroundFixed: Boolean,
        isBackgroundFixed: Boolean,
        allowedAlwaysState: ButtonState,
        allowedForegroundState: ButtonState,
        askState: ButtonState,
        deniedState: ButtonState,
        deniedForegroundState: ButtonState
    ): Boolean {
        if (isBackgroundFixed && isForegroundFixed) {
            // Background and foreground are both policy fixed. Disable everything
            allowedAlwaysState.isEnabled = false
            allowedForegroundState.isEnabled = false
            askState.isEnabled = false
            deniedState.isEnabled = false

            if (askState.isChecked) {
                askState.isChecked = false
                deniedState.isChecked = true
            }
        } else if (isBackgroundFixed && !isForegroundFixed) {
            if (group.background.isGranted) {
                // Background policy fixed as granted, foreground flexible. Granting
                // foreground implies background comes with it in this case.
                // Only allow user to grant background or deny (which only toggles fg)
                allowedForegroundState.isEnabled = false
                askState.isEnabled = false
                deniedState.isShown = false
                deniedForegroundState.isShown = true
                deniedForegroundState.isChecked = deniedState.isChecked

                if (askState.isChecked) {
                    askState.isChecked = false
                    deniedState.isChecked = true
                }
            } else {
                // Background policy fixed as not granted, foreground flexible
                allowedAlwaysState.isEnabled = false
            }
        } else if (!isBackgroundFixed && isForegroundFixed) {
            if (group.foreground.isGranted) {
                // Foreground is fixed as granted, background flexible.
                // Allow switching between foreground and background. No denying
                allowedForegroundState.isEnabled = allowedAlwaysState.isShown
                askState.isEnabled = false
                deniedState.isEnabled = false
            } else {
                // Foreground is fixed denied. Background irrelevant
                allowedAlwaysState.isEnabled = false
                allowedForegroundState.isEnabled = false
                askState.isEnabled = false
                deniedState.isEnabled = false

                if (askState.isChecked) {
                    askState.isChecked = false
                    deniedState.isChecked = true
                }
            }
        } else {
            return false
        }
        return true
    }

    /**
     * Shows the Permission Rationale Dialog. For use with U+ only, otherwise no-op.
     *
     * @param activity The current activity
     * @param groupName The name of the permission group whose fragment should be opened
     */
    fun showPermissionRationaleActivity(activity: Activity, groupName: String) {
        if (!SdkLevel.isAtLeastU()) {
            return
        }

        // logPermissionChanges logs the button clicks for settings and any associated permission
        // change that occurred. Since no permission change takes place, just pass the current
        // permission state.
        lightAppPermGroup?.let { group ->
            logAppPermissionFragmentActionReportedForPermissionGroup(
                /* changeId= */ Random().nextLong(),
                group,
                APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PERMISSION_RATIONALE
            )
        }

        val intent =
            Intent(activity, PermissionRationaleActivity::class.java).apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_SHOULD_SHOW_SETTINGS_SECTION, false)
            }
        activity.startActivity(intent)
    }

    /**
     * Navigate to either the App Permission Groups screen, or the Permission Apps Screen.
     *
     * @param fragment The current fragment
     * @param action The action to be taken
     * @param args The arguments to pass to the fragment
     */
    fun showBottomLinkPage(fragment: Fragment, action: String, args: Bundle) {
        var actionId = R.id.app_to_perm_groups
        if (action == Intent.ACTION_MANAGE_PERMISSION_APPS) {
            actionId = R.id.app_to_perm_apps
        }

        fragment.findNavController().navigateSafe(actionId, args)
    }

    fun openPhotoPicker(fragment: Fragment) {
        val appPermGroup = lightAppPermGroup ?: return
        openPhotoPickerForApp(
            fragment.requireActivity(),
            appPermGroup.packageInfo.uid,
            appPermGroup.foregroundPermNames,
            0
        )
    }

    /**
     * Request to grant/revoke permissions group.
     *
     * Does <u>not</u> handle:
     * * Individually granted permissions
     * * Permission groups with background permissions
     *
     * <u>Does</u> handle:
     * * Default grant permissions
     *
     * @param setOneTime Whether or not to set this permission as one time
     * @param fragment The fragment calling this method
     * @param defaultDeny The system which will show the default deny dialog. Usually the same as
     *   the fragment.
     * @param changeRequest Which permission group (foreground/background/both) should be changed
     * @param buttonClicked button which was pressed to initiate the change, one of
     *   AppPermissionFragmentActionReported.button_pressed constants
     * @return The dialogue to show, if applicable, or if the request was processed.
     */
    fun requestChange(
        setOneTime: Boolean,
        fragment: Fragment,
        defaultDeny: ConfirmDialogShowingFragment,
        changeRequest: ChangeRequest,
        buttonClicked: Int
    ) {
        val context = fragment.context ?: return
        val group = lightAppPermGroup ?: return
        val wasForegroundGranted = group.foreground.isGranted
        val wasBackgroundGranted = group.background.isGranted

        if (LocationUtils.isLocationGroupAndProvider(context, permGroupName, packageName)) {
            val packageLabel = KotlinUtils.getPackageLabel(app, packageName, user)
            LocationUtils.showLocationDialog(context, packageLabel)
        }

        if (changeRequest == ChangeRequest.GRANT_FINE_LOCATION) {
            if (!group.isOneTime) {
                val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)
                logPermissionChanges(group, newGroup, buttonClicked)
            }
            KotlinUtils.setFlagsWhenLocationAccuracyChanged(app, group, true)
            return
        }

        if (changeRequest == ChangeRequest.REVOKE_FINE_LOCATION) {
            if (!group.isOneTime) {
                val newGroup =
                    KotlinUtils.revokeForegroundRuntimePermissions(
                        app,
                        group,
                        filterPermissions = listOf(ACCESS_FINE_LOCATION)
                    )
                logPermissionChanges(group, newGroup, buttonClicked)
            }
            KotlinUtils.setFlagsWhenLocationAccuracyChanged(app, group, false)
            return
        }

        if (changeRequest == ChangeRequest.PHOTOS_SELECTED) {
            val partialGrantPerms = getPartialStorageGrantPermissionsForGroup(group)
            val nonSelectedPerms = group.permissions.keys.filter { it !in partialGrantPerms }
            var newGroup =
                KotlinUtils.revokeForegroundRuntimePermissions(
                    app,
                    group,
                    filterPermissions = nonSelectedPerms
                )
            newGroup =
                KotlinUtils.grantForegroundRuntimePermissions(
                    app,
                    newGroup,
                    filterPermissions = partialGrantPerms.toList()
                )
            logPermissionChanges(group, newGroup, buttonClicked)
            return
        }

        val shouldGrantForeground = changeRequest andValue ChangeRequest.GRANT_FOREGROUND != 0
        val shouldGrantBackground = changeRequest andValue ChangeRequest.GRANT_BACKGROUND != 0
        val shouldRevokeForeground = changeRequest andValue ChangeRequest.REVOKE_FOREGROUND != 0
        val shouldRevokeBackground = changeRequest andValue ChangeRequest.REVOKE_BACKGROUND != 0
        var showDefaultDenyDialog = false
        var showGrantedByDefaultWarning = false
        var showCDMWarning = false

        if (shouldRevokeForeground && wasForegroundGranted) {
            showDefaultDenyDialog =
                (group.foreground.isGrantedByDefault ||
                    !group.supportsRuntimePerms ||
                    group.hasInstallToRuntimeSplit)
            showGrantedByDefaultWarning =
                showGrantedByDefaultWarning || group.foreground.isGrantedByDefault
            showCDMWarning = showCDMWarning || group.foreground.isGrantedByRole
        }

        if (shouldRevokeBackground && wasBackgroundGranted) {
            showDefaultDenyDialog =
                showDefaultDenyDialog ||
                    group.background.isGrantedByDefault ||
                    !group.supportsRuntimePerms ||
                    group.hasInstallToRuntimeSplit
            showGrantedByDefaultWarning =
                showGrantedByDefaultWarning || group.background.isGrantedByDefault
            showCDMWarning = showCDMWarning || group.background.isGrantedByRole
        }

        if (showCDMWarning) {
            // Refine showCDMWarning to only trigger for apps holding a device profile role
            val heldRoles =
                context
                    .getSystemService(android.app.role.RoleManager::class.java)!!
                    .getHeldRolesFromController(packageName)
            val heldProfiles = heldRoles.filter { it.startsWith(DEVICE_PROFILE_ROLE_PREFIX) }
            showCDMWarning = showCDMWarning && heldProfiles.isNotEmpty()
        }

        if (expandsToStorageSupergroup(group)) {
            if (group.permGroupName == Manifest.permission_group.STORAGE) {
                showDefaultDenyDialog = false
            } else if (changeRequest == ChangeRequest.GRANT_FOREGROUND) {
                showMediaConfirmDialog(
                    setOneTime,
                    defaultDeny,
                    ChangeRequest.GRANT_STORAGE_SUPERGROUP,
                    buttonClicked,
                    group.permGroupName,
                    group.packageInfo.targetSdkVersion
                )
                return
            } else if (changeRequest == ChangeRequest.REVOKE_BOTH) {
                showMediaConfirmDialog(
                    setOneTime,
                    defaultDeny,
                    ChangeRequest.REVOKE_STORAGE_SUPERGROUP,
                    buttonClicked,
                    group.permGroupName,
                    group.packageInfo.targetSdkVersion
                )
                return
            } else {
                showDefaultDenyDialog = false
            }
        }

        if (showDefaultDenyDialog && !hasConfirmedRevoke && showGrantedByDefaultWarning) {
            defaultDeny.showConfirmDialog(
                changeRequest,
                R.string.system_warning,
                buttonClicked,
                setOneTime
            )
            return
        }

        if (showDefaultDenyDialog && !hasConfirmedRevoke) {
            defaultDeny.showConfirmDialog(
                changeRequest,
                R.string.old_sdk_deny_warning,
                buttonClicked,
                setOneTime
            )
            return
        }

        if (showCDMWarning) {
            defaultDeny.showConfirmDialog(
                changeRequest,
                R.string.cdm_profile_revoke_warning,
                buttonClicked,
                setOneTime
            )
            return
        }

        val groupsToUpdate = expandToSupergroup(group)
        for (group2 in groupsToUpdate) {
            var newGroup = group2
            val oldGroup = group2

            if (
                shouldRevokeBackground &&
                    group2.hasBackgroundGroup &&
                    (wasBackgroundGranted ||
                        group2.background.isUserFixed ||
                        group2.isOneTime != setOneTime)
            ) {
                newGroup =
                    KotlinUtils.revokeBackgroundRuntimePermissions(
                        app,
                        newGroup,
                        oneTime = setOneTime,
                        forceRemoveRevokedCompat = shouldClearOneTimeRevokedCompat(newGroup)
                    )

                // only log if we have actually denied permissions, not if we switch from
                // "ask every time" to denied
                if (wasBackgroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup, true)
                }
            }

            if (
                shouldRevokeForeground && (wasForegroundGranted || group2.isOneTime != setOneTime)
            ) {
                newGroup =
                    KotlinUtils.revokeForegroundRuntimePermissions(
                        app,
                        newGroup,
                        userFixed = false,
                        oneTime = setOneTime,
                        forceRemoveRevokedCompat = shouldClearOneTimeRevokedCompat(newGroup)
                    )

                // only log if we have actually denied permissions, not if we switch from
                // "ask every time" to denied
                if (wasForegroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup)
                }
            }

            if (shouldGrantForeground) {
                newGroup =
                    if (shouldShowLocationAccuracy == true && !isFineLocationChecked(newGroup)) {
                        KotlinUtils.grantForegroundRuntimePermissions(
                            app,
                            newGroup,
                            filterPermissions = listOf(ACCESS_COARSE_LOCATION)
                        )
                    } else {
                        KotlinUtils.grantForegroundRuntimePermissions(app, newGroup)
                    }

                if (!wasForegroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup)
                }
            }

            if (shouldGrantBackground && group2.hasBackgroundGroup) {
                newGroup = KotlinUtils.grantBackgroundRuntimePermissions(app, newGroup)

                if (!wasBackgroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup, true)
                }
            }

            logPermissionChanges(oldGroup, newGroup, buttonClicked)

            fullStorageStateLiveData.value?.let { FullStoragePermissionAppsLiveData.recalculate() }
        }
    }

    private fun shouldClearOneTimeRevokedCompat(group: LightAppPermGroup): Boolean {
        return isPhotoPickerPromptEnabled() &&
            permGroupName == READ_MEDIA_VISUAL &&
            group.permissions.values.any { it.isCompatRevoked && it.isOneTime }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun expandsToStorageSupergroup(group: LightAppPermGroup): Boolean {
        return group.packageInfo.targetSdkVersion <= Build.VERSION_CODES.S_V2 &&
            group.permGroupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS
    }

    private fun expandToSupergroup(group: LightAppPermGroup): List<LightAppPermGroup> {
        val mediaSupergroup =
            PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS.mapNotNull {
                mediaStorageSupergroupPermGroups[it]
            }
        return if (expandsToStorageSupergroup(group)) {
            mediaSupergroup
        } else {
            listOf(group)
        }
    }

    private fun getPermGroupIcon(permGroup: String) =
        Utils.getGroupInfo(permGroup, app.applicationContext)?.icon ?: R.drawable.ic_empty_icon

    private val storagePermGroupIcon = getPermGroupIcon(Manifest.permission_group.STORAGE)

    private val auralPermGroupIcon =
        if (SdkLevel.isAtLeastT()) {
            getPermGroupIcon(Manifest.permission_group.READ_MEDIA_AURAL)
        } else {
            R.drawable.ic_empty_icon
        }

    private val visualPermGroupIcon =
        if (SdkLevel.isAtLeastT()) {
            getPermGroupIcon(Manifest.permission_group.READ_MEDIA_VISUAL)
        } else {
            R.drawable.ic_empty_icon
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showMediaConfirmDialog(
        setOneTime: Boolean,
        confirmDialog: ConfirmDialogShowingFragment,
        changeRequest: ChangeRequest,
        buttonClicked: Int,
        groupName: String,
        targetSdk: Int
    ) {
        val aural = groupName == Manifest.permission_group.READ_MEDIA_AURAL
        val visual = groupName == Manifest.permission_group.READ_MEDIA_VISUAL
        val allow = changeRequest === ChangeRequest.GRANT_STORAGE_SUPERGROUP
        val deny = changeRequest === ChangeRequest.REVOKE_STORAGE_SUPERGROUP

        val (iconId, titleId, messageId) =
            when {
                targetSdk < Build.VERSION_CODES.Q && aural && allow ->
                    Triple(
                        storagePermGroupIcon,
                        R.string.media_confirm_dialog_title_a_to_p_aural_allow,
                        R.string.media_confirm_dialog_message_a_to_p_aural_allow
                    )
                targetSdk < Build.VERSION_CODES.Q && aural && deny ->
                    Triple(
                        storagePermGroupIcon,
                        R.string.media_confirm_dialog_title_a_to_p_aural_deny,
                        R.string.media_confirm_dialog_message_a_to_p_aural_deny
                    )
                targetSdk < Build.VERSION_CODES.Q && visual && allow ->
                    Triple(
                        storagePermGroupIcon,
                        R.string.media_confirm_dialog_title_a_to_p_visual_allow,
                        R.string.media_confirm_dialog_message_a_to_p_visual_allow
                    )
                targetSdk < Build.VERSION_CODES.Q && visual && deny ->
                    Triple(
                        storagePermGroupIcon,
                        R.string.media_confirm_dialog_title_a_to_p_visual_deny,
                        R.string.media_confirm_dialog_message_a_to_p_visual_deny
                    )
                targetSdk <= Build.VERSION_CODES.S_V2 && aural && allow ->
                    Triple(
                        visualPermGroupIcon,
                        R.string.media_confirm_dialog_title_q_to_s_aural_allow,
                        R.string.media_confirm_dialog_message_q_to_s_aural_allow
                    )
                targetSdk <= Build.VERSION_CODES.S_V2 && aural && deny ->
                    Triple(
                        visualPermGroupIcon,
                        R.string.media_confirm_dialog_title_q_to_s_aural_deny,
                        R.string.media_confirm_dialog_message_q_to_s_aural_deny
                    )
                targetSdk <= Build.VERSION_CODES.S_V2 && visual && allow ->
                    Triple(
                        auralPermGroupIcon,
                        R.string.media_confirm_dialog_title_q_to_s_visual_allow,
                        R.string.media_confirm_dialog_message_q_to_s_visual_allow
                    )
                targetSdk <= Build.VERSION_CODES.S_V2 && visual && deny ->
                    Triple(
                        auralPermGroupIcon,
                        R.string.media_confirm_dialog_title_q_to_s_visual_deny,
                        R.string.media_confirm_dialog_message_q_to_s_visual_deny
                    )
                else -> Triple(0, 0, 0)
            }

        if (iconId == 0 || titleId == 0 || messageId == 0) {
            throw UnsupportedOperationException()
        }

        confirmDialog.showAdvancedConfirmDialog(
            AdvancedConfirmDialogArgs(
                iconId = iconId,
                titleId = titleId,
                messageId = messageId,
                negativeButtonTextId = R.string.media_confirm_dialog_negative_button,
                positiveButtonTextId = R.string.media_confirm_dialog_positive_button,
                changeRequest =
                    if (allow) ChangeRequest.GRANT_STORAGE_SUPERGROUP_CONFIRMED
                    else ChangeRequest.REVOKE_STORAGE_SUPERGROUP_CONFIRMED,
                setOneTime = setOneTime,
                buttonClicked = buttonClicked
            )
        )
    }

    /**
     * Once the user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @param changeRequest whether to change foreground, background, or both.
     * @param buttonPressed button pressed to initiate the change, one of
     *   AppPermissionFragmentActionReported.button_pressed constants
     * @param oneTime whether the change should show that the permission was selected as one-time
     */
    fun onDenyAnyWay(changeRequest: ChangeRequest, buttonPressed: Int, oneTime: Boolean) {
        val unexpandedGroup = lightAppPermGroup ?: return

        for (group in expandToSupergroup(unexpandedGroup)) {
            val wasForegroundGranted = group.foreground.isGranted
            val wasBackgroundGranted = group.background.isGranted
            var hasDefaultPermissions = false

            var newGroup = group
            val oldGroup = group

            if (
                changeRequest andValue ChangeRequest.REVOKE_BACKGROUND != 0 &&
                    group.hasBackgroundGroup
            ) {
                newGroup =
                    KotlinUtils.revokeBackgroundRuntimePermissions(app, newGroup, false, oneTime)

                if (wasBackgroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup)
                }
                hasDefaultPermissions = hasDefaultPermissions || group.background.isGrantedByDefault
            }

            if (changeRequest andValue ChangeRequest.REVOKE_FOREGROUND != 0) {
                newGroup =
                    KotlinUtils.revokeForegroundRuntimePermissions(app, newGroup, false, oneTime)
                if (wasForegroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup)
                }
                hasDefaultPermissions = group.foreground.isGrantedByDefault
            }
            logPermissionChanges(oldGroup, newGroup, buttonPressed)

            if (hasDefaultPermissions || !group.supportsRuntimePerms) {
                hasConfirmedRevoke = true
            }

            fullStorageStateLiveData.value?.let { FullStoragePermissionAppsLiveData.recalculate() }
        }
    }

    /**
     * Set the All Files access for this app
     *
     * @param granted Whether to grant or revoke access
     */
    fun setAllFilesAccess(granted: Boolean) {
        val aom = app.getSystemService(AppOpsManager::class.java)!!
        val uid = lightAppPermGroup?.packageInfo?.uid ?: return
        val mode =
            if (granted) {
                MODE_ALLOWED
            } else {
                MODE_ERRORED
            }
        val fullStorageGrant = fullStorageStateLiveData.value?.isGranted
        if (fullStorageGrant != null && fullStorageGrant != granted) {
            aom.setUidMode(OPSTR_MANAGE_EXTERNAL_STORAGE, uid, mode)
            FullStoragePermissionAppsLiveData.recalculate()
        }
    }

    /**
     * Show the All App Permissions screen with the proper filter group, package name, and user.
     *
     * @param fragment The current fragment we wish to transition from
     */
    fun showAllPermissions(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.app_to_all_perms, args)
    }

    private fun getIndividualPermissionDetailResId(group: LightAppPermGroup): Pair<Int, Int> {
        return when (val numRevoked = group.permissions.filter { !it.value.isGranted }.size) {
            0 -> R.string.permission_revoked_none to numRevoked
            group.permissions.size -> R.string.permission_revoked_all to numRevoked
            else -> R.string.permission_revoked_count to numRevoked
        }
    }

    /**
     * Get the detail string id of a permission group if it is at least partially fixed by policy.
     */
    private fun getDetailResIdForFixedByPolicyPermissionGroup(
        group: LightAppPermGroup,
        hasAdmin: Boolean
    ): Int {
        val isForegroundPolicyDenied = group.foreground.isPolicyFixed && !group.foreground.isGranted
        val isPolicyFullyFixedWithGrantedOrNoBkg =
            group.isPolicyFullyFixed && (group.background.isGranted || !group.hasBackgroundGroup)
        if (group.foreground.isSystemFixed || group.background.isSystemFixed) {
            return R.string.permission_summary_enabled_system_fixed
        } else if (hasAdmin) {
            // Permission is fully controlled by policy and cannot be switched
            if (isForegroundPolicyDenied) {
                return com.android.settingslib.widget.restricted.R.string.disabled_by_admin
            } else if (isPolicyFullyFixedWithGrantedOrNoBkg) {
                return com.android.settingslib.widget.restricted.R.string.enabled_by_admin
            } else if (group.isPolicyFullyFixed) {
                return R.string.permission_summary_enabled_by_admin_foreground_only
            }

            // Part of the permission group can still be switched
            if (group.background.isPolicyFixed && group.background.isGranted) {
                return R.string.permission_summary_enabled_by_admin_background_only
            } else if (group.background.isPolicyFixed) {
                return R.string.permission_summary_disabled_by_admin_background_only
            } else if (group.foreground.isPolicyFixed) {
                return R.string.permission_summary_enabled_by_admin_foreground_only
            }
        } else {
            // Permission is fully controlled by policy and cannot be switched
            if ((isForegroundPolicyDenied) || isPolicyFullyFixedWithGrantedOrNoBkg) {
                // Permission is fully controlled by policy and cannot be switched
                // State will be displayed by switch, so no need to add text for that
                return R.string.permission_summary_enforced_by_policy
            } else if (group.isPolicyFullyFixed) {
                return R.string.permission_summary_enabled_by_policy_foreground_only
            }

            // Part of the permission group can still be switched
            if (group.background.isPolicyFixed && group.background.isGranted) {
                return R.string.permission_summary_enabled_by_policy_background_only
            } else if (group.background.isPolicyFixed) {
                return R.string.permission_summary_disabled_by_policy_background_only
            } else if (group.foreground.isPolicyFixed) {
                return R.string.permission_summary_enabled_by_policy_foreground_only
            }
        }
        return 0
    }

    @SuppressLint("NewApi")
    private fun logPermissionChanges(
        oldGroup: LightAppPermGroup,
        newGroup: LightAppPermGroup,
        buttonPressed: Int
    ) {
        val changeId = Random().nextLong()

        for ((permName, permission) in oldGroup.permissions) {
            val newPermission = newGroup.permissions[permName] ?: continue

            if (
                permission.isGranted != newPermission.isGranted ||
                    permission.flags != newPermission.flags
            ) {
                logAppPermissionFragmentActionReported(changeId, newPermission, buttonPressed)
                PermissionDecisionStorageImpl.recordPermissionDecision(
                    app.applicationContext,
                    packageName,
                    permGroupName,
                    newPermission.isGranted
                )
                PermissionChangeStorageImpl.recordPermissionChange(packageName)
            }
        }
    }

    private fun logAppPermissionFragmentActionReportedForPermissionGroup(
        changeId: Long,
        group: LightAppPermGroup,
        buttonPressed: Int
    ) {
        group.permissions.forEach { (_, permission) ->
            logAppPermissionFragmentActionReported(changeId, permission, buttonPressed)
        }
    }

    private fun logAppPermissionFragmentActionReported(
        changeId: Long,
        permission: LightPermission,
        buttonPressed: Int
    ) {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        PermissionControllerStatsLog.write(
            APP_PERMISSION_FRAGMENT_ACTION_REPORTED,
            sessionId,
            changeId,
            uid,
            packageName,
            permission.permInfo.name,
            permission.isGranted,
            permission.flags,
            buttonPressed
        )
        Log.i(
            LOG_TAG,
            "Permission changed via UI with sessionId=$sessionId changeId=" +
                "$changeId uid=$uid packageName=$packageName permission=" +
                permission.permInfo.name +
                " isGranted=" +
                permission.isGranted +
                " permissionFlags=" +
                permission.flags +
                " buttonPressed=$buttonPressed"
        )
    }

    /** Logs information about this AppPermissionGroup and view session */
    fun logAppPermissionFragmentViewed() {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return

        val permissionRationaleShown = showPermissionRationaleLiveData.value ?: false
        PermissionControllerStatsLog.write(
            APP_PERMISSION_FRAGMENT_VIEWED,
            sessionId,
            uid,
            packageName,
            permGroupName,
            permissionRationaleShown
        )
        Log.i(
            LOG_TAG,
            "AppPermission fragment viewed with sessionId=$sessionId uid=$uid " +
                "packageName=$packageName permGroupName=$permGroupName " +
                "permissionRationaleShown=$permissionRationaleShown"
        )
    }

    /**
     * A partial storage grant happens when: An app which doesn't support the photo picker has
     * READ_MEDIA_VISUAL_USER_SELECTED granted, or An app which does support the photo picker has
     * READ_MEDIA_VISUAL_USER_SELECTED and/or ACCESS_MEDIA_LOCATION granted
     */
    private fun isPartialStorageGrant(group: LightAppPermGroup): Boolean {
        if (!isPhotoPickerPromptEnabled() || group.permGroupName != READ_MEDIA_VISUAL) {
            return false
        }

        val partialPerms = getPartialStorageGrantPermissionsForGroup(group)

        return group.isGranted &&
            group.permissions.values.all {
                it.name in partialPerms || (it.name !in partialPerms && !it.isGranted)
            }
    }
}

/**
 * Factory for an AppPermissionViewModel
 *
 * @param app The current application
 * @param packageName The name of the package this ViewModel represents
 * @param permGroupName The name of the permission group this ViewModel represents
 * @param user The user of the package
 * @param sessionId A session ID used in logs to identify this particular session
 */
class AppPermissionViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppPermissionViewModel(app, packageName, permGroupName, user, sessionId) as T
    }
}

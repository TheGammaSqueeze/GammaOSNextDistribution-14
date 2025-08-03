/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils

import android.Manifest
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BACKUP
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission_group.NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_FOREGROUND
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.app.AppOpsManager.permissionToOp
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_INFO
import android.content.Intent.CATEGORY_LAUNCHER
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_ONE_TIME
import android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED
import android.content.pm.PackageManager.FLAG_PERMISSION_REVOKED_COMPAT
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE
import android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.health.connect.HealthConnectManager
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.permission.PermissionManager
import android.provider.DeviceConfig
import android.provider.MediaStore
import android.provider.Settings
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.text.Html
import android.text.TextUtils
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.model.livedatatypes.PermState
import com.android.permissioncontroller.permission.service.LocationAccessCheck
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * A set of util functions designed to work with kotlin, though they can work with java, as well.
 */
object KotlinUtils {

    private const val LOG_TAG = "PermissionController Utils"

    private const val PERMISSION_CONTROLLER_CHANGED_FLAG_MASK =
        FLAG_PERMISSION_USER_SET or
            FLAG_PERMISSION_USER_FIXED or
            FLAG_PERMISSION_ONE_TIME or
            FLAG_PERMISSION_REVOKED_COMPAT or
            FLAG_PERMISSION_ONE_TIME or
            FLAG_PERMISSION_REVIEW_REQUIRED or
            FLAG_PERMISSION_AUTO_REVOKED

    private const val KILL_REASON_APP_OP_CHANGE = "Permission related app op changed"
    private const val SAFETY_PROTECTION_RESOURCES_ENABLED = "safety_protection_enabled"

    /**
     * Importance level to define the threshold for whether a package is in a state which resets the
     * timer on its one-time permission session
     */
    private val ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_RESET_TIMER =
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    /**
     * Importance level to define the threshold for whether a package is in a state which keeps its
     * one-time permission session alive after the timer ends
     */
    private val ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_KEEP_SESSION_ALIVE =
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE

    /** Whether to show the mic and camera icons. */
    private const val PROPERTY_CAMERA_MIC_ICONS_ENABLED = "camera_mic_icons_enabled"

    /** Whether to show the location indicators. */
    private const val PROPERTY_LOCATION_INDICATORS_ENABLED = "location_indicators_enabled"

    /** Whether to show 7-day toggle in privacy hub. */
    private const val PRIVACY_DASHBOARD_7_DAY_TOGGLE = "privacy_dashboard_7_day_toggle"

    /** Whether to show the photo picker option in permission prompts. */
    private const val PROPERTY_PHOTO_PICKER_PROMPT_ENABLED = "photo_picker_prompt_enabled"

    /**
     * The minimum amount of time to wait, after scheduling the safety label changes job, before the
     * job actually runs for the first time.
     */
    private const val PROPERTY_SAFETY_LABEL_CHANGES_JOB_DELAY_MILLIS =
        "safety_label_changes_job_delay_millis"

    /** How often the safety label changes job service will run its job. */
    private const val PROPERTY_SAFETY_LABEL_CHANGES_JOB_INTERVAL_MILLIS =
        "safety_label_changes_job_interval_millis"

    /** Whether the safety label changes job should only be run when the device is idle. */
    private const val PROPERTY_SAFETY_LABEL_CHANGES_JOB_RUN_WHEN_IDLE =
        "safety_label_changes_job_run_when_idle"

    /** Whether the kill switch is set for [SafetyLabelChangesJobService]. */
    private const val PROPERTY_SAFETY_LABEL_CHANGES_JOB_SERVICE_KILL_SWITCH =
        "safety_label_changes_job_service_kill_switch"

    data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    /**
     * Whether to show Camera and Mic Icons.
     *
     * @return whether to show the icons.
     */
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
    fun shouldShowCameraMicIndicators(): Boolean {
        return SdkLevel.isAtLeastS() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_CAMERA_MIC_ICONS_ENABLED,
                true
            )
    }

    /** Whether to show the location indicators. */
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
    fun shouldShowLocationIndicators(): Boolean {
        return SdkLevel.isAtLeastS() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_LOCATION_INDICATORS_ENABLED,
                false
            )
    }

    /** Whether the location accuracy feature is enabled */
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
    fun isLocationAccuracyEnabled(): Boolean {
        return SdkLevel.isAtLeastS()
    }

    /**
     * Whether we should enable the 7-day toggle in privacy dashboard
     *
     * @return whether the flag is enabled
     */
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
    fun is7DayToggleEnabled(): Boolean {
        return SdkLevel.isAtLeastS() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PRIVACY_DASHBOARD_7_DAY_TOGGLE,
                false
            )
    }

    /**
     * Whether the Photo Picker Prompt is enabled
     *
     * @return `true` iff the Location Access Check is enabled.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun isPhotoPickerPromptEnabled(): Boolean {
        return isPhotoPickerPromptSupported() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_PHOTO_PICKER_PROMPT_ENABLED,
                true
            )
    }

    /** Whether the Photo Picker Prompt is supported by the device */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun isPhotoPickerPromptSupported(): Boolean {
        val app = PermissionControllerApplication.get()
        return SdkLevel.isAtLeastU() &&
            !DeviceUtils.isAuto(app) &&
            !DeviceUtils.isTelevision(app) &&
            !DeviceUtils.isWear(app)
    }

    /*
     * Whether we should enable the permission rationale in permission settings and grant dialog
     *
     * @return whether the flag is enabled
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun isPermissionRationaleEnabled(): Boolean {
        return SdkLevel.isAtLeastU() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PERMISSION_RATIONALE_ENABLED,
                true
            )
    }

    /**
     * Whether we should enable the safety label change notifications and data sharing updates UI.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun isSafetyLabelChangeNotificationsEnabled(context: Context): Boolean {
        return SdkLevel.isAtLeastU() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
                true
            ) &&
            !DeviceUtils.isAuto(context) &&
            !DeviceUtils.isTelevision(context) &&
            !DeviceUtils.isWear(context)
    }

    /**
     * Whether the kill switch is set for [SafetyLabelChangesJobService]. If {@code true}, the
     * service is effectively disabled and will not run or schedule any jobs.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun safetyLabelChangesJobServiceKillSwitch(): Boolean {
        return SdkLevel.isAtLeastU() &&
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_LABEL_CHANGES_JOB_SERVICE_KILL_SWITCH,
                false
            )
    }

    /** How often the safety label changes job will run. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun getSafetyLabelChangesJobIntervalMillis(): Long {
        return DeviceConfig.getLong(
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_SAFETY_LABEL_CHANGES_JOB_INTERVAL_MILLIS,
            Duration.ofDays(30).toMillis()
        )
    }

    /** Whether the safety label changes job should only be run when the device is idle. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
    fun runSafetyLabelChangesJobOnlyWhenDeviceIdle(): Boolean {
        return DeviceConfig.getBoolean(
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_SAFETY_LABEL_CHANGES_JOB_RUN_WHEN_IDLE,
            true
        )
    }

    /**
     * Given a Map, and a List, determines which elements are in the list, but not the map, and vice
     * versa. Used primarily for determining which liveDatas are already being watched, and which
     * need to be removed or added
     *
     * @param oldValues A map of key type K, with any value type
     * @param newValues A list of type K
     * @return A pair, where the first value is all items in the list, but not the map, and the
     *   second is all keys in the map, but not the list
     */
    fun <K> getMapAndListDifferences(
        newValues: Collection<K>,
        oldValues: Map<K, *>
    ): Pair<Set<K>, Set<K>> {
        val mapHas = oldValues.keys.toMutableSet()
        val listHas = newValues.toMutableSet()
        for (newVal in newValues) {
            if (oldValues.containsKey(newVal)) {
                mapHas.remove(newVal)
                listHas.remove(newVal)
            }
        }
        return listHas to mapHas
    }

    /**
     * Sort a given PreferenceGroup by the given comparison function.
     *
     * @param compare The function comparing two preferences, which will be used to sort
     * @param hasHeader Whether the group contains a LargeHeaderPreference, which will be kept at
     *   the top of the list
     */
    fun sortPreferenceGroup(
        group: PreferenceGroup,
        compare: (lhs: Preference, rhs: Preference) -> Int,
        hasHeader: Boolean
    ) {
        val preferences = mutableListOf<Preference>()
        for (i in 0 until group.preferenceCount) {
            preferences.add(group.getPreference(i))
        }

        if (hasHeader) {
            preferences.sortWith(
                Comparator { lhs, rhs ->
                    if (lhs is SettingsWithLargeHeader.LargeHeaderPreference) {
                        -1
                    } else if (rhs is SettingsWithLargeHeader.LargeHeaderPreference) {
                        1
                    } else {
                        compare(lhs, rhs)
                    }
                }
            )
        } else {
            preferences.sortWith(Comparator(compare))
        }

        for (i in 0 until preferences.size) {
            preferences[i].order = i
        }
    }

    /**
     * Gets a permission group's icon from the system.
     *
     * @param context The context from which to get the icon
     * @param groupName The name of the permission group whose icon we want
     * @return The permission group's icon, the ic_perm_device_info icon if the group has no icon,
     *   or the group does not exist
     */
    @JvmOverloads
    fun getPermGroupIcon(context: Context, groupName: String, tint: Int? = null): Drawable? {
        val groupInfo = Utils.getGroupInfo(groupName, context)
        var icon: Drawable? = null
        if (groupInfo != null && groupInfo.icon != 0) {
            icon = Utils.loadDrawable(context.packageManager, groupInfo.packageName, groupInfo.icon)
        }

        if (icon == null) {
            icon = context.getDrawable(R.drawable.ic_perm_device_info)
        }

        if (tint == null) {
            return Utils.applyTint(context, icon, android.R.attr.colorControlNormal)
        }

        icon?.setTint(tint)
        return icon
    }

    /**
     * Gets a permission group's label from the system.
     *
     * @param context The context from which to get the label
     * @param groupName The name of the permission group whose label we want
     * @return The permission group's label, or the group name, if the group is invalid
     */
    fun getPermGroupLabel(context: Context, groupName: String): CharSequence {
        val groupInfo = Utils.getGroupInfo(groupName, context) ?: return groupName
        return groupInfo.loadSafeLabel(
            context.packageManager,
            0f,
            TextUtils.SAFE_STRING_FLAG_FIRST_LINE or TextUtils.SAFE_STRING_FLAG_TRIM
        )
    }

    /**
     * Gets a permission group's description from the system.
     *
     * @param context The context from which to get the description
     * @param groupName The name of the permission group whose description we want
     * @return The permission group's description, or an empty string, if the group is invalid, or
     *   its description does not exist
     */
    fun getPermGroupDescription(context: Context, groupName: String): CharSequence {
        val groupInfo = Utils.getGroupInfo(groupName, context)
        var description: CharSequence = ""

        if (groupInfo is PermissionGroupInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        } else if (groupInfo is PermissionInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        }
        return description
    }

    /**
     * Gets a permission's label from the system.
     *
     * @param context The context from which to get the label
     * @param permName The name of the permission whose label we want
     * @return The permission's label, or the permission name, if the permission is invalid
     */
    fun getPermInfoLabel(context: Context, permName: String): CharSequence {
        return try {
            context.packageManager
                .getPermissionInfo(permName, 0)
                .loadSafeLabel(
                    context.packageManager,
                    20000.toFloat(),
                    TextUtils.SAFE_STRING_FLAG_TRIM
                )
        } catch (e: PackageManager.NameNotFoundException) {
            permName
        }
    }

    /**
     * Gets a permission's icon from the system.
     *
     * @param context The context from which to get the icon
     * @param permName The name of the permission whose icon we want
     * @return The permission's icon, or the permission's group icon if the icon isn't set, or the
     *   ic_perm_device_info icon if the permission is invalid.
     */
    fun getPermInfoIcon(context: Context, permName: String): Drawable? {
        return try {
            val permInfo = context.packageManager.getPermissionInfo(permName, 0)
            var icon: Drawable? = null
            if (permInfo.icon != 0) {
                icon =
                    Utils.applyTint(
                        context,
                        permInfo.loadUnbadgedIcon(context.packageManager),
                        android.R.attr.colorControlNormal
                    )
            }

            if (icon == null) {
                val groupName = PermissionMapping.getGroupOfPermission(permInfo) ?: permInfo.name
                icon = getPermGroupIcon(context, groupName)
            }

            icon
        } catch (e: PackageManager.NameNotFoundException) {
            Utils.applyTint(
                context,
                context.getDrawable(R.drawable.ic_perm_device_info),
                android.R.attr.colorControlNormal
            )
        }
    }

    /**
     * Gets a permission's description from the system.
     *
     * @param context The context from which to get the description
     * @param permName The name of the permission whose description we want
     * @return The permission's description, or an empty string, if the group is invalid, or its
     *   description does not exist
     */
    fun getPermInfoDescription(context: Context, permName: String): CharSequence {
        return try {
            val permInfo = context.packageManager.getPermissionInfo(permName, 0)
            permInfo.loadDescription(context.packageManager) ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /**
     * Get the settings icon
     *
     * @param app The current application
     * @param user The user for whom we want the icon
     * @param pm The PackageManager
     * @return Bitmap of the setting's icon, or null
     */
    fun getSettingsIcon(app: Application, user: UserHandle, pm: PackageManager): Bitmap? {
        val settingsPackageName =
            getPackageNameForIntent(pm, Settings.ACTION_SETTINGS)
                ?: Constants.SETTINGS_PACKAGE_NAME_FALLBACK
        return getBadgedPackageIconBitmap(app, user, settingsPackageName)
    }

    /**
     * Gets a package's badged icon from the system.
     *
     * @param app The current application
     * @param packageName The name of the package whose icon we want
     * @param user The user for whom we want the package icon
     * @return The package's icon, or null, if the package does not exist
     */
    fun getBadgedPackageIcon(app: Application, packageName: String, user: UserHandle): Drawable? {
        return try {
            val userContext = Utils.getUserContext(app, user)
            val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
            Utils.getBadgedIcon(app, appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get the icon of a package
     *
     * @param application The current application
     * @param user The user for whom we want the icon
     * @param packageName The name of the package whose icon we want
     * @return Bitmap of the package icon, or null
     */
    fun getBadgedPackageIconBitmap(
        application: Application,
        user: UserHandle,
        packageName: String
    ): Bitmap? {
        val drawable = getBadgedPackageIcon(application, packageName, user)

        val icon =
            if (drawable != null) {
                convertToBitmap(drawable)
            } else {
                null
            }
        return icon
    }

    /**
     * Gets a package's badged label from the system.
     *
     * @param app The current application
     * @param packageName The name of the package whose label we want
     * @param user The user for whom we want the package label
     * @return The package's label
     */
    fun getPackageLabel(app: Application, packageName: String, user: UserHandle): String {
        return try {
            val userContext = Utils.getUserContext(app, user)
            val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
            Utils.getFullAppLabel(appInfo, app)
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun convertToBitmap(pkgIcon: Drawable): Bitmap {
        val pkgIconBmp =
            Bitmap.createBitmap(
                pkgIcon.intrinsicWidth,
                pkgIcon.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        // Draw the icon so it can be displayed.
        val canvas = Canvas(pkgIconBmp)
        pkgIcon.setBounds(0, 0, pkgIcon.intrinsicWidth, pkgIcon.intrinsicHeight)
        pkgIcon.draw(canvas)
        return pkgIconBmp
    }

    /**
     * Returns the name of the package that resolves the specified intent action
     *
     * @param pm The PackageManager
     * @param intentAction The name of the intent action
     * @return The package's name, or null
     */
    fun getPackageNameForIntent(pm: PackageManager, intentAction: String): String? {
        val intent = Intent(intentAction)
        return intent.resolveActivity(pm)?.packageName
    }

    /**
     * Gets a package's uid, using a cached liveData value, if the liveData is currently being
     * observed (and thus has an up-to-date value).
     *
     * @param app The current application
     * @param packageName The name of the package whose uid we want
     * @param user The user we want the package uid for
     * @return The package's UID, or null if the package or user is invalid
     */
    fun getPackageUid(app: Application, packageName: String, user: UserHandle): Int? {
        val liveData = LightPackageInfoLiveData[packageName, user]
        val liveDataUid = liveData.value?.uid
        return if (liveDataUid != null && liveData.hasActiveObservers()) liveDataUid
        else {
            val userContext = Utils.getUserContext(app, user)
            try {
                val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
                appInfo.uid
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun openPhotoPickerForApp(
        activity: Activity,
        uid: Int,
        requestedPermissions: List<String>,
        requestCode: Int
    ) {
        // A clone profile doesn't have a MediaProvider. If the app's user is a clone profile, open
        // the photo picker in the parent profile
        val appUser = UserHandle.getUserHandleForUid(uid)
        val userManager =
            activity.createContextAsUser(appUser, 0).getSystemService(UserManager::class.java)!!
        val user =
            if (userManager.isCloneProfile) {
                userManager.getProfileParent(appUser) ?: appUser
            } else {
                appUser
            }
        val pickerIntent =
            Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP)
                .putExtra(Intent.EXTRA_UID, uid)
                .setType(getMimeTypeForPermissions(requestedPermissions))
        activity.startActivityForResultAsUser(pickerIntent, requestCode, user)
    }

    /** Return a specific MIME type, if a set of permissions is associated with one */
    fun getMimeTypeForPermissions(permissions: List<String>): String? {
        if (permissions.contains(READ_MEDIA_IMAGES) && !permissions.contains(READ_MEDIA_VIDEO)) {
            return "image/*"
        }
        if (permissions.contains(READ_MEDIA_VIDEO) && !permissions.contains(READ_MEDIA_IMAGES)) {
            return "video/*"
        }

        return null
    }

    /**
     * Determines if an app is R or above, or if it is Q-, and has auto revoke enabled
     *
     * @param app The currenct application
     * @param packageName The package name to check
     * @param user The user whose package we want to check
     * @return true if the package is R+ (and not a work profile) or has auto revoke enabled
     */
    fun isROrAutoRevokeEnabled(app: Application, packageName: String, user: UserHandle): Boolean {
        val userContext = Utils.getUserContext(app, user)
        val liveDataValue = LightPackageInfoLiveData[packageName, user].value
        val (targetSdk, uid) =
            if (liveDataValue != null) {
                liveDataValue.targetSdkVersion to liveDataValue.uid
            } else {
                val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
                appInfo.targetSdkVersion to appInfo.uid
            }

        if (targetSdk <= Build.VERSION_CODES.Q) {
            val opsManager = app.getSystemService(AppOpsManager::class.java)!!
            return opsManager.unsafeCheckOpNoThrow(
                OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED,
                uid,
                packageName
            ) == MODE_ALLOWED
        }
        return true
    }

    /**
     * Determine if the given permission should be treated as split from a non-runtime permission
     * for an application targeting the given SDK level.
     */
    @JvmStatic
    fun isPermissionSplitFromNonRuntime(app: Context, permName: String, targetSdk: Int): Boolean {
        val permissionManager = app.getSystemService(PermissionManager::class.java) ?: return false
        val splitPerms = permissionManager.splitPermissions
        val size = splitPerms.size
        for (i in 0 until size) {
            val splitPerm = splitPerms[i]
            if (targetSdk < splitPerm.targetSdk && splitPerm.newPermissions.contains(permName)) {
                val perm = app.packageManager.getPermissionInfo(splitPerm.splitPermission, 0)
                return perm != null && perm.protection != PermissionInfo.PROTECTION_DANGEROUS
            }
        }
        return false
    }

    /**
     * Set a list of flags for a set of permissions of a LightAppPermGroup
     *
     * @param app: The current application
     * @param group: The LightAppPermGroup whose permission flags we wish to set
     * @param flags: Pairs of <FlagInt, ShouldSetFlag>
     * @param filterPermissions: A list of permissions to filter by. Only the filtered permissions
     *   will be set
     * @return A new LightAppPermGroup with the flags set.
     */
    fun setGroupFlags(
        app: Application,
        group: LightAppPermGroup,
        vararg flags: Pair<Int, Boolean>,
        filterPermissions: List<String> = group.permissions.keys.toList()
    ): LightAppPermGroup {
        var flagMask = 0
        var flagsToSet = 0
        for ((flag, shouldSet) in flags) {
            flagMask = flagMask or flag
            if (shouldSet) {
                flagsToSet = flagsToSet or flag
            }
        }

        val deviceId = group.deviceId
        // Create a new context with the given deviceId so that permission updates will be bound
        // to the device
        val context = ContextCompat.createDeviceContext(app.applicationContext, deviceId)
        val newPerms = mutableMapOf<String, LightPermission>()
        for ((permName, perm) in group.permissions) {
            if (permName !in filterPermissions) {
                continue
            }
            // Check if flags need to be updated
            if (flagMask and (perm.flags xor flagsToSet) != 0) {
                context.packageManager.updatePermissionFlags(
                    permName,
                    group.packageName,
                    group.userHandle,
                    *flags
                )
            }
            newPerms[permName] =
                LightPermission(
                    group.packageInfo,
                    perm.permInfo,
                    perm.isGranted,
                    perm.flags or flagsToSet,
                    perm.foregroundPerms
                )
        }
        return LightAppPermGroup(
            group.packageInfo,
            group.permGroupInfo,
            newPerms,
            group.hasInstallToRuntimeSplit,
            group.specialLocationGrant,
            group.specialFixedStorageGrant,
        )
    }

    /**
     * Grant all foreground runtime permissions of a LightAppPermGroup
     *
     * <p>This also automatically grants all app ops for permissions that have app ops.
     *
     * @param app The current application
     * @param group The group whose permissions should be granted
     * @param filterPermissions If not specified, all permissions of the group will be granted.
     *   Otherwise only permissions in {@code filterPermissions} will be granted.
     * @return a new LightAppPermGroup, reflecting the new state
     */
    @JvmOverloads
    fun grantForegroundRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        filterPermissions: Collection<String> = group.permissions.keys,
        isOneTime: Boolean = false,
        userFixed: Boolean = false,
        withoutAppOps: Boolean = false,
    ): LightAppPermGroup {
        return grantRuntimePermissions(
            app,
            group,
            false,
            isOneTime,
            userFixed,
            withoutAppOps,
            filterPermissions
        )
    }

    /**
     * Grant all background runtime permissions of a LightAppPermGroup
     *
     * <p>This also automatically grants all app ops for permissions that have app ops.
     *
     * @param app The current application
     * @param group The group whose permissions should be granted
     * @param filterPermissions If not specified, all permissions of the group will be granted.
     *   Otherwise only permissions in {@code filterPermissions} will be granted.
     * @return a new LightAppPermGroup, reflecting the new state
     */
    @JvmOverloads
    fun grantBackgroundRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        filterPermissions: Collection<String> = group.permissions.keys
    ): LightAppPermGroup {
        return grantRuntimePermissions(
            app,
            group,
            grantBackground = true,
            isOneTime = false,
            userFixed = false,
            withoutAppOps = false,
            filterPermissions = filterPermissions
        )
    }

    @SuppressLint("MissingPermission")
    private fun grantRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        grantBackground: Boolean,
        isOneTime: Boolean = false,
        userFixed: Boolean = false,
        withoutAppOps: Boolean = false,
        filterPermissions: Collection<String> = group.permissions.keys
    ): LightAppPermGroup {
        val deviceId = group.deviceId
        val newPerms = group.permissions.toMutableMap()
        var shouldKillForAnyPermission = false
        for (permName in filterPermissions) {
            val perm = group.permissions[permName] ?: continue
            val isBackgroundPerm = permName in group.backgroundPermNames
            if (isBackgroundPerm == grantBackground) {
                val (newPerm, shouldKill) =
                    grantRuntimePermission(app, perm, group, isOneTime, userFixed, withoutAppOps)
                newPerms[newPerm.name] = newPerm
                shouldKillForAnyPermission = shouldKillForAnyPermission || shouldKill
            }
        }

        // Create a new context with the given deviceId so that permission updates will be bound
        // to the device
        val context = ContextCompat.createDeviceContext(app.applicationContext, deviceId)

        if (!newPerms.isEmpty()) {
            val user = UserHandle.getUserHandleForUid(group.packageInfo.uid)
            for (groupPerm in group.allPermissions.values) {
                var permFlags = groupPerm.flags
                permFlags = permFlags.clearFlag(FLAG_PERMISSION_AUTO_REVOKED)
                if (groupPerm.flags != permFlags) {
                    context.packageManager.updatePermissionFlags(
                        groupPerm.name,
                        group.packageInfo.packageName,
                        PERMISSION_CONTROLLER_CHANGED_FLAG_MASK,
                        permFlags,
                        user
                    )
                }
            }
        }

        if (shouldKillForAnyPermission) {
            (app.getSystemService(ActivityManager::class.java) as ActivityManager).killUid(
                group.packageInfo.uid,
                KILL_REASON_APP_OP_CHANGE
            )
        }
        val newGroup =
            LightAppPermGroup(
                group.packageInfo,
                group.permGroupInfo,
                newPerms,
                group.hasInstallToRuntimeSplit,
                group.specialLocationGrant,
                group.specialFixedStorageGrant,
            )
        // If any permission in the group is one time granted, start one time permission session.
        if (newGroup.permissions.any { it.value.isOneTime && it.value.isGranted }) {
            if (SdkLevel.isAtLeastT()) {
                context
                    .getSystemService(PermissionManager::class.java)!!
                    .startOneTimePermissionSession(
                        group.packageName,
                        Utils.getOneTimePermissionsTimeout(),
                        Utils.getOneTimePermissionsKilledDelay(false),
                        ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_RESET_TIMER,
                        ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_KEEP_SESSION_ALIVE
                    )
            } else {
                context
                    .getSystemService(PermissionManager::class.java)!!
                    .startOneTimePermissionSession(
                        group.packageName,
                        Utils.getOneTimePermissionsTimeout(),
                        ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_RESET_TIMER,
                        ONE_TIME_PACKAGE_IMPORTANCE_LEVEL_TO_KEEP_SESSION_ALIVE
                    )
            }
        }
        return newGroup
    }

    /**
     * Grants a single runtime permission
     *
     * @param app The current application
     * @param perm The permission which should be granted.
     * @param group An app permission group in which to look for background or foreground
     * @param isOneTime Whether this is a one-time permission grant permissions
     * @param userFixed Whether to mark the permissions as user fixed when granted
     * @param withoutAppOps If these permission have app ops associated, and this value is true,
     *   then do not grant the app op when the permission is granted, and add the REVOKED_COMPAT
     *   flag.
     * @return a LightPermission and boolean pair <permission with updated state (or the original
     *   state, if it wasn't changed), should kill app>
     */
    private fun grantRuntimePermission(
        app: Application,
        perm: LightPermission,
        group: LightAppPermGroup,
        isOneTime: Boolean,
        userFixed: Boolean = false,
        withoutAppOps: Boolean = false
    ): Pair<LightPermission, Boolean> {
        val pkgInfo = group.packageInfo
        val user = UserHandle.getUserHandleForUid(pkgInfo.uid)
        val deviceId = group.deviceId
        val supportsRuntime = pkgInfo.targetSdkVersion >= Build.VERSION_CODES.M
        val isGrantingAllowed =
            (!pkgInfo.isInstantApp || perm.isInstantPerm) &&
                (supportsRuntime || !perm.isRuntimeOnly)
        // Do not touch permissions fixed by the system, or permissions that cannot be granted
        if (!isGrantingAllowed || perm.isSystemFixed) {
            return perm to false
        }

        var newFlags = perm.flags
        var oldFlags = perm.flags
        var isGranted = perm.isGranted
        var shouldKill = false

        // Create a new context with the given deviceId so that permission updates will be bound
        // to the device
        val context = ContextCompat.createDeviceContext(app.applicationContext, deviceId)

        // Grant the permission if needed.
        if (!perm.isGranted) {
            val affectsAppOp = permissionToOp(perm.name) != null || perm.isBackgroundPermission

            // TODO 195016052: investigate adding split permission handling
            if (supportsRuntime) {
                // If granting without app ops, explicitly disallow app op first, while setting the
                // flag, so that the PermissionPolicyService doesn't reset the app op state
                if (affectsAppOp && withoutAppOps) {
                    oldFlags = oldFlags.setFlag(PackageManager.FLAG_PERMISSION_REVOKED_COMPAT)
                    context.packageManager.updatePermissionFlags(
                        perm.name,
                        group.packageName,
                        PERMISSION_CONTROLLER_CHANGED_FLAG_MASK,
                        oldFlags,
                        user
                    )
                    // TODO: Update this method once AppOp is device aware
                    disallowAppOp(app, perm, group)
                }
                context.packageManager.grantRuntimePermission(group.packageName, perm.name, user)
                isGranted = true
            } else if (affectsAppOp) {
                // Legacy apps do not know that they have to retry access to a
                // resource due to changes in runtime permissions (app ops in this
                // case). Therefore, we restart them on app op change, so they
                // can pick up the change.
                shouldKill = true
                isGranted = true
            }
            newFlags =
                if (affectsAppOp && withoutAppOps) {
                    newFlags.setFlag(PackageManager.FLAG_PERMISSION_REVOKED_COMPAT)
                } else {
                    newFlags.clearFlag(PackageManager.FLAG_PERMISSION_REVOKED_COMPAT)
                }
            newFlags = newFlags.clearFlag(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED)

            // If this permission affects an app op, ensure the permission app op is enabled
            // before the permission grant.
            if (affectsAppOp && !withoutAppOps) {
                // TODO: Update this method once AppOp is device aware
                allowAppOp(app, perm, group)
            }
        }

        // Granting a permission explicitly means the user already
        // reviewed it so clear the review flag on every grant.
        newFlags = newFlags.clearFlag(FLAG_PERMISSION_REVIEW_REQUIRED)

        // Update the permission flags
        if (!withoutAppOps && !userFixed) {
            // Now the apps can ask for the permission as the user
            // no longer has it fixed in a denied state.
            newFlags = newFlags.clearFlag(FLAG_PERMISSION_USER_FIXED)
            newFlags = newFlags.setFlag(FLAG_PERMISSION_USER_SET)
        } else if (userFixed) {
            newFlags = newFlags.setFlag(FLAG_PERMISSION_USER_FIXED)
        }
        newFlags = newFlags.clearFlag(FLAG_PERMISSION_AUTO_REVOKED)

        newFlags =
            if (isOneTime) {
                newFlags.setFlag(FLAG_PERMISSION_ONE_TIME)
            } else {
                newFlags.clearFlag(FLAG_PERMISSION_ONE_TIME)
            }

        // If we newly grant background access to the fine location, double-guess the user some
        // time later if this was really the right choice.
        if (!perm.isGranted && isGranted) {
            var triggerLocationAccessCheck = false
            if (perm.name == ACCESS_FINE_LOCATION) {
                val bgPerm = group.permissions[perm.backgroundPermission]
                triggerLocationAccessCheck = bgPerm?.isGranted == true
            } else if (perm.name == ACCESS_BACKGROUND_LOCATION) {
                val fgPerm = group.permissions[ACCESS_FINE_LOCATION]
                triggerLocationAccessCheck = fgPerm?.isGranted == true
            }
            if (triggerLocationAccessCheck) {
                // trigger location access check
                LocationAccessCheck(app, null).checkLocationAccessSoon()
            }
        }

        if (oldFlags != newFlags) {
            context.packageManager.updatePermissionFlags(
                perm.name,
                group.packageInfo.packageName,
                PERMISSION_CONTROLLER_CHANGED_FLAG_MASK,
                newFlags,
                user
            )
        }

        val newState = PermState(newFlags, isGranted)
        return LightPermission(perm.pkgInfo, perm.permInfo, newState, perm.foregroundPerms) to
            shouldKill
    }

    /**
     * Revoke all foreground runtime permissions of a LightAppPermGroup
     *
     * <p>This also disallows all app ops for permissions that have app ops.
     *
     * @param app The current application
     * @param group The group whose permissions should be revoked
     * @param userFixed If the user requested that they do not want to be asked again
     * @param oneTime If the permission should be mark as one-time
     * @param filterPermissions If not specified, all permissions of the group will be revoked.
     *   Otherwise only permissions in {@code filterPermissions} will be revoked.
     * @return a LightAppPermGroup representing the new state
     */
    @JvmOverloads
    fun revokeForegroundRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        userFixed: Boolean = false,
        oneTime: Boolean = false,
        forceRemoveRevokedCompat: Boolean = false,
        filterPermissions: Collection<String> = group.permissions.keys
    ): LightAppPermGroup {
        return revokeRuntimePermissions(
            app,
            group,
            false,
            userFixed,
            oneTime,
            forceRemoveRevokedCompat,
            filterPermissions
        )
    }

    /**
     * Revoke all background runtime permissions of a LightAppPermGroup
     *
     * <p>This also disallows all app ops for permissions that have app ops.
     *
     * @param app The current application
     * @param group The group whose permissions should be revoked
     * @param userFixed If the user requested that they do not want to be asked again
     * @param filterPermissions If not specified, all permissions of the group will be revoked.
     *   Otherwise only permissions in {@code filterPermissions} will be revoked.
     * @return a LightAppPermGroup representing the new state
     */
    @JvmOverloads
    fun revokeBackgroundRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        userFixed: Boolean = false,
        oneTime: Boolean = false,
        forceRemoveRevokedCompat: Boolean = false,
        filterPermissions: Collection<String> = group.permissions.keys
    ): LightAppPermGroup {
        return revokeRuntimePermissions(
            app,
            group,
            true,
            userFixed,
            oneTime,
            forceRemoveRevokedCompat,
            filterPermissions
        )
    }

    private fun revokeRuntimePermissions(
        app: Application,
        group: LightAppPermGroup,
        revokeBackground: Boolean,
        userFixed: Boolean,
        oneTime: Boolean,
        forceRemoveRevokedCompat: Boolean = false,
        filterPermissions: Collection<String>
    ): LightAppPermGroup {
        val deviceId = group.deviceId
        val wasOneTime = group.isOneTime
        val newPerms = group.permissions.toMutableMap()
        var shouldKillForAnyPermission = false
        for (permName in filterPermissions) {
            val perm = group.permissions[permName] ?: continue
            val isBackgroundPerm = permName in group.backgroundPermNames
            if (isBackgroundPerm == revokeBackground) {
                val (newPerm, shouldKill) =
                    revokeRuntimePermission(
                        app,
                        perm,
                        userFixed,
                        oneTime,
                        forceRemoveRevokedCompat,
                        group
                    )
                newPerms[newPerm.name] = newPerm
                shouldKillForAnyPermission = shouldKillForAnyPermission || shouldKill
            }
        }

        if (shouldKillForAnyPermission && !shouldSkipKillForGroup(app, group)) {
            (app.getSystemService(ActivityManager::class.java) as ActivityManager).killUid(
                group.packageInfo.uid,
                KILL_REASON_APP_OP_CHANGE
            )
        }

        val newGroup =
            LightAppPermGroup(
                group.packageInfo,
                group.permGroupInfo,
                newPerms,
                group.hasInstallToRuntimeSplit,
                group.specialLocationGrant,
                group.specialFixedStorageGrant,
            )

        if (wasOneTime && !anyPermsOfPackageOneTimeGranted(app, newGroup.packageInfo, newGroup)) {
            // Create a new context with the given deviceId so that permission updates will be bound
            // to the device
            val context = ContextCompat.createDeviceContext(app.applicationContext, deviceId)
            context
                .getSystemService(PermissionManager::class.java)!!
                .stopOneTimePermissionSession(group.packageName)
        }
        return newGroup
    }

    /**
     * Revoke background permissions
     *
     * @param context context
     * @param packageName Name of the package
     * @param permissionGroupName Name of the permission group
     * @param user User handle
     * @param postRevokeHandler Optional callback that lets us perform an action on revoke
     */
    fun revokeBackgroundRuntimePermissions(
        context: Context,
        packageName: String,
        permissionGroupName: String,
        user: UserHandle,
        postRevokeHandler: Runnable?
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            val group =
                LightAppPermGroupLiveData[packageName, permissionGroupName, user]
                    .getInitializedValue()
            if (group != null) {
                revokeBackgroundRuntimePermissions(context.application, group)
            }
            if (postRevokeHandler != null) {
                postRevokeHandler.run()
            }
        }
    }

    /**
     * Determines if any permissions of a package are granted for one-time only
     *
     * @param app The current application
     * @param packageInfo The packageInfo we wish to examine
     * @param group Optional, the current app permission group we are examining
     * @return true if any permission in the package is granted for one time, false otherwise
     */
    private fun anyPermsOfPackageOneTimeGranted(
        app: Application,
        packageInfo: LightPackageInfo,
        group: LightAppPermGroup? = null
    ): Boolean {
        val user = group?.userHandle ?: UserHandle.getUserHandleForUid(packageInfo.uid)
        if (group?.isOneTime == true) {
            return true
        }
        for ((idx, permName) in packageInfo.requestedPermissions.withIndex()) {
            if (permName in group?.permissions ?: emptyMap()) {
                continue
            }
            val flags =
                app.packageManager.getPermissionFlags(permName, packageInfo.packageName, user) and
                    FLAG_PERMISSION_ONE_TIME
            val granted =
                packageInfo.requestedPermissionsFlags[idx] == PackageManager.PERMISSION_GRANTED &&
                    (flags and FLAG_PERMISSION_REVOKED_COMPAT) == 0
            if (granted && (flags and FLAG_PERMISSION_ONE_TIME) != 0) {
                return true
            }
        }
        return false
    }
    /**
     * Revokes a single runtime permission.
     *
     * @param app The current application
     * @param perm The permission which should be revoked.
     * @param userFixed If the user requested that they do not want to be asked again
     * @param group An optional app permission group in which to look for background or foreground
     *   permissions
     * @return a LightPermission and boolean pair <permission with updated state (or the original
     *   state, if it wasn't changed), should kill app>
     */
    private fun revokeRuntimePermission(
        app: Application,
        perm: LightPermission,
        userFixed: Boolean,
        oneTime: Boolean,
        forceRemoveRevokedCompat: Boolean,
        group: LightAppPermGroup
    ): Pair<LightPermission, Boolean> {
        // Do not touch permissions fixed by the system.
        if (perm.isSystemFixed) {
            return perm to false
        }

        val user = UserHandle.getUserHandleForUid(group.packageInfo.uid)
        var newFlags = perm.flags
        val deviceId = group.deviceId
        var isGranted = perm.isGranted
        val supportsRuntime = group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.M
        var shouldKill = false

        val affectsAppOp = permissionToOp(perm.name) != null || perm.isBackgroundPermission

        // Create a new context with the given deviceId so that permission updates will be bound
        // to the device
        val context = ContextCompat.createDeviceContext(app.applicationContext, deviceId)

        if (perm.isGranted || (perm.isCompatRevoked && forceRemoveRevokedCompat)) {
            if (
                supportsRuntime &&
                    !isPermissionSplitFromNonRuntime(
                        app,
                        perm.name,
                        group.packageInfo.targetSdkVersion
                    )
            ) {
                // Revoke the permission if needed.
                context.packageManager.revokeRuntimePermission(
                    group.packageInfo.packageName,
                    perm.name,
                    user
                )
                isGranted = false
                if (forceRemoveRevokedCompat) {
                    newFlags = newFlags.clearFlag(PackageManager.FLAG_PERMISSION_REVOKED_COMPAT)
                }
            } else if (affectsAppOp) {
                // If the permission has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.

                // Disabling an app op may put the app in a situation in which it
                // has a handle to state it shouldn't have, so we have to kill the
                // app. This matches the revoke runtime permission behavior.
                shouldKill = true
                newFlags = newFlags.setFlag(PackageManager.FLAG_PERMISSION_REVOKED_COMPAT)
                isGranted = false
            }

            newFlags = newFlags.clearFlag(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED)
            if (affectsAppOp) {
                // TODO: Update this method once AppOp is device aware
                disallowAppOp(app, perm, group)
            }
        }

        // Update the permission flags.
        // Take a note that the user fixed the permission, if applicable.
        newFlags =
            if (userFixed) newFlags.setFlag(PackageManager.FLAG_PERMISSION_USER_FIXED)
            else newFlags.clearFlag(PackageManager.FLAG_PERMISSION_USER_FIXED)
        newFlags =
            if (oneTime) newFlags.clearFlag(PackageManager.FLAG_PERMISSION_USER_SET)
            else newFlags.setFlag(PackageManager.FLAG_PERMISSION_USER_SET)
        newFlags =
            if (oneTime) newFlags.setFlag(PackageManager.FLAG_PERMISSION_ONE_TIME)
            else newFlags.clearFlag(PackageManager.FLAG_PERMISSION_ONE_TIME)
        newFlags = newFlags.clearFlag(PackageManager.FLAG_PERMISSION_AUTO_REVOKED)
        newFlags = newFlags.clearFlag(PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED)

        if (perm.flags != newFlags) {
            context.packageManager.updatePermissionFlags(
                perm.name,
                group.packageInfo.packageName,
                PERMISSION_CONTROLLER_CHANGED_FLAG_MASK,
                newFlags,
                user
            )
        }

        // If we revoke background access to the fine location, we trigger a check to remove
        // notification warning about background location access
        if (perm.isGranted && !isGranted) {
            var cancelLocationAccessWarning = false
            if (perm.name == ACCESS_FINE_LOCATION) {
                val bgPerm = group.permissions[perm.backgroundPermission]
                cancelLocationAccessWarning = bgPerm?.isGranted == true
            } else if (perm.name == ACCESS_BACKGROUND_LOCATION) {
                val fgPerm = group.permissions[ACCESS_FINE_LOCATION]
                cancelLocationAccessWarning = fgPerm?.isGranted == true
            }
            if (cancelLocationAccessWarning) {
                // cancel location access warning notification
                LocationAccessCheck(app, null)
                    .cancelBackgroundAccessWarningNotification(
                        group.packageInfo.packageName,
                        user,
                        true
                    )
            }
        }

        val newState = PermState(newFlags, isGranted)
        return LightPermission(perm.pkgInfo, perm.permInfo, newState, perm.foregroundPerms) to
            shouldKill
    }

    private fun Int.setFlag(flagToSet: Int): Int {
        return this or flagToSet
    }

    private fun Int.clearFlag(flagToSet: Int): Int {
        return this and flagToSet.inv()
    }

    /**
     * Allow the app op for a permission/uid.
     *
     * <p>There are three cases: <dl> <dt>The permission is not split into
     * foreground/background</dt> <dd>The app op matching the permission will be set to {@link
     * AppOpsManager#MODE_ALLOWED}</dd> <dt>The permission is a foreground permission:</dt>
     * <dd><dl><dt>The background permission permission is granted</dt> <dd>The app op matching the
     * permission will be set to {@link AppOpsManager#MODE_ALLOWED}</dd> <dt>The background
     * permission permission is <u>not</u> granted</dt> <dd>The app op matching the permission will
     * be set to {@link AppOpsManager#MODE_FOREGROUND}</dd> </dl></dd> <dt>The permission is a
     * background permission:</dt> <dd>All granted foreground permissions for this background
     * permission will be set to {@link AppOpsManager#MODE_ALLOWED}</dd> </dl>
     *
     * @param app The current application
     * @param perm The LightPermission whose app op should be allowed
     * @param group The LightAppPermGroup which will be looked in for foreground or background
     *   LightPermission objects
     * @return {@code true} iff app-op was changed
     */
    private fun allowAppOp(
        app: Application,
        perm: LightPermission,
        group: LightAppPermGroup
    ): Boolean {
        val packageName = group.packageInfo.packageName
        val uid = group.packageInfo.uid
        val appOpsManager = app.getSystemService(AppOpsManager::class.java) as AppOpsManager
        var wasChanged = false

        if (perm.isBackgroundPermission && perm.foregroundPerms != null) {
            for (foregroundPermName in perm.foregroundPerms) {
                val fgPerm = group.permissions[foregroundPermName]
                val appOpName = permissionToOp(foregroundPermName) ?: continue

                if (fgPerm != null && fgPerm.isGranted) {
                    wasChanged =
                        setOpMode(appOpName, uid, packageName, MODE_ALLOWED, appOpsManager) ||
                            wasChanged
                }
            }
        } else {
            val appOpName = permissionToOp(perm.name) ?: return false
            if (perm.backgroundPermission != null) {
                wasChanged =
                    if (group.permissions.containsKey(perm.backgroundPermission)) {
                        val bgPerm = group.permissions[perm.backgroundPermission]
                        val mode =
                            if (bgPerm != null && bgPerm.isGranted) MODE_ALLOWED
                            else MODE_FOREGROUND

                        setOpMode(appOpName, uid, packageName, mode, appOpsManager)
                    } else {
                        // The app requested a permission that has a background permission but it
                        // did
                        // not request the background permission, hence it can never get background
                        // access
                        setOpMode(appOpName, uid, packageName, MODE_FOREGROUND, appOpsManager)
                    }
            } else {
                wasChanged = setOpMode(appOpName, uid, packageName, MODE_ALLOWED, appOpsManager)
            }
        }
        return wasChanged
    }

    /**
     * Disallow the app op for a permission/uid.
     *
     * <p>There are three cases: <dl> <dt>The permission is not split into
     * foreground/background</dt> <dd>The app op matching the permission will be set to {@link
     * AppOpsManager#MODE_IGNORED}</dd> <dt>The permission is a foreground permission:</dt> <dd>The
     * app op matching the permission will be set to {@link AppOpsManager#MODE_IGNORED}</dd> <dt>The
     * permission is a background permission:</dt> <dd>All granted foreground permissions for this
     * background permission will be set to {@link AppOpsManager#MODE_FOREGROUND}</dd> </dl>
     *
     * @param app The current application
     * @param perm The LightPermission whose app op should be allowed
     * @param group The LightAppPermGroup which will be looked in for foreground or background
     *   LightPermission objects
     * @return {@code true} iff app-op was changed
     */
    private fun disallowAppOp(
        app: Application,
        perm: LightPermission,
        group: LightAppPermGroup
    ): Boolean {
        val packageName = group.packageInfo.packageName
        val uid = group.packageInfo.uid
        val appOpsManager = app.getSystemService(AppOpsManager::class.java) as AppOpsManager
        var wasChanged = false

        if (perm.isBackgroundPermission && perm.foregroundPerms != null) {
            for (foregroundPermName in perm.foregroundPerms) {
                val fgPerm = group.permissions[foregroundPermName]
                if (fgPerm != null && fgPerm.isGranted) {
                    val appOpName = permissionToOp(foregroundPermName) ?: return false
                    wasChanged =
                        wasChanged ||
                            setOpMode(appOpName, uid, packageName, MODE_FOREGROUND, appOpsManager)
                }
            }
        } else {
            val appOpName = permissionToOp(perm.name) ?: return false
            wasChanged = setOpMode(appOpName, uid, packageName, MODE_IGNORED, appOpsManager)
        }
        return wasChanged
    }

    /**
     * Set mode of an app-op if needed.
     *
     * @param op The op to set
     * @param uid The uid the app-op belongs to
     * @param packageName The package the app-op belongs to
     * @param mode The new mode
     * @param manager The app ops manager to use to change the app op
     * @return {@code true} iff app-op was changed
     */
    private fun setOpMode(
        op: String,
        uid: Int,
        packageName: String,
        mode: Int,
        manager: AppOpsManager
    ): Boolean {
        val currentMode = manager.unsafeCheckOpRaw(op, uid, packageName)
        if (currentMode == mode) {
            return false
        }
        manager.setUidMode(op, uid, mode)
        return true
    }

    private fun shouldSkipKillForGroup(app: Application, group: LightAppPermGroup): Boolean {
        if (group.permGroupName != NOTIFICATIONS) {
            return false
        }

        return shouldSkipKillOnPermDeny(
            app,
            POST_NOTIFICATIONS,
            group.packageName,
            group.userHandle
        )
    }

    /**
     * Determine if the usual "kill app on permission denial" should be skipped. It should be
     * skipped if the permission is POST_NOTIFICATIONS, the app holds the BACKUP permission, and a
     * backup restore is currently in progress.
     *
     * @param app the current application
     * @param permission the permission being denied
     * @param packageName the package the permission was denied for
     * @param user the user whose package the permission was denied for
     * @return true if the permission denied was POST_NOTIFICATIONS, the app is a backup app, and a
     *   backup restore is in progress, false otherwise
     */
    fun shouldSkipKillOnPermDeny(
        app: Application,
        permission: String,
        packageName: String,
        user: UserHandle
    ): Boolean {
        val userContext: Context = Utils.getUserContext(app, user)
        if (
            permission != POST_NOTIFICATIONS ||
                userContext.packageManager.checkPermission(BACKUP, packageName) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val isInSetup =
                Settings.Secure.getInt(
                    userContext.contentResolver,
                    Settings.Secure.USER_SETUP_COMPLETE,
                    user.identifier
                ) == 0
            val isInDeferredSetup =
                Settings.Secure.getInt(
                    userContext.contentResolver,
                    Settings.Secure.USER_SETUP_PERSONALIZATION_STATE,
                    user.identifier
                ) == Settings.Secure.USER_SETUP_PERSONALIZATION_STARTED
            isInSetup || isInDeferredSetup
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(LOG_TAG, "Failed to check if the user is in restore: $e")
            false
        }
    }

    /**
     * Determine if a given package has a launch intent. Will function correctly even if called
     * before user is unlocked.
     *
     * @param context: The context from which to retrieve the package
     * @param packageName: The package name to check
     * @return whether or not the given package has a launch intent
     */
    fun packageHasLaunchIntent(context: Context, packageName: String): Boolean {
        val intentToResolve = Intent(ACTION_MAIN)
        intentToResolve.addCategory(CATEGORY_INFO)
        intentToResolve.setPackage(packageName)
        var resolveInfos =
            context.packageManager.queryIntentActivities(
                intentToResolve,
                MATCH_DIRECT_BOOT_AWARE or MATCH_DIRECT_BOOT_UNAWARE
            )

        if (resolveInfos.size <= 0) {
            intentToResolve.removeCategory(CATEGORY_INFO)
            intentToResolve.addCategory(CATEGORY_LAUNCHER)
            intentToResolve.setPackage(packageName)
            resolveInfos =
                context.packageManager.queryIntentActivities(
                    intentToResolve,
                    MATCH_DIRECT_BOOT_AWARE or MATCH_DIRECT_BOOT_UNAWARE
                )
        }
        return resolveInfos.size > 0
    }

    /**
     * Set selected location accuracy flags for COARSE and FINE location permissions.
     *
     * @param app: The current application
     * @param group: The LightAppPermGroup whose permission flags we wish to set
     * @param isFineSelected: Whether fine location is selected
     */
    fun setFlagsWhenLocationAccuracyChanged(
        app: Application,
        group: LightAppPermGroup,
        isFineSelected: Boolean
    ) {
        if (isFineSelected) {
            setGroupFlags(
                app,
                group,
                PackageManager.FLAG_PERMISSION_SELECTED_LOCATION_ACCURACY to true,
                filterPermissions = listOf(ACCESS_FINE_LOCATION)
            )
            setGroupFlags(
                app,
                group,
                PackageManager.FLAG_PERMISSION_SELECTED_LOCATION_ACCURACY to false,
                filterPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            setGroupFlags(
                app,
                group,
                PackageManager.FLAG_PERMISSION_SELECTED_LOCATION_ACCURACY to false,
                filterPermissions = listOf(ACCESS_FINE_LOCATION)
            )
            setGroupFlags(
                app,
                group,
                PackageManager.FLAG_PERMISSION_SELECTED_LOCATION_ACCURACY to true,
                filterPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    /**
     * Determines whether we should show the safety protection resources. We show the resources only
     * if (1) the build version is T or after and (2) the feature flag safety_protection_enabled is
     * enabled and (3) the config value config_safetyProtectionEnabled is enabled/true and (4) the
     * resources exist (currently the resources only exist on GMS devices)
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun shouldShowSafetyProtectionResources(context: Context): Boolean {
        return try {
            SdkLevel.isAtLeastT() &&
                DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    SAFETY_PROTECTION_RESOURCES_ENABLED,
                    false
                ) &&
                context
                    .getResources()
                    .getBoolean(
                        Resources.getSystem()
                            .getIdentifier("config_safetyProtectionEnabled", "bool", "android")
                    ) &&
                context.getDrawable(android.R.drawable.ic_safety_protection) != null &&
                !context.getString(android.R.string.safety_protection_display_text).isNullOrEmpty()
        } catch (e: Resources.NotFoundException) {
            // We should expect the resources to not exist for non-pixel devices
            // (except for the OEMs that opt-in)
            false
        }
    }

    fun addHealthPermissions(context: Context) {
        val permissions = HealthConnectManager.getHealthPermissions(context)
        PermissionMapping.addHealthPermissionsToPlatform(permissions)
    }

    /**
     * Returns an [Intent] to the installer app store for a given package name, or {@code null} if
     * none found
     */
    fun getAppStoreIntent(
        context: Context,
        installerPackageName: String?,
        packageName: String?
    ): Intent? {
        val intent: Intent = Intent(Intent.ACTION_SHOW_APP_INFO).setPackage(installerPackageName)
        val result: Intent? = resolveActivityForIntent(context, intent)
        if (result != null) {
            result.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            return result
        }
        return null
    }

    /**
     * Verify that a component that supports the intent with action and return a new intent with
     * same action and resolved class name set. Returns null if no activity resolution.
     */
    private fun resolveActivityForIntent(context: Context, intent: Intent): Intent? {
        val result: ResolveInfo? = context.packageManager.resolveActivity(intent, 0)
        return if (result != null) {
            Intent(intent.action)
                .setClassName(result.activityInfo.packageName, result.activityInfo.name)
        } else {
            null
        }
    }

    data class NotificationResources(val appLabel: String, val smallIcon: Icon, val color: Int)

    fun getSafetyCenterNotificationResources(context: Context): NotificationResources {
        val appLabel: String
        val smallIcon: Icon
        val color: Int
        // If U resources are available, and this is a U+ device, use those
        if (SdkLevel.isAtLeastU()) {
            val safetyCenterResourcesApk = SafetyCenterResourcesApk(context)
            val uIcon =
                safetyCenterResourcesApk.getIconByDrawableName("ic_notification_badge_general")
            val uColor = safetyCenterResourcesApk.getColorByName("notification_tint_normal")
            if (uIcon != null && uColor != null) {
                appLabel = context.getString(R.string.safety_privacy_qs_tile_title)
                return NotificationResources(appLabel, uIcon, uColor)
            }
        }

        // Use PbA branding if available, otherwise default to more generic branding
        if (shouldShowSafetyProtectionResources(context)) {
            appLabel =
                Html.fromHtml(context.getString(android.R.string.safety_protection_display_text), 0)
                    .toString()
            smallIcon = Icon.createWithResource(context, android.R.drawable.ic_safety_protection)
            color = context.getColor(R.color.safety_center_info)
        } else {
            appLabel = context.getString(R.string.safety_center_notification_app_label)
            smallIcon = Icon.createWithResource(context, R.drawable.ic_settings_notification)
            color = context.getColor(android.R.color.system_notification_accent_color)
        }
        return NotificationResources(appLabel, smallIcon, color)
    }
}

/** Get the [value][LiveData.getValue], suspending until [isInitialized] if not yet so */
suspend fun <T, LD : LiveData<T>> LD.getInitializedValue(
    observe: LD.(Observer<T>) -> Unit = { observeForever(it) },
    isValueInitialized: LD.() -> Boolean = { value != null }
): T {
    return if (isValueInitialized()) {
        @Suppress("UNCHECKED_CAST")
        value as T
    } else {
        suspendCoroutine { continuation: Continuation<T> ->
            val observer = AtomicReference<Observer<T>>()
            observer.set(
                Observer { newValue ->
                    if (isValueInitialized()) {
                        GlobalScope.launch(Dispatchers.Main) {
                            observer.getAndSet(null)?.let { observerSnapshot ->
                                removeObserver(observerSnapshot)
                                continuation.resume(newValue)
                            }
                        }
                    }
                }
            )

            GlobalScope.launch(Dispatchers.Main) { observe(observer.get()) }
        }
    }
}

/**
 * A parallel equivalent of [map]
 *
 * Starts the given suspending function for each item in the collection without waiting for previous
 * ones to complete, then suspends until all the started operations finish.
 */
suspend inline fun <T, R> Iterable<T>.mapInParallel(
    context: CoroutineContext,
    scope: CoroutineScope = GlobalScope,
    crossinline transform: suspend CoroutineScope.(T) -> R
): List<R> = map { scope.async(context) { transform(it) } }.map { it.await() }

/**
 * A parallel equivalent of [forEach]
 *
 * See [mapInParallel]
 */
suspend inline fun <T> Iterable<T>.forEachInParallel(
    context: CoroutineContext,
    scope: CoroutineScope = GlobalScope,
    crossinline action: suspend CoroutineScope.(T) -> Unit
) {
    mapInParallel(context, scope) { action(it) }
}

/**
 * Check that we haven't already started transitioning to a given destination. If we haven't, start
 * navigating to that destination.
 *
 * @param destResId The ID of the desired destination
 * @param args The optional bundle of args to be passed to the destination
 */
fun NavController.navigateSafe(destResId: Int, args: Bundle? = null) {
    val navAction = currentDestination?.getAction(destResId) ?: graph.getAction(destResId)
    navAction?.let { action ->
        if (currentDestination?.id != action.destinationId) {
            navigate(destResId, args)
        }
    }
}

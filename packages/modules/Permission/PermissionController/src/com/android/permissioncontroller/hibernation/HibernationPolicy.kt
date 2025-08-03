/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.hibernation

import android.Manifest
import android.Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.role.RoleManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.printservice.PrintService
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION
import android.provider.Settings
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.Action
import android.service.autofill.AutofillService
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import android.service.voice.VoiceInteractionService
import android.service.wallpaper.WallpaperService
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS
import android.util.Log
import android.view.inputmethod.InputMethod
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.v31.HibernationController
import com.android.permissioncontroller.hibernation.v31.InstallerPackagesLiveData
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.AppOpLiveData
import com.android.permissioncontroller.permission.data.BroadcastReceiverLiveData
import com.android.permissioncontroller.permission.data.CarrierPrivilegedStatusLiveData
import com.android.permissioncontroller.permission.data.DataRepositoryForPackage
import com.android.permissioncontroller.permission.data.HasIntentAction
import com.android.permissioncontroller.permission.data.LauncherPackagesLiveData
import com.android.permissioncontroller.permission.data.ServiceLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.getUnusedPackages
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.service.revokeAppPermissions
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.forEachInParallel
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val LOG_TAG = "HibernationPolicy"
const val DEBUG_OVERRIDE_THRESHOLDS = false
const val DEBUG_HIBERNATION_POLICY = false

private var SKIP_NEXT_RUN = false

private val DEFAULT_UNUSED_THRESHOLD_MS = TimeUnit.DAYS.toMillis(90)

fun getUnusedThresholdMs() =
    when {
        DEBUG_OVERRIDE_THRESHOLDS -> TimeUnit.SECONDS.toMillis(1)
        else ->
            DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PERMISSIONS,
                Utils.PROPERTY_HIBERNATION_UNUSED_THRESHOLD_MILLIS,
                DEFAULT_UNUSED_THRESHOLD_MS
            )
    }

private val DEFAULT_CHECK_FREQUENCY_MS = TimeUnit.DAYS.toMillis(15)

private fun getCheckFrequencyMs() =
    DeviceConfig.getLong(
        DeviceConfig.NAMESPACE_PERMISSIONS,
        Utils.PROPERTY_HIBERNATION_CHECK_FREQUENCY_MILLIS,
        DEFAULT_CHECK_FREQUENCY_MS
    )

// Intentionally kept value of the key same as before because we want to continue reading value of
// this shared preference stored by previous versions of PermissionController
const val PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING = "first_boot_time"
const val PREF_KEY_BOOT_TIME_SNAPSHOT = "ah_boot_time_snapshot"
const val PREF_KEY_ELAPSED_REALTIME_SNAPSHOT = "ah_elapsed_realtime_snapshot"

private const val PREFS_FILE_NAME = "unused_apps_prefs"
private const val PREF_KEY_UNUSED_APPS_REVIEW = "unused_apps_need_review"
const val SNAPSHOT_UNINITIALIZED = -1L
private const val ACTION_SET_UP_HIBERNATION =
    "com.android.permissioncontroller.action.SET_UP_HIBERNATION"
val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)

fun isHibernationEnabled(): Boolean {
    return SdkLevel.isAtLeastS() &&
        DeviceConfig.getBoolean(
            NAMESPACE_APP_HIBERNATION,
            Utils.PROPERTY_APP_HIBERNATION_ENABLED,
            true /* defaultValue */
        )
}

/**
 * Whether hibernation defaults on and affects apps that target pre-S. Has no effect if
 * [isHibernationEnabled] is false.
 */
fun hibernationTargetsPreSApps(): Boolean {
    return DeviceConfig.getBoolean(
        NAMESPACE_APP_HIBERNATION,
        Utils.PROPERTY_HIBERNATION_TARGETS_PRE_S_APPS,
        false /* defaultValue */
    )
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "UpsideDownCake")
fun isSystemExemptFromHibernationEnabled(): Boolean {
    return SdkLevel.isAtLeastU() &&
        DeviceConfig.getBoolean(
            NAMESPACE_APP_HIBERNATION,
            Utils.PROPERTY_SYSTEM_EXEMPT_HIBERNATION_ENABLED,
            true /* defaultValue */
        )
}

/** Remove the unused apps notification. */
fun cancelUnusedAppsNotification(context: Context) {
    context
        .getSystemService(NotificationManager::class.java)!!
        .cancel(HibernationJobService::class.java.simpleName, Constants.UNUSED_APPS_NOTIFICATION_ID)
}

/**
 * Checks if we need to show the safety center card and sends the appropriate source data. If the
 * user has not reviewed the latest auto-revoked apps, we show the card. Otherwise, we ensure
 * nothing is shown.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun rescanAndPushDataToSafetyCenter(
    context: Context,
    sessionId: Long,
    safetyEvent: SafetyEvent,
) {
    val safetyCenterManager: SafetyCenterManager =
        context.getSystemService(SafetyCenterManager::class.java)!!
    if (getUnusedAppsReviewNeeded(context)) {
        val seeUnusedAppsAction =
            Action.Builder(
                    Constants.UNUSED_APPS_SAFETY_CENTER_SEE_UNUSED_APPS_ID,
                    context.getString(R.string.unused_apps_safety_center_action_title),
                    makeUnusedAppsIntent(context, sessionId)
                )
                .build()

        val issue =
            SafetySourceIssue.Builder(
                    Constants.UNUSED_APPS_SAFETY_CENTER_ISSUE_ID,
                    context.getString(R.string.unused_apps_safety_center_card_title),
                    context.getString(R.string.unused_apps_safety_center_card_content),
                    SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    Constants.UNUSED_APPS_SAFETY_CENTER_ISSUE_ID
                )
                .addAction(seeUnusedAppsAction)
                .setOnDismissPendingIntent(makeDismissIntent(context, sessionId))
                .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
                .build()

        val safetySourceData = SafetySourceData.Builder().addIssue(issue).build()

        safetyCenterManager.setSafetySourceData(
            Constants.UNUSED_APPS_SAFETY_CENTER_SOURCE_ID,
            safetySourceData,
            safetyEvent
        )
    } else {
        safetyCenterManager.setSafetySourceData(
            Constants.UNUSED_APPS_SAFETY_CENTER_SOURCE_ID,
            /* safetySourceData= */ null,
            safetyEvent
        )
    }
}

/**
 * Set whether we show the safety center card to the user to review their auto-revoked permissions.
 */
fun setUnusedAppsReviewNeeded(context: Context, needsReview: Boolean) {
    val sharedPreferences = context.sharedPreferences
    if (
        sharedPreferences.contains(PREF_KEY_UNUSED_APPS_REVIEW) &&
            sharedPreferences.getBoolean(PREF_KEY_UNUSED_APPS_REVIEW, false) == needsReview
    ) {
        return
    }
    sharedPreferences.edit().putBoolean(PREF_KEY_UNUSED_APPS_REVIEW, needsReview).apply()
}

private fun getUnusedAppsReviewNeeded(context: Context): Boolean {
    return context.sharedPreferences.getBoolean(PREF_KEY_UNUSED_APPS_REVIEW, false)
}

/**
 * Receiver of the following broadcasts:
 * <ul>
 * <li> {@link Intent.ACTION_BOOT_COMPLETED}
 * <li> {@link #ACTION_SET_UP_HIBERNATION}
 * <li> {@link Intent.ACTION_TIME_CHANGED}
 * <li> {@link Intent.ACTION_TIMEZONE_CHANGED}
 * </ul>
 */
class HibernationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_SET_UP_HIBERNATION) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(
                    LOG_TAG,
                    "scheduleHibernationJob " +
                        "with frequency ${getCheckFrequencyMs()}ms " +
                        "and threshold ${getUnusedThresholdMs()}ms"
                )
            }

            initStartTimeOfUnusedAppTracking(context.sharedPreferences)

            // If this user is a profile, then its hibernation/auto-revoke will be handled by the
            // primary user
            if (isProfile(context)) {
                if (DEBUG_HIBERNATION_POLICY) {
                    DumpableLog.i(
                        LOG_TAG,
                        "user ${Process.myUserHandle().identifier} is a profile." +
                            " Not running hibernation job."
                    )
                }
                return
            } else if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(
                    LOG_TAG,
                    "user ${Process.myUserHandle().identifier} is a profile" +
                        "owner. Running hibernation job."
                )
            }

            if (isNewJobScheduleRequired(context)) {
                // periodic jobs normally run immediately, which is unnecessarily premature
                SKIP_NEXT_RUN = true
                val jobInfo =
                    JobInfo.Builder(
                            Constants.HIBERNATION_JOB_ID,
                            ComponentName(context, HibernationJobService::class.java)
                        )
                        .setPeriodic(getCheckFrequencyMs())
                        // persist this job across boots
                        .setPersisted(true)
                        .build()
                val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
                if (status != JobScheduler.RESULT_SUCCESS) {
                    DumpableLog.e(
                        LOG_TAG,
                        "Could not schedule " +
                            "${HibernationJobService::class.java.simpleName}: $status"
                    )
                }
            }
        }
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            adjustStartTimeOfUnusedAppTracking(context.sharedPreferences)
        }
    }

    // UserManager#isProfile was already a systemAPI, linter started complaining after it
    // was exposed as a public API thinking it was a newly exposed API.
    @SuppressLint("NewApi")
    private fun isProfile(context: Context): Boolean {
        val userManager = context.getSystemService(UserManager::class.java)!!
        return userManager.isProfile
    }

    /**
     * Returns whether a new job needs to be scheduled. A persisted job is used to keep the schedule
     * across boots, but that job needs to be scheduled a first time and whenever the check
     * frequency changes.
     */
    private fun isNewJobScheduleRequired(context: Context): Boolean {
        // check if the job is already scheduled or needs a change
        var scheduleNewJob = false
        val existingJob: JobInfo? =
            context
                .getSystemService(JobScheduler::class.java)!!
                .getPendingJob(Constants.HIBERNATION_JOB_ID)
        if (existingJob == null) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "No existing job, scheduling a new one")
            }
            scheduleNewJob = true
        } else if (existingJob.intervalMillis != getCheckFrequencyMs()) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Interval frequency has changed, updating job")
            }
            scheduleNewJob = true
        } else {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Job already scheduled.")
            }
        }
        return scheduleNewJob
    }
}

/**
 * Gets apps that are unused and should hibernate as a map of the user and their hibernateable apps.
 */
@MainThread
private suspend fun getAppsToHibernate(
    context: Context,
): Map<UserHandle, List<LightPackageInfo>> {
    val now = System.currentTimeMillis()
    val startTimeOfUnusedAppTracking = getStartTimeOfUnusedAppTracking(context.sharedPreferences)

    val allPackagesByUser = AllPackageInfosLiveData.getInitializedValue(forceUpdate = true)
    val allPackagesByUserByUid =
        allPackagesByUser.mapValues { (_, pkgs) -> pkgs.groupBy { pkg -> pkg.uid } }
    val unusedApps = allPackagesByUser.toMutableMap()

    val userStats =
        UsageStatsLiveData[
                getUnusedThresholdMs(),
                if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY]
            .getInitializedValue()
    if (DEBUG_HIBERNATION_POLICY) {
        for ((user, stats) in userStats) {
            DumpableLog.i(
                LOG_TAG,
                "Usage stats for user ${user.identifier}: " +
                    stats
                        .map { stat -> stat.packageName to Date(stat.lastTimePackageUsed()) }
                        .toMap()
            )
        }
    }
    for (user in unusedApps.keys.toList()) {
        if (user !in userStats.keys) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Ignoring user ${user.identifier}")
            }
            unusedApps.remove(user)
        }
    }

    for ((user, stats) in userStats) {
        var unusedUserApps = unusedApps[user] ?: continue

        unusedUserApps =
            unusedUserApps.filter { packageInfo ->
                val pkgName = packageInfo.packageName

                val uidPackages =
                    allPackagesByUserByUid[user]!![packageInfo.uid]?.map { info ->
                        info.packageName
                    }
                        ?: emptyList()
                if (pkgName !in uidPackages) {
                    Log.wtf(
                        LOG_TAG,
                        "Package $pkgName not among packages for " +
                            "its uid ${packageInfo.uid}: $uidPackages"
                    )
                }
                var lastTimePkgUsed: Long = stats.lastTimePackageUsed(uidPackages)

                // Limit by install time
                lastTimePkgUsed = Math.max(lastTimePkgUsed, packageInfo.firstInstallTime)

                // Limit by first boot time
                lastTimePkgUsed = Math.max(lastTimePkgUsed, startTimeOfUnusedAppTracking)

                // Handle cross-profile apps
                if (context.isPackageCrossProfile(pkgName)) {
                    for ((otherUser, otherStats) in userStats) {
                        if (otherUser == user) {
                            continue
                        }
                        lastTimePkgUsed =
                            maxOf(lastTimePkgUsed, otherStats.lastTimePackageUsed(pkgName))
                    }
                }

                // Threshold check - whether app is unused
                now - lastTimePkgUsed > getUnusedThresholdMs()
            }

        unusedApps[user] = unusedUserApps
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(
                LOG_TAG,
                "Unused apps for user ${user.identifier}: " +
                    "${unusedUserApps.map { it.packageName }}"
            )
        }
    }

    val appsToHibernate = mutableMapOf<UserHandle, List<LightPackageInfo>>()
    val userManager = context.getSystemService(UserManager::class.java)
    for ((user, userApps) in unusedApps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }
        var userAppsToHibernate = mutableListOf<LightPackageInfo>()
        userApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
            if (isPackageHibernationExemptBySystem(pkg, user)) {
                return@forEachInParallel
            }

            if (isPackageHibernationExemptByUser(context, pkg)) {
                return@forEachInParallel
            }

            val packageName = pkg.packageName
            val packageImportance =
                context
                    .getSystemService(ActivityManager::class.java)!!
                    .getPackageImportance(packageName)
            if (packageImportance <= IMPORTANCE_CANT_SAVE_STATE) {
                // Process is running in a state where it should not be killed
                DumpableLog.i(
                    LOG_TAG,
                    "Skipping hibernation - $packageName running with importance " +
                        "$packageImportance"
                )
                return@forEachInParallel
            }

            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(
                    LOG_TAG,
                    "unused app $packageName - last used on " +
                        userStats[user]?.lastTimePackageUsed(packageName)?.let(::Date)
                )
            }

            synchronized(userAppsToHibernate) { userAppsToHibernate.add(pkg) }
        }
        appsToHibernate.put(user, userAppsToHibernate)
    }
    return appsToHibernate
}

/**
 * Gets the last time we consider the package used based off its usage stats. On pre-S devices this
 * looks at last time visible which tracks explicit usage. In S, we add component usage which tracks
 * various forms of implicit usage (e.g. service bindings).
 */
fun UsageStats.lastTimePackageUsed(): Long {
    var lastTimePkgUsed = this.lastTimeVisible
    if (SdkLevel.isAtLeastS()) {
        lastTimePkgUsed = maxOf(lastTimePkgUsed, this.lastTimeAnyComponentUsed)
    }
    return lastTimePkgUsed
}

private fun List<UsageStats>.lastTimePackageUsed(pkgNames: List<String>): Long {
    var result = 0L
    for (stat in this) {
        if (stat.packageName in pkgNames) {
            result = Math.max(result, stat.lastTimePackageUsed())
        }
    }
    return result
}

private fun List<UsageStats>.lastTimePackageUsed(pkgName: String): Long {
    return lastTimePackageUsed(listOf(pkgName))
}

/** Checks if the given package is exempt from hibernation in a way that's not user-overridable */
suspend fun isPackageHibernationExemptBySystem(
    pkg: LightPackageInfo,
    user: UserHandle,
): Boolean {
    if (!LauncherPackagesLiveData.getInitializedValue().contains(pkg.packageName)) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - Package is not on launcher")
        }
        return true
    }
    if (!ExemptServicesLiveData[user].getInitializedValue()[pkg.packageName].isNullOrEmpty()) {
        return true
    }
    if (Utils.isUserDisabledOrWorkProfile(user)) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(
                LOG_TAG,
                "Exempted ${pkg.packageName} - $user is disabled or a work profile"
            )
        }
        return true
    }

    if (pkg.uid == Process.SYSTEM_UID) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - Package shares system uid")
        }
        return true
    }

    val context = PermissionControllerApplication.get()
    if (context.getSystemService(DevicePolicyManager::class.java)!!.isDeviceManaged) {
        // TODO(b/237065504): Use proper system API to check if the device is financed in U.
        val isFinancedDevice =
            Settings.Global.getInt(context.contentResolver, "device_owner_type", 0) == 1
        if (!isFinancedDevice) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - device is managed")
            }
            return true
        }
    }

    val carrierPrivilegedStatus =
        CarrierPrivilegedStatusLiveData[pkg.packageName].getInitializedValue()
    if (
        carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_HAS_ACCESS &&
            carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_NO_ACCESS
    ) {
        DumpableLog.w(
            LOG_TAG,
            "Error carrier privileged status for ${pkg.packageName}: " + carrierPrivilegedStatus
        )
    }
    if (carrierPrivilegedStatus == CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - carrier privileged")
        }
        return true
    }

    if (
        PermissionControllerApplication.get()
            .packageManager
            .checkPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE, pkg.packageName) ==
            PERMISSION_GRANTED
    ) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(
                LOG_TAG,
                "Exempted ${pkg.packageName} " + "- holder of READ_PRIVILEGED_PHONE_STATE"
            )
        }
        return true
    }

    val emergencyRoleHolders =
        context
            .getSystemService(android.app.role.RoleManager::class.java)!!
            .getRoleHolders(RoleManager.ROLE_EMERGENCY)
    if (emergencyRoleHolders.contains(pkg.packageName)) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - emergency app")
        }
        return true
    }

    if (SdkLevel.isAtLeastS()) {
        val hasInstallOrUpdatePermissions =
            context.checkPermission(Manifest.permission.INSTALL_PACKAGES, -1 /* pid */, pkg.uid) ==
                PERMISSION_GRANTED ||
                context.checkPermission(
                    Manifest.permission.INSTALL_PACKAGE_UPDATES,
                    -1 /* pid */,
                    pkg.uid
                ) == PERMISSION_GRANTED
        val hasUpdatePackagesWithoutUserActionPermission =
            context.checkPermission(UPDATE_PACKAGES_WITHOUT_USER_ACTION, -1 /* pid */, pkg.uid) ==
                PERMISSION_GRANTED
        val isInstallerOfRecord =
            InstallerPackagesLiveData[user].getInitializedValue().contains(pkg.packageName) &&
                hasUpdatePackagesWithoutUserActionPermission
        // Grant if app w/ privileged install/update permissions or app is an installer app that
        // updates packages without user action.
        if (hasInstallOrUpdatePermissions || isInstallerOfRecord) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - installer app")
            }
            return true
        }

        val roleHolders =
            context
                .getSystemService(android.app.role.RoleManager::class.java)!!
                .getRoleHolders(RoleManager.ROLE_SYSTEM_WELLBEING)
        if (roleHolders.contains(pkg.packageName)) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - wellbeing app")
            }
            return true
        }
    }

    if (SdkLevel.isAtLeastT()) {
        val roleHolders =
            context
                .getSystemService(android.app.role.RoleManager::class.java)!!
                .getRoleHolders(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT)
        if (roleHolders.contains(pkg.packageName)) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - device policy manager app")
            }
            return true
        }
    }

    if (
        isSystemExemptFromHibernationEnabled() &&
            AppOpLiveData[
                    pkg.packageName, AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION, pkg.uid]
                .getInitializedValue() == AppOpsManager.MODE_ALLOWED
    ) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(
                LOG_TAG,
                "Exempted ${pkg.packageName} - has OP_SYSTEM_EXEMPT_FROM_HIBERNATION"
            )
        }
        return true
    }

    return false
}

/**
 * Checks if the given package is exempt from hibernation/auto revoke in a way that's
 * user-overridable
 */
suspend fun isPackageHibernationExemptByUser(
    context: Context,
    pkg: LightPackageInfo,
): Boolean {
    val packageName = pkg.packageName
    val packageUid = pkg.uid

    val allowlistAppOpMode =
        AppOpLiveData[
                packageName, AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageUid]
            .getInitializedValue()
    if (allowlistAppOpMode == AppOpsManager.MODE_DEFAULT) {
        // Initial state - allowlist not explicitly overridden by either user or installer
        if (DEBUG_OVERRIDE_THRESHOLDS) {
            // Suppress exemptions to allow debugging
            return false
        }

        if (hibernationTargetsPreSApps()) {
            // Default on if overridden
            return false
        }

        // Q- packages exempt by default, except R- on Auto since Auto-Revoke was skipped in R
        val maxTargetSdkVersionForExemptApps =
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                android.os.Build.VERSION_CODES.R
            } else {
                android.os.Build.VERSION_CODES.Q
            }

        return pkg.targetSdkVersion <= maxTargetSdkVersionForExemptApps
    }
    // Check whether user/installer exempt
    return allowlistAppOpMode != AppOpsManager.MODE_ALLOWED
}

private fun Context.isPackageCrossProfile(pkg: String): Boolean {
    return packageManager.checkPermission(Manifest.permission.INTERACT_ACROSS_PROFILES, pkg) ==
        PERMISSION_GRANTED ||
        packageManager.checkPermission(Manifest.permission.INTERACT_ACROSS_USERS, pkg) ==
            PERMISSION_GRANTED ||
        packageManager.checkPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL, pkg) ==
            PERMISSION_GRANTED
}

val Context.sharedPreferences: SharedPreferences
    get() {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

internal class SystemTime {
    var actualSystemTime: Long = SNAPSHOT_UNINITIALIZED
    var actualRealtime: Long = SNAPSHOT_UNINITIALIZED
    var diffSystemTime: Long = SNAPSHOT_UNINITIALIZED
}

private fun getSystemTime(sharedPreferences: SharedPreferences): SystemTime {
    val systemTime = SystemTime()
    val systemTimeSnapshot =
        sharedPreferences.getLong(PREF_KEY_BOOT_TIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
    if (systemTimeSnapshot == SNAPSHOT_UNINITIALIZED) {
        DumpableLog.e(LOG_TAG, "PREF_KEY_BOOT_TIME_SNAPSHOT is not initialized")
        return systemTime
    }

    val realtimeSnapshot =
        sharedPreferences.getLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
    if (realtimeSnapshot == SNAPSHOT_UNINITIALIZED) {
        DumpableLog.e(LOG_TAG, "PREF_KEY_ELAPSED_REALTIME_SNAPSHOT is not initialized")
        return systemTime
    }
    systemTime.actualSystemTime = System.currentTimeMillis()
    systemTime.actualRealtime = SystemClock.elapsedRealtime()
    val expectedSystemTime = systemTime.actualRealtime - realtimeSnapshot + systemTimeSnapshot
    systemTime.diffSystemTime = systemTime.actualSystemTime - expectedSystemTime
    return systemTime
}

fun getStartTimeOfUnusedAppTracking(sharedPreferences: SharedPreferences): Long {
    val startTimeOfUnusedAppTracking =
        sharedPreferences.getLong(
            PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
            SNAPSHOT_UNINITIALIZED
        )

    // If the preference is not initialized then use the current system time.
    if (startTimeOfUnusedAppTracking == SNAPSHOT_UNINITIALIZED) {
        val actualSystemTime = System.currentTimeMillis()
        sharedPreferences
            .edit()
            .putLong(PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING, actualSystemTime)
            .apply()
        return actualSystemTime
    }

    val diffSystemTime = getSystemTime(sharedPreferences).diffSystemTime
    // If the value stored is older than a day adjust start time.
    if (diffSystemTime > ONE_DAY_MS) {
        adjustStartTimeOfUnusedAppTracking(sharedPreferences)
    }
    return sharedPreferences.getLong(
        PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
        SNAPSHOT_UNINITIALIZED
    )
}

private fun initStartTimeOfUnusedAppTracking(sharedPreferences: SharedPreferences) {
    val systemTimeSnapshot = System.currentTimeMillis()
    if (
        sharedPreferences.getLong(
            PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
            SNAPSHOT_UNINITIALIZED
        ) == SNAPSHOT_UNINITIALIZED
    ) {
        sharedPreferences
            .edit()
            .putLong(PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING, systemTimeSnapshot)
            .apply()
    }
    val realtimeSnapshot = SystemClock.elapsedRealtime()
    sharedPreferences
        .edit()
        .putLong(PREF_KEY_BOOT_TIME_SNAPSHOT, systemTimeSnapshot)
        .putLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, realtimeSnapshot)
        .apply()
}

private fun adjustStartTimeOfUnusedAppTracking(sharedPreferences: SharedPreferences) {
    val systemTime = getSystemTime(sharedPreferences)
    val startTimeOfUnusedAppTracking =
        sharedPreferences.getLong(
            PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
            SNAPSHOT_UNINITIALIZED
        )
    if (startTimeOfUnusedAppTracking == SNAPSHOT_UNINITIALIZED) {
        DumpableLog.e(LOG_TAG, "PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING is not initialized")
        return
    }
    val adjustedStartTimeOfUnusedAppTracking =
        startTimeOfUnusedAppTracking + systemTime.diffSystemTime
    sharedPreferences
        .edit()
        .putLong(PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING, adjustedStartTimeOfUnusedAppTracking)
        .putLong(PREF_KEY_BOOT_TIME_SNAPSHOT, systemTime.actualSystemTime)
        .putLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, systemTime.actualRealtime)
        .apply()
}

/** Make intent to go to unused apps page. */
private fun makeUnusedAppsIntent(context: Context, sessionId: Long): PendingIntent {
    val clickIntent =
        Intent(Intent.ACTION_MANAGE_UNUSED_APPS).apply {
            putExtra(Constants.EXTRA_SESSION_ID, sessionId)
            flags = FLAG_ACTIVITY_NEW_TASK
        }
    val pendingIntent =
        PendingIntent.getActivity(context, 0, clickIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
    return pendingIntent
}

/** Make intent for when safety center card is dismissed. */
private fun makeDismissIntent(context: Context, sessionId: Long): PendingIntent {
    val dismissIntent =
        Intent(context, DismissHandler::class.java).apply {
            putExtra(Constants.EXTRA_SESSION_ID, sessionId)
            flags = FLAG_RECEIVER_FOREGROUND
        }
    return PendingIntent.getBroadcast(
        context,
        /* requestCode= */ 0,
        dismissIntent,
        FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
    )
}

/** Broadcast receiver class for when safety center card is dismissed. */
class DismissHandler : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        setUnusedAppsReviewNeeded(context!!, false)
    }
}

/**
 * A job to check for apps unused in the last [getUnusedThresholdMs]ms every [getCheckFrequencyMs]ms
 * and hibernate the app / revoke their runtime permissions.
 */
class HibernationJobService : JobService() {
    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "onStartJob")
        }

        if (SKIP_NEXT_RUN) {
            SKIP_NEXT_RUN = false
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "Skipping auto revoke first run when scheduled by system")
            }
            jobFinished(params, false)
            return true
        }

        jobStartTime = System.currentTimeMillis()
        job =
            GlobalScope.launch(Main) {
                try {
                    var sessionId = Constants.INVALID_SESSION_ID
                    while (sessionId == Constants.INVALID_SESSION_ID) {
                        sessionId = Random().nextLong()
                    }

                    val appsToHibernate = getAppsToHibernate(this@HibernationJobService)
                    var hibernatedApps: Set<Pair<String, UserHandle>> = emptySet()
                    if (isHibernationEnabled()) {
                        val hibernationController =
                            HibernationController(
                                this@HibernationJobService,
                                getUnusedThresholdMs(),
                                hibernationTargetsPreSApps()
                            )
                        hibernatedApps = hibernationController.hibernateApps(appsToHibernate)
                    }
                    val revokedApps =
                        revokeAppPermissions(appsToHibernate, this@HibernationJobService, sessionId)
                    val unusedApps: Set<Pair<String, UserHandle>> = hibernatedApps + revokedApps
                    if (unusedApps.isNotEmpty()) {
                        showUnusedAppsNotification(
                            unusedApps.size,
                            sessionId,
                            Process.myUserHandle()
                        )
                        if (
                            SdkLevel.isAtLeastT() &&
                                revokedApps.isNotEmpty() &&
                                getSystemService(SafetyCenterManager::class.java)!!
                                    .isSafetyCenterEnabled
                        ) {
                            setUnusedAppsReviewNeeded(this@HibernationJobService, true)
                            rescanAndPushDataToSafetyCenter(
                                this@HibernationJobService,
                                sessionId,
                                SafetyEvent.Builder(
                                        SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
                                    )
                                    .build()
                            )
                        }
                    }
                } catch (e: Exception) {
                    DumpableLog.e(LOG_TAG, "Failed to auto-revoke permissions", e)
                }
                jobFinished(params, false)
            }
        return true
    }

    private fun showUnusedAppsNotification(numUnused: Int, sessionId: Long, user: UserHandle) {
        val notificationManager = getSystemService(NotificationManager::class.java)!!

        val permissionReminderChannel =
            NotificationChannel(
                Constants.PERMISSION_REMINDER_CHANNEL_ID,
                getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_LOW
            )
        notificationManager.createNotificationChannel(permissionReminderChannel)

        var notifTitle: String
        var notifContent: String
        if (isHibernationEnabled()) {
            notifTitle =
                StringUtils.getIcuPluralsString(
                    this,
                    R.string.unused_apps_notification_title,
                    numUnused
                )
            notifContent = getString(R.string.unused_apps_notification_content)
        } else {
            notifTitle = getString(R.string.auto_revoke_permission_notification_title)
            notifContent = getString(R.string.auto_revoke_permission_notification_content)
        }

        // Notification won't appear on TV, because notifications are considered distruptive on TV
        val b =
            Notification.Builder(this, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setContentTitle(notifTitle)
                .setContentText(notifContent)
                .setStyle(Notification.BigTextStyle().bigText(notifContent))
                .setColor(getColor(android.R.color.system_notification_accent_color))
                .setAutoCancel(true)
                .setContentIntent(makeUnusedAppsIntent(this, sessionId))
        val extras = Bundle()
        if (DeviceUtils.isAuto(this)) {
            val settingsIcon =
                KotlinUtils.getSettingsIcon(application, user, applicationContext.packageManager)
            extras.putBoolean(Constants.NOTIFICATION_EXTRA_USE_LAUNCHER_ICON, false)
            b.setLargeIcon(settingsIcon)
        }
        if (
            SdkLevel.isAtLeastT() &&
                getSystemService(SafetyCenterManager::class.java)!!.isSafetyCenterEnabled
        ) {
            val notificationResources = KotlinUtils.getSafetyCenterNotificationResources(this)

            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, notificationResources.appLabel)
            b.setSmallIcon(notificationResources.smallIcon)
                .setColor(notificationResources.color)
                .addExtras(extras)
        } else {
            // Use standard Settings branding
            Utils.getSettingsLabelForNotifications(applicationContext.packageManager)?.let {
                settingsLabel ->
                extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, settingsLabel.toString())
                b.setSmallIcon(R.drawable.ic_settings_24dp).addExtras(extras)
            }
        }

        notificationManager.notify(
            HibernationJobService::class.java.simpleName,
            Constants.UNUSED_APPS_NOTIFICATION_ID,
            b.build()
        )
        GlobalScope.launch(IPC) {
            // Preload the unused packages
            getUnusedPackages().getInitializedValue(staleOk = true)
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        DumpableLog.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}

/**
 * Packages using exempt services for the current user (package-name -> list<service-interfaces>
 * implemented by the package)
 */
class ExemptServicesLiveData(private val user: UserHandle) :
    SmartUpdateMediatorLiveData<Map<String, List<String>>>() {
    private val serviceLiveDatas: List<SmartUpdateMediatorLiveData<Set<String>>> =
        listOf(
            ServiceLiveData[
                InputMethod.SERVICE_INTERFACE, Manifest.permission.BIND_INPUT_METHOD, user],
            ServiceLiveData[
                NotificationListenerService.SERVICE_INTERFACE,
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                user],
            ServiceLiveData[
                AccessibilityService.SERVICE_INTERFACE,
                Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                user],
            ServiceLiveData[
                WallpaperService.SERVICE_INTERFACE, Manifest.permission.BIND_WALLPAPER, user],
            ServiceLiveData[
                VoiceInteractionService.SERVICE_INTERFACE,
                Manifest.permission.BIND_VOICE_INTERACTION,
                user],
            ServiceLiveData[
                PrintService.SERVICE_INTERFACE, Manifest.permission.BIND_PRINT_SERVICE, user],
            ServiceLiveData[
                DreamService.SERVICE_INTERFACE, Manifest.permission.BIND_DREAM_SERVICE, user],
            ServiceLiveData[
                AutofillService.SERVICE_INTERFACE, Manifest.permission.BIND_AUTOFILL_SERVICE, user],
            ServiceLiveData[
                DevicePolicyManager.ACTION_DEVICE_ADMIN_SERVICE,
                Manifest.permission.BIND_DEVICE_ADMIN,
                user],
            BroadcastReceiverLiveData[
                DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                Manifest.permission.BIND_DEVICE_ADMIN,
                user]
        )

    init {
        serviceLiveDatas.forEach { addSource(it) { update() } }
    }

    override fun onUpdate() {
        if (serviceLiveDatas.all { it.isInitialized }) {
            val pksToServices = mutableMapOf<String, MutableList<String>>()

            serviceLiveDatas.forEach { serviceLD ->
                serviceLD.value!!.forEach { packageName ->
                    pksToServices
                        .getOrPut(packageName, { mutableListOf() })
                        .add((serviceLD as? HasIntentAction)?.intentAction ?: "???")
                }
            }

            value = pksToServices
        }
    }

    /**
     * Repository for ExemptServiceLiveData
     *
     * <p> Key value is user
     */
    companion object : DataRepositoryForPackage<UserHandle, ExemptServicesLiveData>() {
        override fun newValue(key: UserHandle): ExemptServicesLiveData {
            return ExemptServicesLiveData(key)
        }
    }
}

/** Live data for whether the hibernation feature is enabled or not. */
object HibernationEnabledLiveData : MutableLiveData<Boolean>() {
    init {
        postValue(
            SdkLevel.isAtLeastS() &&
                DeviceConfig.getBoolean(
                    NAMESPACE_APP_HIBERNATION,
                    Utils.PROPERTY_APP_HIBERNATION_ENABLED,
                    true /* defaultValue */
                )
        )
        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_APP_HIBERNATION,
            PermissionControllerApplication.get().mainExecutor,
            { properties ->
                for (key in properties.keyset) {
                    if (key == Utils.PROPERTY_APP_HIBERNATION_ENABLED) {
                        value =
                            SdkLevel.isAtLeastS() &&
                                properties.getBoolean(key, true /* defaultValue */)
                        break
                    }
                }
            }
        )
    }
}

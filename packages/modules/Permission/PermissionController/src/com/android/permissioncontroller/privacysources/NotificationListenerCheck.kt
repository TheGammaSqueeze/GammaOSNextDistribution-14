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

package com.android.permissioncontroller.privacysources

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.Intent.EXTRA_COMPONENT_NAME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.DeviceConfig
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
import android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.util.Preconditions
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CARD_DISMISSED
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CLICKED_CTA1
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__DISMISSED
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import java.lang.System.currentTimeMillis
import java.util.Random
import java.util.concurrent.TimeUnit.DAYS
import java.util.function.BooleanSupplier
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val TAG = "NotificationListenerCheck"
private const val DEBUG = false
const val SC_NLS_SOURCE_ID = "AndroidNotificationListener"
@VisibleForTesting const val SC_NLS_DISABLE_ACTION_ID = "disable_nls_component"

/** Device config property for whether notification listener check is enabled on the device */
@VisibleForTesting
const val PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED = "notification_listener_check_enabled"

/**
 * Device config property for time period in milliseconds after which current enabled notification
 * listeners are queried
 */
private const val PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
    "notification_listener_check_interval_millis"

private val DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS = DAYS.toMillis(1)

private fun isNotificationListenerCheckFlagEnabled(): Boolean {
    // TODO: b/249789657 Set default to true after policy exemption + impact analysis
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
        false
    )
}

/**
 * Get time in between two periodic checks.
 *
 * Default: 1 day
 *
 * @return The time in between check in milliseconds
 */
private fun getPeriodicCheckIntervalMillis(): Long {
    return DeviceConfig.getLong(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS,
        DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS
    )
}

/**
 * Flexibility of the periodic check.
 *
 * 10% of [.getPeriodicCheckIntervalMillis]
 *
 * @return The flexibility of the periodic check in milliseconds
 */
private fun getFlexForPeriodicCheckMillis(): Long {
    return getPeriodicCheckIntervalMillis() / 10
}

/**
 * Minimum time in between showing two notifications.
 *
 * This is just small enough so that the periodic check can always show a notification.
 *
 * @return The minimum time in milliseconds
 */
private fun getInBetweenNotificationsMillis(): Long {
    return getPeriodicCheckIntervalMillis() - (getFlexForPeriodicCheckMillis() * 2.1).toLong()
}

/** Notification Listener Check requires Android T or later */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun checkNotificationListenerCheckSupported(): Boolean {
    return SdkLevel.isAtLeastT()
}

/**
 * Returns {@code true} when Notification listener check is supported, feature flag enabled and
 * Safety Center enabled
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun checkNotificationListenerCheckEnabled(context: Context): Boolean {
    return checkNotificationListenerCheckSupported() &&
        isNotificationListenerCheckFlagEnabled() &&
        getSystemServiceSafe(context, SafetyCenterManager::class.java).isSafetyCenterEnabled
}

private fun getSafetySourceIssueIdFromComponentName(componentName: ComponentName): String {
    return "notification_listener_${componentName.flattenToString()}"
}

/**
 * Show notification that double-guesses the user if they really wants to grant notification
 * listener permission to an app.
 *
 * <p>A notification is scheduled periodically, or on demand
 *
 * <p>We rate limit the number of notification we show and only ever show one notification at a
 * time.
 *
 * <p>As there are many cases why a notification should not been shown, we always schedule a
 * {@link #addNotificationListenerNotificationIfNeeded check} which then might add a notification.
 *
 * @param context Used to resolve managers
 * @param shouldCancel If supplied, can be used to interrupt long-running operations
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@VisibleForTesting
class NotificationListenerCheckInternal(
    context: Context,
    private val shouldCancel: BooleanSupplier?
) {
    private val parentUserContext = Utils.getParentUserContext(context)
    private val random = Random()
    private val sharedPrefs: SharedPreferences =
        parentUserContext.getSharedPreferences(NLS_PREFERENCE_FILE, MODE_PRIVATE)

    // Don't initialize until used. Delegate used for testing
    @VisibleForTesting
    val exemptPackagesDelegate = lazy {
        getExemptedPackages(
            getSystemServiceSafe(parentUserContext, RoleManager::class.java),
            parentUserContext
        )
    }
    @VisibleForTesting val exemptPackages: Set<String> by exemptPackagesDelegate

    companion object {
        @VisibleForTesting const val NLS_PREFERENCE_FILE = "nls_preference"
        private const val KEY_ALREADY_NOTIFIED_COMPONENTS = "already_notified_services"

        @VisibleForTesting const val SC_NLS_ISSUE_TYPE_ID = "notification_listener_privacy_issue"
        @VisibleForTesting
        const val SC_SHOW_NLS_SETTINGS_ACTION_ID = "show_notification_listener_settings"

        private const val SYSTEM_PKG = "android"

        private const val SYSTEM_AMBIENT_AUDIO_INTELLIGENCE =
            "android.app.role.SYSTEM_AMBIENT_AUDIO_INTELLIGENCE"
        private const val SYSTEM_UI_INTELLIGENCE = "android.app.role.SYSTEM_UI_INTELLIGENCE"
        private const val SYSTEM_AUDIO_INTELLIGENCE = "android.app.role.SYSTEM_AUDIO_INTELLIGENCE"
        private const val SYSTEM_NOTIFICATION_INTELLIGENCE =
            "android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE"
        private const val SYSTEM_TEXT_INTELLIGENCE = "android.app.role.SYSTEM_TEXT_INTELLIGENCE"
        private const val SYSTEM_VISUAL_INTELLIGENCE = "android.app.role.SYSTEM_VISUAL_INTELLIGENCE"

        // This excludes System intelligence roles
        private val EXEMPTED_ROLES =
            arrayOf(
                SYSTEM_AMBIENT_AUDIO_INTELLIGENCE,
                SYSTEM_UI_INTELLIGENCE,
                SYSTEM_AUDIO_INTELLIGENCE,
                SYSTEM_NOTIFICATION_INTELLIGENCE,
                SYSTEM_TEXT_INTELLIGENCE,
                SYSTEM_VISUAL_INTELLIGENCE
            )

        /** Lock required for all public methods */
        private val nlsLock = Mutex()

        /** lock for shared preferences writes */
        private val sharedPrefsLock = Mutex()

        private val sourceStateChangedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
    }

    /**
     * Check for enabled notification listeners and notify user if needed.
     *
     * <p>Always run async inside a {@NotificationListenerCheckJobService} via coroutine.
     */
    @WorkerThread
    suspend fun getEnabledNotificationListenersAndNotifyIfNeeded(
        params: JobParameters,
        service: NotificationListenerCheckJobService
    ) {
        nlsLock.withLock {
            try {
                getEnabledNotificationListenersAndNotifyIfNeededLocked()
                service.jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Could not check for notification listeners", e)
                service.jobFinished(params, true)
            } finally {
                service.clearJob()
            }
        }
    }

    @Throws(InterruptedException::class)
    private suspend fun getEnabledNotificationListenersAndNotifyIfNeededLocked() {
        val enabledComponents: List<ComponentName> = getEnabledNotificationListeners()

        // Clear disabled but previously notified components from notified components data
        removeDisabledComponentsFromNotifiedComponents(enabledComponents)
        val notifiedComponents =
            getNotifiedComponents().mapNotNull { ComponentName.unflattenFromString(it) }

        // Filter to unnotified components
        val unNotifiedComponents = enabledComponents.filter { it !in notifiedComponents }
        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }
        if (DEBUG) {
            Log.d(
                TAG,
                "Found ${enabledComponents.size} enabled notification listeners. " +
                    "${notifiedComponents.size} already notified. ${unNotifiedComponents.size} " +
                    "unnotified, sessionId = $sessionId"
            )
        }

        throwInterruptedExceptionIfTaskIsCanceled()

        postSystemNotificationIfNeeded(unNotifiedComponents, sessionId)
        sendIssuesToSafetyCenter(enabledComponents, sessionId)
    }

    /**
     * Get the [components][ComponentName] which have enabled notification listeners for the
     * parent/context user. Excludes exempt packages.
     *
     * @throws InterruptedException If [.shouldCancel]
     */
    @Throws(InterruptedException::class)
    private fun getEnabledNotificationListeners(): List<ComponentName> {
        // Get all enabled NotificationListenerService components for primary user. NLS from managed
        // profiles are never bound.
        val enabledNotificationListeners =
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .enabledNotificationListeners

        // Filter to components not in exempt packages
        val enabledNotificationListenersExcludingExemptPackages =
            enabledNotificationListeners.filter { !exemptPackages.contains(it.packageName) }

        if (DEBUG) {
            Log.d(
                TAG,
                "enabledNotificationListeners=$enabledNotificationListeners\n" +
                    "enabledNotificationListenersExcludingExemptPackages=" +
                    "$enabledNotificationListenersExcludingExemptPackages"
            )
        }

        throwInterruptedExceptionIfTaskIsCanceled()
        return enabledNotificationListenersExcludingExemptPackages
    }

    /** Get all the exempted packages. */
    private fun getExemptedPackages(roleManager: RoleManager, context: Context): Set<String> {
        val exemptedPackages: MutableSet<String> = HashSet()
        exemptedPackages.add(SYSTEM_PKG)
        EXEMPTED_ROLES.forEach { role -> exemptedPackages.addAll(roleManager.getRoleHolders(role)) }
        exemptedPackages.addAll(NotificationListenerPregrants(context).pregrantedPackages)
        return exemptedPackages
    }

    @VisibleForTesting
    fun getNotifiedComponents(): MutableSet<String> {
        return sharedPrefs.getStringSet(KEY_ALREADY_NOTIFIED_COMPONENTS, mutableSetOf<String>())!!
    }

    suspend fun removeDisabledComponentsFromNotifiedComponents(
        enabledComponents: Collection<ComponentName>
    ) {
        sharedPrefsLock.withLock {
            val enabledComponentsStringSet =
                enabledComponents.map { it.flattenToShortString() }.toSet()
            val notifiedComponents = getNotifiedComponents()
            // Filter to only components that have enabled listeners
            val enabledNotifiedComponents =
                notifiedComponents.filter { enabledComponentsStringSet.contains(it) }.toSet()
            sharedPrefs
                .edit()
                .putStringSet(KEY_ALREADY_NOTIFIED_COMPONENTS, enabledNotifiedComponents)
                .apply()
        }
    }

    suspend fun markComponentAsNotified(component: ComponentName) {
        sharedPrefsLock.withLock {
            val notifiedComponents = getNotifiedComponents()
            notifiedComponents.add(component.flattenToShortString())
            sharedPrefs
                .edit()
                .putStringSet(KEY_ALREADY_NOTIFIED_COMPONENTS, notifiedComponents)
                .apply()
        }
    }

    suspend fun removeFromNotifiedComponents(packageName: String) {
        sharedPrefsLock.withLock {
            val notifiedComponents = getNotifiedComponents()
            val filteredServices =
                notifiedComponents
                    .filter {
                        val notifiedComponentName = ComponentName.unflattenFromString(it)
                        return@filter notifiedComponentName?.packageName != packageName
                    }
                    .toSet()
            if (filteredServices.size < notifiedComponents.size) {
                sharedPrefs
                    .edit()
                    .putStringSet(KEY_ALREADY_NOTIFIED_COMPONENTS, filteredServices)
                    .apply()
            }
        }
    }

    suspend fun removeFromNotifiedComponents(component: ComponentName) {
        val componentNameShortString = component.flattenToShortString()
        sharedPrefsLock.withLock {
            val notifiedComponents = getNotifiedComponents()
            val componentRemoved = notifiedComponents.remove(componentNameShortString)
            if (componentRemoved) {
                sharedPrefs
                    .edit()
                    .putStringSet(KEY_ALREADY_NOTIFIED_COMPONENTS, notifiedComponents)
                    .apply()
            }
        }
    }

    private fun getLastNotificationShownTimeMillis(): Long {
        return sharedPrefs.getLong(KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN, 0)
    }

    private suspend fun updateLastShownNotificationTime() {
        sharedPrefsLock.withLock {
            sharedPrefs
                .edit()
                .putLong(KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN, currentTimeMillis())
                .apply()
        }
    }

    @Throws(InterruptedException::class)
    private suspend fun postSystemNotificationIfNeeded(
        components: List<ComponentName>,
        sessionId: Long
    ) {
        val componentsInternal = components.toMutableList()

        // Don't show too many notification within certain timespan
        if (
            currentTimeMillis() - getLastNotificationShownTimeMillis() <
                getInBetweenNotificationsMillis()
        ) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "Notification not posted, within " +
                        "$DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS ms"
                )
            }
            return
        }

        // Check for existing notification first, exit if one already present
        if (getCurrentlyShownNotificationLocked() != null) {
            if (DEBUG) {
                Log.d(TAG, "Notification not posted, previous notification has not been dismissed")
            }
            return
        }

        // Get a random package and resolve package info
        var pkgInfo: PackageInfo? = null
        var componentToNotifyFor: ComponentName? = null
        while (pkgInfo == null || componentToNotifyFor == null) {
            throwInterruptedExceptionIfTaskIsCanceled()

            if (componentsInternal.isEmpty()) {
                if (DEBUG) {
                    Log.d(TAG, "Notification not posted, no unnotified enabled listeners")
                }
                return
            }

            componentToNotifyFor = componentsInternal[random.nextInt(componentsInternal.size)]
            try {
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "Attempting to get PackageInfo for " + componentToNotifyFor.packageName
                    )
                }
                pkgInfo =
                    Utils.getPackageInfoForComponentName(parentUserContext, componentToNotifyFor)
            } catch (e: PackageManager.NameNotFoundException) {
                if (DEBUG) {
                    Log.w(TAG, "${componentToNotifyFor.packageName} not found")
                }
                componentsInternal.remove(componentToNotifyFor)
            }
        }

        createPermissionReminderChannel()
        createNotificationForNotificationListener(componentToNotifyFor, pkgInfo, sessionId)

        // Mark as notified, since we don't get the on-click
        markComponentAsNotified(componentToNotifyFor)
    }

    /** Create the channel the notification listener notifications should be posted to. */
    private fun createPermissionReminderChannel() {
        val permissionReminderChannel =
            NotificationChannel(
                Constants.PERMISSION_REMINDER_CHANNEL_ID,
                parentUserContext.getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_LOW
            )

        val notificationManager =
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)

        notificationManager.createNotificationChannel(permissionReminderChannel)
    }

    /**
     * Create a notification reminding the user that a package has an enabled notification listener.
     * From this notification the user can directly go to Safety Center to assess issue.
     *
     * @param componentName the [ComponentName] of the Notification Listener
     * @param pkg The [PackageInfo] for the [ComponentName] package
     */
    private suspend fun createNotificationForNotificationListener(
        componentName: ComponentName,
        pkg: PackageInfo,
        sessionId: Long
    ) {
        val pkgLabel = Utils.getApplicationLabel(parentUserContext, pkg.applicationInfo!!)
        val uid = pkg.applicationInfo!!.uid

        val deletePendingIntent =
            getNotificationDeletePendingIntent(parentUserContext, componentName, uid, sessionId)
        val clickPendingIntent =
            getSafetyCenterActivityPendingIntent(parentUserContext, componentName, uid, sessionId)

        val title =
            parentUserContext.getString(R.string.notification_listener_reminder_notification_title)
        val text =
            parentUserContext.getString(
                R.string.notification_listener_reminder_notification_content,
                pkgLabel
            )

        val (appLabel, smallIcon, color) =
            KotlinUtils.getSafetyCenterNotificationResources(parentUserContext)

        val b: Notification.Builder =
            Notification.Builder(parentUserContext, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(text)
                // Ensure entire text can be displayed, instead of being truncated to one line
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setSmallIcon(smallIcon)
                .setColor(color)
                .setAutoCancel(true)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(clickPendingIntent)

        if (appLabel.isNotEmpty()) {
            val appNameExtras = Bundle()
            appNameExtras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appLabel)
            b.addExtras(appNameExtras)
        }

        val notificationManager =
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
        notificationManager.notify(
            componentName.flattenToString(),
            NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID,
            b.build()
        )

        if (DEBUG) {
            Log.d(
                TAG,
                "Notification listener check notification shown with component=" +
                    "${componentName.flattenToString()}, uid=$uid, sessionId=$sessionId"
            )
        }

        PermissionControllerStatsLog.write(
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER,
            uid,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN,
            sessionId
        )
        updateLastShownNotificationTime()
    }

    /** @return [PendingIntent] to safety center */
    private fun getNotificationDeletePendingIntent(
        context: Context,
        componentName: ComponentName,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(
                    parentUserContext,
                    NotificationListenerCheckNotificationDeleteHandler::class.java
                )
                .apply {
                    putExtra(EXTRA_COMPONENT_NAME, componentName)
                    putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                    putExtra(Intent.EXTRA_UID, uid)
                    flags = FLAG_RECEIVER_FOREGROUND
                    identifier = componentName.flattenToString()
                }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
    }

    /** @return [PendingIntent] to safety center */
    private fun getSafetyCenterActivityPendingIntent(
        context: Context,
        componentName: ComponentName,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_SAFETY_CENTER).apply {
                putExtra(EXTRA_SAFETY_SOURCE_ID, SC_NLS_SOURCE_ID)
                putExtra(
                    EXTRA_SAFETY_SOURCE_ISSUE_ID,
                    getSafetySourceIssueIdFromComponentName(componentName)
                )
                putExtra(EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                flags = FLAG_ACTIVITY_NEW_TASK
                identifier = componentName.flattenToString()
            }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
    }

    /**
     * Get currently shown notification. We only ever show one notification per profile group. Also
     * only show notifications on the parent user/profile due to NotificationManager only binding
     * non-managed NLS.
     *
     * @return The notification or `null` if no notification is currently shown
     */
    private fun getCurrentlyShownNotificationLocked(): StatusBarNotification? {
        val notifications =
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .activeNotifications

        return notifications.firstOrNull { it.id == NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID }
    }

    /** Remove any posted notifications for this feature */
    internal fun removeAnyNotification() {
        cancelNotification()
    }

    /** Remove notification if present for a package */
    internal fun removeNotificationsForPackage(pkg: String) {
        val notification: StatusBarNotification = getCurrentlyShownNotificationLocked() ?: return
        val notificationComponent = ComponentName.unflattenFromString(notification.tag)
        if (notificationComponent == null || notificationComponent.packageName != pkg) {
            return
        }
        cancelNotification(notification.tag)
    }

    /** Remove notification if present for a [ComponentName] */
    internal fun removeNotificationsForComponent(component: ComponentName) {
        val notification: StatusBarNotification = getCurrentlyShownNotificationLocked() ?: return
        val notificationComponent = ComponentName.unflattenFromString(notification.tag)
        if (notificationComponent == null || notificationComponent != component) {
            return
        }
        cancelNotification(notification.tag)
    }

    private fun cancelNotification(notificationTag: String) {
        getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
            .cancel(notificationTag, NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
    }

    private fun cancelNotification() {
        getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
            .cancel(NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
    }

    internal fun sendIssuesToSafetyCenter(
        safetyEvent: SafetyEvent = sourceStateChangedSafetyEvent
    ) {
        val enabledComponents = getEnabledNotificationListeners()
        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }
        sendIssuesToSafetyCenter(enabledComponents, sessionId, safetyEvent)
    }

    private fun sendIssuesToSafetyCenter(
        enabledComponents: List<ComponentName>,
        sessionId: Long,
        safetyEvent: SafetyEvent = sourceStateChangedSafetyEvent
    ) {
        val pendingIssues = enabledComponents.mapNotNull { createSafetySourceIssue(it, sessionId) }
        val dataBuilder = SafetySourceData.Builder()
        pendingIssues.forEach { dataBuilder.addIssue(it) }
        val safetySourceData = dataBuilder.build()
        val safetyCenterManager =
            getSystemServiceSafe(parentUserContext, SafetyCenterManager::class.java)
        safetyCenterManager.setSafetySourceData(SC_NLS_SOURCE_ID, safetySourceData, safetyEvent)
    }

    /**
     * @param componentName enabled [NotificationListenerService]
     * @return safety source issue, shown as the warning card in safety center. Null if unable to
     *   create safety source issue
     */
    @VisibleForTesting
    fun createSafetySourceIssue(componentName: ComponentName, sessionId: Long): SafetySourceIssue? {
        val pkgInfo: PackageInfo
        try {
            pkgInfo = Utils.getPackageInfoForComponentName(parentUserContext, componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            if (DEBUG) {
                Log.w(TAG, "${componentName.packageName} not found")
            }
            return null
        }
        val pkgLabel = Utils.getApplicationLabel(parentUserContext, pkgInfo.applicationInfo!!)
        val safetySourceIssueId = getSafetySourceIssueIdFromComponentName(componentName)
        val uid = pkgInfo.applicationInfo!!.uid

        val disableNlsPendingIntent =
            getDisableNlsPendingIntent(
                parentUserContext,
                safetySourceIssueId,
                componentName,
                uid,
                sessionId
            )

        val disableNlsAction =
            SafetySourceIssue.Action.Builder(
                    SC_NLS_DISABLE_ACTION_ID,
                    parentUserContext.getString(
                        R.string.notification_listener_remove_access_button_label
                    ),
                    disableNlsPendingIntent
                )
                .setWillResolve(true)
                .setSuccessMessage(
                    parentUserContext.getString(
                        R.string.notification_listener_remove_access_success_label
                    )
                )
                .build()

        val notificationListenerDetailSettingsPendingIntent =
            getNotificationListenerDetailSettingsPendingIntent(
                parentUserContext,
                componentName,
                uid,
                sessionId
            )

        val showNotificationListenerSettingsAction =
            SafetySourceIssue.Action.Builder(
                    SC_SHOW_NLS_SETTINGS_ACTION_ID,
                    parentUserContext.getString(
                        R.string.notification_listener_review_app_button_label
                    ),
                    notificationListenerDetailSettingsPendingIntent
                )
                .build()

        val actionCardDismissPendingIntent =
            getActionCardDismissalPendingIntent(parentUserContext, componentName, uid, sessionId)

        val title =
            parentUserContext.getString(R.string.notification_listener_reminder_notification_title)
        val summary =
            parentUserContext.getString(R.string.notification_listener_warning_card_content)

        return SafetySourceIssue.Builder(
                safetySourceIssueId,
                title,
                summary,
                SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                SC_NLS_ISSUE_TYPE_ID
            )
            .setSubtitle(pkgLabel)
            .addAction(disableNlsAction)
            .addAction(showNotificationListenerSettingsAction)
            .setOnDismissPendingIntent(actionCardDismissPendingIntent)
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
            .build()
    }

    /** @return [PendingIntent] for remove access button on the warning card. */
    private fun getDisableNlsPendingIntent(
        context: Context,
        safetySourceIssueId: String,
        componentName: ComponentName,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(context, DisableNotificationListenerComponentHandler::class.java).apply {
                putExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID, safetySourceIssueId)
                putExtra(EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                flags = FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
            }

        return PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
    }

    /** @return [PendingIntent] to Notification Listener Detail Settings page */
    private fun getNotificationListenerDetailSettingsPendingIntent(
        context: Context,
        componentName: ComponentName,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                flags = FLAG_ACTIVITY_NEW_TASK
                identifier = componentName.flattenToString()
                putExtra(
                    EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    componentName.flattenToString()
                )
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                putExtra(Constants.EXTRA_IS_FROM_SLICE, true)
            }
        return PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
    }

    private fun getActionCardDismissalPendingIntent(
        context: Context,
        componentName: ComponentName,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(context, NotificationListenerActionCardDismissalReceiver::class.java).apply {
                putExtra(EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                flags = FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
            }
        return PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
    }

    /** If [.shouldCancel] throw an [InterruptedException]. */
    @Throws(InterruptedException::class)
    private fun throwInterruptedExceptionIfTaskIsCanceled() {
        if (shouldCancel != null && shouldCancel.asBoolean) {
            throw InterruptedException()
        }
    }
}

/** Checks if a new notification should be shown. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerCheckJobService : JobService() {
    private var notificationListenerCheckInternal: NotificationListenerCheckInternal? = null
    private val jobLock = Object()

    /** We currently check if we should show a notification, the task executing the check */
    @GuardedBy("jobLock") private var addNotificationListenerNotificationIfNeededJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "Nls privacy job created")
        if (!checkNotificationListenerCheckEnabled(this)) {
            // NotificationListenerCheck not enabled. End job.
            return
        }

        notificationListenerCheckInternal =
            NotificationListenerCheckInternal(
                this,
                BooleanSupplier {
                    synchronized(jobLock) {
                        val job = addNotificationListenerNotificationIfNeededJob
                        return@BooleanSupplier job?.isCancelled ?: false
                    }
                }
            )
    }

    /**
     * Starts an asynchronous check if a notification listener notification should be shown.
     *
     * @param params Not used other than for interacting with job scheduling
     * @return `false` if another check is already running, or if SDK Check fails (below T)
     */
    override fun onStartJob(params: JobParameters): Boolean {
        if (DEBUG) Log.d(TAG, "Nls privacy job started")
        if (!checkNotificationListenerCheckEnabled(this)) {
            // NotificationListenerCheck not enabled. End job.
            return false
        }

        synchronized(jobLock) {
            if (addNotificationListenerNotificationIfNeededJob != null) {
                if (DEBUG) Log.d(TAG, "Job already running")
                return false
            }
            addNotificationListenerNotificationIfNeededJob =
                GlobalScope.launch(Default) {
                    notificationListenerCheckInternal
                        ?.getEnabledNotificationListenersAndNotifyIfNeeded(
                            params,
                            this@NotificationListenerCheckJobService
                        )
                        ?: jobFinished(params, true)
                }
        }
        return true
    }

    /**
     * Abort the check if still running.
     *
     * @param params ignored
     * @return false
     */
    override fun onStopJob(params: JobParameters): Boolean {
        var job: Job?
        synchronized(jobLock) {
            job =
                if (addNotificationListenerNotificationIfNeededJob == null) {
                    return false
                } else {
                    addNotificationListenerNotificationIfNeededJob
                }
        }
        job?.cancel()
        return false
    }

    fun clearJob() {
        synchronized(jobLock) { addNotificationListenerNotificationIfNeededJob = null }
    }
}

/** On boot set up a periodic job that starts checks. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SetupPeriodicNotificationListenerCheck : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!checkNotificationListenerCheckSupported()) {
            // Notification Listener Check not supported. Exit.
            return
        }

        if (isProfile(context)) {
            // Profile parent handles child profiles too.
            return
        }

        val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)
        if (jobScheduler.getPendingJob(PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID) == null) {
            val job =
                JobInfo.Builder(
                        PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID,
                        ComponentName(context, NotificationListenerCheckJobService::class.java)
                    )
                    .setPeriodic(getPeriodicCheckIntervalMillis(), getFlexForPeriodicCheckMillis())
                    .build()
            val scheduleResult = jobScheduler.schedule(job)
            if (scheduleResult != JobScheduler.RESULT_SUCCESS) {
                Log.e(
                    TAG,
                    "Could not schedule periodic notification listener check $scheduleResult"
                )
            } else if (DEBUG) {
                Log.i(TAG, "Scheduled periodic notification listener check")
            }
        }
    }
}

/** Handle the case where the notification is swiped away without further interaction. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerCheckNotificationDeleteHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!checkNotificationListenerCheckSupported()) {
            return
        }

        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)

        GlobalScope.launch(Default) {
            NotificationListenerCheckInternal(context, null).markComponentAsNotified(componentName)
        }
        if (DEBUG) {
            Log.d(
                TAG,
                "Notification listener check notification declined with component=" +
                    "${componentName.flattenToString()} , uid=$uid, sessionId=$sessionId"
            )
        }
        PermissionControllerStatsLog.write(
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER,
            uid,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__DISMISSED,
            sessionId
        )
    }
}

/** Disable a specified Notification Listener Service component */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DisableNotificationListenerComponentHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "DisableComponentHandler.onReceive $intent")
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)

        GlobalScope.launch(Default) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "DisableComponentHandler: disabling $componentName," +
                        "uid=$uid, sessionId=$sessionId"
                )
            }

            val safetyEventBuilder =
                try {
                    val notificationManager =
                        getSystemServiceSafe(context, NotificationManager::class.java)
                    disallowNlsLock.withLock {
                        notificationManager.setNotificationListenerAccessGranted(
                            componentName,
                            /* granted= */ false,
                            /* userSet= */ true
                        )
                    }

                    SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                } catch (e: Exception) {
                    Log.w(TAG, "error occurred in disabling notification listener service.", e)
                    SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                }

            val safetySourceIssueId: String? = intent.getStringExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID)
            val safetyEvent =
                safetyEventBuilder
                    .setSafetySourceIssueId(safetySourceIssueId)
                    .setSafetySourceIssueActionId(SC_NLS_DISABLE_ACTION_ID)
                    .build()

            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForComponent(componentName)
                removeFromNotifiedComponents(componentName)
                sendIssuesToSafetyCenter(safetyEvent)
            }
            PermissionControllerStatsLog.write(
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION,
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER,
                uid,
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CLICKED_CTA1,
                sessionId
            )
        }
    }

    companion object {
        private val disallowNlsLock = Mutex()
    }
}

/* A Safety Center action card for a specified component was dismissed */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerActionCardDismissalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "ActionCardDismissalReceiver.onReceive $intent")
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)

        GlobalScope.launch(Default) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "ActionCardDismissalReceiver: $componentName dismissed," +
                        "uid=$uid, sessionId=$sessionId"
                )
            }
            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForComponent(componentName)
                markComponentAsNotified(componentName)
            }
        }
        PermissionControllerStatsLog.write(
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION,
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__NOTIFICATION_LISTENER,
            uid,
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CARD_DISMISSED,
            sessionId
        )
    }
}

/**
 * If a package gets removed or the data of the package gets cleared, forget that we showed a
 * notification for it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerPackageResetHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (
            action != Intent.ACTION_PACKAGE_DATA_CLEARED &&
                action != Intent.ACTION_PACKAGE_FULLY_REMOVED
        ) {
            return
        }

        if (!checkNotificationListenerCheckEnabled(context)) {
            return
        }

        if (isProfile(context)) {
            if (DEBUG) {
                Log.d(TAG, "NotificationListenerCheck only supports parent profile")
            }
            return
        }

        val data = Preconditions.checkNotNull(intent.data)
        val pkg: String = data.schemeSpecificPart

        if (DEBUG) Log.i(TAG, "Reset $pkg")

        GlobalScope.launch(Default) {
            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForPackage(pkg)
                removeFromNotifiedComponents(pkg)
                sendIssuesToSafetyCenter()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerPrivacySource : PrivacySource {
    override val shouldProcessProfileRequest: Boolean = false

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        NotificationListenerCheckInternal(context, null).run { removeAnyNotification() }
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        if (!isNotificationListenerCheckFlagEnabled()) {
            return
        }

        val safetyRefreshEvent = getSafetyCenterEvent(refreshEvent, intent)

        NotificationListenerCheckInternal(context, null).run {
            sendIssuesToSafetyCenter(safetyRefreshEvent)
        }
    }
}

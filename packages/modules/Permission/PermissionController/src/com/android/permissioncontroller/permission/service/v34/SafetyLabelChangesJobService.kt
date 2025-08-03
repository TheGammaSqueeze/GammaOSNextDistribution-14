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

package com.android.permissioncontroller.permission.service.v34

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.DeviceConfig
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION__ACTION__DISMISSED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v34.AppDataSharingUpdatesLiveData
import com.android.permissioncontroller.permission.data.v34.LightInstallSourceInfoLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_ALWAYS
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel as SafetyLabelForPersistence
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/**
 * Runs a monthly job that performs Safety Labels-related tasks. (E.g., data policy changes
 * notification, hygiene, etc.)
 */
// TODO(b/265202443): Review support for safe cancellation of this Job. Currently this is
//  implemented by implementing `onStopJob` method and including `yield()` calls in computation
//  loops.
// TODO(b/276511043): Refactor this class into separate components
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetyLabelChangesJobService : JobService() {
    private val mutex = Mutex()
    private var detectUpdatesJob: Job? = null
    private var notificationJob: Job? = null
    private val context = this@SafetyLabelChangesJobService
    private val random = Random()

    class Receiver : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Received broadcast with intent action '${intent.action}'")
            }
            if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled(receiverContext)) {
                Log.i(LOG_TAG, "onReceive: Safety label change notifications are not enabled.")
                return
            }
            if (KotlinUtils.safetyLabelChangesJobServiceKillSwitch()) {
                Log.i(LOG_TAG, "onReceive: kill switch is set.")
                return
            }
            if (isContextInProfileUser(receiverContext)) {
                Log.i(
                    LOG_TAG,
                    "onReceive: Received broadcast in profile, not scheduling safety label" +
                        " change job"
                )
                return
            }
            if (
                intent.action != ACTION_BOOT_COMPLETED &&
                    intent.action != ACTION_SET_UP_SAFETY_LABEL_CHANGES_JOB
            ) {
                return
            }
            scheduleDetectUpdatesJob(receiverContext)
            schedulePeriodicNotificationJob(receiverContext)
        }

        private fun isContextInProfileUser(context: Context): Boolean {
            val userManager: UserManager = context.getSystemService(UserManager::class.java)!!
            return userManager.isProfile
        }
    }

    /** Handle the case where the notification is swiped away without further interaction. */
    class NotificationDeleteHandler : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            Log.d(LOG_TAG, "NotificationDeleteHandler: received broadcast")
            if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled(receiverContext)) {
                Log.i(
                    LOG_TAG,
                    "NotificationDeleteHandler: " +
                        "safety label change notifications are not enabled."
                )
                return
            }
            val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID)
            val numberOfAppUpdates = intent.getIntExtra(EXTRA_NUMBER_OF_APP_UPDATES, 0)
            logAppDataSharingUpdatesNotificationInteraction(
                sessionId,
                APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION__ACTION__DISMISSED,
                numberOfAppUpdates
            )
        }
    }

    /**
     * Called for two different jobs: the detect updates job
     * [SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID] and the notification job
     * [SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID].
     */
    override fun onStartJob(params: JobParameters): Boolean {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStartJob called for job id: ${params.jobId}")
        }
        if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled(context)) {
            Log.w(LOG_TAG, "Not starting job: safety label change notifications are not enabled.")
            return false
        }
        if (KotlinUtils.safetyLabelChangesJobServiceKillSwitch()) {
            Log.i(LOG_TAG, "Not starting job: kill switch is set.")
            return false
        }
        when (params.jobId) {
            SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID -> {
                dispatchDetectUpdatesJob(params)
                return true
            }
            SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID -> {
                dispatchNotificationJob(params)
                return true
            }
            else -> Log.w(LOG_TAG, "Unexpected job Id: ${params.jobId}")
        }
        return false
    }

    private fun dispatchDetectUpdatesJob(params: JobParameters) {
        Log.i(LOG_TAG, "Dispatching detect updates job")
        detectUpdatesJob =
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    Log.i(LOG_TAG, "Detect updates job started")
                    runDetectUpdatesJob()
                    Log.i(LOG_TAG, "Detect updates job finished successfully")
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "Detect updates job failed", e)
                    throw e
                } finally {
                    jobFinished(params, false)
                }
            }
    }

    private fun dispatchNotificationJob(params: JobParameters) {
        Log.i(LOG_TAG, "Dispatching notification job")
        notificationJob =
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    Log.i(LOG_TAG, "Notification job started")
                    runNotificationJob()
                    Log.i(LOG_TAG, "Notification job finished successfully")
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "Notification job failed", e)
                    throw e
                } finally {
                    jobFinished(params, false)
                }
            }
    }

    private suspend fun runDetectUpdatesJob() {
        mutex.withLock { recordSafetyLabelsIfMissing() }
    }

    private suspend fun runNotificationJob() {
        mutex.withLock {
            recordSafetyLabelsIfMissing()
            deleteSafetyLabelsNoLongerNeeded()
            postSafetyLabelChangedNotification()
        }
    }

    /**
     * Records safety labels for apps that may not have propagated their safety labels to
     * persistence through [SafetyLabelChangedBroadcastReceiver].
     *
     * This is done by:
     * 1. Initializing safety labels for apps that are relevant, but have no persisted safety labels
     *    yet.
     * 2. Update safety labels for apps that are relevant and have persisted safety labels, if we
     *    identify that we have missed an update for them.
     */
    private suspend fun recordSafetyLabelsIfMissing() {
        val historyFile = AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(context)
        val safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant> =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelsLastUpdatedTimes(historyFile)
        // Retrieve all installed packages that are store installed on the system and
        // that request the location permission; these are the packages that we care about for the
        // safety labels feature. The variable name does not specify all these filters for brevity.
        val packagesRequestingLocation: Set<Pair<String, UserHandle>> =
            getAllStoreInstalledPackagesRequestingLocation()

        val safetyLabelsToRecord = mutableSetOf<SafetyLabelForPersistence>()
        val packageNamesWithPersistedSafetyLabels =
            safetyLabelsLastUpdatedTimes.keys.map { it.packageName }

        // Partition relevant apps by whether we already store safety labels for them.
        val (packagesToConsiderUpdate, packagesToInitialize) =
            packagesRequestingLocation.partition { (packageName, _) ->
                packageName in packageNamesWithPersistedSafetyLabels
            }
        if (DEBUG) {
            Log.d(
                LOG_TAG,
                "recording safety labels if missing:" +
                    " packagesRequestingLocation:" +
                    " $packagesRequestingLocation, packageNamesWithPersistedSafetyLabels:" +
                    " $packageNamesWithPersistedSafetyLabels"
            )
        }
        safetyLabelsToRecord.addAll(getSafetyLabels(packagesToInitialize))
        safetyLabelsToRecord.addAll(
            getSafetyLabelsIfUpdatesMissed(packagesToConsiderUpdate, safetyLabelsLastUpdatedTimes)
        )

        AppsSafetyLabelHistoryPersistence.recordSafetyLabels(safetyLabelsToRecord, historyFile)
    }

    private suspend fun getSafetyLabels(
        packages: List<Pair<String, UserHandle>>
    ): List<SafetyLabelForPersistence> {
        val safetyLabelsToPersist = mutableListOf<SafetyLabelForPersistence>()

        for ((packageName, user) in packages) {
            yield() // cancellation point
            val safetyLabelToPersist = getSafetyLabelToPersist(Pair(packageName, user))
            if (safetyLabelToPersist != null) {
                safetyLabelsToPersist.add(safetyLabelToPersist)
            }
        }
        return safetyLabelsToPersist
    }

    private suspend fun getSafetyLabelsIfUpdatesMissed(
        packages: List<Pair<String, UserHandle>>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ): List<SafetyLabelForPersistence> {
        val safetyLabelsToPersist = mutableListOf<SafetyLabelForPersistence>()
        for ((packageName, user) in packages) {
            yield() // cancellation point

            // If safety labels are considered up-to-date, continue as there is no need to retrieve
            // the latest safety label; it was already captured.
            if (areSafetyLabelsUpToDate(Pair(packageName, user), safetyLabelsLastUpdatedTimes)) {
                continue
            }

            val safetyLabelToPersist = getSafetyLabelToPersist(Pair(packageName, user))
            if (safetyLabelToPersist != null) {
                safetyLabelsToPersist.add(safetyLabelToPersist)
            }
        }

        return safetyLabelsToPersist
    }

    /**
     * Returns whether the provided app's safety labels are up-to-date by checking that there have
     * been no app updates since the persisted safety label history was last updated.
     */
    private suspend fun areSafetyLabelsUpToDate(
        packageKey: Pair<String, UserHandle>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ): Boolean {
        val lightPackageInfo = LightPackageInfoLiveData[packageKey].getInitializedValue()
        val lastAppUpdateTime: Instant = Instant.ofEpochMilli(lightPackageInfo?.lastUpdateTime ?: 0)
        val latestSafetyLabelUpdateTime: Instant? =
            safetyLabelsLastUpdatedTimes[AppInfo(packageKey.first)]
        return latestSafetyLabelUpdateTime != null &&
            !lastAppUpdateTime.isAfter(latestSafetyLabelUpdateTime)
    }

    private suspend fun getSafetyLabelToPersist(
        packageKey: Pair<String, UserHandle>
    ): SafetyLabelForPersistence? {
        val (packageName, user) = packageKey

        // Get the context for the user in which the app is installed.
        val userContext =
            if (user == Process.myUserHandle()) {
                context
            } else {
                context.createContextAsUser(user, 0)
            }
        val appMetadataBundle: PersistableBundle =
            try {
                userContext.packageManager.getAppMetadata(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "Package $packageName not found while retrieving app metadata")
                return null
            }
        val appMetadataSafetyLabel: AppMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(appMetadataBundle) ?: return null
        val lastUpdateTime =
            Instant.ofEpochMilli(
                LightPackageInfoLiveData[packageKey].getInitializedValue()?.lastUpdateTime ?: 0
            )

        val safetyLabelForPersistence: SafetyLabelForPersistence =
            AppsSafetyLabelHistory.SafetyLabel.extractLocationSharingSafetyLabel(
                packageName,
                lastUpdateTime,
                appMetadataSafetyLabel
            )

        return safetyLabelForPersistence
    }

    /**
     * Deletes safety labels from persistence that are no longer necessary to persist.
     *
     * This is done by:
     * 1. Deleting safety labels for apps that are no longer relevant (e.g. app not installed or app
     *    not requesting location permission).
     * 2. Delete safety labels if there are multiple safety labels prior to the update period; at
     *    most one safety label is necessary to be persisted prior to the update period to determine
     *    updates to safety labels.
     */
    private suspend fun deleteSafetyLabelsNoLongerNeeded() {
        val historyFile = AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(context)
        val safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant> =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelsLastUpdatedTimes(historyFile)
        // Retrieve all installed packages that are store installed on the system and
        // that request the location permission; these are the packages that we care about for the
        // safety labels feature. The variable name does not specify all these filters for brevity.
        val packagesRequestingLocation: Set<Pair<String, UserHandle>> =
            getAllStoreInstalledPackagesRequestingLocation()

        val packageNamesWithPersistedSafetyLabels: List<String> =
            safetyLabelsLastUpdatedTimes.keys.map { appInfo -> appInfo.packageName }
        val packageNamesWithRelevantSafetyLabels: List<String> =
            packagesRequestingLocation.map { (packageName, _) -> packageName }

        val appInfosToDelete: Set<AppInfo> =
            packageNamesWithPersistedSafetyLabels
                .filter { packageName -> packageName !in packageNamesWithRelevantSafetyLabels }
                .map { packageName -> AppInfo(packageName) }
                .toSet()
        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsForApps(appInfosToDelete, historyFile)

        val updatePeriod =
            DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PRIVACY,
                DATA_SHARING_UPDATE_PERIOD_PROPERTY,
                Duration.ofDays(DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS).toMillis()
            )
        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsOlderThan(
            Instant.now().atZone(ZoneId.systemDefault()).toInstant().minusMillis(updatePeriod),
            historyFile
        )
    }

    // TODO(b/261607291): Modify this logic when we enable safety label change notifications for
    //  preinstalled apps.
    private suspend fun getAllStoreInstalledPackagesRequestingLocation():
        Set<Pair<String, UserHandle>> =
        getAllPackagesRequestingLocation().filter { isSafetyLabelSupported(it) }.toSet()

    private suspend fun getAllPackagesRequestingLocation(): Set<Pair<String, UserHandle>> =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
            .getInitializedValue(staleOk = false, forceUpdate = true)
            .keys

    private suspend fun getAllPackagesGrantedLocation(): Set<Pair<String, UserHandle>> =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
            .getInitializedValue(staleOk = false, forceUpdate = true)
            .filter { (_, appPermGroupUiInfo) -> appPermGroupUiInfo.isPermissionGranted() }
            .keys

    private fun AppPermGroupUiInfo.isPermissionGranted() =
        permGrantState in setOf(PERMS_ALLOWED_ALWAYS, PERMS_ALLOWED_FOREGROUND_ONLY)

    private suspend fun isSafetyLabelSupported(packageUser: Pair<String, UserHandle>): Boolean {
        val lightInstallSourceInfo =
            LightInstallSourceInfoLiveData[packageUser].getInitializedValue()
        return lightInstallSourceInfo.supportsSafetyLabel
    }

    private suspend fun postSafetyLabelChangedNotification() {
        val numberOfAppUpdates = getNumberOfAppsWithDataSharingChanged()
        if (numberOfAppUpdates > 0) {
            Log.i(LOG_TAG, "Showing notification: data sharing has changed")
            showNotification(numberOfAppUpdates)
        } else {
            cancelNotification()
            Log.i(LOG_TAG, "Not showing notification: data sharing has not changed")
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStopJob called for job id: ${params?.jobId}")
        }
        runBlocking {
            when (params?.jobId) {
                SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID -> {
                    Log.i(LOG_TAG, "onStopJob: cancelling detect updates job")
                    detectUpdatesJob?.cancel()
                    detectUpdatesJob = null
                }
                SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID -> {
                    Log.i(LOG_TAG, "onStopJob: cancelling notification job")
                    notificationJob?.cancel()
                    notificationJob = null
                }
                else -> Log.w(LOG_TAG, "onStopJob: unexpected job Id: ${params?.jobId}")
            }
        }
        return true
    }

    /**
     * Count the number of packages that have location granted and have location sharing updates.
     */
    private suspend fun getNumberOfAppsWithDataSharingChanged(): Int {
        val appDataSharingUpdates =
            AppDataSharingUpdatesLiveData(PermissionControllerApplication.get())
                .getInitializedValue()

        return appDataSharingUpdates
            .map { appDataSharingUpdate ->
                val locationDataSharingUpdate =
                    appDataSharingUpdate.categorySharingUpdates[CATEGORY_LOCATION]

                if (locationDataSharingUpdate == null) {
                    emptyList()
                } else {
                    val users =
                        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
                            .getUsersWithPermGrantedForApp(appDataSharingUpdate.packageName)
                    users
                }
            }
            .flatten()
            .count()
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

    private fun AppDataSharingUpdate.containsLocationCategoryUpdate() =
        categorySharingUpdates[CATEGORY_LOCATION] != null

    private fun showNotification(numberOfAppUpdates: Int) {
        var sessionId = INVALID_SESSION_ID
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }
        val context = PermissionControllerApplication.get() as Context
        val notificationManager = getSystemServiceSafe(context, NotificationManager::class.java)
        createNotificationChannel(context, notificationManager)

        val (appLabel, smallIcon, color) = KotlinUtils.getSafetyCenterNotificationResources(this)
        val smallIconCompat =
            IconCompat.createFromIcon(smallIcon)
                ?: IconCompat.createWithResource(this, R.drawable.ic_info)
        val title = context.getString(R.string.safety_label_changes_notification_title)
        val text = context.getString(R.string.safety_label_changes_notification_desc)
        var notificationBuilder =
            NotificationCompat.Builder(context, PERMISSION_REMINDER_CHANNEL_ID)
                .setColor(color)
                .setSmallIcon(smallIconCompat)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(createIntentToOpenAppDataSharingUpdates(context, sessionId))
                .setDeleteIntent(
                    createIntentToLogDismissNotificationEvent(
                        context,
                        sessionId,
                        numberOfAppUpdates
                    )
                )
        notificationBuilder.addExtras(
            Bundle().apply { putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appLabel) }
        )

        notificationManager.notify(
            SAFETY_LABEL_CHANGES_NOTIFICATION_ID,
            notificationBuilder.build()
        )

        logAppDataSharingUpdatesNotificationInteraction(
            sessionId,
            APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN,
            numberOfAppUpdates
        )
        Log.v(LOG_TAG, "Safety label change notification sent.")
    }

    private fun cancelNotification() {
        val notificationManager = getSystemServiceSafe(context, NotificationManager::class.java)
        notificationManager.cancel(SAFETY_LABEL_CHANGES_NOTIFICATION_ID)
        Log.v(LOG_TAG, "Safety label change notification cancelled.")
    }

    private fun createIntentToOpenAppDataSharingUpdates(
        context: Context,
        sessionId: Long
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createIntentToLogDismissNotificationEvent(
        context: Context,
        sessionId: Long,
        numberOfAppUpdates: Int
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationDeleteHandler::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_NUMBER_OF_APP_UPDATES, numberOfAppUpdates)
            },
            PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(
        context: Context,
        notificationManager: NotificationManager
    ) {
        val notificationChannel =
            NotificationChannel(
                PERMISSION_REMINDER_CHANNEL_ID,
                context.getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_LOW
            )

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        private val LOG_TAG = SafetyLabelChangesJobService::class.java.simpleName
        private const val DEBUG = true

        private const val ACTION_SET_UP_SAFETY_LABEL_CHANGES_JOB =
            "com.android.permissioncontroller.action.SET_UP_SAFETY_LABEL_CHANGES_JOB"
        private const val EXTRA_NUMBER_OF_APP_UPDATES =
            "com.android.permissioncontroller.extra.NUMBER_OF_APP_UPDATES"

        private const val DATA_SHARING_UPDATE_PERIOD_PROPERTY = "data_sharing_update_period_millis"
        private const val DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS: Long = 30

        private fun scheduleDetectUpdatesJob(context: Context) {
            try {
                val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)

                if (
                    jobScheduler.getPendingJob(SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID) != null
                ) {
                    Log.i(LOG_TAG, "Not scheduling detect updates job: already scheduled.")
                    return
                }

                val job =
                    JobInfo.Builder(
                            SAFETY_LABEL_CHANGES_DETECT_UPDATES_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java)
                        )
                        .setRequiresDeviceIdle(
                            KotlinUtils.runSafetyLabelChangesJobOnlyWhenDeviceIdle()
                        )
                        .build()
                val result = jobScheduler.schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.w(LOG_TAG, "Detect updates job not scheduled, result code: $result")
                } else {
                    Log.i(LOG_TAG, "Detect updates job scheduled successfully.")
                }
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to schedule detect updates job", e)
                throw e
            }
        }

        private fun schedulePeriodicNotificationJob(context: Context) {
            try {
                val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)
                if (
                    jobScheduler.getPendingJob(SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID) !=
                        null
                ) {
                    Log.i(LOG_TAG, "Not scheduling notification job: already scheduled.")
                    return
                }

                val job =
                    JobInfo.Builder(
                            SAFETY_LABEL_CHANGES_PERIODIC_NOTIFICATION_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java)
                        )
                        .setRequiresDeviceIdle(
                            KotlinUtils.runSafetyLabelChangesJobOnlyWhenDeviceIdle()
                        )
                        .setPeriodic(KotlinUtils.getSafetyLabelChangesJobIntervalMillis())
                        .setPersisted(true)
                        .build()
                val result = jobScheduler.schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.w(LOG_TAG, "Notification job not scheduled, result code: $result")
                } else {
                    Log.i(LOG_TAG, "Notification job scheduled successfully.")
                }
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to schedule notification job", e)
                throw e
            }
        }

        private fun logAppDataSharingUpdatesNotificationInteraction(
            sessionId: Long,
            interactionType: Int,
            numberOfAppUpdates: Int
        ) {
            PermissionControllerStatsLog.write(
                APP_DATA_SHARING_UPDATES_NOTIFICATION_INTERACTION,
                sessionId,
                interactionType,
                numberOfAppUpdates
            )
            Log.v(
                LOG_TAG,
                "Notification interaction occurred with" +
                    " sessionId=$sessionId" +
                    " action=$interactionType" +
                    " numberOfAppUpdates=$numberOfAppUpdates"
            )
        }
    }
}

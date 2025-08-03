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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.UserHandle
import android.permission.PermissionManager
import android.text.BidiFormatter
import androidx.annotation.VisibleForTesting
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_REMINDER_NOTIFICATION_INTERACTED__RESULT__NOTIFICATION_PRESENTED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.auto.AutoReviewPermissionDecisionsFragment
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.permission.utils.Utils
import java.util.Random

/**
 * Service that collects permissions decisions made while driving and when the vehicle is no longer
 * in a UX-restricted state shows a notification reminding the user of their decisions.
 */
class DrivingDecisionReminderService : Service() {

    /** Information needed to show a reminder about a permission decisions. */
    data class PermissionReminder(
        val packageName: String,
        val permissionGroup: String,
        val user: UserHandle
    )

    private var scheduled = false
    private var carUxRestrictionsManager: CarUxRestrictionsManager? = null
    private val permissionReminders: MutableSet<PermissionReminder> = mutableSetOf()
    private var car: Car? = null
    private var sessionId = Constants.INVALID_SESSION_ID

    companion object {
        private const val LOG_TAG = "DrivingDecisionReminderService"

        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PERMISSION_GROUP = "permission_group"
        const val EXTRA_USER = "user"

        /**
         * Create an intent to launch [DrivingDecisionReminderService], including information about
         * the permission decision to reminder the user about.
         *
         * @param context application context
         * @param packageName package name of app effected by the permission decision
         * @param permissionGroup permission group for the permission decision
         * @param user user that made the permission decision
         */
        fun createIntent(
            context: Context,
            packageName: String,
            permissionGroup: String,
            user: UserHandle
        ): Intent {
            val intent = Intent(context, DrivingDecisionReminderService::class.java)
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(EXTRA_PERMISSION_GROUP, permissionGroup)
            intent.putExtra(EXTRA_USER, user)
            return intent
        }

        /**
         * Starts the [DrivingDecisionReminderService] if the vehicle currently requires distraction
         * optimization.
         */
        fun startServiceIfCurrentlyRestricted(
            context: Context,
            packageName: String,
            permGroupName: String
        ) {
            Car.createCar(context, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) {
                car: Car,
                ready: Boolean ->
                // just give up if we can't connect to the car
                if (ready) {
                    val restrictionsManager =
                        car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                            as CarUxRestrictionsManager?
                    if (restrictionsManager != null) {
                        val currentCarUxRestrictions = restrictionsManager.currentCarUxRestrictions
                        if (currentCarUxRestrictions != null) {
                            if (currentCarUxRestrictions.isRequiresDistractionOptimization) {
                                context.startService(
                                    createIntent(
                                        context,
                                        packageName,
                                        permGroupName,
                                        Process.myUserHandle()
                                    )
                                )
                            }
                        } else {
                            DumpableLog.e(
                                LOG_TAG,
                                "Reminder service not created because CarUxRestrictions is null"
                            )
                        }
                    } else {
                        DumpableLog.e(
                            LOG_TAG,
                            "Reminder service not created because CarUxRestrictionsManager is null"
                        )
                    }
                }
                car.disconnect()
            }
        }

        fun cancelNotification(context: Context) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)!!
            notificationManager.cancel(
                DrivingDecisionReminderService::class.java.simpleName,
                Constants.PERMISSION_DECISION_REMINDER_NOTIFICATION_ID
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val decisionReminder = parseStartIntent(intent) ?: return START_NOT_STICKY
        permissionReminders.add(decisionReminder)
        if (scheduled) {
            DumpableLog.d(LOG_TAG, "Start service - reminder notification already scheduled")
            return START_STICKY
        }
        scheduleNotificationForUnrestrictedState()
        scheduled = true
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = Random().nextLong()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        car?.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun scheduleNotificationForUnrestrictedState() {
        Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) {
            createdCar: Car?,
            ready: Boolean ->
            car = createdCar
            if (ready) {
                onCarReady()
            } else {
                DumpableLog.w(
                    LOG_TAG,
                    "Car service disconnected, no notification will be scheduled"
                )
                stopSelf()
            }
        }
    }

    private fun onCarReady() {
        carUxRestrictionsManager =
            car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        DumpableLog.d(LOG_TAG, "Registering UX restriction listener")
        carUxRestrictionsManager?.registerListener { restrictions ->
            if (!restrictions.isRequiresDistractionOptimization) {
                DumpableLog.d(
                    LOG_TAG,
                    "UX restrictions no longer required - showing reminder notification"
                )
                showRecentGrantDecisionsPostDriveNotification()
                stopSelf()
            }
        }
    }

    private fun parseStartIntent(intent: Intent?): PermissionReminder? {
        if (
            intent == null ||
                !intent.hasExtra(EXTRA_PACKAGE_NAME) ||
                !intent.hasExtra(EXTRA_PERMISSION_GROUP) ||
                !intent.hasExtra(EXTRA_USER)
        ) {
            DumpableLog.e(LOG_TAG, "Missing extras from intent $intent")
            return null
        }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val permissionGroup = intent.getStringExtra(EXTRA_PERMISSION_GROUP)
        val user = intent.getParcelableExtra<UserHandle>(EXTRA_USER)
        return PermissionReminder(packageName!!, permissionGroup!!, user!!)
    }

    @VisibleForTesting
    fun showRecentGrantDecisionsPostDriveNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)!!

        val permissionReminderChannel =
            NotificationChannel(
                Constants.PERMISSION_REMINDER_CHANNEL_ID,
                getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_HIGH
            )
        notificationManager.createNotificationChannel(permissionReminderChannel)

        notificationManager.notify(
            DrivingDecisionReminderService::class.java.simpleName,
            Constants.PERMISSION_DECISION_REMINDER_NOTIFICATION_ID,
            createNotification(createNotificationTitle(), createNotificationContent())
        )

        logNotificationPresented()
    }

    private fun createNotificationTitle(): String {
        return applicationContext.getString(R.string.post_drive_permission_decision_reminder_title)
    }

    @VisibleForTesting
    fun createNotificationContent(): String {
        val packageLabels: MutableList<String> = mutableListOf()
        val permissionGroupNames: MutableList<String> = mutableListOf()
        for (permissionReminder in permissionReminders) {
            val packageLabel =
                getLabelForPackage(permissionReminder.packageName, permissionReminder.user)
            val permissionGroupLabel =
                getPermGroupLabel(applicationContext, permissionReminder.permissionGroup).toString()
            packageLabels.add(packageLabel)
            permissionGroupNames.add(permissionGroupLabel)
        }
        val packageLabelsDistinct = packageLabels.distinct()
        val permissionGroupNamesDistinct = permissionGroupNames.distinct()
        return if (packageLabelsDistinct.size > 1) {
            StringUtils.getIcuPluralsString(
                applicationContext,
                R.string.post_drive_permission_decision_reminder_summary_multi_apps,
                (packageLabels.size - 1),
                packageLabelsDistinct[0]
            )
        } else if (permissionGroupNamesDistinct.size == 2) {
            getString(
                R.string.post_drive_permission_decision_reminder_summary_1_app_2_permissions,
                packageLabelsDistinct[0],
                permissionGroupNamesDistinct[0],
                permissionGroupNamesDistinct[1]
            )
        } else if (permissionGroupNamesDistinct.size > 2) {
            getString(
                R.string.post_drive_permission_decision_reminder_summary_1_app_multi_permission,
                permissionGroupNamesDistinct.size,
                packageLabelsDistinct[0]
            )
        } else {
            getString(
                R.string.post_drive_permission_decision_reminder_summary_1_app_1_permission,
                packageLabelsDistinct[0],
                permissionGroupNamesDistinct[0]
            )
        }
    }

    @VisibleForTesting
    fun getLabelForPackage(packageName: String, user: UserHandle): String {
        return BidiFormatter.getInstance()
            .unicodeWrap(getPackageLabel(application, packageName, user))
    }

    private fun createNotification(title: String, body: String): Notification {
        val clickIntent =
            Intent(PermissionManager.ACTION_REVIEW_PERMISSION_DECISIONS).apply {
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(
                    AutoReviewPermissionDecisionsFragment.EXTRA_SOURCE,
                    AutoReviewPermissionDecisionsFragment.EXTRA_SOURCE_NOTIFICATION
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                clickIntent,
                PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val settingsIcon =
            KotlinUtils.getSettingsIcon(
                application,
                permissionReminders.first().user,
                applicationContext.packageManager
            )

        val b =
            Notification.Builder(this, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_settings_24dp)
                .setLargeIcon(settingsIcon)
                .setColor(getColor(android.R.color.system_notification_accent_color))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addExtras(
                    Bundle().apply {
                        putBoolean(Constants.NOTIFICATION_EXTRA_USE_LAUNCHER_ICON, false)
                    }
                )
                // Auto doesn't show icons for actions
                .addAction(
                    Notification.Action.Builder(
                            /* icon= */ null,
                            getString(R.string.go_to_settings),
                            pendingIntent
                        )
                        .build()
                )
        Utils.getSettingsLabelForNotifications(applicationContext.packageManager)?.let { label ->
            val extras = Bundle()
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, label.toString())
            b.addExtras(extras)
        }
        return b.build()
    }

    private fun logNotificationPresented() {
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.PERMISSION_REMINDER_NOTIFICATION_INTERACTED,
            sessionId,
            PERMISSION_REMINDER_NOTIFICATION_INTERACTED__RESULT__NOTIFICATION_PRESENTED
        )
    }
}

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

package com.android.server.healthconnect.migration.notification;

import static com.android.server.healthconnect.migration.MigrationConstants.HC_PACKAGE_NAME_CONFIG_NAME;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_APP_UPDATE_NEEDED;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_CANCELLED;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_COMPLETE;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_IN_PROGRESS;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_MORE_SPACE_NEEDED;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_PAUSED;
import static com.android.server.healthconnect.migration.notification.MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_RESUME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Binder;

import androidx.annotation.VisibleForTesting;

/**
 * A factory for creating Health Connect Migration notifications.
 *
 * @hide
 */
public class MigrationNotificationFactory {
    private final Context mContext;
    private final HealthConnectResourcesContext mResContext;
    private Icon mAppIcon;

    // String names used to fetch resources
    private static final String MIGRATION_IN_PROGRESS_NOTIFICATION_TITLE =
            "migration_in_progress_notification_title";
    private static final String MIGRATION_APP_UPDATE_NEEDED_NOTIFICATION_TITLE =
            "migration_app_update_needed_notification_title";
    private static final String MIGRATION_MODULE_UPDATE_NEEDED_NOTIFICATION_TITLE =
            "migration_module_update_needed_notification_title";
    private static final String MIGRATION_UPDATE_NEEDED_NOTIFICATION_CONTENT =
            "migration_update_needed_notification_content";
    private static final String MIGRATION_UPDATE_NEEDED_NOTIFICATION_ACTION =
            "migration_update_needed_notification_action";
    private static final String MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TITLE =
            "migration_more_space_needed_notification_title";
    private static final String MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_CONTENT =
            "migration_more_space_needed_notification_content";
    private static final String MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TRY_AGAIN_ACTION =
            "try_again_button";
    private static final String MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_FREE_UP_SPACE_ACTION =
            "free_up_space_button";

    private static final String MIGRATION_PAUSED_NOTIFICATION_TITLE =
            "migration_paused_notification_title";
    private static final String MIGRATION_PAUSED_NOTIFICATION_CONTENT =
            "migration_paused_notification_content";
    private static final String MIGRATION_PAUSED_NOTIFICATION_ACTION = "resume_button";

    private static final String RESUME_MIGRATION_NOTIFICATION_TITLE =
            "resume_migration_notification_title";
    private static final String RESUME_MIGRATION_NOTIFICATION_CONTENT =
            "resume_migration_notification_content";
    private static final String RESUME_MIGRATION_NOTIFICATION_ACTION =
            "resume_migration_banner_button";

    private static final String MIGRATION_NOT_COMPLETE_NOTIFICATION_TITLE =
            "migration_not_complete_notification_title";
    private static final String MIGRATION_NOT_COMPLETE_NOTIFICATION_ACTION =
            "migration_not_complete_notification_action";
    private static final String MIGRATION_COMPLETE_NOTIFICATION_TITLE =
            "migration_complete_notification_title";
    private static final String MIGRATION_COMPLETE_NOTIFICATION_ACTION =
            "migration_complete_notification_action";

    private static final String HEALTH_HOME_ACTION =
            "android.health.connect.action.HEALTH_HOME_SETTINGS";
    private static final String SHOW_MIGRATION_INFO_ACTION =
            "android.health.connect.action.SHOW_MIGRATION_INFO";
    private static final String SYSTEM_UPDATE_ACTION = "android.settings.SYSTEM_UPDATE_SETTINGS";
    private static final String SYSTEM_STORAGE_ACTION =
            "android.settings.INTERNAL_STORAGE_SETTINGS";
    private static final String SYSTEM_SETTINGS_FALLBACK_ACTION = "android.settings.SETTINGS";
    private static final Intent FALLBACK_INTENT = new Intent(SYSTEM_SETTINGS_FALLBACK_ACTION);

    @VisibleForTesting static final String APP_ICON_DRAWABLE_NAME = "health_connect_logo";

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public MigrationNotificationFactory(@NonNull Context context) {
        mContext = context;
        mResContext = new HealthConnectResourcesContext(mContext);
    }

    /**
     * Creates a notification based on the passed notificationType and assigns it the correct
     * channel ID.
     */
    @NonNull
    public Notification createNotification(
            @MigrationNotificationSender.MigrationNotificationType int notificationType,
            @NonNull String channelId)
            throws IllegalMigrationNotificationStateException {
        Notification notification;

        switch (notificationType) {
            case NOTIFICATION_TYPE_MIGRATION_IN_PROGRESS:
                notification = getMigrationInProgressNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_COMPLETE:
                notification = getMigrationCompleteNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_APP_UPDATE_NEEDED:
                notification = getAppUpdateNeededNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED:
                notification = getModuleUpdateNeededNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_MORE_SPACE_NEEDED:
                notification = getMoreSpaceNeededNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_PAUSED:
                notification = getMigrationPausedNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_RESUME:
                notification = getResumeMigrationNotification(channelId);
                break;
            case NOTIFICATION_TYPE_MIGRATION_CANCELLED:
                notification = getMigrationCancelledNotification(channelId);
                break;
            default:
                throw new IllegalMigrationNotificationStateException(
                        "Notification type not supported");
        }

        return notification;
    }

    /** Retrieves a string resource by name from the Health Connect resources. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    public String getStringResource(@NonNull String name) {
        return mResContext.getStringByName(name);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    private String getStringResourceWithArgs(@NonNull String name, Object... formatArgs) {
        return mResContext.getStringByNameWithArgs(name, formatArgs);
    }

    @NonNull
    private Notification getMigrationInProgressNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getHealthHomeSettingsPendingIntent();
        String notificationTitle = getStringResource(MIGRATION_IN_PROGRESS_NOTIFICATION_TITLE);

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentIntent(pendingIntent)
                        .setProgress(0, 0, true)
                        .setOngoing(true)
                        .build();

        notification.flags = Notification.FLAG_NO_CLEAR;
        return notification;
    }

    @NonNull
    private Notification getModuleUpdateNeededNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getSystemUpdatePendingIntent();

        String notificationTitle =
                getStringResource(MIGRATION_MODULE_UPDATE_NEEDED_NOTIFICATION_TITLE);
        String notificationContent =
                getStringResource(MIGRATION_UPDATE_NEEDED_NOTIFICATION_CONTENT);

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationContent)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build();

        return notification;
    }

    @NonNull
    private Notification getAppUpdateNeededNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getAppStorePendingIntent();

        String notificationTitle =
                getStringResource(MIGRATION_APP_UPDATE_NEEDED_NOTIFICATION_TITLE);
        String notificationContent =
                getStringResource(MIGRATION_UPDATE_NEEDED_NOTIFICATION_CONTENT);

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationContent)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build();

        return notification;
    }

    @NonNull
    private Notification getMoreSpaceNeededNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getSystemStoragePendingIntent();

        String notificationTitle =
                getStringResource(MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TITLE);
        // TODO (b/271440427) replace with space needed
        String notificationContent =
                getStringResourceWithArgs(
                        MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_CONTENT, "500MB");

        String notificationTryAgainAction =
                getStringResource(MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TRY_AGAIN_ACTION);
        String notificationFreeUpSpaceAction =
                getStringResource(MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_FREE_UP_SPACE_ACTION);

        Notification.Action tryAgainAction =
                new Notification.Action.Builder(
                                getAppIcon(), notificationTryAgainAction, pendingIntent)
                        .build();

        Notification.Action freeUpSpaceAction =
                new Notification.Action.Builder(
                                getAppIcon(), notificationFreeUpSpaceAction, pendingIntent)
                        .build();

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationContent)
                        .setContentIntent(pendingIntent)
                        .setActions(tryAgainAction, freeUpSpaceAction)
                        .setOngoing(true)
                        .build();

        notification.flags = Notification.FLAG_NO_CLEAR;
        return notification;
    }

    @NonNull
    private Notification getResumeMigrationNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getHealthHomeSettingsPendingIntent();

        String notificationTitle = getStringResource(RESUME_MIGRATION_NOTIFICATION_TITLE);
        // TODO (b/275685600) replace with timeout
        String notificationContent =
                getStringResourceWithArgs(RESUME_MIGRATION_NOTIFICATION_CONTENT, "1 day");

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationContent)
                        .setOngoing(true)
                        .build();

        notification.flags = Notification.FLAG_NO_CLEAR;
        return notification;
    }

    @NonNull
    private Notification getMigrationCancelledNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getHealthHomeSettingsPendingIntent();

        String notificationTitle = getStringResource(MIGRATION_NOT_COMPLETE_NOTIFICATION_TITLE);

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();

        notification.flags = Notification.FLAG_NO_CLEAR;
        return notification;
    }

    @NonNull
    private Notification getMigrationCompleteNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getHealthHomeSettingsPendingIntent();
        String notificationTitle = getStringResource(MIGRATION_COMPLETE_NOTIFICATION_TITLE);

        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();

        notification.flags = Notification.FLAG_NO_CLEAR;
        return notification;
    }

    @NonNull
    private Notification getMigrationPausedNotification(@NonNull String channelId) {
        PendingIntent pendingIntent = getMigrationInfoPendingIntent();
        String notificationTitle = getStringResource(MIGRATION_PAUSED_NOTIFICATION_TITLE);
        String notificationContent = getStringResource(MIGRATION_PAUSED_NOTIFICATION_CONTENT);
        Notification notification =
                new Notification.Builder(mContext, channelId)
                        .setSmallIcon(getAppIcon())
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationContent)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build();

        return notification;
    }

    @Nullable
    private PendingIntent getPendingIntent(@NonNull Intent intent) {
        // This call requires Binder identity to be cleared for getIntentSender() to be allowed to
        // send as another package.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Nullable
    private PendingIntent getMigrationInfoPendingIntent() {
        Intent intent = new Intent(SHOW_MIGRATION_INFO_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        if (result == null) {
            return getPendingIntent(FALLBACK_INTENT);
        }
        return getPendingIntent(intent);
    }

    @Nullable
    private PendingIntent getHealthHomeSettingsPendingIntent() {
        Intent intent = new Intent(HEALTH_HOME_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        if (result == null) {
            return getPendingIntent(FALLBACK_INTENT);
        }
        return getPendingIntent(intent);
    }

    @Nullable
    private PendingIntent getSystemUpdatePendingIntent() {
        Intent intent = new Intent(SYSTEM_UPDATE_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        if (result == null) {
            return getPendingIntent(FALLBACK_INTENT);
        }
        return getPendingIntent(intent);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Nullable
    private PendingIntent getAppStorePendingIntent() {
        String dataMigratorPackageName =
                mContext.getString(
                        mContext.getResources()
                                .getIdentifier(HC_PACKAGE_NAME_CONFIG_NAME, null, null));
        Intent intent = IntentsUtil.createAppStoreIntent(mContext, dataMigratorPackageName);
        return getPendingIntent(intent);
    }

    @Nullable
    private PendingIntent getSystemStoragePendingIntent() {
        Intent intent = new Intent(SYSTEM_STORAGE_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        if (result == null) {
            return getPendingIntent(FALLBACK_INTENT);
        }
        return getPendingIntent(intent);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @VisibleForTesting
    @Nullable
    Icon getAppIcon() {
        // Caches the first valid appIcon
        if (mAppIcon == null) {
            mAppIcon = mResContext.getIconByDrawableName(APP_ICON_DRAWABLE_NAME);
        }
        return mAppIcon;
    }

    /** Thrown when an illegal notification state is detected. */
    public static final class IllegalMigrationNotificationStateException extends Exception {
        public IllegalMigrationNotificationStateException(String message) {
            super(message);
        }
    }

    @VisibleForTesting
    public static String[] getNotificationStringResources() {
        return new String[] {
            MIGRATION_IN_PROGRESS_NOTIFICATION_TITLE,
            MIGRATION_APP_UPDATE_NEEDED_NOTIFICATION_TITLE,
            MIGRATION_MODULE_UPDATE_NEEDED_NOTIFICATION_TITLE,
            MIGRATION_UPDATE_NEEDED_NOTIFICATION_CONTENT,
            MIGRATION_UPDATE_NEEDED_NOTIFICATION_ACTION,
            MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TITLE,
            MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_CONTENT,
            MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_TRY_AGAIN_ACTION,
            MIGRATION_MORE_SPACE_NEEDED_NOTIFICATION_FREE_UP_SPACE_ACTION,
            MIGRATION_PAUSED_NOTIFICATION_TITLE,
            MIGRATION_PAUSED_NOTIFICATION_CONTENT,
            MIGRATION_PAUSED_NOTIFICATION_ACTION,
            RESUME_MIGRATION_NOTIFICATION_TITLE,
            RESUME_MIGRATION_NOTIFICATION_CONTENT,
            RESUME_MIGRATION_NOTIFICATION_ACTION,
            MIGRATION_NOT_COMPLETE_NOTIFICATION_TITLE,
            MIGRATION_NOT_COMPLETE_NOTIFICATION_ACTION,
            MIGRATION_COMPLETE_NOTIFICATION_TITLE,
            MIGRATION_COMPLETE_NOTIFICATION_ACTION
        };
    }
}

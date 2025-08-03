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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * HealthConnectNotificationSender implementation
 *
 * @hide
 */
public final class MigrationNotificationSender {

    private static final String TAG = "HealthConnectNS";

    // We use a fixed notification ID because notifications are keyed by (tag, id) and it easier
    // to differentiate our notifications using the tag
    private static final int FIXED_NOTIFICATION_ID = 9876;
    // We use the same tag across notification because we want at most one notification
    // about migration active at any point in time, we we effectively just update its content
    private static final String NOTIFICATION_TAG = "HealthConnectTag";
    private static final String CHANNEL_ID = "healthconnect-channel";
    private static final String CHANNEL_GROUP_ID = "healthconnect-channel-group";
    private static final String CHANNEL_NAME_RESOURCE = "app_label";

    private final Context mContext;
    private final MigrationNotificationFactory mNotificationFactory;

    public MigrationNotificationSender(@NonNull Context context) {
        mContext = context;
        mNotificationFactory = new MigrationNotificationFactory(mContext);
    }

    /** Sends a notification to the current user based on the notification type. */
    public void sendNotification(
            @MigrationNotificationType int notificationType, @NonNull UserHandle userHandle) {
        if (!HealthConnectDeviceConfigManager.getInitialisedInstance()
                .areMigrationNotificationsEnabled()) {
            return;
        }
        createNotificationChannel(userHandle);
        try {
            Notification notification =
                    mNotificationFactory.createNotification(notificationType, CHANNEL_ID);

            NotificationManager notificationManager = getNotificationManagerForUser(userHandle);
            notifyFromSystem(notificationManager, notification);

        } catch (MigrationNotificationFactory.IllegalMigrationNotificationStateException ignored) {
            // Do not send any notification
        }
    }

    /** Cancels all Health Connect notifications. */
    public void clearNotifications(@NonNull UserHandle userHandle) {
        NotificationManager notificationManager = getNotificationManagerForUser(userHandle);
        cancelFromSystem(notificationManager);
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @Nullable
    private NotificationManager getNotificationManagerForUser(@NonNull UserHandle userHandle) {
        Context contextAsUser = mContext.createContextAsUser(userHandle, 0);
        return contextAsUser.getSystemService(NotificationManager.class);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void notifyFromSystem(
            @Nullable NotificationManager notificationManager, @NonNull Notification notification) {
        // This call is needed to send a notification from the system and this also grants the
        // necessary POST_NOTIFICATIONS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            // We use the same (tag, id)
            notificationManager.notify(NOTIFICATION_TAG, FIXED_NOTIFICATION_ID, notification);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to send system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void cancelFromSystem(@Nullable NotificationManager notificationManager) {
        final long callingId = Binder.clearCallingIdentity();
        try {
            // We use the same (tag, id)
            notificationManager.cancel(NOTIFICATION_TAG, FIXED_NOTIFICATION_ID);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to cancel system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void createNotificationChannel(@NonNull UserHandle userHandle) {

        final String channelGroupName =
                mNotificationFactory.getStringResource(CHANNEL_NAME_RESOURCE);
        CharSequence channelName = mNotificationFactory.getStringResource(CHANNEL_NAME_RESOURCE);

        // group def
        NotificationChannelGroup group =
                new NotificationChannelGroup(CHANNEL_GROUP_ID, channelGroupName);

        // channel def
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel notificationChannel =
                new NotificationChannel(CHANNEL_ID, channelName, importance);
        notificationChannel.setGroup(CHANNEL_GROUP_ID);
        notificationChannel.setBlockable(false);

        final long callingId = Binder.clearCallingIdentity();

        NotificationManager notificationManager = getNotificationManagerForUser(userHandle);

        try {
            notificationManager.createNotificationChannelGroup(group);
            notificationManager.createNotificationChannel(notificationChannel);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to create notification channel", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Constants used to identify migration notification types. */
    public static final int NOTIFICATION_TYPE_MIGRATION_IN_PROGRESS = 0;

    public static final int NOTIFICATION_TYPE_MIGRATION_COMPLETE = 1;
    public static final int NOTIFICATION_TYPE_MIGRATION_APP_UPDATE_NEEDED = 2;
    public static final int NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED = 3;
    public static final int NOTIFICATION_TYPE_MIGRATION_MORE_SPACE_NEEDED = 4;
    public static final int NOTIFICATION_TYPE_MIGRATION_PAUSED = 5;
    public static final int NOTIFICATION_TYPE_MIGRATION_RESUME = 6;
    public static final int NOTIFICATION_TYPE_MIGRATION_CANCELLED = 7;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        NOTIFICATION_TYPE_MIGRATION_IN_PROGRESS,
        NOTIFICATION_TYPE_MIGRATION_COMPLETE,
        NOTIFICATION_TYPE_MIGRATION_APP_UPDATE_NEEDED,
        NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED,
        NOTIFICATION_TYPE_MIGRATION_MORE_SPACE_NEEDED,
        NOTIFICATION_TYPE_MIGRATION_PAUSED,
        NOTIFICATION_TYPE_MIGRATION_RESUME,
        NOTIFICATION_TYPE_MIGRATION_CANCELLED,
    })
    public @interface MigrationNotificationType {}
}

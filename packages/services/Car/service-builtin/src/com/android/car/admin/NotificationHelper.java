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

package com.android.car.admin;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.car.ICarResultReceiver;
import android.car.builtin.util.Slogf;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.car.R;
import com.android.car.admin.ui.ManagedDeviceTextView;
import com.android.car.internal.NotificationHelperBase;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Objects;

// TODO(b/196947649): move this class to CarSettings or at least to some common package (not
// car-admin-ui-lib)
/**
 * Helper for notification-related tasks
 */
public final class NotificationHelper extends NotificationHelperBase {
    // TODO: Move these constants to a common place. Right now a copy of these is present in
    // CarSettings' FactoryResetActivity.
    public static final String EXTRA_FACTORY_RESET_CALLBACK = "factory_reset_callback";
    public static final int FACTORY_RESET_NOTIFICATION_ID = 42;
    public static final int NEW_USER_DISCLAIMER_NOTIFICATION_ID = 108;

    public static final String CAR_SERVICE_PACKAGE_NAME = "com.android.car";
    @VisibleForTesting
    public static final String CHANNEL_ID_DEFAULT = "channel_id_default";
    @VisibleForTesting
    public static final String CHANNEL_ID_HIGH = "channel_id_high";
    private static final boolean DEBUG = false;
    @VisibleForTesting
    static final String TAG = NotificationHelper.class.getSimpleName();

    /**
     * Creates a notification (and its notification channel) for the given importance type, setting
     * its name to be {@code Android System}.
     *
     * @param context context for showing the notification
     * @param importance notification importance. Currently only
     * {@link NotificationManager.IMPORTANCE_HIGH} is supported.
     */
    @NonNull
    public static Notification.Builder newNotificationBuilder(Context context,
            @NotificationManager.Importance int importance) {
        Objects.requireNonNull(context, "context cannot be null");

        String channelId, importanceName;
        switch (importance) {
            case NotificationManager.IMPORTANCE_DEFAULT:
                channelId = CHANNEL_ID_DEFAULT;
                importanceName = context.getString(R.string.importance_default);
                break;
            case NotificationManager.IMPORTANCE_HIGH:
                channelId = CHANNEL_ID_HIGH;
                importanceName = context.getString(R.string.importance_high);
                break;
            default:
                throw new IllegalArgumentException("Unsupported importance: " + importance);
        }
        NotificationManager notificationMgr = context.getSystemService(NotificationManager.class);
        notificationMgr.createNotificationChannel(
                new NotificationChannel(channelId, importanceName, importance));

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                context.getString(com.android.internal.R.string.android_system_label));

        return new Notification.Builder(context, channelId).addExtras(extras);
    }

    public NotificationHelper(Context context) {
        super(context);
    }

    @Override
    public void cancelNotificationAsUser(UserHandle user, int notificationId) {
        if (DEBUG) {
            Slogf.d(TAG, "Canceling notification %d for user %s", notificationId, user);
        }
        getContext().getSystemService(NotificationManager.class).cancelAsUser(TAG, notificationId,
                user);
    }

    @Override
    public void showUserDisclaimerNotification(UserHandle user) {
        // TODO(b/175057848) persist status so it's shown again if car service crashes?
        PendingIntent pendingIntent = getPendingUserDisclaimerIntent(getContext(),
                /* extraFlags= */ 0, user);

        Notification notification = NotificationHelper
                .newNotificationBuilder(getContext(), NotificationManager.IMPORTANCE_DEFAULT)
                // TODO(b/177552737): Use a better icon?
                .setSmallIcon(R.drawable.car_ic_mode)
                .setContentTitle(
                        getContext().getString(R.string.new_user_managed_notification_title))
                .setContentText(ManagedDeviceTextView.getManagedDeviceText(getContext()))
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (DEBUG) {
            Slogf.d(TAG, "Showing new managed notification (id "
                    + NEW_USER_DISCLAIMER_NOTIFICATION_ID + " on user " + user);
        }
        getContext().getSystemService(NotificationManager.class)
                .notifyAsUser(TAG, NEW_USER_DISCLAIMER_NOTIFICATION_ID,
                        notification, user);
    }

    @Override
    public void cancelUserDisclaimerNotification(UserHandle user) {
        if (DEBUG) {
            Slogf.d(TAG, "Canceling notification " + NEW_USER_DISCLAIMER_NOTIFICATION_ID
                    + " for user " + user);
        }
        getContext().getSystemService(NotificationManager.class)
                .cancelAsUser(TAG, NEW_USER_DISCLAIMER_NOTIFICATION_ID, user);
        getPendingUserDisclaimerIntent(getContext(),
                PendingIntent.FLAG_UPDATE_CURRENT, user).cancel();
    }

    /**
     * Creates and returns the PendingIntent for User Disclaimer notification.
     */
    @VisibleForTesting
    public static PendingIntent getPendingUserDisclaimerIntent(Context context, int extraFlags,
            UserHandle user) {
        return PendingIntent
                .getActivityAsUser(context, NEW_USER_DISCLAIMER_NOTIFICATION_ID,
                new Intent().setComponent(ComponentName.unflattenFromString(
                        context.getString(R.string.config_newUserDisclaimerActivity)
                )),
                PendingIntent.FLAG_IMMUTABLE | extraFlags, null, user);
    }

    @Override
    public void showResourceOveruseNotificationsAsUser(
            UserHandle user, SparseArray<String> headsUpNotificationPackagesById,
            SparseArray<String> notificationCenterPackagesById) {
        Preconditions.checkArgument(user.getIdentifier() >= 0,
                "Invalid user: %s. Must provide the user handle for a specific user.", user);

        SparseArray<SparseArray<String>> packagesByImportance = new SparseArray<>(2);
        packagesByImportance.put(NotificationManager.IMPORTANCE_HIGH,
                headsUpNotificationPackagesById);
        packagesByImportance.put(NotificationManager.IMPORTANCE_DEFAULT,
                notificationCenterPackagesById);
        showResourceOveruseNotificationsAsUser(getContext(), user, packagesByImportance);
    }

    @Override
    public void showFactoryResetNotification(ICarResultReceiver callback) {
        // The factory request is received by CarService - which runs on system user - but the
        // notification will be sent to all users.
        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());

        ComponentName factoryResetActivity = ComponentName.unflattenFromString(
                getContext().getString(R.string.config_factoryResetActivity));
        @SuppressWarnings("deprecation")
        Intent intent = new Intent()
                .setComponent(factoryResetActivity)
                .putExtra(EXTRA_FACTORY_RESET_CALLBACK, callback.asBinder());
        PendingIntent pendingIntent = PendingIntent.getActivityAsUser(getContext(),
                FACTORY_RESET_NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE,
                /* options= */ null, currentUser);

        Notification notification = NotificationHelper
                .newNotificationBuilder(getContext(), NotificationManager.IMPORTANCE_HIGH)
                .setSmallIcon(R.drawable.car_ic_warning)
                .setColor(getContext().getColor(R.color.red_warning))
                .setContentTitle(getContext().getString(R.string.factory_reset_notification_title))
                .setContentText(getContext().getString(R.string.factory_reset_notification_text))
                .setCategory(Notification.CATEGORY_CAR_WARNING)
                .setOngoing(true)
                .addAction(/* icon= */ 0,
                        getContext().getString(R.string.factory_reset_notification_button),
                        pendingIntent)
                .build();

        Slogf.i(TAG, "Showing factory reset notification on all users");
        getContext().getSystemService(NotificationManager.class)
                .notifyAsUser(TAG, FACTORY_RESET_NOTIFICATION_ID, notification, UserHandle.ALL);
    }

    private static void showResourceOveruseNotificationsAsUser(Context context, UserHandle user,
            SparseArray<SparseArray<String>> packagesByImportance) {
        PackageManager packageManager = context.getPackageManager();
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);

        CharSequence titleTemplate = context.getText(R.string.resource_overuse_notification_title);
        String textDisabledApp =
                context.getString(R.string.resource_overuse_notification_text_disabled_app);
        String actionTitlePrioritizeApp =
                context.getString(R.string.resource_overuse_notification_button_prioritize_app);
        String actionTitleCloseNotification =
                context.getString(R.string.resource_overuse_notification_button_close_app);

        for (int i = 0; i < packagesByImportance.size(); i++) {
            int importance = packagesByImportance.keyAt(i);
            SparseArray<String> packagesById = packagesByImportance.valueAt(i);
            for (int pkgIdx = 0; pkgIdx < packagesById.size(); pkgIdx++) {
                int notificationId = packagesById.keyAt(pkgIdx);
                String packageName = packagesById.valueAt(pkgIdx);

                CharSequence appName;
                try {
                    ApplicationInfo info = packageManager.getApplicationInfoAsUser(packageName,
                            /* flags= */ 0, user);
                    appName = info.loadLabel(packageManager);
                } catch (PackageManager.NameNotFoundException e) {
                    Slogf.e(TAG, e, "Package '%s' not found for user %s", packageName, user);
                    continue;
                }
                PendingIntent closeActionPendingIntent = getPendingIntent(context,
                        CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION, user,
                        packageName, notificationId);
                PendingIntent prioritizeActionPendingIntent = getPendingIntent(context,
                        CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS, user, packageName, notificationId);
                Notification notification = NotificationHelper
                        .newNotificationBuilder(context, importance)
                        .setSmallIcon(R.drawable.car_ic_warning)
                        .setContentTitle(TextUtils.expandTemplate(titleTemplate, appName))
                        .setContentText(textDisabledApp)
                        .setCategory(Notification.CATEGORY_CAR_WARNING)
                        .addAction(new Notification.Action.Builder(/* icon= */ null,
                                actionTitleCloseNotification, closeActionPendingIntent).build())
                        .addAction(new Notification.Action.Builder(/* icon= */ null,
                                actionTitlePrioritizeApp, prioritizeActionPendingIntent).build())
                        .setDeleteIntent(closeActionPendingIntent)
                        .build();

                notificationManager.notifyAsUser(TAG, notificationId, notification, user);

                if (DEBUG) {
                    Slogf.d(TAG,
                            "Sent user notification (id %d) for resource overuse for "
                                    + "user %s.\nNotification { App name: %s, Importance: %d, "
                                    + "Description: %s, Positive button text: %s, Negative button "
                                    + "text: %s }",
                            notificationId, user, appName, importance, textDisabledApp,
                            actionTitleCloseNotification, actionTitlePrioritizeApp);
                }
            }
        }
    }

    @VisibleForTesting
    static PendingIntent getPendingIntent(Context context, String action, UserHandle user,
            String packageName, int notificationId) {
        Intent intent = new Intent(action)
                .putExtra(Intent.EXTRA_USER, user)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(INTENT_EXTRA_NOTIFICATION_ID, notificationId)
                .setPackage(context.getPackageName())
                .setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        return PendingIntent.getBroadcastAsUser(context, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE, user);
    }
}

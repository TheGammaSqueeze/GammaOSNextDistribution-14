/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.IntentDrivenTestActivity;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

public class Utils {

    private static final String TAG = "CtsVerifierByodUtils";
    static final int BUGREPORT_NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "BugReport";
    static final String FILE_PROVIDER_AUTHORITY =
            "com.android.cts.verifier.managedprovisioning.fileprovider";

    static TestListItem createInteractiveTestItem(Activity activity, String id, int titleRes,
            int infoRes, ButtonInfo[] buttonInfos) {
        return TestListItem.newTest(activity, titleRes,
                id, new Intent(activity, IntentDrivenTestActivity.class)
                .putExtra(IntentDrivenTestActivity.EXTRA_ID, id)
                .putExtra(IntentDrivenTestActivity.EXTRA_TITLE, titleRes)
                .putExtra(IntentDrivenTestActivity.EXTRA_INFO, infoRes)
                .putExtra(IntentDrivenTestActivity.EXTRA_BUTTONS, buttonInfos),
                null);
    }

    static TestListItem createInteractiveTestItem(Activity activity, String id, int titleRes,
            int infoRes, ButtonInfo buttonInfo) {
        return createInteractiveTestItem(activity, id, titleRes, infoRes,
                new ButtonInfo[] { buttonInfo });
    }

    static void requestDeleteManagedProfile(Context context) {
        try {
            Intent intent = new Intent(ByodHelperActivity.ACTION_REMOVE_MANAGED_PROFILE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        catch (ActivityNotFoundException e) {
            Log.d(TAG, "requestDeleteProfileOwner: ActivityNotFoundException", e);
        }
    }

    static void provisionManagedProfile(Activity activity, ComponentName admin, int requestCode) {
        Intent sending = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        sending.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
        if (sending.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(sending, requestCode);
        } else {
            showToast(activity, R.string.provisioning_byod_disabled);
        }
    }

    static void showBugreportNotification(Context context, String msg, int notificationId) {
        NotificationManager notificationManager = getNotificationManager(context);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);
        CharSequence title = context.getString(R.string.device_owner_requesting_bugreport_tests);
        Notification notification = new Notification.Builder(context)
                .setChannelId(CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .extend(new Notification.TvExtender())
                .build();
        Log.d(TAG, "Sending notification: id=" + notificationId + ", title='" + title
                + "' text='" + msg + "'");
        notificationManager.notify(notificationId, notification);
    }

    private static NotificationManager getNotificationManager(Context context) {
        if (UserManager.isHeadlessSystemUserMode()) {
            int currentUserId = ActivityManager.getCurrentUser();
            int contextUserId = context.getUser().getIdentifier();
            if (currentUserId != contextUserId) {
                Log.d(TAG, "getNotificationManager(): using context for current user ("
                        + currentUserId + ") instead of user " + contextUserId
                        + " on headless system user mode");
                context = context.createContextAsUser(UserHandle.of(currentUserId), /* flags= */ 0);
            }
        }
        Log.d(TAG, "Returning NotificationManager for context of user " + context.getUserId());
        return context.getSystemService(NotificationManager.class);
    }

    static void showToast(Context context, int messageId) {
        CharSequence msg = context.getString(messageId);
        Log.d(TAG, "showToast(): " + msg);
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Prompts the tester to set a screen lock credential, or change it if one exists.
     *
     * An instruction dialog is shown before the tester is sent to the ChooseLockGeneric activity
     * in Settings.
     *
     * @param activity The calling activity where the result is handled
     * @param requestCode The callback request code when the lock is set
     */
    static void setScreenLock(Activity activity, int requestCode) {
        final Intent intent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.provisioning_byod)
                .setMessage(R.string.provisioning_byod_set_screen_lock_dialog_message)
                .setPositiveButton(R.string.go_button_text, (DialogInterface dialog, int which) ->
                        activity.startActivityForResult(intent, requestCode))
                .show();
    }

    /**
     * Prompts the tester to remove the current screen lock credential.
     *
     * An instruction dialog is shown before the tester is sent to the ChooseLockGeneric activity
     * in Settings.
     *
     * @param activity The calling activity
     */
    static void removeScreenLock(Activity activity) {
        final Intent intent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.provisioning_byod)
                .setMessage(R.string.provisioning_byod_remove_screen_lock_dialog_message)
                .setPositiveButton(R.string.go_button_text, (DialogInterface dialog, int which) ->
                        activity.startActivity(intent))
                .show();
    }


    /**
     * Depending on form factor, the location of enterprise info page is different.
     * The helper function opening settings can cause confusion, hence differentiating location
     * it opens based on type of device.
     *
     * @param context The calling context
     */
    static Intent getManagedSettingsIntent(Context context) {
        if (isWatch(context)) {
            return new Intent(Settings.ACTION_SETTINGS);
        } else {
            return new Intent(Settings.ACTION_SECURITY_SETTINGS);
        }
    }

    /**
     * Depending on form factor, device may not have room to show more than an icon
     * for the enterprise managed cases.
     * Returning an indicator, so the tests can be adjusted.
     *
     * @param context The calling context
     */
    static boolean isLockScreenManagedOrgNameSupported(Context context) {
        return !isWatch(context);
    }


    static boolean isLockscreenSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SECURE_LOCK_SCREEN);
    }

    static boolean isTV(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    static boolean isWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}

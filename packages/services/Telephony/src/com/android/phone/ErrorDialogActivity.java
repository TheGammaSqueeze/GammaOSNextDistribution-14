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

package com.android.phone;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.util.List;

/** Used to display an error dialog from Telephony service. */
public class ErrorDialogActivity extends Activity {

    private static final String TAG = ErrorDialogActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow()
                .addSystemFlags(
                        android.view.WindowManager.LayoutParams
                                .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        showDialog();
    }

    @Override
    public void finish() {
        super.finish();
        // Don't show the return to previous task animation to avoid showing a black screen.
        // Just dismiss the dialog and undim the previous activity immediately.
        overridePendingTransition(0, 0);
    }

    private void showDialog() {
        int managedProfileUserId =
                getManagedProfileUserId(
                        getApplicationContext(), getApplicationContext().getUserId());
        if (managedProfileUserId == UserHandle.USER_NULL) {
            Log.w(TAG, "Error dialog is only applicable to managed profile.");
            finish();
        }
        String defaultMessagesAppPackage =
                getBaseContext()
                    .getSystemService(RoleManager.class)
                    .getSmsRoleHolder(managedProfileUserId);

        Intent smsIntent = new Intent(Intent.ACTION_SENDTO)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("smsto:"));
        Intent marketIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://search?q=messages"));
        int positiveButtonText = 0;
        Intent intent = null;
        boolean showPositiveActionButton = true;
        // A messages app may not be available in the managed profile. We try to handle that
        // gracefully by redirecting to install a suitable app.
        // Failing that, we simply omit the positive action button as the user has no mechanism
        // to send the message.
        if (defaultMessagesAppPackage != null
                || canStartActivityAsUser(
                smsIntent,
                managedProfileUserId)) {
            positiveButtonText = R.string.send_from_work_profile_action_str;
            intent = smsIntent;
        } else if (canStartActivityAsUser(marketIntent, managedProfileUserId)) {
            positiveButtonText = R.string.install_messages_on_work_profile_action_str;
            intent = marketIntent;
        } else {
            showPositiveActionButton = false;
        }

        // Variable has to be effectively final to be passing into the lambda, so copying it
        // here.
        Intent finalIntent = intent;
        final DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                switchToManagedProfile(
                                        managedProfileUserId,
                                        finalIntent);
                                finish();
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                            default:
                                finish();
                        }
                    }
                };

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this)
                .setTitle(R.string.send_from_work_profile_title)
                .setMessage(R.string.send_from_work_profile_description)
                .setNegativeButton(R.string.send_from_work_profile_cancel, listener)
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                finish();
                            }
                        });
        if (showPositiveActionButton) {
            alertDialogBuilder.setPositiveButton(positiveButtonText, listener);
        }
        alertDialogBuilder.show();
    }

    private boolean canStartActivityAsUser(Intent intent, int managedProfileUserId) {
        return !this.getPackageManager()
                .queryIntentActivitiesAsUser(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0),
                        managedProfileUserId)
                .isEmpty();
    }

    private void switchToManagedProfile(int managedProfileUserId, Intent intent) {
        try {
            startActivityAsUser(intent,
                    ActivityOptions.makeOpenCrossProfileAppsAnimation().toBundle(),
                    UserHandle.of(managedProfileUserId));
        } catch (Exception e) {
            Log.e(TAG, "Failed to switch to managed profile.", e);
        }
    }

    private static int getManagedProfileUserId(Context context, int userId) {
        UserManager um = context.getSystemService(UserManager.class);
        List<UserInfo> userProfiles = um.getProfiles(userId);
        for (UserInfo uInfo : userProfiles) {
            if (uInfo.id == userId) {
                continue;
            }
            if (uInfo.isManagedProfile()) {
                return uInfo.id;
            }
        }
        return UserHandle.USER_NULL;
    }
}

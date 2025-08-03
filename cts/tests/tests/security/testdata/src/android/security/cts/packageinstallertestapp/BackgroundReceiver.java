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

package android.security.cts.packageinstallertestapp;

import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_REMOTE_CALLBACK;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.os.Bundle;
import android.os.RemoteCallback;

import java.io.IOException;
import java.util.List;

/**
 * A receiver to invoke APIs in the background.
 */
public class BackgroundReceiver extends BroadcastReceiver {
    private static final String PKG_NAME = "android.security.cts.packageinstallertestapp";
    private static final String KEY_ERROR = "key_error";
    private static final String ACTION_COMMIT_WITH_ACTIVITY_INTENT_SENDER = PKG_NAME
            + ".action.COMMIT_WITH_ACTIVITY_INTENT_SENDER";
    private static final String ACTION_COMMIT_WITH_FG_SERVICE_INTENT_SENDER = PKG_NAME
            + ".action.COMMIT_WITH_FG_SERVICE_INTENT_SENDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        final RemoteCallback remoteCallback = intent.getParcelableExtra(EXTRA_REMOTE_CALLBACK,
                RemoteCallback.class);
        final ComponentName statusReceiver = intent.getParcelableExtra(
                EXTRA_COMPONENT_NAME, ComponentName.class);
        final String action = intent.getAction();

        if (!isAppInBackground(context)) {
            sendError(remoteCallback,
                    new IllegalStateException("App is not in background"));
            return;
        }
        try {
            if (action.equals(ACTION_COMMIT_WITH_ACTIVITY_INTENT_SENDER)) {
                final IntentSender intentSender = PendingIntent.getActivity(context,
                                0 /* requestCode */,
                                new Intent().setComponent(statusReceiver),
                                PendingIntent.FLAG_IMMUTABLE)
                        .getIntentSender();
                sendInstallCommit(context, remoteCallback, intentSender);
            } else if (action.equals(ACTION_COMMIT_WITH_FG_SERVICE_INTENT_SENDER)) {
                final IntentSender intentSender = PendingIntent.getForegroundService(context,
                                0 /* requestCode */,
                                new Intent().setComponent(statusReceiver),
                                PendingIntent.FLAG_IMMUTABLE)
                        .getIntentSender();
                sendInstallCommit(context, remoteCallback, intentSender);
            } else {
                sendError(remoteCallback,
                        new IllegalArgumentException("Unknown action: " + action));
            }
        } catch (Throwable e) {
            sendError(remoteCallback, e);
        }
    }

    private static boolean isAppInBackground(Context context) {
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        final List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        final String packageName = context.getPackageName();
        final RunningAppProcessInfo appInfo = appProcesses.stream()
                .filter(app -> app.processName.equals(packageName))
                .findAny().orElse(null);
        if (appInfo != null
                && appInfo.importance >= RunningAppProcessInfo.IMPORTANCE_SERVICE) {
            return true;
        }
        return false;
    }

    private static void sendInstallCommit(Context context, RemoteCallback remoteCallback,
            IntentSender intentSender) throws IOException {
        final PackageInstaller packageInstaller =
                context.getPackageManager().getPackageInstaller();
        final int sessionId = packageInstaller.createSession(
                new SessionParams(MODE_FULL_INSTALL));
        final Session session = packageInstaller.openSession(sessionId);
        session.commit(intentSender);
        sendSuccess(remoteCallback);
    }

    private static void sendError(RemoteCallback remoteCallback, Throwable failure) {
        Bundle result = new Bundle();
        result.putSerializable(KEY_ERROR, failure);
        remoteCallback.sendResult(result);
    }

    private static void sendSuccess(RemoteCallback remoteCallback) {
        Bundle result = new Bundle();
        remoteCallback.sendResult(result);
    }
}

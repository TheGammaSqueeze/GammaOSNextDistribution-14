/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.deviceowner;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.bedstead.dpmwrapper.DeviceOwnerHelper;
import com.android.cts.devicepolicy.OperationSafetyChangedCallback;
import com.android.cts.devicepolicy.OperationSafetyChangedEvent;

public class BasicAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = BasicAdminReceiver.class.getSimpleName();

    final static String ACTION_USER_ADDED = "com.android.cts.deviceowner.action.USER_ADDED";
    final static String ACTION_USER_REMOVED = "com.android.cts.deviceowner.action.USER_REMOVED";
    final static String ACTION_USER_STARTED = "com.android.cts.deviceowner.action.USER_STARTED";
    final static String ACTION_USER_STOPPED = "com.android.cts.deviceowner.action.USER_STOPPED";
    final static String ACTION_USER_SWITCHED = "com.android.cts.deviceowner.action.USER_SWITCHED";
    final static String EXTRA_USER_HANDLE = "com.android.cts.deviceowner.extra.USER_HANDLE";
    final static String ACTION_NETWORK_LOGS_AVAILABLE =
            "com.android.cts.deviceowner.action.ACTION_NETWORK_LOGS_AVAILABLE";
    final static String EXTRA_NETWORK_LOGS_BATCH_TOKEN =
            "com.android.cts.deviceowner.extra.NETWORK_LOGS_BATCH_TOKEN";

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, BasicAdminReceiver.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DeviceOwnerHelper.runManagerMethod(this, context, intent)) return;

        String action = intent.getAction();
        Log.d(TAG, "onReceive(userId=" + context.getUserId() + "): " + action);
        super.onReceive(context, intent);
    }

    @Override
    public void onUserAdded(Context context, Intent intent, UserHandle userHandle) {
        sendUserBroadcast(context, ACTION_USER_ADDED, userHandle);
    }

    @Override
    public void onUserRemoved(Context context, Intent intent, UserHandle userHandle) {
        sendUserBroadcast(context, ACTION_USER_REMOVED, userHandle);
    }

    @Override
    public void onUserStarted(Context context, Intent intent, UserHandle userHandle) {
        sendUserBroadcast(context, ACTION_USER_STARTED, userHandle);
    }

    @Override
    public void onUserStopped(Context context, Intent intent, UserHandle userHandle) {
        sendUserBroadcast(context, ACTION_USER_STOPPED, userHandle);
    }

    @Override
    public void onUserSwitched(Context context, Intent intent, UserHandle userHandle) {
        sendUserBroadcast(context, ACTION_USER_SWITCHED, userHandle);
    }

    @Override
    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        Log.d(TAG, "onNetworkLogsAvailable() on user " + context.getUserId()
                + ": token=" + batchToken + ", count=" + networkLogsCount);
        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
        // send the broadcast, the rest of the test happens in NetworkLoggingTest
        Intent batchIntent = new Intent(ACTION_NETWORK_LOGS_AVAILABLE);
        batchIntent.putExtra(EXTRA_NETWORK_LOGS_BATCH_TOKEN, batchToken);

        DeviceOwnerHelper.sendBroadcastToTestAppReceivers(context, batchIntent);
    }

    @Override
    public void onOperationSafetyStateChanged(Context context, int reason, boolean isSafe) {
        OperationSafetyChangedEvent event = new OperationSafetyChangedEvent(reason, isSafe);
        Log.d(TAG, "onOperationSafetyStateChanged() on user " + context.getUserId() + ": " + event);

        Intent intent = OperationSafetyChangedCallback.intentFor(event);

        DeviceOwnerHelper.sendBroadcastToTestAppReceivers(context, intent);
    }

    private void sendUserBroadcast(Context context, String action, UserHandle userHandle) {
        Log.d(TAG, "sendUserBroadcast(): action=" + action + ", user=" + userHandle);
        Intent intent = new Intent(action).putExtra(EXTRA_USER_HANDLE, userHandle);

        // NOTE: broadcast locally as user-related tests on headless system user always run on
        // system user, as current user is stopped on switch.
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}

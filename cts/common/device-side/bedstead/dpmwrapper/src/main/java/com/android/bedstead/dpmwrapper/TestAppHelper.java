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
package com.android.bedstead.dpmwrapper;

import static com.android.bedstead.dpmwrapper.Utils.isCurrentUserOnHeadlessSystemUser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Helper class used by the test apps.
 */
public final class TestAppHelper {

    private static final String TAG = TestAppHelper.class.getSimpleName();

    /**
     * Called by test case to register a {@link BrodcastReceiver} to receive intents sent by the
     * device owner's {@link android.app.admin.DeviceAdminReceiver}.
     */
    public static void registerTestCaseReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter) {
        registerTestCaseReceiver(context, receiver, filter, /* forDeviceOwner= */ true);
    }

    /**
     * Called by test case to register a {@link BrodcastReceiver} to receive intents sent by the
     * device owner's {@link android.app.admin.DeviceAdminReceiver}.
     */
    public static void registerTestCaseReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter, boolean forDeviceOwner) {
        if (forDeviceOwner && isCurrentUserOnHeadlessSystemUser(context)) {
            TestAppCallbacksReceiver.registerReceiver(context, receiver, filter);
            return;
        }
        Log.d(TAG, "Registering " + receiver + " to receive " + Utils.toString(filter)
                + " locally on user " + context.getUserId());
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    /**
     * Called by test case to unregister a {@link BrodcastReceiver} that receive intents sent by the
     * device owner's {@link android.app.admin.DeviceAdminReceiver}.
     */
    public static void unregisterTestCaseReceiver(Context context, BroadcastReceiver receiver) {
        unregisterTestCaseReceiver(context, receiver, /* forDeviceOwner= */ true);
    }

    /**
     * Called by test case to unregister a {@link BrodcastReceiver} that receive intents sent by the
     * device owner's {@link android.app.admin.DeviceAdminReceiver}.
     */
    public static void unregisterTestCaseReceiver(Context context, BroadcastReceiver receiver,
            boolean forDeviceOwner) {
        if (forDeviceOwner && isCurrentUserOnHeadlessSystemUser(context)) {
            TestAppCallbacksReceiver.unregisterReceiver(context, receiver);
            return;
        }
        Log.d(TAG, "Unegistering " + receiver + " locally on user " + context.getUserId());
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }

    private TestAppHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}

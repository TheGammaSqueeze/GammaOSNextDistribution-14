/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.net.hostside.app2;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.cts.net.hostside.INetworkStateObserver;

public final class Common {

    static final String TAG = "CtsNetApp2";

    // Constants below must match values defined on app's
    // AbstractRestrictBackgroundNetworkTestCase.java
    static final String MANIFEST_RECEIVER = "ManifestReceiver";
    static final String DYNAMIC_RECEIVER = "DynamicReceiver";

    static final String ACTION_RECEIVER_READY =
            "com.android.cts.net.hostside.app2.action.RECEIVER_READY";
    static final String ACTION_FINISH_ACTIVITY =
            "com.android.cts.net.hostside.app2.action.FINISH_ACTIVITY";
    static final String ACTION_FINISH_JOB =
            "com.android.cts.net.hostside.app2.action.FINISH_JOB";
    static final String ACTION_SHOW_TOAST =
            "com.android.cts.net.hostside.app2.action.SHOW_TOAST";
    // Copied from com.android.server.net.NetworkPolicyManagerService class
    static final String ACTION_SNOOZE_WARNING =
            "com.android.server.net.action.SNOOZE_WARNING";

    static final String NOTIFICATION_TYPE_CONTENT = "CONTENT";
    static final String NOTIFICATION_TYPE_DELETE = "DELETE";
    static final String NOTIFICATION_TYPE_FULL_SCREEN = "FULL_SCREEN";
    static final String NOTIFICATION_TYPE_BUNDLE = "BUNDLE";
    static final String NOTIFICATION_TYPE_ACTION = "ACTION";
    static final String NOTIFICATION_TYPE_ACTION_BUNDLE = "ACTION_BUNDLE";
    static final String NOTIFICATION_TYPE_ACTION_REMOTE_INPUT = "ACTION_REMOTE_INPUT";

    static final String TEST_PKG = "com.android.cts.net.hostside";
    static final String KEY_NETWORK_STATE_OBSERVER = TEST_PKG + ".observer";
    static final String KEY_SKIP_VALIDATION_CHECKS = TEST_PKG + ".skip_validation_checks";

    static final int TYPE_COMPONENT_ACTIVTY = 0;
    static final int TYPE_COMPONENT_FOREGROUND_SERVICE = 1;
    static final int TYPE_COMPONENT_EXPEDITED_JOB = 2;

    static int getUid(Context context) {
        final String packageName = context.getPackageName();
        try {
            return context.getPackageManager().getPackageUid(packageName, 0);
        } catch (NameNotFoundException e) {
            throw new IllegalStateException("Could not get UID for " + packageName, e);
        }
    }

    private static boolean validateComponentState(Context context, int componentType,
            INetworkStateObserver observer) throws RemoteException {
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        switch (componentType) {
            case TYPE_COMPONENT_ACTIVTY: {
                final int procState = activityManager.getUidProcessState(Process.myUid());
                if (procState != ActivityManager.PROCESS_STATE_TOP) {
                    observer.onNetworkStateChecked(
                            INetworkStateObserver.RESULT_ERROR_UNEXPECTED_PROC_STATE,
                            "Unexpected procstate: " + procState);
                    return false;
                }
                return true;
            }
            case TYPE_COMPONENT_FOREGROUND_SERVICE: {
                final int procState = activityManager.getUidProcessState(Process.myUid());
                if (procState != ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE) {
                    observer.onNetworkStateChecked(
                            INetworkStateObserver.RESULT_ERROR_UNEXPECTED_PROC_STATE,
                            "Unexpected procstate: " + procState);
                    return false;
                }
                return true;
            }
            case TYPE_COMPONENT_EXPEDITED_JOB: {
                final int capabilities = activityManager.getUidProcessCapabilities(Process.myUid());
                if ((capabilities
                        & ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK) == 0) {
                    observer.onNetworkStateChecked(
                            INetworkStateObserver.RESULT_ERROR_UNEXPECTED_CAPABILITIES,
                            "Unexpected capabilities: " + capabilities);
                    return false;
                }
                return true;
            }
            default: {
                observer.onNetworkStateChecked(INetworkStateObserver.RESULT_ERROR_OTHER,
                        "Unknown component type: " + componentType);
                return false;
            }
        }
    }

    static void notifyNetworkStateObserver(Context context, Intent intent, int componentType) {
        if (intent == null) {
            return;
        }
        final Bundle extras = intent.getExtras();
        notifyNetworkStateObserver(context, extras, componentType);
    }

    static void notifyNetworkStateObserver(Context context, Bundle extras, int componentType) {
        if (extras == null) {
            return;
        }
        final INetworkStateObserver observer = INetworkStateObserver.Stub.asInterface(
                extras.getBinder(KEY_NETWORK_STATE_OBSERVER));
        if (observer != null) {
            try {
                final boolean skipValidation = extras.getBoolean(KEY_SKIP_VALIDATION_CHECKS);
                if (!skipValidation && !validateComponentState(context, componentType, observer)) {
                    return;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error occurred while informing the validation result: " + e);
            }
            AsyncTask.execute(() -> {
                try {
                    observer.onNetworkStateChecked(
                            INetworkStateObserver.RESULT_SUCCESS_NETWORK_STATE_CHECKED,
                            MyBroadcastReceiver.checkNetworkStatus(context));
                } catch (RemoteException e) {
                    Log.e(TAG, "Error occurred while notifying the observer: " + e);
                }
            });
        }
    }
}

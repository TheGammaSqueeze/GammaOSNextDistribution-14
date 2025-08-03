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
package com.android.cts.devicepolicy;

import static android.app.admin.DevicePolicyManager.operationToString;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.bedstead.dpmwrapper.TestAppHelper;

import junit.framework.AssertionFailedError;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

//TODO(b/174859111): move to automotive-only section
/**
 * Helper class used by test apps to get the safety event received by the device owner's
 * {@link android.app.admin.DeviceAdminReceiver}.
 */
public final class OperationSafetyChangedCallback {

    private static final String TAG = OperationSafetyChangedCallback.class.getSimpleName();

    private static final String ACTION_STATE_CHANGED = "operation_safety_state_changed";
    private static final String EXTRA_REASON = "reason";
    private static final String EXTRA_IS_SAFE = "is_safe";

    private static final long TIMEOUT_MS = 50_000;

    private final LinkedBlockingQueue<OperationSafetyChangedEvent> mEvents =
            new LinkedBlockingQueue<>();

    private final boolean mForDeviceOwner;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "onReceive(): " + intent);
            if (!ACTION_STATE_CHANGED.equals(action)) {
                Log.e(TAG, "Invalid action " + action + " on intent " + intent);
                return;
            }
            if (!intent.hasExtra(EXTRA_REASON)) {
                Log.e(TAG, "No " + EXTRA_REASON + " extra on intent " + intent);
                return;
            }
            if (!intent.hasExtra(EXTRA_IS_SAFE)) {
                Log.e(TAG, "No " + EXTRA_IS_SAFE + " extra on intent " + intent);
                return;
            }
            OperationSafetyChangedEvent event = new OperationSafetyChangedEvent(
                    intent.getIntExtra(EXTRA_REASON, 42),
                    intent.getBooleanExtra(EXTRA_IS_SAFE, false));
            Log.d(TAG, "Received intent with event " + event + " on user " + context.getUserId());
            mEvents.offer(event);
        }
    };

    private OperationSafetyChangedCallback(boolean forDeviceOwner) {
        mForDeviceOwner = forDeviceOwner;
    }

    /**
     * Creates and registers a callback in the given context.
     */
    public static OperationSafetyChangedCallback register(Context context, boolean forDeviceOwner) {
        Log.d(TAG, "Registering " + ACTION_STATE_CHANGED + " on user " + context.getUserId());
        OperationSafetyChangedCallback callback = new OperationSafetyChangedCallback(
                forDeviceOwner);
        TestAppHelper.registerTestCaseReceiver(context, callback.mReceiver,
                new IntentFilter(ACTION_STATE_CHANGED), forDeviceOwner);
        return callback;
    }

    /**
     * Unregister this callback in the given context.
     */
    public void unregister(Context context) {
        Log.d(TAG, "Unregistering " + mReceiver + " on user " + context.getUserId());
        TestAppHelper.unregisterTestCaseReceiver(context, mReceiver, mForDeviceOwner);
    }

    /**
     * Gets the intent for the given event.
     */
    public static Intent intentFor(OperationSafetyChangedEvent event) {
        return new Intent(ACTION_STATE_CHANGED)
                .putExtra(EXTRA_REASON, event.reason)
                .putExtra(EXTRA_IS_SAFE, event.isSafe);
    }

    /**
     * Gets next event or fail.
     */
    public OperationSafetyChangedEvent getNextEvent(int operation) {
        OperationSafetyChangedEvent event = null;
        try {
            event = mEvents.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            String msg = "interrupted waiting for event";
            Log.e(TAG, msg, e);
            Thread.currentThread().interrupt();
            throw new AssertionFailedError(msg);
        }
        if (event == null) {
            String msg = "didn't receive an OperationSafetyChangedEvent for "
                    + operationToString(operation) + " in " + TIMEOUT_MS + "ms on " + this;
            Log.e(TAG, msg);
            throw new AssertionFailedError(msg);
        }
        return event;
    }

    @Override
    public String toString() {
        return "OperationSafetyChangedCallback[events=" + mEvents + "]";
    }
}

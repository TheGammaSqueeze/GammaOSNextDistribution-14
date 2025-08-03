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

package com.android.cts.verifier.bluetooth;

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeWithShellPermissions;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility for controlling the Bluetooth adapter from CTS test.
 *
 * Code mostly copied from android.bluetooth.cts.BTAdapterUtils class.
 */
public class BtAdapterUtils {
    private static final String TAG = "BtAdapterUtils";

    // ADAPTER_ENABLE_TIMEOUT_MS = AdapterState.BLE_START_TIMEOUT_DELAY +
    //                              AdapterState.BREDR_START_TIMEOUT_DELAY
    private static final int ADAPTER_ENABLE_TIMEOUT_MS = 8000;
    // ADAPTER_DISABLE_TIMEOUT_MS = AdapterState.BLE_STOP_TIMEOUT_DELAY +
    //                                  AdapterState.BREDR_STOP_TIMEOUT_DELAY
    private static final int ADAPTER_DISABLE_TIMEOUT_MS = 5000;

    private static BroadcastReceiver sAdapterIntentReceiver;

    private static Condition sConditionAdapterIsEnabled;
    private static ReentrantLock sAdapterStateEnablingLock;

    private static Condition sConditionAdapterIsDisabled;
    private static ReentrantLock sAdapterStateDisablingLock;
    private static boolean sAdapterVarsInitialized;

    private static HandlerThread sHandlerThread;
    private static Looper sLooper;
    private static Handler sHandler;

    private static class AdapterIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                Log.d(TAG, "Previous state: " + previousState + " New state: " + newState);

                if (newState == BluetoothAdapter.STATE_ON) {
                    sAdapterStateEnablingLock.lock();
                    try {
                        Log.d(TAG, "Signaling to mConditionAdapterIsEnabled");
                        sConditionAdapterIsEnabled.signal();
                    } finally {
                        sAdapterStateEnablingLock.unlock();
                    }
                } else if (newState == BluetoothAdapter.STATE_OFF) {
                    sAdapterStateDisablingLock.lock();
                    try {
                        Log.d(TAG, "Signaling to mConditionAdapterIsDisabled");
                        sConditionAdapterIsDisabled.signal();
                    } finally {
                        sAdapterStateDisablingLock.unlock();
                    }
                }
            }
        }
    }

    /** Enables the Bluetooth Adapter. Return true if it is already enabled or is enabled. */
    public static boolean enableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }
        registerIntentReceiver(context);

        if (bluetoothAdapter.isEnabled()) return true;

        invokeWithShellPermissions(() -> bluetoothAdapter.enable());
        sAdapterStateEnablingLock.lock();
        try {
            // Wait for the Adapter to be enabled
            while (!bluetoothAdapter.isEnabled()) {
                if (!sConditionAdapterIsEnabled.await(
                        ADAPTER_ENABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for the Bluetooth Adapter enable");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "enableAdapter: interrupted");
        } finally {
            sAdapterStateEnablingLock.unlock();
        }
        return bluetoothAdapter.isEnabled();
    }

    /** Disable the Bluetooth Adapter. Return true if it is already disabled or is disabled. */
    public static boolean disableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }
        registerIntentReceiver(context);

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) return true;

        invokeWithShellPermissions(() -> bluetoothAdapter.disable());
        sAdapterStateDisablingLock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (bluetoothAdapter.getState() != BluetoothAdapter.STATE_OFF) {
                if (!sConditionAdapterIsDisabled.await(
                        ADAPTER_DISABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for the Bluetooth Adapter disable");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "disableAdapter: interrupted");
        } finally {
            sAdapterStateDisablingLock.unlock();
        }
        return bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF;
    }

    private static void registerIntentReceiver(Context context) {
        sAdapterIntentReceiver = new AdapterIntentReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(sAdapterIntentReceiver, filter, RECEIVER_EXPORTED);
    }

    // Initialize variables required for TestUtils#enableAdapter and TestUtils#disableAdapter
    private static void initAdapterStateVariables(Context context) {
        sAdapterStateEnablingLock = new ReentrantLock();
        sConditionAdapterIsEnabled = sAdapterStateEnablingLock.newCondition();
        sAdapterStateDisablingLock = new ReentrantLock();
        sConditionAdapterIsDisabled = sAdapterStateDisablingLock.newCondition();

        sAdapterVarsInitialized = true;
    }
}

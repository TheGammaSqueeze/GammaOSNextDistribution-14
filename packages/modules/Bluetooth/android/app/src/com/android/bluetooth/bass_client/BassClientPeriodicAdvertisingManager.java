/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.PeriodicAdvertisingManager;
import android.util.Log;

/** Bass Client Periodic Advertising object handler */
class BassClientPeriodicAdvertisingManager {
    private static final String TAG = "BassClientPeriodicAdvertisingManager";

    private static PeriodicAdvertisingManager sPeriodicAdvertisingManager;

    private BassClientPeriodicAdvertisingManager() {}

    /**
     * Return true if initialization of Periodic Advertising Manager instance on Default Bluetooth
     * Adapter is successful.
     */
    public static boolean initializePeriodicAdvertisingManagerOnDefaultAdapter() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (sPeriodicAdvertisingManager != null) {
            Log.w(TAG, "Periodic Advertising Manager already initialized - re-initializing");
        }

        if (adapter == null) {
            Log.e(TAG, "Failed to get Default Bluetooth Adapter");
            return false;
        }

        sPeriodicAdvertisingManager = adapter.getPeriodicAdvertisingManager();

        if (sPeriodicAdvertisingManager == null) {
            Log.e(TAG, "Failed to get Default Periodic Advertising Manager");
            return false;
        }

        Log.d(TAG, "BassClientPeriodicAdvertisingManager initialized");

        return true;
    }

    public static synchronized PeriodicAdvertisingManager getPeriodicAdvertisingManager() {
        if (sPeriodicAdvertisingManager == null) {
            Log.e(
                    TAG,
                    "getPeriodicAdvertisingManager: Periodic Advertising Manager is not "
                            + "initialized");
            return null;
        }

        return sPeriodicAdvertisingManager;
    }

    public static void clearPeriodicAdvertisingManager() {
        if (sPeriodicAdvertisingManager == null) {
            Log.e(
                    TAG,
                    "clearPeriodicAdvertisingManager: Periodic Advertising Manager is not "
                            + "initialized");
            return;
        }

        Log.d(TAG, "BassClientPeriodicAdvertisingManager cleared");

        sPeriodicAdvertisingManager = null;
    }
}

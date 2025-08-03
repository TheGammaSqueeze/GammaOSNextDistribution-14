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

package com.android.bluetooth.bass_client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Looper;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.FeatureFlags;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Factory class for object initialization to help with unit testing
 */
public class BassObjectsFactory {
    private static final String TAG = BassObjectsFactory.class.getSimpleName();
    private static BassObjectsFactory sInstance;
    private static final Object INSTANCE_LOCK = new Object();

    private BassObjectsFactory() {}

    /**
     * Get the singleton instance of object factory
     *
     * @return the singleton instance, guaranteed not null
     */
    public static BassObjectsFactory getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new BassObjectsFactory();
            }
        }
        return sInstance;
    }

    /**
     * Allow unit tests to substitute BassObjectsFactory with a test instance
     *
     * @param objectsFactory a test instance of the BassObjectsFactory
     */
    @VisibleForTesting
    public static void setInstanceForTesting(BassObjectsFactory objectsFactory) {
        Utils.enforceInstrumentationTestMode();
        synchronized (INSTANCE_LOCK) {
            Log.d(TAG, "setInstanceForTesting(), set to " + objectsFactory);
            sInstance = objectsFactory;
        }
    }

    /**
     * Make a {@link BassClientStateMachine}
     *
     * @param device the remote device associated with this state machine
     * @param svc the bass client service
     * @param looper the thread that the state machine is supposed to run on
     * @param featureFlags feature flag from bass client service
     * @return a state machine that is initialized and started, ready to go
     */
    public BassClientStateMachine makeStateMachine(
            BluetoothDevice device,
            BassClientService svc,
            AdapterService adapterService,
            Looper looper,
            FeatureFlags featureFlags) {
        return BassClientStateMachine.make(device, svc, adapterService, looper, featureFlags);
    }

    /**
     * Destroy a state machine
     *
     * @param stateMachine to be destroyed. Cannot be used after this call.
     */
    public void destroyStateMachine(BassClientStateMachine stateMachine) {
        BassClientStateMachine.destroy(stateMachine);
    }

    /**
     * Get a {@link BluetoothLeScannerWrapper} object
     *
     * @param adapter bluetooth adapter
     * @return a bluetooth LE scanner
     */
    public BluetoothLeScannerWrapper getBluetoothLeScannerWrapper(BluetoothAdapter adapter) {
        return new BluetoothLeScannerWrapper(adapter.getBluetoothLeScanner());
    }
}

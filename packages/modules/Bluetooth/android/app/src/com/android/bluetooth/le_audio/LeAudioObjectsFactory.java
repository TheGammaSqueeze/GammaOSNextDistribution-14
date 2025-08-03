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

package com.android.bluetooth.le_audio;

import android.content.Context;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Factory class for object initialization to help with unit testing
 */
public class LeAudioObjectsFactory {
    private static final String TAG = LeAudioObjectsFactory.class.getSimpleName();
    private static LeAudioObjectsFactory sInstance;
    private static final Object INSTANCE_LOCK = new Object();

    private LeAudioObjectsFactory() {}

    /**
     * Get the singleton instance of object factory
     *
     * @return the singleton instance, guaranteed not null
     */
    public static LeAudioObjectsFactory getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new LeAudioObjectsFactory();
            }
        }
        return sInstance;
    }

    /**
     * Allow unit tests to substitute {@link LeAudioObjectsFactory} with a test instance
     *
     * @param objectsFactory a test instance of the {@link LeAudioObjectsFactory}
     */
    @VisibleForTesting
    public static void setInstanceForTesting(LeAudioObjectsFactory objectsFactory) {
        Utils.enforceInstrumentationTestMode();
        synchronized (INSTANCE_LOCK) {
            Log.d(TAG, "setInstanceForTesting(), set to " + objectsFactory);
            sInstance = objectsFactory;
        }
    }

    /**
     * Get a {@link LeAudioTmapGattServer} object
     *
     * @param context local context
     * @return
     */
    public LeAudioTmapGattServer getTmapGattServer(Context context) {
        return new LeAudioTmapGattServer(
                new LeAudioTmapGattServer.BluetoothGattServerProxy(context));
    }
}

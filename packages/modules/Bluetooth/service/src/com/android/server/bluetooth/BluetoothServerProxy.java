/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.os.IBinder;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

/** Proxy class for method calls to help with unit testing */
class BluetoothServerProxy {
    private static final String TAG = BluetoothServerProxy.class.getSimpleName();
    private static final Object INSTANCE_LOCK = new Object();
    private static BluetoothServerProxy sInstance;

    private BluetoothServerProxy() {
    }

    /**
     * Get the singleton instance of proxy
     *
     * @return the singleton instance, guaranteed not null
     */
    static @NonNull BluetoothServerProxy getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new BluetoothServerProxy();
            }
        }
        return sInstance;
    }

    /** Allow unit tests to substitute proxy with a test instance */
    @VisibleForTesting
    static void setInstanceForTesting(BluetoothServerProxy proxy) {
        synchronized (INSTANCE_LOCK) {
            Log.d(TAG, "setInstanceForTesting(), set to " + proxy);
            sInstance = proxy;
        }
    }

    AdapterBinder createAdapterBinder(IBinder binder) {
        return new AdapterBinder(binder);
    }

    String settingsSecureGetString(ContentResolver contentResolver, String name) {
        return Settings.Secure.getString(contentResolver, name);
    }

    int settingsGlobalGetInt(ContentResolver contentResolver, String name, int def) {
        return Settings.Global.getInt(contentResolver, name, def);
    }

    int getBluetoothPersistedState(ContentResolver resolver, int defaultValue) {
        return Settings.Global.getInt(resolver, Settings.Global.BLUETOOTH_ON, defaultValue);
    }
}

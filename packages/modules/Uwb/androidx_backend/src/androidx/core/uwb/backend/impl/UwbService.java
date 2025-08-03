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
package androidx.core.uwb.backend.impl;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.uwb.backend.IUwb;
import androidx.core.uwb.backend.IUwbClient;
import androidx.core.uwb.backend.impl.internal.UwbAvailabilityCallback;
import androidx.core.uwb.backend.impl.internal.UwbFeatureFlags;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

/** Uwb service entry point of the backend. */
public class UwbService extends Service {

    private UwbServiceImpl mUwbServiceImpl;
    @Override
    public void onCreate() {
        super.onCreate();
        UwbFeatureFlags uwbFeatureFlags = new UwbFeatureFlags.Builder()
                .setSkipRangingCapabilitiesCheck(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                .setReversedByteOrderFiraParams(
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
                .build();
        UwbAvailabilityCallback uwbAvailabilityCallback = (isUwbAvailable, reason) -> {
            // TODO: Implement when adding backend support.
        };
        mUwbServiceImpl = new UwbServiceImpl(this, uwbFeatureFlags, uwbAvailabilityCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUwbServiceImpl.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final IUwb.Stub mBinder =
            new IUwb.Stub() {
                @Override
                public IUwbClient getControleeClient() {
                    Log.i("UwbService", "Getting controleeClient");
                    return new UwbControleeClient(mUwbServiceImpl
                            .getControlee(UwbService.this.getApplicationContext()),
                            mUwbServiceImpl);
                }

                @Override
                public IUwbClient getControllerClient() {
                    Log.i("UwbService", "Getting controllerClient");
                    return new UwbControllerClient(mUwbServiceImpl
                            .getController(UwbService.this.getApplicationContext()),
                            mUwbServiceImpl);
                }

                @Override
                public int getInterfaceVersion() {
                    return this.VERSION;
                }

                @Override
                public String getInterfaceHash() {
                    return this.HASH;
                }
            };
}

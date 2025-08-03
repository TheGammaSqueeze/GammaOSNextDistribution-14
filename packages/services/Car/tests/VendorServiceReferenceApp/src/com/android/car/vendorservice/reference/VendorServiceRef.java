/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.vendorservice.reference;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * A service which could be bound by the CarService's VendorServiceController.
 */
public class VendorServiceRef extends Service {

    private static final String TAG = VendorServiceRef.class.getSimpleName();

    // Other components in the app or other apps can directly access IVendorServiceRef depending
    // on the build setup. If OEM wants to expose this binder to other apps as API, they can use
    // the same trick used by car-lib. Car-lib wraps around binder like ICar.aidl,
    // ICarUserService.aidl and exposes java APIs.

    // If the service does not support binding, it may return null from its onBind() method. If it
    // does, then the ServiceConnection's onNullBinding() method will be invoked instead of
    // onServiceConnected(). More info at {@link Context#bindService}.

    // Ideally if service doesn't support binding, it should be started without binding and
    // there are "start" and "startForeground" options available in the vendor service controller
    // configuration for such cases, but currently they are not working as
    // expected. (b/306704239). So in such situation "bind" option in vendor service controller can
    // be used and mBinder = new Binder() can be returned as binder object. This comment would be
    // updated once b/306704239 is resolved.
    private final IVendorServiceRef.Stub mBinder = new IVendorServiceRef.Stub() {
        @Override
        public String testApi(String input) {
            return "result-" + input;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        logServiceIsRunning();
        Log.d(TAG, "Service created");

    }

    // Different classes and initialization can be created in this method, for example listening
    // to the lifecycle events using Car-lib, listening to different intent and registering an
    // intent filter.
    private void logServiceIsRunning() {
        Thread mThread = new Thread(() -> {
            while (true) {
                Log.d(TAG, "Service is running");
                try {
                    // Log every 5 seconds
                    Thread.sleep(5_000);
                } catch (Exception e) {
                }
            }
        });
        mThread.start();
    }

    @Override
    public void onDestroy() {
        // Clean up resources.
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public final int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY means services would be restarted if killed for some reason.
        return START_STICKY;
    }

    @Override
    public final IBinder onBind(Intent intent) {
        // Ideally on bind call should return a valid binder. In theory it is possible to return
        // null for this call, but that is not recommended at this point due to (b/306704239)
        // In that case, mBinder = new Binder() can be used.
        return mBinder;
    }

}

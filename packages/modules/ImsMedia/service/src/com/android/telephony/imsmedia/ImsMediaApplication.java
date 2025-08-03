/**
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.telephony.imsmedia;

import android.app.Application;
import android.content.Context;

import com.android.telephony.imsmedia.util.Log;

/**
 * Extension of Application class to catch unhandled exception and release wake lock.
 */
public class ImsMediaApplication extends Application {
    private static final String TAG = "ImsMediaApplication";
    private static Context sAppContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sAppContext = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
                Log.e(TAG, "UncaughtException. Releasing all wakelocks.");
                WakeLockManager wakeLockManager = WakeLockManager.getInstance();
                if (wakeLockManager != null) {
                    wakeLockManager.cleanup();
                }
            }
        });
    }

    /**
     * Static method to get application context.
     * @return returns application context.
     */
    public static Context getAppContext() {
        return sAppContext;
    }
}

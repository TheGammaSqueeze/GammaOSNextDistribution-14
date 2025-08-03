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

package com.android.tests.loadingprogress.registerapp;

import android.app.Activity;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "RegisterApp";
    @Override
    public void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        LauncherApps.Callback callback = new LauncherApps.Callback() {
            @Override
            public void onPackageRemoved(String packageName, UserHandle user) {
            }

            @Override
            public void onPackageAdded(String packageName, UserHandle user) {
            }

            @Override
            public void onPackageChanged(String packageName, UserHandle user) {
            }

            @Override
            public void onPackagesAvailable(String[] packageNames, UserHandle user,
                    boolean replacing) {
            }

            @Override
            public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                    boolean replacing) {
            }
        };
        // Register a callback to LauncherApps Service. This is required before the test app is
        // first installed so that loading progress listener can be activated for the test app.
        final LauncherApps launcherApps = getApplicationContext().getSystemService(
                LauncherApps.class);
        if (launcherApps != null) {
            launcherApps.registerCallback(callback, new Handler(getMainLooper()));
        }
        Log.i(TAG, "Registered the first callback");
    }
}

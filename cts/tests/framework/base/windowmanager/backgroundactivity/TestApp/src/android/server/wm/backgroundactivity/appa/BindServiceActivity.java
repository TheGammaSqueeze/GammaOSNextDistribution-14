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

package android.server.wm.backgroundactivity.appa;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground activity that makes AppB as foreground.
 */
public class BindServiceActivity extends Activity {
    private static final String TAG = "AppCForegroundActivity";
    private boolean mAllowBackgroundActivityLaunch;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    protected void onStart() {
        super.onStart();
        mAllowBackgroundActivityLaunch = getIntent().getBooleanExtra(
                "android.server.wm.backgroundactivity.appc.ALLOW_BAL", false);
        Log.d(TAG, "mAllowBackgroundActivityLaunch: " + mAllowBackgroundActivityLaunch);
        android.server.wm.backgroundactivity.appa.Components appA =
                android.server.wm.backgroundactivity.appa.Components.get(
                        android.server.wm.backgroundactivity.appa.Components.JAVA_PACKAGE_NAME);
        Intent serviceIntent = new Intent().setComponent(appA.ACTIVITY_START_SERVICE);
        int flags = Context.BIND_AUTO_CREATE;
        if (mAllowBackgroundActivityLaunch) {
            flags |= Context.BIND_ALLOW_ACTIVITY_STARTS;
        }
        bindService(serviceIntent, mServiceConnection, flags);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);
    }
}

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

package android.server.wm.backgroundactivity.appa;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class ActivityStarterService extends Service {
    private static final String TAG = "ActivityStarterService";
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        Log.i(TAG, "ActivityStarterService onCreate");
        super.onCreate();
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "ActivityStarterService onBind");
        startActivity(new Intent(this, BackgroundActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "ActivityStarterService onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "ActivityStarterService onDestroy");
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        ActivityStarterService getService() {
            return ActivityStarterService.this;
        }
    }

}

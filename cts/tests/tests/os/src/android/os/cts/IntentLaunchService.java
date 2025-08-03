/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Service used to verify an UnsafeIntentLaunch StrictMode violation is reported when an Intent
 * is unparceled from the delivered Intent and used to start / bind to another Service.
 */
public class IntentLaunchService extends Service {
    private static final String INNER_INTENT_KEY = "inner-intent";

    private static final ServiceConnection SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    /**
     * Returns an instance of a ServiceConnection that can be used when binding to this Service.
     */
    public static ServiceConnection getServiceConnection() {
        return SERVICE_CONNECTION;
    }

    /**
     * Returns an Intent containing a parceled inner Intent that can be used to start / bind to this
     * Service and verify the StrictMode UnsafeIntentLaunch check is reported as expected.
     */
    public static Intent getTestIntent(Context context) {
        Intent intent = new Intent(context, IntentLaunchService.class);
        Intent innerIntent = new Intent(context, LocalService.class);
        intent.putExtra(INNER_INTENT_KEY, innerIntent);
        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Intent innerIntent = intent.getParcelableExtra(INNER_INTENT_KEY);
        if (innerIntent != null) {
            bindService(innerIntent, SERVICE_CONNECTION, Context.BIND_AUTO_CREATE);
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent innerIntent = intent.getParcelableExtra(INNER_INTENT_KEY);
        if (innerIntent != null) {
            startService(innerIntent);
        }
        stopService(intent);
        return START_NOT_STICKY;
    }
}

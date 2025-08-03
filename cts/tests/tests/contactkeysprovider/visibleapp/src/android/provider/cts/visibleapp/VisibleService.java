/*
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

package android.provider.cts.visibleapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.E2eeContactKeysManager;
import android.util.Log;

import java.util.List;

/**
 * Helper app to test the cases where apps read keys that are owned by other apps. This service is
 * supposed to be queryable by the CTS test.
 */
public class VisibleService extends Service {

    // Should be the same as in the actual test
    private static final String LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String DEVICE_ID = "someDeviceId";
    private static final String ACCOUNT_ID = "someAccountId";
    private static final byte[] KEY_VALUE = new byte[] {(byte) 10};

    private E2eeContactKeysManager mContactKeysManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = this.getApplicationContext();
        mContactKeysManager = context.getSystemService(E2eeContactKeysManager.class);
        mContactKeysManager.updateOrInsertE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);
        mContactKeysManager.updateOrInsertE2eeSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);
        List<E2eeContactKeysManager.E2eeSelfKey> list = mContactKeysManager.getAllE2eeSelfKeys();
        Log.w("MainService", "Test CP3: " + list.size());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mContactKeysManager.removeE2eeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        mContactKeysManager.removeE2eeSelfKey(DEVICE_ID, ACCOUNT_ID);
        super.onDestroy();
    }
}

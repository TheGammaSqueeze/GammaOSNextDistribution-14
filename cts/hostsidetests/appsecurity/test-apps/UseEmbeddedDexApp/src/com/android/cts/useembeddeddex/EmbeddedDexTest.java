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

package com.android.cts.useembeddeddex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.test.InstrumentationTestCase;
import android.util.Log;
import com.android.compatibility.common.util.PollingCheck;

public class EmbeddedDexTest extends InstrumentationTestCase {
    static final String TAG = "EmbeddedDexTest";

    boolean mServiceConnected = false;

    public void testIsolatedService() throws Exception {
        // Start and wait for the isolated service.
        Context context = getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, IsolatedService.class);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "Isolated service connected " + name + " " + service);
                EmbeddedDexTest.this.mServiceConnected = true;
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "Isolated service disconnected " + name);
                EmbeddedDexTest.this.mServiceConnected = false;
            }
        };
        try {
            assertTrue(context.bindService(intent, conn, Context.BIND_AUTO_CREATE));
            PollingCheck.waitFor(() -> EmbeddedDexTest.this.mServiceConnected);
        } finally {
            context.unbindService(conn);
        }
    }
}

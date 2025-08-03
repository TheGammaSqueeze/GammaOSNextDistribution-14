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
package android.app.stubs;

import static android.app.stubs.shared.Shared_getBindingUidImportance.ACTION_TEST_PROVIDER;
import static android.app.stubs.shared.Shared_getBindingUidImportance.ACTION_TEST_SERVICE_BINDING;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Receiver_getBindingUidImportance extends BroadcastReceiver {
    private static final String TAG = "Receiver_getBindingUidImportance";

    private static final String MAIN_CTS_PACKAGE = "android.app.cts.getbindinguidimportance";

    private static final ComponentName SERVICE_NAME = new ComponentName(MAIN_CTS_PACKAGE,
            "android.app.cts.getbindinguidimportance."
                    + "ActivityManager_getBindingUidImportanceTest$MyService");

    private static final String AUTHORITY = "android.app.cts.getbindinguidimportance"
            + ".ActivityManager_getBindingUidImportanceTest.MyProvider";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: intent=" + intent + " my-package: " + context.getPackageName());

        PendingResult pr = goAsync();

        new Thread(() -> {
            try {
                switch (intent.getAction()) {
                    case ACTION_TEST_SERVICE_BINDING:
                        doTestServiceBinding(context.getApplicationContext());
                        break;
                    case ACTION_TEST_PROVIDER:
                        doTestProvider(context.getApplicationContext());
                        break;
                    default:
                        Log.e(TAG, "Unknown intent received: " + intent);
                        break;
                }
            } catch (Throwable e) {
                Log.e(TAG, "Exception caught in receiver", e);
            } finally {
                pr.finish();
            }
        }).start();
    }

    private void doTestServiceBinding(Context context) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        final var sc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Service connected");
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                latch.countDown();
            }
        };

        assertTrue(context.bindService(new Intent().setComponent(SERVICE_NAME),
                sc, Context.BIND_AUTO_CREATE));

        assertTrue("Service didn't connect in time",
                latch.await(60, TimeUnit.SECONDS));

        context.unbindService(sc);
    }

    private void doTestProvider(Context context) throws RemoteException {
        try (var client = context.getContentResolver().acquireContentProviderClient(AUTHORITY)) {
            try (var c = client.query(Uri.parse("content://" + AUTHORITY + "/"),
                    null, null, null)) {
                assertNotNull(c);
            }
        }
    }
}

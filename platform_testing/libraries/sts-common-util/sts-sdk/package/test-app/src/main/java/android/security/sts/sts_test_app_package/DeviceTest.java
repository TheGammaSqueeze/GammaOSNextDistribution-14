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

package android.security.sts.sts_test_app_package;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An example device test that starts an activity and uses broadcasts to wait for the artifact
 * proving vulnerability
 */
@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final String TAG = DeviceTest.class.getSimpleName();
    Context mContext;

    /** Test broadcast action */
    public static final String ACTION_BROADCAST = "action_security_test_broadcast";
    /** Broadcast intent extra name for artifacts */
    public static final String INTENT_ARTIFACT = "artifact";

    /** Device test */
    @Test
    public void testDeviceSideMethod() throws Exception {
        mContext = getApplicationContext();

        AtomicReference<String> actual = new AtomicReference<>();
        final Semaphore broadcastReceived = new Semaphore(0);
        BroadcastReceiver broadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        try {
                            if (!intent.getAction().equals(ACTION_BROADCAST)) {
                                Log.i(
                                        TAG,
                                        String.format(
                                                "got a broadcast that we didn't expect: %s",
                                                intent.getAction()));
                            }
                            actual.set(intent.getStringExtra(INTENT_ARTIFACT));
                            broadcastReceived.release();
                        } catch (Exception e) {
                            Log.e(TAG, "got an exception when handling broadcast", e);
                        }
                    }
                };
        IntentFilter filter = new IntentFilter(); // see if there's a shorthand
        filter.addAction(ACTION_BROADCAST); // what does this return?
        mContext.registerReceiver(broadcastReceiver, filter);

        // start the target app
        try {
            Log.d(TAG, "starting local activity");
            Intent newActivityIntent = new Intent(mContext, PocActivity.class);
            newActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // this could be startActivityForResult, but is generic for illustrative purposes
            mContext.startActivity(newActivityIntent);
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
        assertTrue(
                "Timed out when getting result from other activity",
                broadcastReceived.tryAcquire(/* TIMEOUT_MS */ 5000, TimeUnit.MILLISECONDS));
        assertEquals("The target artifact should have been 'secure'", "secure", actual.get());
    }
}

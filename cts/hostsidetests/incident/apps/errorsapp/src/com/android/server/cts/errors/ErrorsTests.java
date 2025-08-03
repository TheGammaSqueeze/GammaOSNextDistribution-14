/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.errors;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DropBoxReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Used by ErrorTest. Spawns misbehaving activities so reports will appear in Dropbox. */
@RunWith(AndroidJUnit4.class)
public class ErrorsTests {
    private static final String TAG = "ErrorsTests";

    private static final String CRASH_TAG = "data_app_crash";
    private static final String ANR_TAG = "data_app_anr";
    private static final String NATIVE_CRASH_TAG = "data_app_native_crash";
    private static final String TOMBSTONE_TAG = "SYSTEM_TOMBSTONE";

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(
                        "appops set "
                                + mContext.getPackageName()
                                + " android:get_usage_stats allow");

        Runtime.getRuntime().exec("cmd activity reset-dropbox-rate-limiter");
    }

    @Test
    public void testException() throws Exception {
        Log.i(TAG, "testException");

        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        CRASH_TAG,
                        mContext.getPackageName() + ":TestProcess",
                        "java.lang.RuntimeException: This is a test exception");
        Intent intent = new Intent();
        intent.setClass(mContext, ExceptionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Test
    public void testANR() throws Exception {
        Log.i(TAG, "testANR");

        // Require that we get an ANR entry that shows that the activity is blocked in onCreate.
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        ANR_TAG,
                        mContext.getPackageName() + ":TestProcess",
                        "com.android.server.cts.errors.ANRActivity.onCreate");
        Intent intent = new Intent();
        intent.setClass(mContext, ANRActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        final Intent receiverIntent = new Intent(mContext, Receiver.class);
        mContext.sendBroadcast(receiverIntent);
        Log.i(TAG, "testANR -- sent broadcast to " + receiver);

        assertTrue(receiver.await());
    }

    @Test
    public void testNativeCrash() throws Exception {
        Log.i(TAG, "testNativeCrash");

        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":TestProcess",
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, NativeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Test
    public void testTombstone() throws Exception {
        Log.i(TAG, "testTombstone");

        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        TOMBSTONE_TAG,
                        mContext.getPackageName() + ":TestProcess",
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, NativeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }
}

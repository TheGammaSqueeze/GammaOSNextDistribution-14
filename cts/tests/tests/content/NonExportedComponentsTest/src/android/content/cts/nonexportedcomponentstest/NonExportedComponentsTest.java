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

package android.content.cts.nonexportedcomponentstest;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests whether non-exported components match internal implicit intents.
 */
@RunWith(AndroidJUnit4.class)
@AppModeNonSdkSandbox(reason = "Sandboxed SDKs cannot declare components")
public class NonExportedComponentsTest {

    private static final String ACTIVITY_ACTION_NAME =
            "android.intent.action.NON_EXPORTED_ACTIVITY_ACTION";
    private static final String RECEIVER_ACTION_NAME =
            "android.intent.action.NON_EXPORTED_RECEIVER_ACTION";
    private static final String STATIC_RECEIVER_ACTION_NAME =
            "android.intent.action.STATIC_NON_EXPORTED_RECEIVER_ACTION";
    private static final int BROADCAST_TIMEOUT = 10000;

    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);

    private Context mContext = null;

    public static class StaticNonExportedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            COUNT_DOWN_LATCH.countDown();
        }
    }

    public static class NonExportedReceiver extends BroadcastReceiver {
        private boolean mReceivedBroadCast;

        public void onReceive(Context context, Intent intent) {
            mReceivedBroadCast = true;
        }

        public boolean hasReceivedBroadCast() {
            return mReceivedBroadCast;
        }
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testActivityLaunches() {
        try {
            mContext.startActivity(new Intent(ACTIVITY_ACTION_NAME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Assert.fail("Test startActivity should not throw a ActivityNotFoundException here.");
        }
    }

    @Test
    public void testStaticReceiverLaunches() {
        mContext.sendBroadcast(new Intent(STATIC_RECEIVER_ACTION_NAME));
        try {
            Assert.assertTrue(COUNT_DOWN_LATCH.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail("interrupted");
        }
    }

    @Test
    public void testDynamicReceiverLaunches() {
        NonExportedReceiver receiver = new NonExportedReceiver();
        mContext.registerReceiver(receiver,
                new IntentFilter(RECEIVER_ACTION_NAME), Context.RECEIVER_NOT_EXPORTED);
        mContext.sendBroadcast(new Intent(RECEIVER_ACTION_NAME), null);
        try {
            new PollingCheck(BROADCAST_TIMEOUT) {
                @Override
                protected boolean check() {
                    return receiver.hasReceivedBroadCast();
                }
            }.run();
        } catch (AssertionError e) {
            Assert.fail();
        }
        mContext.unregisterReceiver(receiver);
    }
}

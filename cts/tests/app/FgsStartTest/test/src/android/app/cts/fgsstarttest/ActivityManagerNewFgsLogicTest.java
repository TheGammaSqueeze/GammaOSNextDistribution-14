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
package android.app.cts.fgsstarttest;

import static android.app.fgsstarttesthelper.FgsTestCommon.ACTION_START_NEW_LOGIC_TEST;
import static android.app.fgsstarttesthelper.FgsTestCommon.EXTRA_REPLY_INTENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.fgsstarttesthelper.EmptyService;
import android.app.fgsstarttesthelper.FgsNewLogicMessage;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerNewFgsLogicTest {
    private static final String TAG = "ActivityManagerNewFgsLogicTest";

    private static final String HELPER_34 = "android.app.fgsstarttesthelper34";
    private static final String HELPER_CURRENT = "android.app.fgsstarttesthelpercurrent";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() {
        SystemUtil.runShellCommand("am force-stop --user current " + HELPER_34);
        SystemUtil.runShellCommand("am force-stop --user current " + HELPER_CURRENT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NEW_FGS_RESTRICTION_LOGIC)
    public void testForApi34() throws Exception {
        test(mContext, HELPER_34 , true);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NEW_FGS_RESTRICTION_LOGIC)
    public void testForCurrent() throws Exception {
        test(mContext, HELPER_CURRENT, false);
    }

    private void awaitLatch(CountDownLatch latch, String eventName) throws Exception {
        boolean success = latch.await(30, TimeUnit.SECONDS);

        assertTrue("Timed out waiting for " + eventName, success);
    }

    private void test(Context context, String helperPackageName, boolean expectedFgsStarted)
            throws Exception {
        cleanUpHelperAppState(context, helperPackageName);

        // Build a reply intent action
        final var replyIntent = new Intent("ACTION_REPLY_" + new SecureRandom().nextInt());
        replyIntent.setPackage(context.getPackageName());
        replyIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        // Build a request intent
        // The receiver is a manifest receiver, so even if the helper is cached, it'll
        // still be received.
        var request = new Intent().setAction(ACTION_START_NEW_LOGIC_TEST);
        request.setPackage(helperPackageName);
        request.setFlags(Intent.FLAG_RECEIVER_FOREGROUND
                + Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        request.putExtra(EXTRA_REPLY_INTENT, replyIntent);

        // Prepare the response receiver and send the broadcast.

        final var message = new AtomicReference<FgsNewLogicMessage>();
        final var latch = new CountDownLatch(1);

        final var receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                message.set(FgsNewLogicMessage.getFromIntent(intent));
                Log.i(TAG, "Received reply with message " + message.get());
                latch.countDown();
            }
        };

        context.registerReceiver(receiver,
                new IntentFilter(replyIntent.getAction()),
                Context.RECEIVER_EXPORTED);
        try {
            context.sendBroadcast(request);
            Log.i(TAG, "Request sent. Waiting for response... request="
                    + request + " extras=" + request.getExtras());

            awaitLatch(latch, "reply broadcast");

        } finally {
            context.unregisterReceiver(receiver);
        }

        final var unexpectedError = message.get().getUnexpectedErrorMessage();
        if (unexpectedError != null) {
            Assert.fail("Received unexpected error: " + unexpectedError);
        }

        assertEquals("Unexpected 'FGS started'",
                expectedFgsStarted, message.get().isFgsStarted());
    }

    /**
     * - Bind a service in the helper package. This will "un-stop" the package, and also clears
     * "bad app" state even if the helper crashed previously.
     *
     * - Also reset the job quota.
     */
    private void cleanUpHelperAppState(Context context, String helperPackageName) throws Exception {
        Log.i(TAG, "cleanUpHelperAppState: " + helperPackageName);

        SystemUtil.runShellCommand("am force-stop --user current " + helperPackageName);
        Thread.sleep(500);

        SystemUtil.runShellCommand("am set-standby-bucket --user current " + helperPackageName
                 + " active");

        // Un-stop the app
        final var latch = new CountDownLatch(1);

        final var sc = new ServiceConnection() {
            private void unbind(String methodName) {
                Log.i(TAG, methodName);
                context.unbindService(this);
                latch.countDown();
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                unbind("onServiceConnected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                unbind("onServiceDisconnected");
            }

            @Override
            public void onBindingDied(ComponentName name) {
                unbind("onBindingDied");
            }

            @Override
            public void onNullBinding(ComponentName name) {
                unbind("onNullBinding");
            }
        };

        final var i = new Intent().setComponent(
                new ComponentName(helperPackageName, EmptyService.class.getName()));

        Log.i(TAG, "Binding " + helperPackageName);
        context.bindService(i, sc, Context.BIND_AUTO_CREATE + Context.BIND_WAIVE_PRIORITY
                        + Context.BIND_NOT_FOREGROUND);

        awaitLatch(latch, "service binding");

        // Reset job quota
        SystemUtil.runShellCommand(
                "cmd jobscheduler reset-execution-quota -u current " + helperPackageName);
    }
}

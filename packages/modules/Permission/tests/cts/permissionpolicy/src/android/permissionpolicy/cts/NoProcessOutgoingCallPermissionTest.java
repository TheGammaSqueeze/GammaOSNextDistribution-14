/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permissionpolicy.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verify that processing outgoing calls requires Permission.
 */
@AppModeFull(reason = "Instant apps cannot hold PROCESS_OUTGOING_CALL")
public class NoProcessOutgoingCallPermissionTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    // Time to wait for call to be placed.
    private static final int CALL_START_WAIT_TIME_SEC = 30;
    // Time to wait for a broadcast to be received after we verify that the test app which has
    // the proper permission got the broadcast
    private static final int POST_CALL_START_WAIT_TIME_SEC = 5;

    private static final String APK_INSTALL_LOCATION =
            "/data/local/tmp/cts-permissionpolicy/CtsProcessOutgoingCalls.apk";
    private static final String LOG_TAG = "NoProcessOutgoingCallPermissionTest";

    private static final String ACTION_TEST_APP_RECEIVED_CALL =
            "android.permissionpolicy.cts.TEST_APP_RECEIVED_CALL";
    private static final String TEST_PKG_NAME = "android.permissionpolicy.cts.receivecallbroadcast";

    private final CountDownLatch mTestAppBroadcastLatch = new CountDownLatch(1);
    private final CountDownLatch mSystemBroadcastLatch = new CountDownLatch(1);

    private void callPhone() {
        Uri uri = Uri.parse("tel:123456");
        Intent intent = new Intent(Intent.ACTION_CALL, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        Log.i(LOG_TAG, "Called phone: " + uri.toString());
    }

    /**
     * Verify that to process an outgoing call requires Permission.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#PROCESS_OUTGOING_CALLS}
     */
    // TODO: add back to LargeTest when test can cancel initiated call
    @Test
    public void testProcessOutgoingCall() throws InterruptedException {
        final PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
                !pm.hasSystemFeature(PackageManager.FEATURE_SIP_VOIP)) {
            return;
        }

        OutgoingCallBroadcastReceiver rcvr = new OutgoingCallBroadcastReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL);
        filter.addAction(ACTION_TEST_APP_RECEIVED_CALL);
        mContext.registerReceiver(rcvr, filter, Context.RECEIVER_EXPORTED);
        // start the test app, so that it can receive the broadcast
        mContext.startActivity(new Intent().setComponent(new ComponentName(TEST_PKG_NAME,
                        TEST_PKG_NAME + ".ProcessOutgoingCallReceiver$BaseActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        callPhone();

        boolean testAppGotBroadcast =
                mTestAppBroadcastLatch.await(CALL_START_WAIT_TIME_SEC, TimeUnit.SECONDS);
        Assert.assertTrue("Expected test app to receive broadcast within "
                        + CALL_START_WAIT_TIME_SEC + " seconds", testAppGotBroadcast);
        boolean testClassGotBroadcast =
                mSystemBroadcastLatch.await(POST_CALL_START_WAIT_TIME_SEC, TimeUnit.SECONDS);
        Assert.assertFalse("Outgoing call processed without proper permissions",
                testClassGotBroadcast);
    }

    @Before
    public void installApp() {
        String installResult = runShellCommandOrThrow("pm install -g " + APK_INSTALL_LOCATION);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    @After
    public void endCall() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mContext.getSystemService(TelecomManager.class).endCall();
        });
        runShellCommand("pm uninstall " + TEST_PKG_NAME);
    }

    public class OutgoingCallBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TEST_APP_RECEIVED_CALL.equals(intent.getAction())) {
                mTestAppBroadcastLatch.countDown();
                return;
            }
            Bundle xtrs = intent.getExtras();
            Log.e(LOG_TAG, xtrs.toString());
            mSystemBroadcastLatch.countDown();
        }
    }

}


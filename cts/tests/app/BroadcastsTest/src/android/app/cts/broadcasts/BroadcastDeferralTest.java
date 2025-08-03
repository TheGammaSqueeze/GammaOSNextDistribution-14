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

package android.app.cts.broadcasts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import androidx.test.filters.FlakyTest;

import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@FlakyTest(bugId = 288323707)
@RunWith(BroadcastsTestRunner.class)
public class BroadcastDeferralTest extends BaseBroadcastTest {

    @ClassRule
    public static final DeviceConfigStateChangerRule sFreezerTimeoutRule =
            new DeviceConfigStateChangerRule(getContext(),
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER_NATIVE_BOOT,
                    KEY_FREEZE_DEBOUNCE_TIMEOUT,
                    String.valueOf(SHORT_FREEZER_TIMEOUT_MS));

    @Test
    public void testFgBroadcastDeliveryToFrozenApp_withDeferUntilActive() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        assumeTrue(isAppFreezerEnabled());

        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        try {
            final Intent intent = new Intent(TEST_ACTION1)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Bundle options = BroadcastOptions.makeBasic()
                    .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE)
                    .toBundle();
            int testPid = -1;
            TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
            ICommandReceiver cmdReceiver1;
            ICommandReceiver cmdReceiver2;
            try {
                cmdReceiver1 = connection1.getCommandReceiver();
                cmdReceiver2 = connection2.getCommandReceiver();
                testPid = cmdReceiver2.getPid();
                final IntentFilter filter = new IntentFilter(TEST_ACTION1);
                cmdReceiver2.clearCookie(TEST_ACTION1);
                cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

                cmdReceiver1.sendBroadcast(intent, options);
                verifyReceivedBroadcasts(cmdReceiver2, TEST_ACTION1,
                        List.of(intent), true, null);
                cmdReceiver2.clearCookie(TEST_ACTION1);
            } finally {
                connection2.unbind();
            }

            // Verify the test app process is frozen
            waitForProcessFreeze(testPid, SHORT_FREEZER_TIMEOUT_MS * 2);
            cmdReceiver1.sendBroadcast(intent, options);

            // Flush all broadcasts but broadcast to the frozen shouldn't be delivered, so
            // verify that test process is still frozen.
            AmUtils.waitForBroadcastBarrier();
            assertTrue("Process should still be frozen; pid=" + testPid, isProcessFrozen(testPid));

            final long timestampMs = SystemClock.elapsedRealtime();
            connection2 = bindToHelperService(HELPER_PKG2);
            try {
                cmdReceiver2 = connection2.getCommandReceiver();

                // TODO: Add a UidImportanceListener to listen for cached state change
                // as a proxy to know that BroadcastProcessQueue received that state change.
                verifyReceivedBroadcasts(cmdReceiver2, TEST_ACTION1,
                        List.of(intent), true,
                        receipt -> {
                            if (receipt.timestampMs < timestampMs) {
                                return "Broadcast should have been received after service bind;"
                                        + " receipt.timestampMs=" + receipt.timestampMs
                                        + ", timestampMs=" + timestampMs;
                            }
                            return null;
                        });
            } finally {
                connection2.unbind();
            }
        } finally {
            connection1.unbind();
        }
    }

    @Test
    public void testFgBroadcastDeliveryToFrozenApp() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        assumeTrue(isAppFreezerEnabled());

        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        ICommandReceiver cmdReceiver1;
        ICommandReceiver cmdReceiver2;
        try {
            final Intent intent = new Intent(TEST_ACTION1)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            int testPid = -1;
            TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
            try {
                cmdReceiver1 = connection1.getCommandReceiver();
                cmdReceiver2 = connection2.getCommandReceiver();
                testPid = cmdReceiver2.getPid();
                final IntentFilter filter = new IntentFilter(TEST_ACTION1);
                cmdReceiver2.clearCookie(TEST_ACTION1);
                cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);
            } finally {
                connection2.unbind();
            }

            // Verify the test app process is frozen
            waitForProcessFreeze(testPid, SHORT_FREEZER_TIMEOUT_MS * 2);
            cmdReceiver1.sendBroadcast(intent, null);

            // Flush all broadcasts and verify that the broadcast was delivered to the process
            // before we bind to it.
            AmUtils.waitForBroadcastBarrier();

            // Adding a small delay before binding to the service to give opportunity for the
            // broadcast to be received. We can't use a finish callback or flush the application
            // threads to ensure broadcast delivery is completed as that would interfere with
            // what we are trying to verify.
            SystemClock.sleep(100);
            final long timestampMs = SystemClock.elapsedRealtime();
            connection2 = bindToHelperService(HELPER_PKG2);
            try {
                cmdReceiver2 = connection2.getCommandReceiver();

                verifyReceivedBroadcasts(cmdReceiver2, TEST_ACTION1,
                        List.of(intent), true,
                        receipt -> {
                            if (receipt.timestampMs > timestampMs) {
                                return "Broadcast should have been received before service bind;"
                                        + " receipt.timestampMs=" + receipt.timestampMs
                                        + ", timestampMs=" + timestampMs;
                            }
                            return null;
                        });
            } finally {
                connection2.unbind();
            }
        } finally {
            connection1.unbind();
        }
    }
}

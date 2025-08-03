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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerExemptionManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import com.android.app.cts.broadcasts.Common;
import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BroadcastsTestRunner.class)
public class BroadcastFreezerTest extends BaseBroadcastTest {

    @ClassRule
    public static final DeviceConfigStateChangerRule sFreezerTimeoutRule =
            new DeviceConfigStateChangerRule(getContext(),
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER_NATIVE_BOOT,
                    KEY_FREEZE_DEBOUNCE_TIMEOUT,
                    String.valueOf(SHORT_FREEZER_TIMEOUT_MS));

    // How much to delay freezing by.
    // Should be greater than SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS
    private static final long APP_FREEZING_DELAY_MS = 15_000;

    // How long after freezer timeout to verify the app frozen state
    private static final long ERROR_MARGIN_MS = 5_000;

    // Test delaying freezer via BroadcastOptions
    @Test
    public void testBroadcastOptions_appFreezingDelayed() throws Exception {
        assumeTrue(isAppFreezerEnabled());

        final Intent intent = new Intent(Common.ORDERED_BROADCAST_ACTION)
                .putExtra(TEST_EXTRA1, TEST_VALUE1)
                .setPackage(HELPER_PKG1);
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setTemporaryAppAllowlist(APP_FREEZING_DELAY_MS,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_APP_FREEZING_DELAYED,
                PowerExemptionManager.REASON_PUSH_MESSAGING_DEFERRABLE,
                "Normal Priority");
        int testPid;
        long startTimeMs;
        TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
        ICommandReceiver cmdReceiver;
        try {
            cmdReceiver = connection.getCommandReceiver();
            testPid = cmdReceiver.getPid();
            final ResultReceiver resultReceiver = new ResultReceiver();

            SystemUtil.runWithShellPermissionIdentity(() -> {
                mContext.sendOrderedBroadcast(intent, null, options.toBundle(),
                        resultReceiver, null, 0, null, null);
            });
            AmUtils.waitForBroadcastBarrier();
            assertThat(resultReceiver.getResult())
                    .isEqualTo(Common.ORDERED_BROADCAST_RESULT_DATA);
            // Start the timer
            startTimeMs = SystemClock.uptimeMillis();

            // Try to override it with a broadcast with a smaller delay. This is to make sure
            // that a second delay request that's shorter than the first doesn't override
            // the longer first delay.
            options.setTemporaryAppAllowlist(SHORT_FREEZER_TIMEOUT_MS,
                    PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_APP_FREEZING_DELAYED,
                    PowerExemptionManager.REASON_PUSH_MESSAGING_DEFERRABLE,
                    "Normal Priority");
            SystemUtil.runWithShellPermissionIdentity(() -> {
                mContext.sendOrderedBroadcast(intent, null, options.toBundle(),
                        resultReceiver, null, 0, null, null);
            });
            AmUtils.waitForBroadcastBarrier();
            assertThat(resultReceiver.getResult())
                    .isEqualTo(Common.ORDERED_BROADCAST_RESULT_DATA);
        } finally {
            // Unbind service so that the app can be cached
            connection.unbind();
        }

        SystemClock.sleep(SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS
                - (SystemClock.uptimeMillis() - startTimeMs));
        assertFalse("Should not be frozen in "
                + (SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS) + "ms",
                isProcessFrozen(testPid));
        SystemClock.sleep(APP_FREEZING_DELAY_MS + ERROR_MARGIN_MS
                - (SystemClock.uptimeMillis() - startTimeMs));
        // Depending on the MCP limits, the process should have got killed or should be frozen at
        // this point.
        assertThrows("Process is still alive and unfrozen for longer than expected",
                RemoteException.class, () -> cmdReceiver.getPid());
    }

    /**
     * Verify that a broadcast sent to a frozen app with pending sync binder transaction
     * is delivered as expected.
     */
    @Test
    public void testBroadcastDelivery_syncTxnWhileFrozen() throws Exception {
        assumeTrue(isAppFreezerEnabled());

        TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
        ICommandReceiver cmdReceiver;
        int testPid;
        try {
            cmdReceiver = connection.getCommandReceiver();
            testPid = cmdReceiver.getPid();
        } finally {
            // Unbind service so that the app can be cached
            connection.unbind();
        }

        SystemClock.sleep(SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS);
        assertTrue("Test app should have been frozen", isProcessFrozen(testPid));

        // Make a sync binder transaction to the frozen app, which should fail since the process
        // is frozen.
        assertThrows(RemoteException.class, () -> cmdReceiver.getPid());

        final Intent intent = new Intent(Common.ORDERED_BROADCAST_ACTION)
                .putExtra(TEST_EXTRA1, TEST_VALUE1)
                .setPackage(HELPER_PKG1);
        final ResultReceiver resultReceiver = new ResultReceiver();
        mContext.sendOrderedBroadcast(intent, null, (Bundle) null,
                resultReceiver, null, 0, null, null);
        AmUtils.waitForBroadcastBarrier();
        assertThat(resultReceiver.getResult())
                .isEqualTo(Common.ORDERED_BROADCAST_RESULT_DATA);
    }

    @Test
    public void testBroadcastDelivery_pendingFreezeCancelled() throws Exception {
        assumeTrue(isAppFreezerEnabled());

        TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final ICommandReceiver cmdReceiver1;
        try {
            cmdReceiver1 = connection1.getCommandReceiver();

            TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
            final ICommandReceiver cmdReceiver2;
            final Intent intent = new Intent(TEST_ACTION1)
                    .setPackage(HELPER_PKG2);
            final BroadcastOptions options = BroadcastOptions.makeBasic()
                    .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE);
            try {
                cmdReceiver2 = connection2.getCommandReceiver();
                cmdReceiver2.clearCookie(TEST_ACTION1);
                final IntentFilter filter = new IntentFilter(TEST_ACTION1);
                cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

                cmdReceiver1.sendBroadcast(intent, options.toBundle());

                AmUtils.waitForBroadcastBarrier();
                verifyReceivedBroadcasts(cmdReceiver2, TEST_ACTION1, List.of(intent), true, null);
            } finally {
                connection2.unbind();
            }

            // Wait for some time to get the process into a freezable state but not till it is
            // frozen.
            SystemClock.sleep(SHORT_FREEZER_TIMEOUT_MS / 2);

            connection2 = bindToHelperService(HELPER_PKG2);
            try {
                cmdReceiver2.clearCookie(TEST_ACTION1);
                cmdReceiver1.sendBroadcast(intent, options.toBundle());

                AmUtils.waitForBroadcastBarrier();
                verifyReceivedBroadcasts(cmdReceiver2, TEST_ACTION1, List.of(intent), true, null);
            } finally {
                connection2.unbind();
            }
        } finally {
            connection1.unbind();
        }
    }
}

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

package android.app.cts.broadcasts;

import static org.junit.Assume.assumeTrue;

import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.app.cts.broadcasts.ICommandReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BroadcastsTestRunner.class)
public class BroadcastDeliveryGroupTest extends BaseBroadcastTest {

    @Test
    public void testDeliveryGroupPolicy_policyAll() throws Exception {
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            cmdReceiver1.sendBroadcast(intent1, null /* options */);
            cmdReceiver1.sendBroadcast(intent2, null /* options */);

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent1, intent2), true);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_withForceDelay_policyAll() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            // Now force delay the broadcasts to make sure delivery group policies are
            // applied as expected.
            initializeQueue(HELPER_PKG2, cmdReceiver2);
            forceDelayBroadcasts(HELPER_PKG2);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            cmdReceiver1.sendBroadcast(intent1, null /* options */);
            cmdReceiver1.sendBroadcast(intent2, null /* options */);

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent1, intent2), true);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_policyMostRecent() throws Exception {
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
            options.setDeliveryGroupMatchingKey(TEST_ACTION1, TEST_EXTRA1);
            cmdReceiver1.sendBroadcast(intent1, options.toBundle());
            cmdReceiver1.sendBroadcast(intent2, options.toBundle());

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent2), false);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_withForceDelay_policyMostRecent() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            // Now force delay the broadcasts to make sure delivery group policies are
            // applied as expected.
            initializeQueue(HELPER_PKG2, cmdReceiver2);
            forceDelayBroadcasts(HELPER_PKG2);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
            options.setDeliveryGroupMatchingKey(TEST_ACTION1, TEST_EXTRA1);
            cmdReceiver1.sendBroadcast(intent1, options.toBundle());
            cmdReceiver1.sendBroadcast(intent2, options.toBundle());

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent2), true, null);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    private void initializeQueue(String pkg, ICommandReceiver cmdReceiver) throws Exception {
        // TODO: b/294884478 - We can remove this method once forceDelayBroadcastDelivery() is
        // updated to work in any state.
        final String testAction = "com.android.app.cts.test";
        final IntentFilter filter = new IntentFilter(testAction);
        cmdReceiver.monitorBroadcasts(filter, testAction);

        final Intent testIntent = new Intent(testAction)
                .setPackage(pkg);
        getContext().sendBroadcast(testIntent);
    }
}

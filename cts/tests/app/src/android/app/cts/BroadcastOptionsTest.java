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

package android.app.cts;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED;
import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED;
import static android.app.Flags.FLAG_BCAST_EVENT_TIMESTAMPS;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNull;

import android.app.BroadcastOptions;
import android.os.Build;
import android.os.PowerExemptionManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.RavenwoodFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BroadcastOptionsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = RavenwoodRule.isOnRavenwood()
            ? RavenwoodFlagsValueProvider.createAllOnCheckFlagsRule()
            : DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Creates a clone of BroadcastOptions, using toBundle().
     */
    static BroadcastOptions cloneViaBundle(BroadcastOptions bo) {
        return BroadcastOptions.fromBundle(bo.toBundle());
    }

    private void assertBroadcastOptionTemporaryAppAllowList(
            BroadcastOptions bo,
            long expectedDuration,
            int expectedAllowListType,
            int expectedReasonCode,
            String expectedReason) {
        assertEquals(expectedAllowListType, bo.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, bo.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, bo.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, bo.getTemporaryAppAllowlistReason());

        // Clone the BO and check it too.
        BroadcastOptions cloned = cloneViaBundle(bo);
        assertEquals(expectedAllowListType, cloned.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, cloned.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, cloned.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, cloned.getTemporaryAppAllowlistReason());
    }

    private void assertBroadcastOption_noTemporaryAppAllowList(BroadcastOptions bo) {
        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 0,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_UNKNOWN,
                /* reason= */ null);
    }

    @Test
    public void testTemporaryAppAllowlistBroadcastOptions_noDefaultValues() {
        BroadcastOptions bo;

        // Check the default values about temp-allowlist.
        bo = BroadcastOptions.makeBasic();
        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testSetTemporaryAppWhitelistDuration_legacyApi() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();

        bo.setTemporaryAppWhitelistDuration(10);

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_UNKNOWN,
                /* reason= */ null);

        // Clear the temp-allowlist.
        bo.setTemporaryAppWhitelistDuration(0);

        // Check the default values about temp-allowlist.
        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testSetTemporaryAppWhitelistDuration() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();

        bo.setTemporaryAppAllowlist(10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                null);

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                /* reason= */ null);

        // Setting duration 0 will clear the previous call.
        bo.setTemporaryAppAllowlist(0,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_ACTIVITY_RECOGNITION,
                "reason");
        assertBroadcastOption_noTemporaryAppAllowList(bo);

        // Set again.
        bo.setTemporaryAppAllowlist(20,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                "reason");

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 20,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                /* reason= */ "reason");

        // Set to NONE will clear the previous call too.
        bo.setTemporaryAppAllowlist(10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_ACTIVITY_RECOGNITION,
                "reason");

        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testMaxManifestReceiverApiLevel() {
        final BroadcastOptions bo = BroadcastOptions.makeBasic();
        // No MaxManifestReceiverApiLevel set, the default value should be CUR_DEVELOPMENT.
        assertEquals(Build.VERSION_CODES.CUR_DEVELOPMENT, bo.getMaxManifestReceiverApiLevel());

        // Set MaxManifestReceiverApiLevel to P.
        bo.setMaxManifestReceiverApiLevel(Build.VERSION_CODES.P);
        assertEquals(Build.VERSION_CODES.P, bo.getMaxManifestReceiverApiLevel());

        // Clone the BroadcastOptions and check it too.
        final BroadcastOptions cloned = cloneViaBundle(bo);
        assertEquals(Build.VERSION_CODES.P, bo.getMaxManifestReceiverApiLevel());
    }

    @Test
    public void testGetPendingIntentBackgroundActivityLaunchAllowedDefault() {
        BroadcastOptions options = BroadcastOptions.makeBasic();
        // backwards compatibility
        assertTrue(options.isPendingIntentBackgroundActivityLaunchAllowed());
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedTrue() {
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(true);
        assertTrue(options.isPendingIntentBackgroundActivityLaunchAllowed());
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedFalse() {
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(false);
        assertFalse(options.isPendingIntentBackgroundActivityLaunchAllowed());
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityStartModeAllowed() {
        BroadcastOptions options = BroadcastOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        assertTrue(options.isPendingIntentBackgroundActivityLaunchAllowed());
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityStartModeDenied() {
        BroadcastOptions options = BroadcastOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        MODE_BACKGROUND_ACTIVITY_START_DENIED);
        assertFalse(options.isPendingIntentBackgroundActivityLaunchAllowed());
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    @Test
    public void testSetGetDeferralPolicy() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        assertEquals(BroadcastOptions.DEFERRAL_POLICY_DEFAULT,
                options.getDeferralPolicy());

        options.setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE);
        assertEquals(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE,
                options.getDeferralPolicy());

        final BroadcastOptions options2 = cloneViaBundle(options);
        assertEquals(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE,
                options2.getDeferralPolicy());

        options.clearDeferralPolicy();
        assertEquals(BroadcastOptions.DEFERRAL_POLICY_DEFAULT,
                options.getDeferralPolicy());
    }

    @Test
    public void testSetGetDeliveryGroupPolicy() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        final int defaultPolicy = options.getDeliveryGroupPolicy();

        options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
        assertEquals(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT,
                options.getDeliveryGroupPolicy());

        final BroadcastOptions options2 = cloneViaBundle(options);
        assertEquals(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT,
                options2.getDeliveryGroupPolicy());

        options.clearDeliveryGroupPolicy();
        assertEquals(defaultPolicy, options.getDeliveryGroupPolicy());

        // TODO(249160234): Verify the behavior of the set policy.
    }

    @Test
    public void testSetGetDeliveryGroupMatchingKey() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();

        final String namespace = "test_namespace";
        final String key = "test_key";
        options.setDeliveryGroupMatchingKey(namespace, key);
        assertEquals(String.join(":", namespace, key),
                options.getDeliveryGroupMatchingKey());

        final BroadcastOptions options2 = cloneViaBundle(options);
        assertEquals(String.join(":", namespace, key),
                options2.getDeliveryGroupMatchingKey());

        options.clearDeliveryGroupMatchingKey();
        assertNull(options.getDeliveryGroupMatchingKey());
    }

    @RequiresFlagsEnabled(FLAG_BCAST_EVENT_TIMESTAMPS)
    @Test
    public void testSetGetEventTriggerTimestampMillis() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();

        final long timestampMillis = System.currentTimeMillis();
        options.setEventTriggerTimestampMillis(timestampMillis);
        assertEquals(timestampMillis, options.getEventTriggerTimestampMillis());

        final BroadcastOptions options2 = cloneViaBundle(options);
        assertEquals(timestampMillis, options2.getEventTriggerTimestampMillis());
    }

    @RequiresFlagsEnabled(FLAG_BCAST_EVENT_TIMESTAMPS)
    @Test
    public void testSetGetRemoteEventTriggerTimestampMillis() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();

        final long timestampMillis = System.currentTimeMillis();
        options.setRemoteEventTriggerTimestampMillis(timestampMillis);
        assertEquals(timestampMillis, options.getRemoteEventTriggerTimestampMillis());

        final BroadcastOptions options2 = cloneViaBundle(options);
        assertEquals(timestampMillis, options2.getRemoteEventTriggerTimestampMillis());
    }
}

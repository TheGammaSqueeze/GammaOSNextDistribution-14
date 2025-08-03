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

package android.os.cts;

import static android.os.PowerManager.FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY;
import static android.os.PowerManager.LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL;
import static android.os.PowerManager.LOW_POWER_STANDBY_ALLOWED_REASON_TEMP_POWER_SAVE_ALLOWLIST;
import static android.os.PowerManager.LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION;
import static android.os.PowerManager.LowPowerStandbyPortDescription.MATCH_PORT_LOCAL;
import static android.os.PowerManager.LowPowerStandbyPortDescription.MATCH_PORT_REMOTE;
import static android.os.PowerManager.LowPowerStandbyPortDescription.PROTOCOL_TCP;
import static android.os.PowerManager.LowPowerStandbyPortDescription.PROTOCOL_UDP;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static android.os.PowerManager.SYSTEM_WAKELOCK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.PowerExemptionManager;
import android.os.PowerManager;
import android.os.PowerManager.LowPowerStandbyPolicy;
import android.os.PowerManager.LowPowerStandbyPortDescription;
import android.os.PowerManager.WakeLock;
import android.platform.test.annotations.AppModeFull;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.server.power.nano.PowerManagerServiceDumpProto;
import com.android.server.power.nano.WakeLockProto;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class LowPowerStandbyTest {
    private static final String DEVICE_CONFIG_NAMESPACE = "low_power_standby";
    private static final String DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY = "enable_policy";
    private static final String DEVICE_CONFIG_FEATURE_FLAG_ENABLE_STANDBY_PORTS =
            "enable_standby_ports";

    private static final int BROADCAST_TIMEOUT_SEC = 3;
    private static final int WAKELOCK_STATE_TIMEOUT = 1000;
    private static final long LOW_POWER_STANDBY_ACTIVATE_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    private static final String SYSTEM_WAKE_LOCK_TAG = "LowPowerStandbyTest:KeepSystemAwake";
    public static final String TEST_WAKE_LOCK_TAG = "LowPowerStandbyTest:TestWakeLock";
    private static final LowPowerStandbyPortDescription PORT_DESC_1 =
            new LowPowerStandbyPortDescription(PROTOCOL_UDP, MATCH_PORT_REMOTE, 1234);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private PowerManager mPowerManager;
    private LowPowerStandbyPolicy mOriginalPolicy;
    private boolean mOriginalEnabled;
    private WakeLock mSystemWakeLock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mOriginalEnabled = mPowerManager.isLowPowerStandbyEnabled();
        mOriginalPolicy = null;

        try (PermissionContext p = TestApis.permissions().withPermission(
                Manifest.permission.MANAGE_LOW_POWER_STANDBY)) {
            assumeTrue(mPowerManager.isLowPowerStandbySupported());
            mOriginalPolicy = mPowerManager.getLowPowerStandbyPolicy();

            // Reset to the default policy
            mPowerManager.setLowPowerStandbyPolicy(null);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mPowerManager != null) {
            try (PermissionContext p = TestApis.permissions().withPermission(
                    Manifest.permission.MANAGE_LOW_POWER_STANDBY)) {
                wakeUp();
                mPowerManager.setLowPowerStandbyEnabled(mOriginalEnabled);
                mPowerManager.forceLowPowerStandbyActive(false);
                mPowerManager.setLowPowerStandbyPolicy(mOriginalPolicy);
            }
        }
        unforceDoze();

        if (mSystemWakeLock != null) {
            mSystemWakeLock.release();
        }
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyEnabled")
    public void testSetLowPowerStandbyEnabled_withoutPermission_throwsSecurityException() {
        try {
            mPowerManager.setLowPowerStandbyEnabled(false);
            fail("PowerManager.setLowPowerStandbyEnabled() didn't throw SecurityException as "
                    + "expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyEnabled")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_withPermission_doesNotThrowsSecurityException() {
        mPowerManager.setLowPowerStandbyEnabled(false);
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#isLowPowerStandbyEnabled"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_reflectedByIsLowPowerStandbyEnabled() {
        mPowerManager.setLowPowerStandbyEnabled(true);
        assertTrue(mPowerManager.isLowPowerStandbyEnabled());

        mPowerManager.setLowPowerStandbyEnabled(false);
        assertFalse(mPowerManager.isLowPowerStandbyEnabled());
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyEnabled")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_sendsBroadcast() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(false);

        CallbackAsserter broadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED));
        mPowerManager.setLowPowerStandbyEnabled(true);
        broadcastAsserter.assertCalled(
                "ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED broadcast not received",
                BROADCAST_TIMEOUT_SEC);

        broadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED));
        mPowerManager.setLowPowerStandbyEnabled(false);
        broadcastAsserter.assertCalled(
                "ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED broadcast not received",
                BROADCAST_TIMEOUT_SEC);
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyEnabled")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.DEVICE_POWER})
    public void testLowPowerStandby_wakelockIsDisabled() throws Exception {
        keepSystemAwake();

        // Acquire test wakelock, which should be disabled by LPS
        WakeLock testWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, TEST_WAKE_LOCK_TAG);
        testWakeLock.acquire();

        mPowerManager.setLowPowerStandbyEnabled(true);
        goToSleep();
        mPowerManager.forceLowPowerStandbyActive(true);

        PollingCheck.check("Test wakelock not disabled", WAKELOCK_STATE_TIMEOUT,
                () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));
        PollingCheck.check("System wakelock is disabled", WAKELOCK_STATE_TIMEOUT,
                () -> !isWakeLockDisabled(SYSTEM_WAKE_LOCK_TAG));

        testWakeLock.release();
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyActiveDuringMaintenance"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.DEVICE_POWER})
    public void testSetLowPowerStandbyActiveDuringMaintenance() throws Exception {
        // Keep system awake with system wakelock
        keepSystemAwake();

        // Acquire test wakelock, which should be disabled by LPS
        WakeLock testWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK,
                TEST_WAKE_LOCK_TAG);
        testWakeLock.acquire();

        mPowerManager.setLowPowerStandbyEnabled(true);
        mPowerManager.setLowPowerStandbyActiveDuringMaintenance(true);

        goToSleep();
        forceDoze();

        PollingCheck.check(
                "Test wakelock still enabled, expected to be disabled by Low Power Standby",
                LOW_POWER_STANDBY_ACTIVATE_TIMEOUT, () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        enterDozeMaintenance();

        assertTrue(isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.setLowPowerStandbyActiveDuringMaintenance(false);
        PollingCheck.check(
                "Test wakelock disabled during doze maintenance, even though Low Power Standby "
                        + "should not be active during maintenance",
                WAKELOCK_STATE_TIMEOUT, () -> !isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.setLowPowerStandbyActiveDuringMaintenance(true);

        PollingCheck.check(
                "Test wakelock enabled during doze maintenance, even though Low Power Standby "
                        + "should be active during maintenance",
                WAKELOCK_STATE_TIMEOUT, () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        testWakeLock.release();
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyEnabled")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.DEVICE_POWER})
    public void testLowPowerStandby_networkIsBlocked() throws Exception {
        keepSystemAwake();

        NetworkBlockedStateAsserter asserter = new NetworkBlockedStateAsserter(mContext);
        asserter.register();

        try {
            mPowerManager.setLowPowerStandbyEnabled(true);
            goToSleep();
            mPowerManager.forceLowPowerStandbyActive(true);

            asserter.assertNetworkBlocked("Network is not blocked", true);

            wakeUp();
            mPowerManager.forceLowPowerStandbyActive(false);

            asserter.assertNetworkBlocked("Network is blocked after waking up", false);
        } finally {
            asserter.unregister();
        }
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isExemptFromLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isExempt_whenEnabled() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(true);
        mPowerManager.setLowPowerStandbyPolicy(emptyPolicy());
        assertFalse(mPowerManager.isExemptFromLowPowerStandby());

        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        assertTrue(mPowerManager.isExemptFromLowPowerStandby());

        mPowerManager.setLowPowerStandbyPolicy(new LowPowerStandbyPolicy(
                "testLowPowerStandby_isExempt",
                new ArraySet<>(new String[]{"some.other.package"}),
                0,
                Collections.emptySet()
        ));
        assertFalse(mPowerManager.isExemptFromLowPowerStandby());
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isExemptFromLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isExempt_whenDisabled() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(false);
        mPowerManager.setLowPowerStandbyPolicy(emptyPolicy());
        assertTrue(mPowerManager.isExemptFromLowPowerStandby());
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#getLowPowerStandbyPolicy"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_getPolicy() throws Exception {
        LowPowerStandbyPolicy policy = new LowPowerStandbyPolicy(
                "testLowPowerStandby_getPolicy",
                new ArraySet<>(new String[]{"package1", "package2"}),
                123,
                new ArraySet<>(new String[]{"feature1", "feature2"})
        );
        mPowerManager.setLowPowerStandbyPolicy(policy);
        assertEquals(policy, mPowerManager.getLowPowerStandbyPolicy());

        mPowerManager.setLowPowerStandbyPolicy(null);
        assertNotEquals(policy, mPowerManager.getLowPowerStandbyPolicy());
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyPolicy")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_setPolicy_changeBroadcastIsSent() throws Exception {
        CallbackAsserter broadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED));
        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        broadcastAsserter.assertCalled(
                "ACTION_LOW_POWER_STANDBY_POLICY_CHANGED broadcast not received",
                BROADCAST_TIMEOUT_SEC);
    }

    @Test
    @ApiTest(apis = "android.os.PowerManager#setLowPowerStandbyPolicy")
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_setPolicy_exemptPackageIsNotRestricted() throws Exception {
        WakeLock testWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, TEST_WAKE_LOCK_TAG);
        testWakeLock.acquire();

        mPowerManager.forceLowPowerStandbyActive(true);
        PollingCheck.check("Test wakelock not disabled",
                WAKELOCK_STATE_TIMEOUT, () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        PollingCheck.check("Test wakelock disabled, though package should be exempt",
                WAKELOCK_STATE_TIMEOUT, () -> !isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        testWakeLock.release();
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testLowPowerStandby_isAllowedReason_trueIfDisabled() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(false);
        assertTrue(mPowerManager.isAllowedInLowPowerStandby(
                LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isAllowedReason_falseIfNotAllowed() throws Exception {
        mPowerManager.setLowPowerStandbyPolicy(emptyPolicy());
        mPowerManager.setLowPowerStandbyEnabled(true);
        assertFalse(mPowerManager.isAllowedInLowPowerStandby(
                LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isAllowedReason_trueIfAllowed() throws Exception {
        mPowerManager.setLowPowerStandbyPolicy(policyWithAllowedReasons(
                LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION));
        mPowerManager.setLowPowerStandbyEnabled(true);
        assertTrue(mPowerManager.isAllowedInLowPowerStandby(
                LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testLowPowerStandby_isAllowedFeature_trueIfDisabled() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(false);
        assertTrue(
                mPowerManager.isAllowedInLowPowerStandby(FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isAllowedFeature_falseIfNotAllowed() throws Exception {
        mPowerManager.setLowPowerStandbyPolicy(emptyPolicy());
        mPowerManager.setLowPowerStandbyEnabled(true);
        assertFalse(mPowerManager.isAllowedInLowPowerStandby(
                FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#isAllowedInLowPowerStandby"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_isAllowedFeature_trueIfAllowed() throws Exception {
        mPowerManager.setLowPowerStandbyPolicy(policyWithAllowedFeatures(
                FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY));
        mPowerManager.setLowPowerStandbyEnabled(true);
        assertTrue(
                mPowerManager.isAllowedInLowPowerStandby(FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#LOW_POWER_STANDBY_ALLOWED_REASON_TEMP_POWER_SAVE_ALLOWLIST"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_allowedReason_tempPowerSaveAllowlist() throws Exception {
        WakeLock testWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, TEST_WAKE_LOCK_TAG);
        testWakeLock.acquire();
        PollingCheck.check("Test wakelock disabled", WAKELOCK_STATE_TIMEOUT,
                () -> !isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.forceLowPowerStandbyActive(true);
        PollingCheck.check("Test wakelock not disabled", WAKELOCK_STATE_TIMEOUT,
                () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.setLowPowerStandbyPolicy(policyWithAllowedReasons(
                LOW_POWER_STANDBY_ALLOWED_REASON_TEMP_POWER_SAVE_ALLOWLIST));
        assertTrue("Test wakelock not disabled", isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        PowerExemptionManager powerExemptionManager =
                mContext.getSystemService(PowerExemptionManager.class);
        try (PermissionContext p = TestApis.permissions().withPermission(
                Manifest.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST)) {
            powerExemptionManager.addToTemporaryAllowList(mContext.getPackageName(),
                    PowerExemptionManager.REASON_OTHER, "", 5000);
        }
        PollingCheck.check("Test wakelock disabled, though UID should be exempt",
                WAKELOCK_STATE_TIMEOUT, () -> !isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        testWakeLock.release();
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_allowedReason_ongoingCall_phoneCallServiceExempt()
            throws Exception {
        Intent intent = LowPowerStandbyForegroundService.createIntentWithForegroundServiceType(
                mContext, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        mContext.startForegroundService(intent);

        mPowerManager.forceLowPowerStandbyActive(true);
        PollingCheck.check("Test wakelock not disabled", WAKELOCK_STATE_TIMEOUT,
                () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mPowerManager.setLowPowerStandbyPolicy(
                policyWithAllowedReasons(LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL));
        PollingCheck.check("Test wakelock disabled, though UID should be exempt",
                WAKELOCK_STATE_TIMEOUT, () -> !isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mContext.stopService(intent);
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    public void testLowPowerStandby_allowedReason_ongoingCall_otherFgsServiceNotExempt()
            throws Exception {
        Intent intent = LowPowerStandbyForegroundService.createIntentWithForegroundServiceType(
                mContext, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        mContext.startForegroundService(intent);
        mPowerManager.setLowPowerStandbyPolicy(
                policyWithAllowedReasons(LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL));
        mPowerManager.forceLowPowerStandbyActive(true);

        PollingCheck.check("Test wakelock not disabled", WAKELOCK_STATE_TIMEOUT,
                () -> isWakeLockDisabled(TEST_WAKE_LOCK_TAG));

        mContext.stopService(intent);
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#newLowPowerStandbyPortsLock",
            "android.os.PowerManager#getActiveLowPowerStandbyPorts"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.SET_LOW_POWER_STANDBY_PORTS})
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_STANDBY_PORTS)
    public void testActiveStandbyPorts_disabled() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(false);
        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        PowerManager.LowPowerStandbyPortsLock standbyPorts =
                mPowerManager.newLowPowerStandbyPortsLock(List.of(PORT_DESC_1));
        standbyPorts.acquire();

        assertThat(mPowerManager.getActiveLowPowerStandbyPorts()).doesNotContain(PORT_DESC_1);
        standbyPorts.release();
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#newLowPowerStandbyPortsLock",
            "android.os.PowerManager#getActiveLowPowerStandbyPorts"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.SET_LOW_POWER_STANDBY_PORTS})
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_STANDBY_PORTS)
    public void testActiveStandbyPorts_notExempt() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(true);

        PowerManager.LowPowerStandbyPortsLock standbyPorts =
                mPowerManager.newLowPowerStandbyPortsLock(List.of(PORT_DESC_1));
        standbyPorts.acquire();

        assertThat(mPowerManager.getActiveLowPowerStandbyPorts()).doesNotContain(PORT_DESC_1);
        standbyPorts.release();
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#newLowPowerStandbyPortsLock",
            "android.os.PowerManager#getActiveLowPowerStandbyPorts"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.SET_LOW_POWER_STANDBY_PORTS})
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_STANDBY_PORTS)
    public void testActiveStandbyPorts_exempt() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(true);
        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        PowerManager.LowPowerStandbyPortsLock standbyPorts =
                mPowerManager.newLowPowerStandbyPortsLock(List.of(PORT_DESC_1));
        standbyPorts.acquire();

        assertThat(mPowerManager.getActiveLowPowerStandbyPorts()).contains(PORT_DESC_1);
        standbyPorts.release();
    }

    @Test
    @ApiTest(apis = {
            "android.os.PowerManager.LowPowerStandbyPortDescription#getProtocol",
            "android.os.PowerManager.LowPowerStandbyPortDescription#getPortMatcher",
            "android.os.PowerManager.LowPowerStandbyPortDescription#getPortNumber",
            "android.os.PowerManager.LowPowerStandbyPortDescription#getLocalAddress"
    })
    public void testPortDescriptionGetters() throws Exception {
        int protocol = PROTOCOL_UDP;
        int portMatcher = MATCH_PORT_LOCAL;
        int portNumber = 5555;
        LowPowerStandbyPortDescription portDesc =
                new LowPowerStandbyPortDescription(protocol, portMatcher, portNumber);

        assertThat(portDesc.getProtocol()).isEqualTo(protocol);
        assertThat(portDesc.getPortMatcher()).isEqualTo(portMatcher);
        assertThat(portDesc.getPortNumber()).isEqualTo(portNumber);
        assertThat(portDesc.getLocalAddress()).isEqualTo(null);

        protocol = PROTOCOL_TCP;
        portMatcher = MATCH_PORT_REMOTE;
        portNumber = 433;
        InetAddress localAddress = InetAddress.getByName("192.168.5.5");
        portDesc =
                new LowPowerStandbyPortDescription(protocol, portMatcher, portNumber, localAddress);

        assertThat(portDesc.getProtocol()).isEqualTo(protocol);
        assertThat(portDesc.getPortMatcher()).isEqualTo(portMatcher);
        assertThat(portDesc.getPortNumber()).isEqualTo(portNumber);
        assertThat(portDesc.getLocalAddress()).isEqualTo(localAddress);
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#setLowPowerStandbyEnabled",
            "android.os.PowerManager#setLowPowerStandbyPolicy",
            "android.os.PowerManager#newLowPowerStandbyPortsLock",
            "android.os.PowerManager#getActiveLowPowerStandbyPorts"})
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.SET_LOW_POWER_STANDBY_PORTS})
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_POLICY)
    @EnsureFeatureFlagEnabled(namespace = DEVICE_CONFIG_NAMESPACE,
            key = DEVICE_CONFIG_FEATURE_FLAG_ENABLE_STANDBY_PORTS)
    public void testActiveStandbyPorts_becomesActiveOnceExempt() throws Exception {
        mPowerManager.setLowPowerStandbyEnabled(true);
        PowerManager.LowPowerStandbyPortsLock standbyPorts =
                mPowerManager.newLowPowerStandbyPortsLock(List.of(PORT_DESC_1));
        standbyPorts.acquire();

        mPowerManager.setLowPowerStandbyPolicy(ownPackageExemptPolicy());
        assertThat(mPowerManager.getActiveLowPowerStandbyPorts()).contains(PORT_DESC_1);
        standbyPorts.release();
    }

    private LowPowerStandbyPolicy emptyPolicy() {
        return new LowPowerStandbyPolicy(
                "CTS: LowPowerStandbyTest empty policy",
                Collections.emptySet(),
                0,
                Collections.emptySet()
        );
    }

    private LowPowerStandbyPolicy ownPackageExemptPolicy() {
        return new LowPowerStandbyPolicy(
                "CTS: LowPowerStandbyTest own package exempt policy",
                new ArraySet<>(new String[]{mContext.getPackageName()}),
                0,
                Collections.emptySet()
        );
    }

    private LowPowerStandbyPolicy policyWithAllowedReasons(int allowedReasons) {
        return new LowPowerStandbyPolicy(
                "CTS: LowPowerStandbyTest policy",
                Collections.emptySet(),
                allowedReasons,
                Collections.emptySet()
        );
    }

    private LowPowerStandbyPolicy policyWithAllowedFeatures(String... allowedFeatures) {
        return new LowPowerStandbyPolicy(
                "CTS: LowPowerStandbyTest policy",
                Collections.emptySet(),
                0,
                new ArraySet<>(allowedFeatures)
        );
    }

    private void goToSleep() throws Exception {
        if (!mPowerManager.isInteractive()) {
            return;
        }

        final BlockingBroadcastReceiver screenOffReceiver = new BlockingBroadcastReceiver(mContext,
                Intent.ACTION_SCREEN_OFF);
        screenOffReceiver.register();

        executeShellCommand("input keyevent SLEEP");

        screenOffReceiver.awaitForBroadcast(1000);
        screenOffReceiver.unregisterQuietly();
    }

    private void wakeUp() throws Exception {
        if (mPowerManager.isInteractive()) {
            return;
        }

        final BlockingBroadcastReceiver screenOnReceiver = new BlockingBroadcastReceiver(mContext,
                Intent.ACTION_SCREEN_ON);
        screenOnReceiver.register();

        executeShellCommand("input keyevent WAKEUP");

        screenOnReceiver.awaitForBroadcast(1000);
        screenOnReceiver.unregisterQuietly();
    }

    private void forceDoze() throws Exception {
        executeShellCommand("dumpsys deviceidle force-idle deep");
    }

    private void unforceDoze() throws Exception {
        executeShellCommand("dumpsys deviceidle unforce");
    }

    private void enterDozeMaintenance() throws Exception {
        executeShellCommand("dumpsys deviceidle force-idle deep");

        for (int i = 0; i < 4; i++) {
            String stepResult = executeShellCommand("dumpsys deviceidle step deep");
            if (stepResult != null && stepResult.contains("IDLE_MAINTENANCE")) {
                return;
            }
        }

        fail("Failed to enter doze maintenance mode");
    }

    private boolean isWakeLockDisabled(@NonNull String tag) throws Exception {
        final PowerManagerServiceDumpProto powerManagerServiceDump = getPowerManagerDump();
        for (WakeLockProto wakelock : powerManagerServiceDump.wakeLocks) {
            if (tag.equals(wakelock.tag)) {
                return wakelock.isDisabled;
            }
        }
        throw new IllegalStateException("WakeLock " + tag + " is not held");
    }

    private static PowerManagerServiceDumpProto getPowerManagerDump() throws Exception {
        return ProtoUtils.getProto(InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                PowerManagerServiceDumpProto.class, "dumpsys power --proto");
    }

    private void keepSystemAwake() {
        mSystemWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK | SYSTEM_WAKELOCK,
                SYSTEM_WAKE_LOCK_TAG);
        mSystemWakeLock.acquire();
    }

    private String executeShellCommand(String command) throws IOException {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        return uiDevice.executeShellCommand(command);
    }

    private static class NetworkBlockedStateAsserter {
        private final ConnectivityManager mConnectivityManager;
        private final ConnectivityManager.NetworkCallback mNetworkCallback;

        private final Object mLock = new Object();
        private boolean mIsBlocked = false;

        NetworkBlockedStateAsserter(Context context) {
            mConnectivityManager = context.getSystemService(ConnectivityManager.class);
            mNetworkCallback =
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onBlockedStatusChanged(Network network, boolean blocked) {
                            synchronized (mLock) {
                                if (mIsBlocked != blocked) {
                                    mIsBlocked = blocked;
                                    mLock.notify();
                                }
                            }
                        }
                    };
        }

        private void register() {
            mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
        }

        private void assertNetworkBlocked(String message, boolean expected) throws Exception {
            synchronized (mLock) {
                if (mIsBlocked == expected) {
                    return;
                }
                mLock.wait(5000);
                assertEquals(message, expected, mIsBlocked);
            }
        }

        private void unregister() {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
    }
}

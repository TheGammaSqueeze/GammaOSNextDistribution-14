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

package com.android.cts.scheduling;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.provider.DeviceConfig;
import android.scheduling.RebootReadinessManager;
import android.scheduling.RebootReadinessManager.RebootReadinessStatus;
import android.scheduling.RebootReadinessManager.RequestRebootReadinessStatusListener;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test system RebootReadinessManager APIs.
 */
@RunWith(JUnit4.class)
public class RebootReadinessManagerTest {

    private static class RebootCallback implements RequestRebootReadinessStatusListener {
        private final boolean mIsReadyToReboot;
        private final long mEstimatedFinishTime;
        private final String mSubsystemName;

        RebootCallback(boolean isReadyToReboot, long estimatedFinishTime, String subsystemName) {
            mIsReadyToReboot = isReadyToReboot;
            mEstimatedFinishTime = estimatedFinishTime;
            mSubsystemName = subsystemName;
        }

        @Override
        public RebootReadinessStatus onRequestRebootReadinessStatus() {
            return new RebootReadinessStatus(mIsReadyToReboot, mEstimatedFinishTime,
                    mSubsystemName);
        }
    }

    private static final String TEST_CALLBACK_PREFIX = "TESTCOMPONENT";

    private static final RequestRebootReadinessStatusListener BLOCKING_CALLBACK =
            new RebootCallback(false, 0, TEST_CALLBACK_PREFIX + ": blocking component");
    private static final RequestRebootReadinessStatusListener READY_CALLBACK = new RebootCallback(
            true, 0, TEST_CALLBACK_PREFIX + ": non-blocking component");

    private static final String PROPERTY_ACTIVE_POLLING_INTERVAL_MS = "active_polling_interval_ms";
    private static final String PROPERTY_DISABLE_INTERACTIVITY_CHECK =
            "disable_interactivity_check";
    private static final String PROPERTY_INTERACTIVITY_THRESHOLD_MS = "interactivity_threshold_ms";
    private static final String PROPERTY_DISABLE_APP_ACTIVITY_CHECK = "disable_app_activity_check";
    private static final String PROPERTY_DISABLE_SUBSYSTEMS_CHECK = "disable_subsystems_check";

    RebootReadinessManager mRebootReadinessManager =
            (RebootReadinessManager) InstrumentationRegistry.getContext().getSystemService(
                    Context.REBOOT_READINESS_SERVICE);

    private static final HandlerThread sThread = new HandlerThread("RebootReadinessManagerTest");
    private static HandlerExecutor sHandlerExecutor;

    @BeforeClass
    public static void setupClass() throws Exception {
        sThread.start();
        sHandlerExecutor = new HandlerExecutor(sThread.getThreadHandler());
        adoptShellPermissions();
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_ACTIVE_POLLING_INTERVAL_MS, "1000", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_INTERACTIVITY_CHECK, "true", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_APP_ACTIVITY_CHECK, "true", false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_SUBSYSTEMS_CHECK, "true", false);
        // Small delay to allow DeviceConfig changes to propagate.
        Thread.sleep(1000);
    }

    @After
    public void tearDown() {
        mRebootReadinessManager.removeRequestRebootReadinessStatusListener(READY_CALLBACK);
        mRebootReadinessManager.removeRequestRebootReadinessStatusListener(BLOCKING_CALLBACK);
        mRebootReadinessManager.cancelPendingReboot();
    }

    @AfterClass
    public static void teardownClass() {
        sThread.quitSafely();
        dropShellPermissions();
    }

    @Test
    public void testRegisterAndUnregisterCallback() throws Exception {
        assertThat(isReadyToReboot()).isTrue();
        mRebootReadinessManager.cancelPendingReboot();

        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, BLOCKING_CALLBACK);
        assertThat(isReadyToReboot()).isFalse();
        mRebootReadinessManager.removeRequestRebootReadinessStatusListener(BLOCKING_CALLBACK);
        mRebootReadinessManager.cancelPendingReboot();
        assertThat(isReadyToReboot()).isTrue();
    }

    @Test
    public void testCallbackReadyToReboot() throws Exception {
        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, READY_CALLBACK);
        CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean extra = intent.getBooleanExtra(
                        RebootReadinessManager.EXTRA_IS_READY_TO_REBOOT, false);
                assertThat(extra).isEqualTo(true);
                latch.countDown();
            }
        };
        InstrumentationRegistry.getContext().registerReceiver(receiver,
                new IntentFilter(RebootReadinessManager.ACTION_REBOOT_READY));
        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, READY_CALLBACK);
        assertThat(isReadyToReboot()).isTrue();
        InstrumentationRegistry.getContext().unregisterReceiver(receiver);
    }

    @Test
    public void testCallbackNotReadyToReboot() throws Exception {
        assertThat(isReadyToReboot()).isTrue();
        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, READY_CALLBACK);
        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, BLOCKING_CALLBACK);
        mRebootReadinessManager.cancelPendingReboot();
        assertThat(isReadyToReboot()).isFalse();
    }

    @Test
    public void testRebootPermissionCheck() {
        dropShellPermissions();
        try {
            mRebootReadinessManager.markRebootPending();
            fail("Expected to throw SecurityException");
        } catch (SecurityException expected) {
        } finally {
            adoptShellPermissions();
        }
    }

    @Test
    public void testSignalRebootReadinessPermissionCheck() {
        dropShellPermissions();
        try {
            mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                    sHandlerExecutor, READY_CALLBACK);
            fail("Expected to throw SecurityException");
        } catch (SecurityException expected) {
        } finally {
            adoptShellPermissions();
        }
    }


    @Test
    public void testCancelPendingReboot() throws Exception {
        mRebootReadinessManager.addRequestRebootReadinessStatusListener(
                sHandlerExecutor, BLOCKING_CALLBACK);
        mRebootReadinessManager.markRebootPending();
        mRebootReadinessManager.cancelPendingReboot();
        CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                fail("Reboot readiness checks should be cancelled so no broadcast should be sent.");
            }
        };
        InstrumentationRegistry.getContext().registerReceiver(receiver,
                new IntentFilter(RebootReadinessManager.ACTION_REBOOT_READY));
        mRebootReadinessManager.removeRequestRebootReadinessStatusListener(BLOCKING_CALLBACK);

        // Ensure that no broadcast is received when reboot readiness checks are canceled.
        latch.await(10, TimeUnit.SECONDS);
        assertThat(latch.getCount()).isEqualTo(1);
        InstrumentationRegistry.getContext().unregisterReceiver(receiver);
    }

    @Test
    public void testCancelPendingRebootWhenNotRegistered() {
        // Ensure that the process does not crash or throw an exception
        mRebootReadinessManager.cancelPendingReboot();
    }

    @Test
    public void testDisableInteractivityCheck() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_INTERACTIVITY_THRESHOLD_MS, Long.toString(Long.MAX_VALUE), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_INTERACTIVITY_CHECK, "false", false);
        // Allow a small amount of time for DeviceConfig changes to be noted.
        Thread.sleep(1000);
        assertThat(isReadyToReboot()).isFalse();
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                PROPERTY_DISABLE_INTERACTIVITY_CHECK, "true", false);
        // Allow a small amount of time for DeviceConfig changes to be noted.
        Thread.sleep(1000);
        assertThat(isReadyToReboot()).isTrue();
    }

    @Test
    public void testRebootReadinessStatus() {
        RebootReadinessStatus status = new RebootReadinessStatus(false, 1000, "test");
        assertThat(status.isReadyToReboot()).isFalse();
        assertThat(status.getEstimatedFinishTime()).isEqualTo(1000);
        assertThat(status.getLogSubsystemName()).isEqualTo("test");
    }

    @Test
    public void testRebootReadinessStatusWithEmptyNameThrowsException() {
        try {
            RebootReadinessStatus status = new RebootReadinessStatus(false, 1000, "");
            fail("Expected to throw exception when an empty name is supplied.");
        } catch (IllegalArgumentException expected) {
        }
    }

    private boolean isReadyToReboot() throws Exception {
        mRebootReadinessManager.markRebootPending();
        // Add a small timeout to allow the reboot readiness state to be noted.
        // TODO(b/161353402): Negate the need for this timeout.
        Thread.sleep(1000);
        return mRebootReadinessManager.isReadyToReboot();
    }

    private static void adoptShellPermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.REBOOT, Manifest.permission.WRITE_DEVICE_CONFIG,
                Manifest.permission.SIGNAL_REBOOT_READINESS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }
}

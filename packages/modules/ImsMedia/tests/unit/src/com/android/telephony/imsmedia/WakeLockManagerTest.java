/**
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
package com.android.telephony.imsmedia;

import static com.google.common.truth.Truth.assertThat;

import android.telephony.imsmedia.RtpConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WakeLockManagerTest {
    private static final int TEST_SESSION_1_ID = 1;
    private static final int TEST_SESSION_2_ID = 2;
    private WakeLockManager mWakeLockManager;

    @Before
    public void setUp() throws Exception {
        mWakeLockManager = WakeLockManager.getInstance();
        assertThat(mWakeLockManager).isNotEqualTo(null);
    }

    @After
    public void tearDown() throws Exception {
        mWakeLockManager.cleanup();
    }

    @Test
    public void testOneSession_MediaDirectionChanges() {
        // Check NO_FLOW -> SEND_RECEIVE
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check SEND_RECEIVE -> NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(TEST_SESSION_1_ID,
                RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);

        // Check NO_FLOW -> SEND_ONLY
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_ONLY);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check SEND_ONLY -> NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(TEST_SESSION_1_ID,
                RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);

        // Check NO_FLOW -> RECEIVE_ONLY
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_RECEIVE_ONLY);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check RECEIVE_ONLY -> NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(TEST_SESSION_1_ID,
                RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);

        // Check NO_FLOW -> INACTIVE
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_INACTIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check INACTIVE -> NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(TEST_SESSION_1_ID,
                RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);

        // Check SEND_RECEIVE -> SEND_ONLY
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(TEST_SESSION_1_ID,
                RtpConfig.MEDIA_DIRECTION_SEND_ONLY);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check SEND_ONLY -> RECEIVE_ONLY
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_RECEIVE_ONLY);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check RECEIVE_ONLY -> INACTIVE
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_INACTIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Check INACTIVE -> SEND_RECEIVE
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);
    }

    @Test
    public void testTwoSessions_MediaDirectionChanges() {
        // Session#1: NO_FLOW -> SEND_RECEIVE
        // Session#2: NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_2_ID, RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Session#1: SEND_RECEIVE -> NO_FLOW
        // Session#2: NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);

        // Session#1: NO_FLOW
        // Session#2: NO_FLOW -> SEND_RECEIVE
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_2_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Session#1: NO_FLOW
        // Session#2: SEND_RECEIVE -> NO_FLOW
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_2_ID, RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testCleanup() {
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_2_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        mWakeLockManager.cleanup();
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testCleanupForSessionLeak() {
        // 1. Acquired wakelock and add session to list.
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // 2. Increases ref-count of wakelock without adding session to list in WakeLockManager.
        mWakeLockManager.mWakeLock.acquire();

        // 3. Should detect leak and trigger cleanup. Wakelock should be released.
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_NO_FLOW);

        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testExtraWakecallRelease() {
        // 1. Acquired wakelock and add session to list.
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE);
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // 2. Decrease ref-count of wakelock without removing session from list in WakeLockManager.
        mWakeLockManager.mWakeLock.release();

        // 3. Should handle exception because of extra Wakelock.release call.
        mWakeLockManager.manageWakeLockOnMediaDirectionUpdate(
                TEST_SESSION_1_ID, RtpConfig.MEDIA_DIRECTION_NO_FLOW);

        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }
}

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

package com.android.car.systeminterface;

import static com.android.car.systeminterface.SystemStateInterface.DefaultImpl.getRandomizedDelay;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.car.test.util.TemporaryFile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

/**
 * Unit tests for {@link SystemStateInterface}
 *
 * Run:
 * atest SystemStateInterfaceTest
 */
public final class SystemStateInterfaceTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = SystemStateInterfaceTest.class.getSimpleName();

    @Mock
    private Context mMockContext;
    @Mock
    private UserManager mMockUserManager;
    @Captor
    private ArgumentCaptor<BroadcastReceiver> mReceiverCaptor;
    private SystemStateInterface.DefaultImpl mSystemStateInterface;

    public SystemStateInterfaceTest() {
        super(SystemStateInterface.TAG);
    }

    @Before
    public void setUp() throws IOException {
        mSystemStateInterface = new SystemStateInterface.DefaultImpl(mMockContext);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(SystemPowerControlHelper.class);
    }

    @Test
    public void testSleepWhenHelperSucceeds() throws Exception {
        mockGetSysFsPowerControlFile();

        assertThat(mSystemStateInterface.enterDeepSleep()).isTrue();
    }

    @Test
    public void testSleepWhenHelperFails() {
        ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn("");

        assertThat(mSystemStateInterface.enterDeepSleep()).isFalse();
    }

    @Test
    public void testHibernateWhenHelperSucceeds() throws Exception {
        mockGetSysFsPowerControlFile();

        assertThat(mSystemStateInterface.enterHibernation()).isTrue();
    }

    private void mockGetSysFsPowerControlFile() throws Exception {
        assertSpied(SystemPowerControlHelper.class);

        try (TemporaryFile powerStateControlFile = new TemporaryFile(TAG)) {
            ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn(
                    powerStateControlFile.getFile().getAbsolutePath());
        }
    }

    @Test
    public void testHibernateWhenHelperFails() {
        ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn("");

        assertThat(mSystemStateInterface.enterHibernation()).isFalse();
    }

    @Test
    public void testScheduleActionForBootCompleted() throws Exception {
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isUserUnlocked()).thenReturn(false);
        CountDownLatch actionCompleted = new CountDownLatch(1);

        mSystemStateInterface.scheduleActionForBootCompleted(() -> {
            actionCompleted.countDown();
        }, Duration.ofMillis(100));

        verify(mMockContext).registerReceiver(mReceiverCaptor.capture(), any(), anyInt());

        SystemClock.sleep(100);

        assertWithMessage("action must not run if boot not completed").that(
                actionCompleted.getCount()).isEqualTo(1);

        BroadcastReceiver receiver = mReceiverCaptor.getValue();
        receiver.onReceive(mMockContext, new Intent(Intent.ACTION_BOOT_COMPLETED));

        JavaMockitoHelper.await(actionCompleted, /* timeoutMs= */ 1000);
    }

    @Test
    public void testScheduleActionForBootCompleted_receivedIntent() throws Exception {
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isUserUnlocked()).thenReturn(false);
        CountDownLatch actionCompleted = new CountDownLatch(2);

        mSystemStateInterface.scheduleActionForBootCompleted(() -> {
            actionCompleted.countDown();
        }, Duration.ofMillis(100));
        verify(mMockContext).registerReceiver(mReceiverCaptor.capture(), any(), anyInt());
        BroadcastReceiver receiver = mReceiverCaptor.getValue();
        receiver.onReceive(mMockContext, new Intent(Intent.ACTION_BOOT_COMPLETED));
        // After we received the boot completed intent, we should still invoke the action.
        mSystemStateInterface.scheduleActionForBootCompleted(() -> {
            actionCompleted.countDown();
        }, Duration.ofMillis(100));

        JavaMockitoHelper.await(actionCompleted, /* timeoutMs= */ 1000);
    }

    @Test
    public void testScheduleActionForBootCompleted_userUnlocked() throws Exception {
        // If car service (including SystemStateInterface) restarts after bootup complete, then
        // it will never receive the intent again. However, isUserUnlocked should be true.
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        CountDownLatch actionCompleted = new CountDownLatch(1);

        mSystemStateInterface.scheduleActionForBootCompleted(() -> {
            actionCompleted.countDown();
        }, Duration.ofMillis(100));

        JavaMockitoHelper.await(actionCompleted, /* timeoutMs= */ 1000);
    }

    @Test
    public void testGetRandomizedDelay() throws Exception {
        assertThat(getRandomizedDelay(Duration.ZERO, Duration.ZERO)).isEqualTo(Duration.ZERO);

        Duration randomizedDelay = getRandomizedDelay(Duration.ZERO, Duration.ofMillis(1000));
        assertWithMessage("Minimum delay duration").that(randomizedDelay).isAtLeast(Duration.ZERO);
        assertWithMessage("Maximum delay duration").that(randomizedDelay)
                .isLessThan(Duration.ofMillis(1000));

        randomizedDelay = getRandomizedDelay(/* delay= */ Duration.ofMillis(2000),
                /* delayRange= */ Duration.ofMillis(1000));
        assertWithMessage("Minimum delay duration").that(randomizedDelay)
                .isAtLeast(Duration.ofMillis(1000));
        assertWithMessage("Maximum delay duration").that(randomizedDelay)
                .isLessThan(Duration.ofMillis(3000));
    }
}

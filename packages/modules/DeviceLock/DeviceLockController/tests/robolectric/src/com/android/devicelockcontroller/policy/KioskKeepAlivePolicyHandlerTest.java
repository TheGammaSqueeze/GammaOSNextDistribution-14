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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.os.Bundle;
import android.os.OutcomeReceiver;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class KioskKeepAlivePolicyHandlerTest {
    private static final String TEST_KIOSK_PACKAGE = "test.package1";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private SystemDeviceLockManager mSystemDeviceLockManager;
    private KioskKeepAlivePolicyHandler mHandler;

    @Before
    public void setUp() throws ExecutionException, InterruptedException {
        mHandler = new KioskKeepAlivePolicyHandler(mSystemDeviceLockManager,
                Executors.newSingleThreadExecutor());
        setupSetupParameters();
    }

    @Test
    public void onProvisioned_enablesKioskKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnEnableKioskKeepalive(/* isSuccess =*/ true);
        assertThat(mHandler.onProvisioned().get()).isTrue();
        verify(mSystemDeviceLockManager).enableKioskKeepalive(eq(TEST_KIOSK_PACKAGE),
                any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).disableKioskKeepalive(any(Executor.class), any());
    }

    @Test
    public void onProvisioned_onFailure_stillReturnsTrue()
            throws ExecutionException, InterruptedException {
        setExpectationsOnEnableKioskKeepalive(/* isSuccess =*/ false);
        assertThat(mHandler.onProvisioned().get()).isTrue();
        verify(mSystemDeviceLockManager).enableKioskKeepalive(eq(TEST_KIOSK_PACKAGE),
                any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).disableKioskKeepalive(any(Executor.class), any());
    }

    @Test
    public void onCleared_disablesKioskKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableKioskKeepalive(/* isSuccess =*/ true);
        assertThat(mHandler.onCleared().get()).isTrue();
        verify(mSystemDeviceLockManager).disableKioskKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableKioskKeepalive(eq(TEST_KIOSK_PACKAGE),
                any(Executor.class), any());
    }

    @Test
    public void onCleared_onFailure_stillReturnsTrue()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableKioskKeepalive(/* isSuccess =*/ false);
        assertThat(mHandler.onCleared().get()).isTrue();
        verify(mSystemDeviceLockManager).disableKioskKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableKioskKeepalive(eq(TEST_KIOSK_PACKAGE),
                any(Executor.class), any());
    }

    @Test
    public void onProvisionInProgress_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onProvisionPaused_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionPaused().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onProvisionFailed_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionFailed().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onLocked_shouldDoNothing() throws ExecutionException, InterruptedException {
        assertThat(mHandler.onLocked().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onUnlocked_shouldDoNothing() throws ExecutionException, InterruptedException {
        assertThat(mHandler.onUnlocked().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    private void setExpectationsOnEnableKioskKeepalive(boolean isSuccess) {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 2);
            if (isSuccess) {
                callback.onResult(/* result =*/ null);
            } else {
                callback.onError(new Exception());
            }
            return null;
        }).when(mSystemDeviceLockManager).enableKioskKeepalive(anyString(),
                any(Executor.class), any());
    }


    private void setExpectationsOnDisableKioskKeepalive(boolean isSuccess) {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            if (isSuccess) {
                callback.onResult(/* result =*/ null);
            } else {
                callback.onError(new Exception());
            }
            return null;
        }).when(mSystemDeviceLockManager).disableKioskKeepalive(any(Executor.class), any());
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }
}

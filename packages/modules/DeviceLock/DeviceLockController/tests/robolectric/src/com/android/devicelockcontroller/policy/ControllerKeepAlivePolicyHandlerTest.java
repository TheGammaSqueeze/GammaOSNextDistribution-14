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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.os.OutcomeReceiver;

import com.android.devicelockcontroller.SystemDeviceLockManager;

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
public final class ControllerKeepAlivePolicyHandlerTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private SystemDeviceLockManager mSystemDeviceLockManager;
    private ControllerKeepAlivePolicyHandler mHandler;

    @Before
    public void setUp() {
        mHandler = new ControllerKeepAlivePolicyHandler(mSystemDeviceLockManager,
                Executors.newSingleThreadExecutor());
    }

    @Test
    public void onProvisioned_withSuccess_shouldDisableControllerKeepalive()
        throws ExecutionException, InterruptedException {
        setExpectationsOnDisableControllerKeepalive(/* isSuccess =*/ true);
        assertThat(mHandler.onProvisioned().get()).isTrue();
        verify(mSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisioned_withFailure_shouldDisableControllerKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableControllerKeepalive(/* isSuccess =*/ false);
        assertThat(mHandler.onProvisioned().get()).isTrue();
        verify(mSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisionPaused_withSuccess_shouldDisableControllerKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableControllerKeepalive(/* isSuccess =*/ true);
        assertThat(mHandler.onProvisionPaused().get()).isTrue();
        verify(mSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisionPaused_withFailure_shouldDisableControllerKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableControllerKeepalive(/* isSuccess =*/ false);
        assertThat(mHandler.onProvisionPaused().get()).isTrue();
        verify(mSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).enableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisionInProgress_withSuccess_shouldEnableControllerKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnEnableControllerKeepalive(/* isSuccess =*/ true);
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verify(mSystemDeviceLockManager).enableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).disableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisionInProgress_withFailure_shouldEnableControllerKeepalive()
            throws ExecutionException, InterruptedException {
        setExpectationsOnEnableControllerKeepalive(/* isSuccess =*/ false);
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verify(mSystemDeviceLockManager).enableControllerKeepalive(any(Executor.class), any());
        verify(mSystemDeviceLockManager, never()).disableControllerKeepalive(any(Executor.class),
                any());
    }

    @Test
    public void onProvisionFailed_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionFailed().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onCleared_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onCleared().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onLocked_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onLocked().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onUnlocked_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onUnlocked().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    private void setExpectationsOnEnableControllerKeepalive(boolean isSuccess) {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            if (isSuccess) {
                callback.onResult(/* result =*/ null);
            } else {
                callback.onError(new Exception());
            }
            return null;
        }).when(mSystemDeviceLockManager).enableControllerKeepalive(any(Executor.class), any());
    }

    private void setExpectationsOnDisableControllerKeepalive(boolean isSuccess) {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            if (isSuccess) {
                callback.onResult(/* result =*/ null);
            } else {
                callback.onError(new Exception());
            }
            return null;
        }).when(mSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class), any());
    }
}

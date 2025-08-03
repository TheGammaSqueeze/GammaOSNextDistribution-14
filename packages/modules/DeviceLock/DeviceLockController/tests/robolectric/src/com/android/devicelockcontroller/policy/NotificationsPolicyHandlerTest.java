/*
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

package com.android.devicelockcontroller.policy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
public final class NotificationsPolicyHandlerTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private SystemDeviceLockManager mSystemDeviceLockManager;
    private NotificationsPolicyHandler mHandler;

    @Before
    public void setUp() {
        mHandler = new NotificationsPolicyHandler(mSystemDeviceLockManager,
                Executors.newSingleThreadExecutor());
    }

    @Test
    public void onProvisioned_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisioned().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onProvisionPaused_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionPaused().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onProvisionInProgress_withSuccess_shouldTryToSetSystemFixedFlag()
            throws ExecutionException, InterruptedException {
        setExpectationsOnSetPostNotificationsSystemFixedFuture(/* isSuccess =*/ true);
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verify(mSystemDeviceLockManager).setPostNotificationsSystemFixed(eq(true),
                any(Executor.class), any());
    }

    @Test
    public void onProvisionInProgress_withFailure_shouldTryToSetSystemFixedFlag()
            throws ExecutionException, InterruptedException {
        setExpectationsOnSetPostNotificationsSystemFixedFuture(/* isSuccess =*/ false);
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verify(mSystemDeviceLockManager).setPostNotificationsSystemFixed(eq(true),
                any(Executor.class), any());
    }

    @Test
    public void onProvisionFailed_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionFailed().get()).isTrue();
        verifyNoInteractions(mSystemDeviceLockManager);
    }

    @Test
    public void onCleared_withSuccess_shouldTryToClearSystemFixedFlag()
            throws ExecutionException, InterruptedException {
        setExpectationsOnSetPostNotificationsSystemFixedFuture(/* isSuccess =*/ false);
        assertThat(mHandler.onCleared().get()).isTrue();
        verify(mSystemDeviceLockManager).setPostNotificationsSystemFixed(eq(false),
                any(Executor.class), any());
    }

    @Test
    public void onCleared_withFailure_shouldTryToClearSystemFixedFlag()
            throws ExecutionException, InterruptedException {
        setExpectationsOnSetPostNotificationsSystemFixedFuture(/* isSuccess =*/ false);
        assertThat(mHandler.onCleared().get()).isTrue();
        verify(mSystemDeviceLockManager).setPostNotificationsSystemFixed(eq(false),
                any(Executor.class), any());
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

    private void setExpectationsOnSetPostNotificationsSystemFixedFuture(boolean isSuccess) {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 2);
            if (isSuccess) {
                callback.onResult(/* result =*/ null);
            } else {
                callback.onError(new Exception());
            }
            return null;
        }).when(mSystemDeviceLockManager).setPostNotificationsSystemFixed(anyBoolean(),
                any(Executor.class), any());
    }
}

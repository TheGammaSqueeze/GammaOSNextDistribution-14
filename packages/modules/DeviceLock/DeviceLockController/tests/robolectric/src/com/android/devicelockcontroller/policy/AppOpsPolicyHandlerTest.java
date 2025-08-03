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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Bundle;
import android.os.OutcomeReceiver;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import com.google.common.util.concurrent.testing.TestingExecutors;

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

@RunWith(RobolectricTestRunner.class)
public final class AppOpsPolicyHandlerTest {
    public static final String TEST_PACKAGE = "test-package";

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    private SystemDeviceLockManager mSystemDeviceLockManagerMock;

    private AppOpsPolicyHandler mHandler;

    @Before
    public void setUp() throws ExecutionException, InterruptedException {
        mHandler = new AppOpsPolicyHandler(mSystemDeviceLockManagerMock,
                TestingExecutors.sameThreadScheduledExecutor());
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(bundle).get();
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                anyBoolean(), any(Executor.class), any());
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mSystemDeviceLockManagerMock).setDlcAllowedToSendUndismissibleNotifications(
                anyBoolean(), any(Executor.class), any());

        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(3 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mSystemDeviceLockManagerMock).setKioskAppExemptFromRestrictionsState(anyString(),
                anyBoolean(), any(Executor.class), any());
    }

    @Test
    public void onProvisioned_shouldExemptBackgroundStartAndKioskApp()
            throws ExecutionException, InterruptedException {
        mHandler.onProvisioned().get();

        verify(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                eq(true), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setKioskAppExemptFromRestrictionsState(
                eq(TEST_PACKAGE), eq(true), any(Executor.class), any());
    }

    @Test
    public void onProvisionInProgress_shouldExemptBackgroundStartAndAllwUndismissibleNotifs()
            throws ExecutionException, InterruptedException {
        mHandler.onProvisionInProgress().get();

        verify(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                eq(true), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setDlcAllowedToSendUndismissibleNotifications(
                eq(true), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock, never()).setKioskAppExemptFromRestrictionsState(
                anyString(), anyBoolean(), any(Executor.class), any());
    }

    @Test
    public void onProvisionFailed_shouldBanBackgroundStart()
            throws ExecutionException, InterruptedException {
        mHandler.onProvisionFailed().get();

        verify(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                eq(false), any(Executor.class), any());
    }

    @Test
    public void onCleared_shouldResetDlcAndKioskAppExemptions()
            throws ExecutionException, InterruptedException {
        mHandler.onCleared().get();

        verify(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                eq(false), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setDlcAllowedToSendUndismissibleNotifications(
                eq(false), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setKioskAppExemptFromRestrictionsState(
                eq(TEST_PACKAGE), eq(false), any(Executor.class), any());
    }

    @Test
    public void onLocked_shouldExemptBackgroundStartAndKioskApp()
            throws ExecutionException, InterruptedException {
        mHandler.onLocked().get();
        verify(mSystemDeviceLockManagerMock).setDlcExemptFromActivityBgStartRestrictionState(
                eq(true), any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setKioskAppExemptFromRestrictionsState(
                eq(TEST_PACKAGE), eq(true), any(Executor.class), any());
    }

    @Test
    public void onUnlocked_shouldExemptKioskAppNotBackgroundStart()
            throws ExecutionException, InterruptedException {
        mHandler.onUnlocked().get();
        verify(mSystemDeviceLockManagerMock,
                never()).setDlcExemptFromActivityBgStartRestrictionState(anyBoolean(),
                any(Executor.class), any());
        verify(mSystemDeviceLockManagerMock).setKioskAppExemptFromRestrictionsState(
                eq(TEST_PACKAGE), eq(true), any(Executor.class), any());
    }
}

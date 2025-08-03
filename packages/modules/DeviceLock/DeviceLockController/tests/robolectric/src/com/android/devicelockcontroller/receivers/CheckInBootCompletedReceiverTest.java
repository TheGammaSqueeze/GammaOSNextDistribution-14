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

package com.android.devicelockcontroller.receivers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver.PendingResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class CheckInBootCompletedReceiverTest {

    public static final Intent INTENT = new Intent(Intent.ACTION_BOOT_COMPLETED);
    private DeviceLockControllerScheduler mScheduler;
    private CheckInBootCompletedReceiver mReceiver;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() {
        HandlerThread handlerThread = new HandlerThread("test");
        handlerThread.start();
        Handler handler = handlerThread.getThreadHandler();
        mReceiver = new CheckInBootCompletedReceiver(
                MoreExecutors.newSequentialExecutor(handler::post));
        mTestApp = ApplicationProvider.getApplicationContext();
        mScheduler = mTestApp.getDeviceLockControllerScheduler();
    }

    @Test
    public void onReceive_shouldCallMaybeScheduleInitialCheckIn() {
        when(mScheduler.maybeScheduleInitialCheckIn()).thenReturn(Futures.immediateVoidFuture());

        final PendingResult pendingResult = new PendingResult(
                1 /* resultCode */,
                "resultData",
                new Bundle(),
                0 /* type */,
                true /* ordered */,
                false /*sticky*/,
                null /* ibinder token */,
                0 /* userid */,
                0 /* flags */);
        mReceiver.setPendingResult(pendingResult);

        mReceiver.onReceive(mTestApp, INTENT);

        verify(mScheduler).maybeScheduleInitialCheckIn();
    }

    @Test
    public void onReceive_notSystemUser_disablesReceiver() {
        Context secondaryUserContext = mTestApp.createContextAsUser(
                UserHandle.of(/* userId= */ 1), /* flags= */ 0);
        PackageManager packageManager = secondaryUserContext.getPackageManager();

        mReceiver.onReceive(secondaryUserContext, INTENT);

        ComponentName receiverCmp =
                new ComponentName(secondaryUserContext, CheckInBootCompletedReceiver.class);
        assertThat(packageManager.getComponentEnabledSetting(receiverCmp))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        verifyNoInteractions(mScheduler);
    }
}

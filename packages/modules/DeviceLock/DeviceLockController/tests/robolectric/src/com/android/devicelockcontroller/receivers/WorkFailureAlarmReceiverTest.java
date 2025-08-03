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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.WorkManagerExceptionHandler.ALARM_REASON;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.WorkManagerExceptionHandler;
import com.android.devicelockcontroller.WorkManagerExceptionHandler.AlarmReason;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public final class WorkFailureAlarmReceiverTest {
    private static final long TIMEOUT_MILLIS = 1000;

    private DeviceLockControllerScheduler mScheduler;
    private TestDeviceLockControllerApplication mTestApp;
    private Handler mHandler;

    @Before
    public void setUp() {
        final HandlerThread handlerThread = new HandlerThread("BroadcastHandlerThread");
        handlerThread.start();
        mHandler = handlerThread.getThreadHandler();

        mTestApp = ApplicationProvider.getApplicationContext();
        mScheduler = mTestApp.getDeviceLockControllerScheduler();
    }

    @Test
    public void onReceive_reasonInitialization_shouldCallMaybeScheduleInitialCheckIn()
            throws Exception {
        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        when(mScheduler.maybeScheduleInitialCheckIn()).thenReturn(Futures.immediateVoidFuture());

        final ListenableFuture<Void> broadcastComplete =
                sendBroadcastToWorkFailureAlarmReceiver(AlarmReason.INITIALIZATION);

        shadowOf(Looper.getMainLooper()).idle();

        broadcastComplete.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mScheduler).maybeScheduleInitialCheckIn();
        verifyNoMoreInteractions(mScheduler);
    }

    @Test
    public void onReceive_reasonInitialCheckIn_shouldCallMaybeScheduleInitialCheckIn()
            throws Exception {
        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        when(mScheduler.maybeScheduleInitialCheckIn()).thenReturn(Futures.immediateVoidFuture());

        final ListenableFuture<Void> broadcastComplete =
                sendBroadcastToWorkFailureAlarmReceiver(AlarmReason.INITIAL_CHECK_IN);

        shadowOf(Looper.getMainLooper()).idle();

        broadcastComplete.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mScheduler).maybeScheduleInitialCheckIn();
        verifyNoMoreInteractions(mScheduler);
    }

    @Test
    public void onReceive_reasonRetryCheckIn_shouldCallScheduleRetryCheckInWork() throws Exception {
        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        when(mScheduler.scheduleRetryCheckInWork(any(Duration.class)))
                .thenReturn(Futures.immediateVoidFuture());

        final ListenableFuture<Void> broadcastComplete =
                sendBroadcastToWorkFailureAlarmReceiver(AlarmReason.RETRY_CHECK_IN);

        shadowOf(Looper.getMainLooper()).idle();

        broadcastComplete.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mScheduler).scheduleRetryCheckInWork(eq(Duration.ZERO));
        verifyNoMoreInteractions(mScheduler);
    }

    @Test
    public void onReceive_reasonRescheduleCheckIn_shouldCallNotifyNeedRescheduleCheckIn()
            throws Exception {
        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        when(mScheduler.notifyNeedRescheduleCheckIn()).thenReturn(Futures.immediateVoidFuture());

        final ListenableFuture<Void> broadcastComplete =
                sendBroadcastToWorkFailureAlarmReceiver(AlarmReason.RESCHEDULE_CHECK_IN);

        shadowOf(Looper.getMainLooper()).idle();

        broadcastComplete.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mScheduler).notifyNeedRescheduleCheckIn();
        verifyNoMoreInteractions(mScheduler);
    }

    @Test
    public void onReceive_whenProvisionReady_doesNotSchedule() throws Exception {
        GlobalParametersClient.getInstance().setProvisionReady(true).get();

        when(mScheduler.maybeScheduleInitialCheckIn()).thenReturn(Futures.immediateVoidFuture());

        final ListenableFuture<Void> broadcastComplete =
                sendBroadcastToWorkFailureAlarmReceiver(AlarmReason.INITIALIZATION);

        shadowOf(Looper.getMainLooper()).idle();

        broadcastComplete.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verifyNoInteractions(mScheduler);
    }

    private Intent getIntentForAlarmReason(@AlarmReason int alarmReason) {
        final Intent intent = new Intent(mTestApp,
                WorkManagerExceptionHandler.WorkFailureAlarmReceiver.class);
        final Bundle bundle = new Bundle();
        bundle.putInt(ALARM_REASON, alarmReason);
        intent.putExtras(bundle);
        return intent;
    }

    private ListenableFuture<Void> sendBroadcastToWorkFailureAlarmReceiver(
            @AlarmReason int alarmReason) {
        final SettableFuture<Void> broadcastComplete = SettableFuture.create();

        mTestApp.sendOrderedBroadcast(
                getIntentForAlarmReason(alarmReason),
                null /* receiverPermission */,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        broadcastComplete.set(null);
                    }
                },
                mHandler /* scheduler */,
                0 /* initialCode */,
                null /* initialData */,
                null /* initialExtras */);

        return broadcastComplete;
    }
}

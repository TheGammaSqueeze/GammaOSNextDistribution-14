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

package com.android.car.remoteaccess.hal;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.remoteaccess.ApState;
import android.hardware.automotive.remoteaccess.IRemoteAccess;
import android.hardware.automotive.remoteaccess.IRemoteTaskCallback;
import android.hardware.automotive.remoteaccess.ScheduleInfo;
import android.hardware.automotive.remoteaccess.TaskType;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link RemoteAccessHalWrapper}.
 */
public final class RemoteAccessHalWrapperUnitTest extends AbstractExtendedMockitoTestCase {

    private static final String VEHICLE_ID_FOR_TESTING = "vehicleIdForTesting";
    private static final String SERVICE_NAME_FOR_TESTING = "serviceNameForTesting";
    private static final String TEST_CLIENT_ID = "test client id";
    private static final String TEST_SCHEDULE_ID = "test schedule id";

    @Mock private IBinder mBinder;
    @Mock private IRemoteAccess mRemoteAccessHal;

    private RemoteAccessHalCallbackImpl mCallback = new RemoteAccessHalCallbackImpl();
    private RemoteAccessHalWrapper mHalWrapper;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(RemoteAccessHalWrapper.class);
    }

    private void setUpNormalHalService() throws Exception {
        doReturn(mBinder).when(RemoteAccessHalWrapper::getRemoteAccessHalService);
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mRemoteAccessHal);
        when(mRemoteAccessHal.getVehicleId()).thenReturn(VEHICLE_ID_FOR_TESTING);
        when(mRemoteAccessHal.getWakeupServiceName()).thenReturn(SERVICE_NAME_FOR_TESTING);
        when(mRemoteAccessHal.getInterfaceVersion()).thenReturn(2);
        mHalWrapper.init();
    }

    private void setUpNoHalService() {
        doReturn(null).when(RemoteAccessHalWrapper::getRemoteAccessHalService);
    }

    @Before
    public void setUp() {
        mHalWrapper = new RemoteAccessHalWrapper(mCallback);
    }

    @Test
    public void testInitialize() throws Exception {
        setUpNormalHalService();

        verify(mRemoteAccessHal).setRemoteTaskCallback(any(IRemoteTaskCallback.class));
    }

    @Test
    public void testRelease() throws Exception {
        setUpNormalHalService();

        mHalWrapper.release();

        verify(mRemoteAccessHal).clearRemoteTaskCallback();
    }

    @Test
    public void testRemoteAccessHalDied() throws Exception {
        setUpNormalHalService();

        mHalWrapper.binderDied();

        // setRemoteTaskCallback is called twice at init() and binderDied().
        // When remote access HAL dies, RemoteAccessHalWrapper attempts to reconnect.
        verify(mRemoteAccessHal, times(2)).setRemoteTaskCallback(any(IRemoteTaskCallback.class));
    }

    @Test
    public void testGetVehicleId() throws Exception {
        setUpNormalHalService();

        assertWithMessage("Vehicle ID").that(mHalWrapper.getVehicleId())
                .isEqualTo(VEHICLE_ID_FOR_TESTING);
    }

    @Test
    public void testGetVehicleId_noHalService() throws Exception {
        setUpNoHalService();

        assertThrows(IllegalStateException.class, () -> mHalWrapper.getVehicleId());
    }

    @Test
    public void testGetWakeupServiceName() throws Exception {
        setUpNormalHalService();

        assertWithMessage("Wakeup service name").that(mHalWrapper.getWakeupServiceName())
                .isEqualTo(SERVICE_NAME_FOR_TESTING);
    }

    @Test
    public void testGetWakeupServiceName_noHalService() throws Exception {
        setUpNoHalService();

        assertThrows(IllegalStateException.class, () -> mHalWrapper.getWakeupServiceName());
    }

    @Test
    public void testNotifyApStateChange() throws Exception {
        setUpNormalHalService();
        ArgumentCaptor<ApState> captor = ArgumentCaptor.forClass(ApState.class);

        mHalWrapper.notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ true);
        verify(mRemoteAccessHal).notifyApStateChange(captor.capture());
        ApState state = captor.getValue();

        assertWithMessage("Ready for remote task").that(state.isReadyForRemoteTask).isFalse();
        assertWithMessage("Wakeup required").that(state.isWakeupRequired).isTrue();
    }

    @Test
    public void testNotifyApStateChange_noHalService() throws Exception {
        setUpNoHalService();

        assertWithMessage("Return value").that(mHalWrapper.notifyApStateChange(
                /* isReadyForRemoteTask= */ false, /* isWakeupRequired= */ true)).isFalse();
        verify(mRemoteAccessHal, never()).notifyApStateChange(any(ApState.class));
    }

    @Test
    public void testOnRemoteTaskRequestedFromHal_withoutData() throws Exception {
        setUpNormalHalService();
        IRemoteTaskCallback remoteTaskCallback = captureRemoteTaskCallback();

        remoteTaskCallback.onRemoteTaskRequested(VEHICLE_ID_FOR_TESTING, /* data= */ null);

        assertWithMessage("Client ID").that(mCallback.getClientId())
                .isEqualTo(VEHICLE_ID_FOR_TESTING);
        assertWithMessage("Data").that(mCallback.getData()).isNull();
    }

    @Test
    public void testOnRemoteTaskRequestedFromHal_withData() throws Exception {
        setUpNormalHalService();
        IRemoteTaskCallback remoteTaskCallback = captureRemoteTaskCallback();
        byte[] data = new byte[]{1, 0, 0, 4};

        remoteTaskCallback.onRemoteTaskRequested(VEHICLE_ID_FOR_TESTING, data);

        assertWithMessage("Client ID").that(mCallback.getClientId())
                .isEqualTo(VEHICLE_ID_FOR_TESTING);
        assertWithMessage("Data").that(mCallback.getData()).isEqualTo(new byte[]{1, 0, 0, 4});
    }

    @Test
    public void testIsTaskScheduleSupported() throws Exception {
        setUpNormalHalService();
        when(mRemoteAccessHal.isTaskScheduleSupported()).thenReturn(true);

        assertThat(mHalWrapper.isTaskScheduleSupported()).isTrue();
    }

    @Test
    public void testIsTaskScheduleSupported_halVersionNotSupported() throws Exception {
        setUpNormalHalService();
        when(mRemoteAccessHal.getInterfaceVersion()).thenReturn(1);

        assertThat(mHalWrapper.isTaskScheduleSupported()).isFalse();
        verify(mRemoteAccessHal, times(0)).isTaskScheduleSupported();
    }

    @Test
    public void testIsTaskScheduleSupported_remoteException() throws Exception {
        setUpNormalHalService();
        when(mRemoteAccessHal.isTaskScheduleSupported()).thenThrow(new RemoteException());

        assertThat(mHalWrapper.isTaskScheduleSupported()).isFalse();
        verify(mRemoteAccessHal).isTaskScheduleSupported();
    }

    @Test
    public void testIsTaskScheduleSupported_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        when(mRemoteAccessHal.isTaskScheduleSupported()).thenThrow(new ServiceSpecificException(0));

        assertThat(mHalWrapper.isTaskScheduleSupported()).isFalse();
        verify(mRemoteAccessHal).isTaskScheduleSupported();
    }

    @Test
    public void testScheduleTask() throws Exception {
        setUpNormalHalService();
        ScheduleInfo scheduleInfo = mock(ScheduleInfo.class);

        mHalWrapper.scheduleTask(scheduleInfo);

        verify(mRemoteAccessHal).scheduleTask(scheduleInfo);
    }

    @Test
    public void testScheduleTask_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).scheduleTask(any());
        ScheduleInfo scheduleInfo = mock(ScheduleInfo.class);

        assertThrows(RemoteException.class, () -> mHalWrapper.scheduleTask(scheduleInfo));
    }

    @Test
    public void testScheduleTask_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal).scheduleTask(any());
        ScheduleInfo scheduleInfo = mock(ScheduleInfo.class);

        assertThrows(ServiceSpecificException.class, () -> mHalWrapper.scheduleTask(scheduleInfo));
    }

    @Test
    public void testUnscheduleTask() throws Exception {
        setUpNormalHalService();

        mHalWrapper.unscheduleTask(TEST_CLIENT_ID, TEST_SCHEDULE_ID);

        verify(mRemoteAccessHal).unscheduleTask(TEST_CLIENT_ID, TEST_SCHEDULE_ID);
    }

    @Test
    public void testUnscheduleTask_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).unscheduleTask(any(), any());

        assertThrows(RemoteException.class, () -> mHalWrapper.unscheduleTask(
                TEST_CLIENT_ID, TEST_SCHEDULE_ID));
    }

    @Test
    public void testUnscheduleTask_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal).unscheduleTask(
                any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHalWrapper.unscheduleTask(
                TEST_CLIENT_ID, TEST_SCHEDULE_ID));
    }

    @Test
    public void testUnscheduleAllTasks() throws Exception {
        setUpNormalHalService();

        mHalWrapper.unscheduleAllTasks(TEST_CLIENT_ID);

        verify(mRemoteAccessHal).unscheduleAllTasks(TEST_CLIENT_ID);
    }

    @Test
    public void testUnscheduleAllTasks_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).unscheduleAllTasks(any());

        assertThrows(RemoteException.class, () -> mHalWrapper.unscheduleAllTasks(TEST_CLIENT_ID));
    }

    @Test
    public void testUnscheduleAllTasks_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal).unscheduleAllTasks(any());

        assertThrows(ServiceSpecificException.class, () -> mHalWrapper.unscheduleAllTasks(
                TEST_CLIENT_ID));
    }

    @Test
    public void testIsTaskScheduled() throws Exception {
        setUpNormalHalService();

        when(mRemoteAccessHal.isTaskScheduled(TEST_CLIENT_ID, TEST_SCHEDULE_ID)).thenReturn(true);

        assertThat(mHalWrapper.isTaskScheduled(TEST_CLIENT_ID, TEST_SCHEDULE_ID)).isTrue();
    }

    @Test
    public void testIsTaskScheduled_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).isTaskScheduled(any(), any());

        assertThrows(RemoteException.class, () -> mHalWrapper.isTaskScheduled(
                TEST_CLIENT_ID, TEST_SCHEDULE_ID));
    }

    @Test
    public void testIsTaskScheduled_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal).isTaskScheduled(
                any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHalWrapper.isTaskScheduled(
                TEST_CLIENT_ID, TEST_SCHEDULE_ID));
    }

    @Test
    public void testGetAllPendingScheduledTasks() throws Exception {
        setUpNormalHalService();
        ScheduleInfo mockScheduleInfo = mock(ScheduleInfo.class);
        List<ScheduleInfo> scheduleInfo = new ArrayList<>();
        scheduleInfo.add(mockScheduleInfo);

        doReturn(scheduleInfo).when(mRemoteAccessHal).getAllPendingScheduledTasks(TEST_CLIENT_ID);

        assertThat(mHalWrapper.getAllPendingScheduledTasks(TEST_CLIENT_ID)).isEqualTo(scheduleInfo);
    }

    @Test
    public void testGetAllPendingScheduledTasks_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).getAllPendingScheduledTasks(
                TEST_CLIENT_ID);

        assertThrows(RemoteException.class, () -> mHalWrapper.getAllPendingScheduledTasks(
                TEST_CLIENT_ID));
    }

    @Test
    public void testGetAllPendingScheduledTasks_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal).getAllPendingScheduledTasks(
                TEST_CLIENT_ID);

        assertThrows(ServiceSpecificException.class, () -> mHalWrapper.getAllPendingScheduledTasks(
                TEST_CLIENT_ID));
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling() throws Exception {
        setUpNormalHalService();
        int[] taskTypes = new int[]{TaskType.ENTER_GARAGE_MODE};
        doReturn(taskTypes).when(mRemoteAccessHal).getSupportedTaskTypesForScheduling();

        assertThat(mHalWrapper.getSupportedTaskTypesForScheduling()).isEqualTo(taskTypes);
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling_remoteException() throws Exception {
        setUpNormalHalService();
        doThrow(new RemoteException()).when(mRemoteAccessHal).getSupportedTaskTypesForScheduling();

        assertThrows(RemoteException.class, () -> mHalWrapper.getSupportedTaskTypesForScheduling());
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling_serviceSpecificException() throws Exception {
        setUpNormalHalService();
        doThrow(new ServiceSpecificException(0)).when(mRemoteAccessHal)
                .getSupportedTaskTypesForScheduling();

        assertThrows(ServiceSpecificException.class,
                () -> mHalWrapper.getSupportedTaskTypesForScheduling());
    }

    private IRemoteTaskCallback captureRemoteTaskCallback() throws Exception {
        ArgumentCaptor<IRemoteTaskCallback> captor =
                ArgumentCaptor.forClass(IRemoteTaskCallback.class);
        verify(mRemoteAccessHal).setRemoteTaskCallback(captor.capture());
        return captor.getValue();
    }

    private static final class RemoteAccessHalCallbackImpl implements RemoteAccessHalCallback {
        private String mClientId;
        private byte[] mData;

        @Override
        public void onRemoteTaskRequested(String clientId, byte[] data) {
            mClientId = clientId;
            mData = data;
        }

        @Nullable
        public String getClientId() {
            return mClientId;
        }

        @Nullable
        public byte[] getData() {
            return mData;
        }
    }
}

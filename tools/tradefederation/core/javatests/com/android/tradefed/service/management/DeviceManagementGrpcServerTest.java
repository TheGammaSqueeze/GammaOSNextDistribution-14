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
package com.android.tradefed.service.management;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IManagedTestDevice;

import com.proto.tradefed.device.DeviceStatus.ReservationStatus;
import com.proto.tradefed.device.GetDevicesStatusRequest;
import com.proto.tradefed.device.GetDevicesStatusResponse;
import com.proto.tradefed.device.ReleaseReservationRequest;
import com.proto.tradefed.device.ReleaseReservationResponse;
import com.proto.tradefed.device.ReserveDeviceRequest;
import com.proto.tradefed.device.ReserveDeviceResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

import io.grpc.Server;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/** Unit tests for {@link DeviceManagementGrpcServer}. */
@RunWith(JUnit4.class)
public class DeviceManagementGrpcServerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private DeviceManagementGrpcServer mServer;
    @Mock private IDeviceManager mMockDeviceManager;
    @Mock private ICommandScheduler mMockCommandScheduler;
    @Mock private StreamObserver<GetDevicesStatusResponse> mGetDevicesStatusObserver;
    @Mock private ServerCallStreamObserver<ReserveDeviceResponse> mReserveDeviceResponseObserver;
    @Mock private StreamObserver<ReleaseReservationResponse> mReleaseReservationResponseObserver;
    @Captor ArgumentCaptor<GetDevicesStatusResponse> mGetDevicesStatusCaptor;
    @Captor ArgumentCaptor<ReserveDeviceResponse> mReserveDeviceResponseCaptor;
    @Captor ArgumentCaptor<ReleaseReservationResponse> mReleaseReservationResponseCaptor;

    @Before
    public void setUp() {
        Server server = null;
        mServer = new DeviceManagementGrpcServer(server, mMockDeviceManager, mMockCommandScheduler);
    }

    @Test
    public void testGetDevicesStatus() {
        List<DeviceDescriptor> descriptors = new ArrayList<>();
        descriptors.add(createDescriptor("serial1", DeviceAllocationState.Available));
        descriptors.add(createDescriptor("serial2", DeviceAllocationState.Allocated));
        descriptors.add(createDescriptor("serial3", DeviceAllocationState.Unavailable));
        descriptors.add(createDescriptor("serial4", DeviceAllocationState.Unknown));
        when(mMockDeviceManager.listAllDevices(true)).thenReturn(descriptors);

        GetDevicesStatusRequest.Builder requestBuilder = GetDevicesStatusRequest.newBuilder();
        mServer.getDevicesStatus(requestBuilder.build(), mGetDevicesStatusObserver);
        verify(mGetDevicesStatusObserver).onNext(mGetDevicesStatusCaptor.capture());
        GetDevicesStatusResponse response = mGetDevicesStatusCaptor.getValue();

        assertThat(response.getDeviceStatusList()).hasSize(4);
        assertThat(response.getDeviceStatusList().get(0).getDeviceId()).isEqualTo("serial1");
        assertThat(response.getDeviceStatusList().get(0).getReservationStatus())
                .isEqualTo(ReservationStatus.READY);

        assertThat(response.getDeviceStatusList().get(1).getDeviceId()).isEqualTo("serial2");
        assertThat(response.getDeviceStatusList().get(1).getReservationStatus())
                .isEqualTo(ReservationStatus.ALLOCATED);

        assertThat(response.getDeviceStatusList().get(2).getDeviceId()).isEqualTo("serial3");
        assertThat(response.getDeviceStatusList().get(2).getReservationStatus())
                .isEqualTo(ReservationStatus.UNAVAILABLE);

        assertThat(response.getDeviceStatusList().get(3).getDeviceId()).isEqualTo("serial4");
        assertThat(response.getDeviceStatusList().get(3).getReservationStatus())
                .isEqualTo(ReservationStatus.UNKNOWN);
    }

    @Test
    public void testGetDevicesStatus_filter() {
        when(mMockDeviceManager.getDeviceDescriptor("serial2"))
                .thenReturn(createDescriptor("serial2", DeviceAllocationState.Allocated));

        GetDevicesStatusRequest.Builder requestBuilder =
                GetDevicesStatusRequest.newBuilder().addDeviceId("serial2");
        mServer.getDevicesStatus(requestBuilder.build(), mGetDevicesStatusObserver);
        verify(mGetDevicesStatusObserver).onNext(mGetDevicesStatusCaptor.capture());
        GetDevicesStatusResponse response = mGetDevicesStatusCaptor.getValue();

        assertThat(response.getDeviceStatusList()).hasSize(1);
        assertThat(response.getDeviceStatusList().get(0).getDeviceId()).isEqualTo("serial2");
        assertThat(response.getDeviceStatusList().get(0).getReservationStatus())
                .isEqualTo(ReservationStatus.ALLOCATED);
    }

    @Test
    public void testReserveAndRelease_freeDevice() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Available))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Allocated));
        when(mMockCommandScheduler.isDeviceInInvocationThread(Mockito.any())).thenReturn(false);
        IManagedTestDevice mockedDevice = Mockito.mock(IManagedTestDevice.class);
        when(mMockDeviceManager.allocateDevice(Mockito.any())).thenReturn(mockedDevice);
        // Allocate a device
        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        ReserveDeviceResponse reservation = mReserveDeviceResponseCaptor.getValue();
        assertThat(reservation.getResult()).isEqualTo(ReserveDeviceResponse.Result.SUCCEED);
        String reservationId = reservation.getReservationId();
        assertThat(reservationId).isNotEmpty();
        // Get status of reserved device
        GetDevicesStatusRequest.Builder requestBuilder =
                GetDevicesStatusRequest.newBuilder().addDeviceId("serial1");
        mServer.getDevicesStatus(requestBuilder.build(), mGetDevicesStatusObserver);
        verify(mGetDevicesStatusObserver).onNext(mGetDevicesStatusCaptor.capture());
        GetDevicesStatusResponse response = mGetDevicesStatusCaptor.getValue();
        assertThat(response.getDeviceStatusList()).hasSize(1);
        assertThat(response.getDeviceStatus(0).getReservationStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        // Release the device
        mServer.releaseReservation(
                ReleaseReservationRequest.newBuilder().setReservationId(reservationId).build(),
                mReleaseReservationResponseObserver);
        // Releasing again should fail since device is untracked
        mServer.releaseReservation(
                ReleaseReservationRequest.newBuilder().setReservationId(reservationId).build(),
                mReleaseReservationResponseObserver);

        verify(mReleaseReservationResponseObserver, times(2))
                .onNext(mReleaseReservationResponseCaptor.capture());
        List<ReleaseReservationResponse> releases =
                mReleaseReservationResponseCaptor.getAllValues();
        ReleaseReservationResponse release = releases.get(0);
        assertThat(release.getResult()).isEqualTo(ReleaseReservationResponse.Result.SUCCEED);
        verify(mMockDeviceManager).freeDevice(mockedDevice, FreeDeviceState.AVAILABLE);

        ReleaseReservationResponse untracked = releases.get(1);
        assertThat(untracked.getResult())
                .isEqualTo(ReleaseReservationResponse.Result.RESERVATION_NOT_EXIST);
    }

    @Test
    public void testReserveAndRelease_notFreeDevice() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Available))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Allocated));
        when(mMockCommandScheduler.isDeviceInInvocationThread(Mockito.any())).thenReturn(true);
        ITestDevice mockedDevice = Mockito.mock(ITestDevice.class);
        when(mMockDeviceManager.allocateDevice(Mockito.any())).thenReturn(mockedDevice);
        // Allocate a device
        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        String reservationId = mReserveDeviceResponseCaptor.getValue().getReservationId();

        // Release the device
        mServer.releaseReservation(
                ReleaseReservationRequest.newBuilder().setReservationId(reservationId).build(),
                mReleaseReservationResponseObserver);

        verify(mReleaseReservationResponseObserver)
                .onNext(mReleaseReservationResponseCaptor.capture());
        ReleaseReservationResponse response = mReleaseReservationResponseCaptor.getValue();
        assertThat(response.getResult()).isEqualTo(ReleaseReservationResponse.Result.DEVICE_IN_USE);
        assertThat(mServer.getDeviceFromReservation(reservationId)).isNotNull();
        verify(mMockDeviceManager, never()).freeDevice(mockedDevice, FreeDeviceState.AVAILABLE);
    }

    @Test
    public void testReserve_allocated() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Allocated));

        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        ReserveDeviceResponse reservation = mReserveDeviceResponseCaptor.getValue();
        assertThat(reservation.getResult())
                .isEqualTo(ReserveDeviceResponse.Result.ALREADY_ALLOCATED);
        String reservationId = reservation.getReservationId();
        assertThat(reservationId).isEmpty();
        verify(mMockDeviceManager, never()).allocateDevice(Mockito.any());
    }

    @Test
    public void testReserve_unavailable() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Unavailable));

        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        ReserveDeviceResponse reservation = mReserveDeviceResponseCaptor.getValue();
        assertThat(reservation.getResult()).isEqualTo(ReserveDeviceResponse.Result.UNAVAILABLE);
        String reservationId = reservation.getReservationId();
        assertThat(reservationId).isEmpty();
        verify(mMockDeviceManager, never()).allocateDevice(Mockito.any());
    }

    @Test
    public void testReserve_cancelledBeforeReserved() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Available));
        when(mReserveDeviceResponseObserver.isCancelled()).thenReturn(true);
        // Allocate a device
        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        ReserveDeviceResponse reservation = mReserveDeviceResponseCaptor.getValue();
        assertThat(reservation.getResult()).isEqualTo(ReserveDeviceResponse.Result.UNKNOWN);
        String reservationId = reservation.getReservationId();
        assertThat(reservationId).isEmpty();
        verify(mMockDeviceManager, never()).allocateDevice(Mockito.any());
    }

    @Test
    public void testReserve_cancelledAfterReserved() {
        when(mMockDeviceManager.getDeviceDescriptor("serial1"))
                .thenReturn(createDescriptor("serial1", DeviceAllocationState.Available));
        ITestDevice mockedDevice = Mockito.mock(ITestDevice.class);
        when(mMockDeviceManager.allocateDevice(Mockito.any())).thenReturn(mockedDevice);
        when(mReserveDeviceResponseObserver.isCancelled()).thenReturn(false).thenReturn(true);
        // Allocate a device
        mServer.reserveDevice(
                ReserveDeviceRequest.newBuilder().setDeviceId("serial1").build(),
                mReserveDeviceResponseObserver);
        verify(mReserveDeviceResponseObserver).onNext(mReserveDeviceResponseCaptor.capture());
        ReserveDeviceResponse reservation = mReserveDeviceResponseCaptor.getValue();
        assertThat(reservation.getResult()).isEqualTo(ReserveDeviceResponse.Result.UNKNOWN);
        String reservationId = reservation.getReservationId();
        assertThat(reservationId).isEmpty();
        verify(mMockDeviceManager).allocateDevice(Mockito.any());
        verify(mMockDeviceManager).freeDevice(mockedDevice, FreeDeviceState.AVAILABLE);
    }


    private DeviceDescriptor createDescriptor(String serial, DeviceAllocationState state) {
        return new DeviceDescriptor(serial, false, state, "", "", "", "", "");
    }
}

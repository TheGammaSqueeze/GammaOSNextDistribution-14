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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.FileUtil;

import com.proto.tradefed.invocation.InvocationDetailRequest;
import com.proto.tradefed.invocation.InvocationDetailResponse;
import com.proto.tradefed.invocation.InvocationStatus;
import com.proto.tradefed.invocation.NewTestCommandRequest;
import com.proto.tradefed.invocation.NewTestCommandResponse;
import com.proto.tradefed.invocation.StopInvocationRequest;
import com.proto.tradefed.invocation.StopInvocationResponse;

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

import java.io.File;
import java.util.Arrays;

import io.grpc.Server;
import io.grpc.stub.StreamObserver;

/** Unit tests for {@link TestInvocationManagementServer}. */
@RunWith(JUnit4.class)
public class TestInvocationManagementServerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private TestInvocationManagementServer mServer;
    @Mock private ICommandScheduler mMockScheduler;
    @Mock private DeviceManagementGrpcServer mMockDeviceManagement;
    @Mock private StreamObserver<NewTestCommandResponse> mRequestObserver;
    @Mock private StreamObserver<InvocationDetailResponse> mDetailObserver;
    @Mock private StreamObserver<StopInvocationResponse> mStopInvocationObserver;
    @Captor ArgumentCaptor<NewTestCommandResponse> mResponseCaptor;
    @Captor ArgumentCaptor<InvocationDetailResponse> mResponseDetailCaptor;
    @Captor ArgumentCaptor<StopInvocationResponse> mStopInvocationCaptor;

    @Before
    public void setUp() {
        Server server = null;
        mServer = new TestInvocationManagementServer(server, mMockScheduler, mMockDeviceManagement);
    }

    @Test
    public void testSubmitTestCommand_andDetails() throws Exception {
        doAnswer(
                        invocation -> {
                            Object listeners = invocation.getArgument(0);
                            ((IScheduledInvocationListener) listeners)
                                    .invocationComplete(null, null);
                            return null;
                        })
                .when(mMockScheduler)
                .execCommand(Mockito.any(), Mockito.any());
        NewTestCommandRequest.Builder requestBuilder =
                NewTestCommandRequest.newBuilder().addArgs("empty");
        mServer.submitTestCommand(requestBuilder.build(), mRequestObserver);

        verify(mRequestObserver).onNext(mResponseCaptor.capture());
        NewTestCommandResponse response = mResponseCaptor.getValue();
        assertThat(response.getInvocationId()).isNotEmpty();

        InvocationDetailRequest.Builder detailBuilder =
                InvocationDetailRequest.newBuilder().setInvocationId(response.getInvocationId());
        mServer.getInvocationDetail(detailBuilder.build(), mDetailObserver);
        verify(mDetailObserver).onNext(mResponseDetailCaptor.capture());
        InvocationDetailResponse responseDetails = mResponseDetailCaptor.getValue();
        assertThat(responseDetails.getInvocationStatus().getStatus())
                .isEqualTo(InvocationStatus.Status.DONE);
        File record = new File(responseDetails.getTestRecordPath());
        assertThat(record.exists()).isTrue();
        FileUtil.deleteFile(record);
    }

    @Test
    public void testSubmitTestCommand_schedulingError() throws Exception {
        doThrow(
                        new ConfigurationException(
                                "failed to schedule",
                                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR))
                .when(mMockScheduler)
                .execCommand(Mockito.any(), Mockito.any());

        NewTestCommandRequest.Builder requestBuilder =
                NewTestCommandRequest.newBuilder().addArgs("empty");
        mServer.submitTestCommand(requestBuilder.build(), mRequestObserver);

        verify(mRequestObserver).onNext(mResponseCaptor.capture());
        NewTestCommandResponse response = mResponseCaptor.getValue();
        assertThat(response.getInvocationId()).isEmpty();
        assertThat(response.getCommandErrorInfo()).isNotNull();
        assertThat(response.getCommandErrorInfo().getErrorName())
                .isEqualTo(InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR.name());
        assertThat(response.getCommandErrorInfo().getErrorCode())
                .isEqualTo(InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR.code());
    }

    @Test
    public void testSubmitTestCommand_reservedDevice() throws Exception {
        ITestDevice mockDevice = Mockito.mock(ITestDevice.class);
        doAnswer(
                        invocation -> {
                            Object listeners = invocation.getArgument(0);
                            ((IScheduledInvocationListener) listeners)
                                    .invocationComplete(null, null);
                            return null;
                        })
                .when(mMockScheduler)
                .execCommand(Mockito.any(), Mockito.eq(Arrays.asList(mockDevice)), Mockito.any());
        when(mMockDeviceManagement.getDeviceFromReservation(Mockito.eq("reservation-1")))
                .thenReturn(mockDevice);
        NewTestCommandRequest.Builder requestBuilder =
                NewTestCommandRequest.newBuilder()
                        .addArgs("empty")
                        .addReservationId("reservation-1");
        mServer.submitTestCommand(requestBuilder.build(), mRequestObserver);

        verify(mRequestObserver).onNext(mResponseCaptor.capture());
        NewTestCommandResponse response = mResponseCaptor.getValue();
        assertThat(response.getInvocationId()).isNotEmpty();

        InvocationDetailRequest.Builder detailBuilder =
                InvocationDetailRequest.newBuilder().setInvocationId(response.getInvocationId());
        mServer.getInvocationDetail(detailBuilder.build(), mDetailObserver);
        verify(mDetailObserver).onNext(mResponseDetailCaptor.capture());
        InvocationDetailResponse responseDetails = mResponseDetailCaptor.getValue();
        assertThat(responseDetails.getInvocationStatus().getStatus())
                .isEqualTo(InvocationStatus.Status.DONE);
        File record = new File(responseDetails.getTestRecordPath());
        assertThat(record.exists()).isTrue();
        FileUtil.deleteFile(record);
    }

    @Test
    public void testSubmitTestCommand_reservedMultiDevice() throws Exception {
        ITestDevice mockDevice = Mockito.mock(ITestDevice.class);
        ITestDevice mockDevice2 = Mockito.mock(ITestDevice.class);
        doAnswer(
                        invocation -> {
                            Object listeners = invocation.getArgument(0);
                            ((IScheduledInvocationListener) listeners)
                                    .invocationComplete(null, null);
                            return null;
                        })
                .when(mMockScheduler)
                .execCommand(
                        Mockito.any(),
                        Mockito.eq(Arrays.asList(mockDevice, mockDevice2)),
                        Mockito.any());
        when(mMockDeviceManagement.getDeviceFromReservation(Mockito.eq("reservation-1")))
                .thenReturn(mockDevice);
        when(mMockDeviceManagement.getDeviceFromReservation(Mockito.eq("reservation-2")))
                .thenReturn(mockDevice2);
        NewTestCommandRequest.Builder requestBuilder =
                NewTestCommandRequest.newBuilder()
                        .addArgs("empty")
                        .addReservationId("reservation-1")
                        .addReservationId("reservation-2");
        mServer.submitTestCommand(requestBuilder.build(), mRequestObserver);

        verify(mRequestObserver).onNext(mResponseCaptor.capture());
        NewTestCommandResponse response = mResponseCaptor.getValue();
        assertThat(response.getInvocationId()).isNotEmpty();

        InvocationDetailRequest.Builder detailBuilder =
                InvocationDetailRequest.newBuilder().setInvocationId(response.getInvocationId());
        mServer.getInvocationDetail(detailBuilder.build(), mDetailObserver);
        verify(mDetailObserver).onNext(mResponseDetailCaptor.capture());
        InvocationDetailResponse responseDetails = mResponseDetailCaptor.getValue();
        assertThat(responseDetails.getInvocationStatus().getStatus())
                .isEqualTo(InvocationStatus.Status.DONE);
        File record = new File(responseDetails.getTestRecordPath());
        assertThat(record.exists()).isTrue();
        FileUtil.deleteFile(record);
    }

    @Test
    public void testSubmitTestCommand_andStop() throws Exception {
        doAnswer(
                        invocation -> {
                            Object listeners = invocation.getArgument(0);
                            ((IScheduledInvocationListener) listeners)
                                    .invocationComplete(null, null);
                            return 1L;
                        })
                .when(mMockScheduler)
                .execCommand(Mockito.any(), Mockito.any());
        when(mMockScheduler.stopInvocation(Mockito.anyInt(), Mockito.any())).thenReturn(true);
        NewTestCommandRequest.Builder requestBuilder =
                NewTestCommandRequest.newBuilder().addArgs("empty");
        mServer.submitTestCommand(requestBuilder.build(), mRequestObserver);

        verify(mRequestObserver).onNext(mResponseCaptor.capture());
        NewTestCommandResponse response = mResponseCaptor.getValue();
        String invocationId = response.getInvocationId();
        assertThat(invocationId).isNotEmpty();

        StopInvocationRequest.Builder stopRequest =
                StopInvocationRequest.newBuilder()
                        .setInvocationId(invocationId)
                        .setReason("stopping you");
        mServer.stopInvocation(stopRequest.build(), mStopInvocationObserver);
        verify(mStopInvocationObserver).onNext(mStopInvocationCaptor.capture());
        StopInvocationResponse stopResponse = mStopInvocationCaptor.getValue();
        assertThat(stopResponse.getStatus()).isEqualTo(StopInvocationResponse.Status.SUCCESS);

        // Query the file to delete it
        InvocationDetailRequest.Builder detailBuilder =
                InvocationDetailRequest.newBuilder().setInvocationId(response.getInvocationId());
        mServer.getInvocationDetail(detailBuilder.build(), mDetailObserver);
        verify(mDetailObserver).onNext(mResponseDetailCaptor.capture());
        InvocationDetailResponse responseDetails = mResponseDetailCaptor.getValue();
        assertThat(responseDetails.getInvocationStatus().getStatus())
                .isEqualTo(InvocationStatus.Status.DONE);
        File record = new File(responseDetails.getTestRecordPath());
        assertThat(record.exists()).isTrue();
        FileUtil.deleteFile(record);
    }
}

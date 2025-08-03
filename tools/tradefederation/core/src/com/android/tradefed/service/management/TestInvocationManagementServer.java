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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import com.proto.tradefed.invocation.CommandErrorInfo;
import com.proto.tradefed.invocation.InvocationDetailRequest;
import com.proto.tradefed.invocation.InvocationDetailResponse;
import com.proto.tradefed.invocation.InvocationStatus;
import com.proto.tradefed.invocation.InvocationStatus.Status;
import com.proto.tradefed.invocation.NewTestCommandRequest;
import com.proto.tradefed.invocation.NewTestCommandResponse;
import com.proto.tradefed.invocation.ShutdownTradefedRequest;
import com.proto.tradefed.invocation.ShutdownTradefedResponse;
import com.proto.tradefed.invocation.StopInvocationRequest;
import com.proto.tradefed.invocation.StopInvocationResponse;
import com.proto.tradefed.invocation.TestInvocationManagementGrpc.TestInvocationManagementImplBase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.protobuf.services.ProtoReflectionService;

/**
 * GRPC server helping to management test invocation and their lifecycle. This service isn't
 * currently mandatory and only runs if configured with a port.
 */
public class TestInvocationManagementServer extends TestInvocationManagementImplBase {
    private static final String TF_INVOCATION_SERVER_PORT = "TF_INVOCATION_SERVER_PORT";

    private final Server mServer;
    private final ICommandScheduler mCommandScheduler;
    private final DeviceManagementGrpcServer mDeviceReservationManager;
    private Map<String, InvocationInformation> mTracker = new ConcurrentHashMap<>();

    public class InvocationInformation {
        public final long invocationId;
        public final ScheduledInvocationForwarder scheduledInvocationForwarder;

        InvocationInformation(
                long invocationId, ScheduledInvocationForwarder scheduledInvocationForwarder) {
            this.invocationId = invocationId;
            this.scheduledInvocationForwarder = scheduledInvocationForwarder;
        }
    }

    /** Returns the port used by the server. */
    public static Integer getPort() {
        return System.getenv(TF_INVOCATION_SERVER_PORT) != null
                ? Integer.parseInt(System.getenv(TF_INVOCATION_SERVER_PORT))
                : null;
    }

    public TestInvocationManagementServer(
            int port,
            ICommandScheduler commandScheduler,
            DeviceManagementGrpcServer deviceReservationManager) {
        this(ServerBuilder.forPort(port), commandScheduler, deviceReservationManager);
    }

    @VisibleForTesting
    public TestInvocationManagementServer(
            ServerBuilder<?> serverBuilder,
            ICommandScheduler commandScheduler,
            DeviceManagementGrpcServer deviceReservationManager) {
        mServer =
                serverBuilder
                        .addService(this)
                        .addService(ProtoReflectionService.newInstance())
                        .build();
        mCommandScheduler = commandScheduler;
        mDeviceReservationManager = deviceReservationManager;
    }

    @VisibleForTesting
    public TestInvocationManagementServer(
            Server server,
            ICommandScheduler commandScheduler,
            DeviceManagementGrpcServer deviceReservationManager) {
        mServer = server;
        mCommandScheduler = commandScheduler;
        mDeviceReservationManager = deviceReservationManager;
    }

    /** Start the grpc server. */
    public void start() {
        try {
            CLog.d("Starting invocation server.");
            mServer.start();
        } catch (IOException e) {
            CLog.w("Invocation server already started: %s", e.getMessage());
        }
    }

    /** Stop the grpc server. */
    public void shutdown() throws InterruptedException {
        if (mServer != null) {
            CLog.d("Stopping invocation server.");
            if (mTracker.size() > 0) {
                CLog.d("Remaining tracked test invocations: %s", mTracker.size());
            }
            mServer.shutdown();
            mServer.awaitTermination();
        }
    }

    /** Stop the tradefed process. */
    public void exitTradefed() throws InterruptedException {
        CLog.d("Stopping tradefed process.");
        System.exit(0);
    }

    @Override
    public void submitTestCommand(
            NewTestCommandRequest request,
            StreamObserver<NewTestCommandResponse> responseObserver) {
        NewTestCommandResponse.Builder responseBuilder = NewTestCommandResponse.newBuilder();
        String[] command = request.getArgsList().toArray(new String[0]);
        File record = null;
        try {
            record = FileUtil.createTempFile("test_record", ".pb");
            CommandStatusHandler handler = new CommandStatusHandler();
            FileProtoResultReporter fileReporter = new FileProtoResultReporter();
            fileReporter.setOutputFile(record);
            fileReporter.setDelimitedOutput(false);
            fileReporter.setGranularResults(false);
            ScheduledInvocationForwarder forwarder =
                    new ScheduledInvocationForwarder(handler, fileReporter);
            List<ITestDevice> devices = null;
            if (!request.getReservationIdList().isEmpty()) {
                devices = getReservedDevices(request.getReservationIdList());
            }
            long invocationId = -1;
            if (devices == null) {
                invocationId = mCommandScheduler.execCommand(forwarder, command);
            } else {
                invocationId = mCommandScheduler.execCommand(forwarder, devices, command);
            }
            if (invocationId == -1) {
                responseBuilder.setCommandErrorInfo(
                        CommandErrorInfo.newBuilder()
                                .setErrorMessage("Something went wrong to execute the command."));
            } else {
                // TODO: Align trackerId with true invocation id
                String trackerId = UUID.randomUUID().toString();
                mTracker.put(trackerId, new InvocationInformation(invocationId, forwarder));
                responseBuilder.setInvocationId(trackerId);
            }
        } catch (ConfigurationException | IOException | RuntimeException e) {
            // TODO: Expand proto to convey those errors
            // return a response without invocation id
            FileUtil.deleteFile(record);
            CommandErrorInfo.Builder commandError = CommandErrorInfo.newBuilder();
            commandError.setErrorMessage(StreamUtil.getStackTrace(e));
            if (e instanceof IHarnessException && ((IHarnessException) e).getErrorId() != null) {
                commandError.setErrorName(((IHarnessException) e).getErrorId().name());
                commandError.setErrorCode(((IHarnessException) e).getErrorId().code());
            }
            responseBuilder.setCommandErrorInfo(commandError);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getInvocationDetail(
            InvocationDetailRequest request,
            StreamObserver<InvocationDetailResponse> responseObserver) {
        InvocationDetailResponse.Builder responseBuilder = InvocationDetailResponse.newBuilder();
        String invocationId = request.getInvocationId();
        if (mTracker.containsKey(invocationId)) {
            responseBuilder.setInvocationStatus(
                    createStatus(
                            mTracker.get(invocationId)
                                    .scheduledInvocationForwarder
                                    .getListeners()));
            if (responseBuilder.getInvocationStatus().getStatus().equals(Status.DONE)) {
                responseBuilder.setTestRecordPath(
                        getProtoPath(
                                mTracker.get(invocationId)
                                        .scheduledInvocationForwarder
                                        .getListeners()));
                // Finish the tracking after returning the first status done.
                mTracker.remove(invocationId);
            }
        } else {
            responseBuilder.setInvocationStatus(
                    InvocationStatus.newBuilder()
                            .setStatus(Status.UNKNOWN)
                            .setStatusReason("invocation id is not tracked."));
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopInvocation(
            StopInvocationRequest request,
            StreamObserver<StopInvocationResponse> responseObserver) {
        StopInvocationResponse.Builder responseBuilder = StopInvocationResponse.newBuilder();
        String invocationId = request.getInvocationId();
        if (mTracker.containsKey(invocationId)) {
            long realInvocationId = mTracker.get(invocationId).invocationId;
            boolean found =
                    mCommandScheduler.stopInvocation((int) realInvocationId, request.getReason());
            if (found) {
                responseBuilder.setStatus(StopInvocationResponse.Status.SUCCESS);
            } else {
                responseBuilder
                        .setStatus(StopInvocationResponse.Status.ERROR)
                        .setCommandErrorInfo(
                                CommandErrorInfo.newBuilder()
                                        .setErrorMessage(
                                                "No running matching invocation to stop."));
            }
        } else {
            responseBuilder
                    .setStatus(StopInvocationResponse.Status.ERROR)
                    .setCommandErrorInfo(
                            CommandErrorInfo.newBuilder()
                                    .setErrorMessage("invocation id is not tracked."));
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void shutdownTradefed(
            ShutdownTradefedRequest request,
            StreamObserver<ShutdownTradefedResponse> responseObserver) {
        ShutdownTradefedResponse.Builder responseBuilder = ShutdownTradefedResponse.newBuilder();
        int shutdownDelayMs = request.getShutdownDelay();
        CLog.i(
                "Received Tradefed Process exit gRPC request with delay %s milliseconds",
                shutdownDelayMs);
        new Thread(
                        () -> {
                            RunUtil.getDefault().sleep(shutdownDelayMs);
                            CLog.i(
                                    "Tradefed shutdown delay reached, exiting the Tradefed java"
                                            + " process.");
                            System.exit(0);
                        })
                .start();
        responseBuilder.setStatus(ShutdownTradefedResponse.Status.RECEIVED);
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private InvocationStatus createStatus(List<ITestInvocationListener> listeners) {
        InvocationStatus.Builder invocationStatusBuilder = InvocationStatus.newBuilder();
        Status status = Status.UNKNOWN;
        for (ITestInvocationListener listener : listeners) {
            if (listener instanceof CommandStatusHandler) {
                status = ((CommandStatusHandler) listener).getCurrentStatus();
            }
        }
        invocationStatusBuilder.setStatus(status);
        if (Status.UNKNOWN.equals(status)) {
            invocationStatusBuilder.setStatusReason("Failed to find the CommandStatusHandler.");
        }
        return invocationStatusBuilder.build();
    }

    private String getProtoPath(List<ITestInvocationListener> listeners) {
        for (ITestInvocationListener listener : listeners) {
            if (listener instanceof FileProtoResultReporter) {
                return ((FileProtoResultReporter) listener).getOutputFile().getAbsolutePath();
            }
        }
        return null;
    }

    private List<ITestDevice> getReservedDevices(List<String> reservationIds) {
        if (mDeviceReservationManager == null) {
            return null;
        }

        List<ITestDevice> devices = new ArrayList<>();
        for (String id : reservationIds) {
            ITestDevice device = mDeviceReservationManager.getDeviceFromReservation(id);
            if (device == null) {
                throw new RuntimeException(
                        String.format("Device with reservationId %s not found", id));
            }
            devices.add(device);
        }
        return devices;
    }
}

package com.android.tradefed.service.management;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.command.CommandScheduler;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.base.Strings;
import com.proto.tradefed.device.DeviceManagementGrpc.DeviceManagementImplBase;
import com.proto.tradefed.device.DeviceStatus;
import com.proto.tradefed.device.DeviceStatus.ReservationStatus;
import com.proto.tradefed.device.GetDevicesStatusRequest;
import com.proto.tradefed.device.GetDevicesStatusResponse;
import com.proto.tradefed.device.ReleaseReservationRequest;
import com.proto.tradefed.device.ReleaseReservationResponse;
import com.proto.tradefed.device.ReserveDeviceRequest;
import com.proto.tradefed.device.ReserveDeviceResponse;
import com.proto.tradefed.device.ReserveDeviceResponse.Result;
import com.proto.tradefed.device.StopLeasingRequest;
import com.proto.tradefed.device.StopLeasingResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/** GRPC server allowing to reserve a device from Tradefed. */
public class DeviceManagementGrpcServer extends DeviceManagementImplBase {
    private static final String TF_DEVICE_MANAGEMENT_PORT = "TF_DEVICE_MANAGEMENT_PORT";

    private final Server mServer;
    private final IDeviceManager mDeviceManager;
    private final ICommandScheduler mCommandScheduler;
    private final Map<String, ReservationInformation> mSerialToReservation =
            new ConcurrentHashMap<>();

    /** Returns the port used by the server. */
    public static Integer getPort() {
        return System.getenv(TF_DEVICE_MANAGEMENT_PORT) != null
                ? Integer.parseInt(System.getenv(TF_DEVICE_MANAGEMENT_PORT))
                : null;
    }

    public DeviceManagementGrpcServer(
            int port, IDeviceManager deviceManager, ICommandScheduler scheduler) {
        this(ServerBuilder.forPort(port), deviceManager, scheduler);
    }

    @VisibleForTesting
    public DeviceManagementGrpcServer(
            ServerBuilder<?> serverBuilder,
            IDeviceManager deviceManager,
            ICommandScheduler scheduler) {
        mServer = serverBuilder.addService(this).build();
        mDeviceManager = deviceManager;
        mCommandScheduler = scheduler;
    }

    @VisibleForTesting
    public DeviceManagementGrpcServer(
            Server server, IDeviceManager deviceManager, ICommandScheduler scheduler) {
        mServer = server;
        mDeviceManager = deviceManager;
        mCommandScheduler = scheduler;
    }

    /** Start the grpc server. */
    public void start() {
        try {
            CLog.d("Starting device management server.");
            mServer.start();
        } catch (IOException e) {
            CLog.w("Device management server already started: %s", e.getMessage());
        }
    }

    /** Stop the grpc server. */
    public void shutdown() throws InterruptedException {
        if (mServer != null) {
            CLog.d("Stopping device management server.");
            mServer.shutdown();
            mServer.awaitTermination();
        }
    }

    @Override
    public void getDevicesStatus(
            GetDevicesStatusRequest request,
            StreamObserver<GetDevicesStatusResponse> responseObserver) {
        GetDevicesStatusResponse.Builder responseBuilder = GetDevicesStatusResponse.newBuilder();
        if (request.getDeviceIdList().isEmpty()) {
            for (DeviceDescriptor descriptor : mDeviceManager.listAllDevices(true)) {
                responseBuilder.addDeviceStatus(descriptorToStatus(descriptor));
            }
        } else {
            for (String serial : request.getDeviceIdList()) {
                DeviceDescriptor descriptor = mDeviceManager.getDeviceDescriptor(serial);
                responseBuilder.addDeviceStatus(descriptorToStatus(descriptor));
            }
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void releaseReservation(
            ReleaseReservationRequest request,
            StreamObserver<ReleaseReservationResponse> responseObserver) {
        ReleaseReservationResponse.Builder responseBuilder =
                ReleaseReservationResponse.newBuilder();
        ITestDevice device = getDeviceFromReservation(request.getReservationId());
        if (device == null) {
            responseBuilder
                    .setResult(ReleaseReservationResponse.Result.RESERVATION_NOT_EXIST)
                    .setMessage(
                            String.format(
                                    "Reservation id released '%s' is untracked",
                                    request.getReservationId()));
        } else if (mCommandScheduler.isDeviceInInvocationThread(device)) {
            responseBuilder
                    .setResult(ReleaseReservationResponse.Result.DEVICE_IN_USE)
                    .setMessage(
                            String.format(
                                    "Reservation '%s' is still in use",
                                    request.getReservationId()));
        } else {
            releaseReservationInternal(request.getReservationId());
            responseBuilder.setResult(ReleaseReservationResponse.Result.SUCCEED);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void reserveDevice(
            ReserveDeviceRequest request, StreamObserver<ReserveDeviceResponse> responseObserver) {
        ReserveDeviceResponse.Builder responseBuilder = ReserveDeviceResponse.newBuilder();
        ServerCallStreamObserver<ReserveDeviceResponse> serverCallStreamObserver =
                (ServerCallStreamObserver<ReserveDeviceResponse>) responseObserver;
        String serial = request.getDeviceId();
        if (Strings.isNullOrEmpty(serial)) {
            responseBuilder
                    .setResult(Result.UNKNOWN)
                    .setMessage("serial requested was null or empty.");
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            return;
        }
        if (mCommandScheduler instanceof CommandScheduler) {
            if (((CommandScheduler) mCommandScheduler).isShuttingDown()) {
                responseBuilder
                        .setResult(Result.UNKNOWN)
                        .setMessage("Tradefed is shutting down, rejecting reservation.");
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                return;
            }
        }

        DeviceDescriptor descriptor = mDeviceManager.getDeviceDescriptor(serial);
        if (descriptor == null) {
            responseBuilder
                    .setResult(Result.UNKNOWN)
                    .setMessage("No descriptor found for serial " + serial);
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            return;
        }
        if (DeviceAllocationState.Allocated.equals(descriptor.getState())) {
            Result result = Result.ALREADY_ALLOCATED;
            if (mSerialToReservation.containsKey(serial)) {
                result = Result.ALREADY_RESERVED;
            }
            responseBuilder.setResult(result).setMessage("device is currently in allocated state.");
        } else if (DeviceAllocationState.Unavailable.equals(descriptor.getState())) {
            responseBuilder
                    .setResult(Result.UNAVAILABLE)
                    .setMessage("device is currently in unavailable state.");
        } else if (!serverCallStreamObserver.isCancelled()) {
            DeviceSelectionOptions selection = new DeviceSelectionOptions();
            // Allow reservation to hold any placeholder
            // We have to match serial because selection is exclusive and doesn't
            // currently allow a wide match. We could improve that in the future.
            if (serial.startsWith("gce-device")) {
                selection.setGceDeviceRequested(true);
            }
            if (serial.startsWith("null-device")) {
                selection.setNullDeviceRequested(true);
            }
            selection.addSerial(serial);
            ITestDevice device = mDeviceManager.allocateDevice(selection);
            if (device == null) {
                responseBuilder
                        .setResult(Result.UNKNOWN)
                        .setMessage(
                                String.format(
                                        "Failed to allocate '%s' reason: '%s'",
                                        serial, selection.getNoMatchReason()));
            } else {
                String reservationId = UUID.randomUUID().toString();
                responseBuilder.setResult(Result.SUCCEED).setReservationId(reservationId);
                mSerialToReservation.put(serial, new ReservationInformation(device, reservationId));
            }
        }
        // Double check isCancelled because the client may cancel the RPC when allocating device.
        if (serverCallStreamObserver.isCancelled()) {
            CLog.d("The client call is cancelled.");
            if (responseBuilder.getResult().equals(Result.SUCCEED)
                    && !responseBuilder.getReservationId().isEmpty()) {
                releaseReservationInternal(responseBuilder.getReservationId());
            }
            responseBuilder
                    .clear()
                    .setResult(Result.UNKNOWN)
                    .setMessage("The device reservation RPC is cancelled by client.");
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopLeasing(
            StopLeasingRequest request, StreamObserver<StopLeasingResponse> responseObserver) {
        StopLeasingResponse.Builder responseBuilder = StopLeasingResponse.newBuilder();

        // Notify to stop leasing
        try {
            mCommandScheduler.stopScheduling();
            responseBuilder.setResult(StopLeasingResponse.Result.SUCCEED);
        } catch (RuntimeException e) {
            // This might happen in case scheduler isn't started or in bad state.
            responseBuilder.setResult(StopLeasingResponse.Result.FAIL);
            responseBuilder.setMessage(e.getMessage());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private void releaseReservationInternal(String reservationId) {
        Entry<String, ReservationInformation> entry = getDeviceEntryFromReservation(reservationId);
        if (entry != null) {
            mDeviceManager.freeDevice(entry.getValue().device, FreeDeviceState.AVAILABLE);
            // Only release reservation when done with free.
            mSerialToReservation.remove(entry.getKey());
        }
    }

    private DeviceStatus descriptorToStatus(DeviceDescriptor descriptor) {
        DeviceStatus.Builder deviceStatusBuilder = DeviceStatus.newBuilder();
        deviceStatusBuilder.setDeviceId(descriptor.getSerial());
        deviceStatusBuilder.setReservationStatus(
                allocationStateToReservation(descriptor.getState(), descriptor.getSerial()));
        return deviceStatusBuilder.build();
    }

    private ReservationStatus allocationStateToReservation(
            DeviceAllocationState state, String serial) {
        switch (state) {
            case Available:
                return ReservationStatus.READY;
            case Allocated:
                if (mSerialToReservation.containsKey(serial)) {
                    return ReservationStatus.RESERVED;
                }
                return ReservationStatus.ALLOCATED;
            case Unavailable:
            case Ignored:
                return ReservationStatus.UNAVAILABLE;
            case Checking_Availability:
            case Unknown:
            default:
                return ReservationStatus.UNKNOWN;
        }
    }

    private Entry<String, ReservationInformation> getDeviceEntryFromReservation(
            String reservationId) {
        for (Entry<String, ReservationInformation> info : mSerialToReservation.entrySet()) {
            if (info.getValue().reservationId.equals(reservationId)) {
                return info;
            }
        }
        return null;
    }

    public ITestDevice getDeviceFromReservation(String reservationId) {
        Entry<String, ReservationInformation> entry = getDeviceEntryFromReservation(reservationId);
        if (entry != null) {
            return entry.getValue().device;
        }
        return null;
    }

    private class ReservationInformation {
        final ITestDevice device;
        final String reservationId;

        ReservationInformation(ITestDevice device, String reservationId) {
            this.device = device;
            this.reservationId = reservationId;
        }
    }
}

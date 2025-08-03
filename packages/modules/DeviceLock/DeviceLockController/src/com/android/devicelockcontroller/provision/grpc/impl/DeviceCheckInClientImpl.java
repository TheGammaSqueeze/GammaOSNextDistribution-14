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

package com.android.devicelockcontroller.provision.grpc.impl;

import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_DEADLINE_PASSED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_UNSPECIFIED;

import android.util.ArraySet;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.proto.ClientDeviceIdentifier;
import com.android.devicelockcontroller.proto.ClientProvisionFailureReason;
import com.android.devicelockcontroller.proto.ClientProvisionState;
import com.android.devicelockcontroller.proto.DeviceIdentifierType;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceBlockingStub;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.proto.IsDeviceInApprovedCountryRequest;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningReason;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningRequest;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionStateRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.util.ThreadAsserts;

import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A client for the {@link  com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc}
 * service.
 */
@Keep
public final class DeviceCheckInClientImpl extends DeviceCheckInClient {
    private final DeviceLockCheckinServiceBlockingStub mBlockingStub;

    public DeviceCheckInClientImpl() {
        mBlockingStub = DeviceLockCheckinServiceGrpc.newBlockingStub(
                        OkHttpChannelBuilder
                                .forAddress(sHostName, sPortNumber)
                                .build())
                .withInterceptors(new ApiKeyClientInterceptor(sApiKey));
    }

    @Override
    public GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken) {
        ThreadAsserts.assertWorkerThread("getDeviceCheckInStatus");
        try {
            final GetDeviceCheckInStatusGrpcResponse response =
                    new GetDeviceCheckInStatusGrpcResponseWrapper(
                            mBlockingStub.getDeviceCheckinStatus(
                                    createGetDeviceCheckinStatusRequest(deviceIds, carrierInfo)));
            return response;
        } catch (StatusRuntimeException e) {
            return new GetDeviceCheckInStatusGrpcResponseWrapper(e.getStatus());
        }
    }

    /**
     * Check if the device is in an approved country for the device lock program.
     *
     * @param carrierInfo The information of the device's sim operator which is used to determine
     *                    the device's geological location and eventually eligibility of the
     *                    DeviceLock program. Could be null if unavailable.
     * @return A class that encapsulate the response from the backend server.
     */
    @Override
    public IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo) {
        ThreadAsserts.assertWorkerThread("isDeviceInApprovedCountry");
        try {
            return new IsDeviceInApprovedCountryGrpcResponseWrapper(
                    mBlockingStub.isDeviceInApprovedCountry(
                            createIsDeviceInApprovedCountryRequest(carrierInfo, sRegisteredId)));
        } catch (StatusRuntimeException e) {
            return new IsDeviceInApprovedCountryGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason) {
        ThreadAsserts.assertWorkerThread("pauseDeviceProvisioning");
        try {
            mBlockingStub.pauseDeviceProvisioning(
                    createPauseDeviceProvisioningRequest(sRegisteredId, reason));
            return new PauseDeviceProvisioningGrpcResponse();
        } catch (StatusRuntimeException e) {
            return new PauseDeviceProvisioningGrpcResponse(e.getStatus());
        }
    }

    /**
     * Reports the current provision state of the device.
     *
     * @param lastReceivedProvisionState one of {@link DeviceProvisionState}.
     *                                   It must be the value from the response when this API
     *                                   was called last time. If this API is called for the first
     *                                   time, then
     *                                   {@link
     *                                   DeviceProvisionState#PROVISION_STATE_UNSPECIFIED }
     *                                   must be used.
     * @param isSuccessful               true if the device has been setup for DeviceLock program
     *                                   successful; false otherwise.
     * @return A class that encapsulate the response from the backend server.
     */
    @Override
    public ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            int lastReceivedProvisionState, boolean isSuccessful,
            @ProvisionFailureReason int reason) {
        ThreadAsserts.assertWorkerThread("reportDeviceProvisionState");
        try {
            return new ReportDeviceProvisionStateGrpcResponseWrapper(
                    mBlockingStub.reportDeviceProvisionState(
                            createReportDeviceProvisionStateRequest(lastReceivedProvisionState,
                                    isSuccessful, sRegisteredId, reason)));
        } catch (StatusRuntimeException e) {
            return new ReportDeviceProvisionStateGrpcResponseWrapper(e.getStatus());
        }
    }

    private static GetDeviceCheckinStatusRequest createGetDeviceCheckinStatusRequest(
            ArraySet<DeviceId> deviceIds, String carrierInfo) {
        GetDeviceCheckinStatusRequest.Builder builder = GetDeviceCheckinStatusRequest.newBuilder();
        for (DeviceId deviceId : deviceIds) {
            DeviceIdentifierType type;
            switch (deviceId.getType()) {
                case DeviceIdType.DEVICE_ID_TYPE_UNSPECIFIED:
                    type = DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_UNSPECIFIED;
                    break;
                case DeviceIdType.DEVICE_ID_TYPE_IMEI:
                    type = DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_IMEI;
                    break;
                case DeviceIdType.DEVICE_ID_TYPE_MEID:
                    type = DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_MEID;
                    break;
                default:
                    throw new IllegalStateException(
                            "Unexpected DeviceId type: " + deviceId.getType());
            }
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(type)
                            .setDeviceIdentifier(deviceId.getId()));
        }
        builder.setCarrierMccmnc(carrierInfo);
        builder.setDeviceManufacturer(android.os.Build.MANUFACTURER);
        builder.setDeviceModel(android.os.Build.MODEL);
        builder.setDeviceInternalName(android.os.Build.DEVICE);
        return builder.build();
    }

    private static IsDeviceInApprovedCountryRequest createIsDeviceInApprovedCountryRequest(
            String carrierInfo, String registeredId) {
        return IsDeviceInApprovedCountryRequest.newBuilder()
                .setCarrierMccmnc(carrierInfo)
                .setRegisteredDeviceIdentifier(registeredId)
                .build();
    }

    private static PauseDeviceProvisioningRequest createPauseDeviceProvisioningRequest(
            String registeredId,
            @DeviceLockConstants.PauseDeviceProvisioningReason int reason) {
        return PauseDeviceProvisioningRequest.newBuilder()
                .setRegisteredDeviceIdentifier(registeredId)
                .setPauseDeviceProvisioningReason(
                        PauseDeviceProvisioningReason.forNumber(reason))
                .build();
    }

    private static ReportDeviceProvisionStateRequest createReportDeviceProvisionStateRequest(
            @DeviceProvisionState int lastReceivedProvisionState,
            boolean isSuccessful,
            String registeredId,
            @ProvisionFailureReason int reason) {
        ClientProvisionState state;
        switch (lastReceivedProvisionState) {
            case DeviceProvisionState.PROVISION_STATE_UNSPECIFIED:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_UNSPECIFIED;
                break;
            case DeviceProvisionState.PROVISION_STATE_RETRY:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_RETRY;
                break;
            case DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_DISMISSIBLE_UI;
                break;
            case DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_PERSISTENT_UI;
                break;
            case DeviceProvisionState.PROVISION_STATE_FACTORY_RESET:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_FACTORY_RESET;
                break;
            case DeviceProvisionState.PROVISION_STATE_SUCCESS:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_SUCCESS;
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected value: " + lastReceivedProvisionState);
        }
        ClientProvisionFailureReason reasonProto;
        switch (reason) {
            case ProvisionFailureReason.UNKNOWN_REASON:
                reasonProto = PROVISION_FAILURE_REASON_UNSPECIFIED;
                break;
            case ProvisionFailureReason.PLAY_TASK_UNAVAILABLE:
                reasonProto = PROVISION_FAILURE_REASON_PLAY_TASK_UNAVAILABLE;
                break;
            case ProvisionFailureReason.PLAY_INSTALLATION_FAILED:
                reasonProto = PROVISION_FAILURE_REASON_PLAY_INSTALLATION_FAILED;
                break;
            case ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE:
                reasonProto = PROVISION_FAILURE_REASON_COUNTRY_INFO_UNAVAILABLE;
                break;
            case ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY:
                reasonProto = PROVISION_FAILURE_REASON_NOT_IN_ELIGIBLE_COUNTRY;
                break;
            case ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED:
                reasonProto = PROVISION_FAILURE_REASON_POLICY_ENFORCEMENT_FAILED;
                break;
            case ProvisionFailureReason.DEADLINE_PASSED:
                reasonProto = PROVISION_FAILURE_REASON_DEADLINE_PASSED;
                break;
            default:
                throw new IllegalArgumentException("Unexpected value: " + reason);
        }
        ReportDeviceProvisionStateRequest.Builder builder =
                ReportDeviceProvisionStateRequest.newBuilder()
                        .setPreviousClientProvisionState(state)
                        .setProvisionSuccess(isSuccessful)
                        .setRegisteredDeviceIdentifier(registeredId);
        if (!isSuccessful) {
            builder.setClientProvisionFailureReason(reasonProto);
        }
        return builder.build();
    }
}

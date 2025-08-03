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

import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.RETRY_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STATUS_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STOP_CHECK_IN;
import static com.android.devicelockcontroller.proto.DeviceProvisionType.DEVICE_PROVISION_TYPE_MANDATORY;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceCheckInStatus;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.proto.ConfigurationInfo;
import com.android.devicelockcontroller.proto.DeviceProvisioningInformation;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusResponse;
import com.android.devicelockcontroller.proto.NextCheckinInformation;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;

import com.google.protobuf.Timestamp;

import io.grpc.Status;

import java.time.Instant;

/**
 * Wrapper for response and status objects for a GetDeviceCheckinStatusResponse.
 */
final class GetDeviceCheckInStatusGrpcResponseWrapper extends GetDeviceCheckInStatusGrpcResponse {
    @Nullable
    private final GetDeviceCheckinStatusResponse mResponse;
    @Nullable
    private final NextStepInformation mNextStep;

    GetDeviceCheckInStatusGrpcResponseWrapper(@NonNull Status status) {
        super(status);
        mResponse = null;
        mNextStep = null;
    }

    GetDeviceCheckInStatusGrpcResponseWrapper(
            @NonNull GetDeviceCheckinStatusResponse response) {
        super();
        mResponse = response;
        mNextStep = getNextStepInformation();
    }

    @Override
    @DeviceCheckInStatus
    public int getDeviceCheckInStatus() {
        if (mResponse != null) {
            switch (mResponse.getClientCheckinStatus()) {
                case CLIENT_CHECKIN_STATUS_READY_FOR_PROVISION:
                    return READY_FOR_PROVISION;
                case CLIENT_CHECKIN_STATUS_RETRY_CHECKIN:
                    return RETRY_CHECK_IN;
                case CLIENT_CHECKIN_STATUS_STOP_CHECKIN:
                    return STOP_CHECK_IN;
                case CLIENT_CHECKIN_STATUS_UNSPECIFIED:
                default:
                    // fall through
            }
        }
        return STATUS_UNSPECIFIED;
    }

    @Override
    @Nullable
    public String getRegisteredDeviceIdentifier() {
        return mResponse != null ? mResponse.getRegisteredDeviceIdentifier() : null;
    }

    @Override
    @Nullable
    public Instant getNextCheckInTime() {
        if (mResponse == null || !mNextStep.isNextCheckInInformationAvailable()) {
            return null;
        }
        Timestamp nextCheckInTime = mNextStep.getNextCheckInInformation().getNextCheckinTimestamp();
        return Instant.ofEpochSecond(nextCheckInTime.getSeconds(), nextCheckInTime.getNanos());
    }

    @Override
    @Nullable
    public ProvisioningConfiguration getProvisioningConfig() {
        if (mResponse == null || !mNextStep.isDeviceProvisioningInformationAvailable()) {
            return null;
        }
        ConfigurationInfo info =
                mNextStep.getDeviceProvisioningInformation().getConfigurationInformation();
        return new ProvisioningConfiguration(
                info.getKioskAppProviderName(),
                info.getKioskAppPackage(),
                info.getKioskAppAllowlistPackagesList(),
                info.getKioskAppEnableOutgoingCalls(),
                info.getKioskAppEnableNotifications(),
                info.getDisallowInstallingFromUnknownSources(),
                info.getTermsAndConditionsUrl(),
                info.getSupportUrl());
    }

    @Override
    @ProvisioningType
    public int getProvisioningType() {
        if (mResponse == null || !mNextStep.isDeviceProvisioningInformationAvailable()) {
            return ProvisioningType.TYPE_UNDEFINED;
        }

        switch (mNextStep.getDeviceProvisioningInformation().getEnrollmentType()) {
            case ENROLLMENT_TYPE_FINANCE:
                return ProvisioningType.TYPE_FINANCED;
            case ENROLLMENT_TYPE_SUBSIDY:
                return ProvisioningType.TYPE_SUBSIDY;
            case ENROLLMENT_TYPE_UNSPECIFIED:
                return ProvisioningType.TYPE_UNDEFINED;
            default:
                throw new IllegalArgumentException("Unknown enrollment type");
        }
    }

    @Override
    public boolean isProvisioningMandatory() {
        if (mResponse == null || !mNextStep.isDeviceProvisioningInformationAvailable()) {
            return false;
        }

        return mNextStep.getDeviceProvisioningInformation().getDeviceProvisionType()
                == DEVICE_PROVISION_TYPE_MANDATORY;
    }

    @Override
    public boolean isProvisionForced() {
        if (mResponse == null || !mNextStep.isDeviceProvisioningInformationAvailable()) {
            return false;
        }

        return getNextStepInformation().getDeviceProvisioningInformation().getForceProvisioning();
    }

    @Override
    public boolean isDeviceInApprovedCountry() {
        if (mResponse == null || !mNextStep.isDeviceProvisioningInformationAvailable()) {
            return false;
        }

        return getNextStepInformation().getDeviceProvisioningInformation()
                .getIsDeviceInApprovedCountry();
    }

    @NonNull
    private NextStepInformation getNextStepInformation() {
        if (mResponse != null) {
            switch (mResponse.getNextStepsCase()) {
                case NEXT_CHECKIN_INFORMATION:
                    return new NextStepInformation(mResponse.getNextCheckinInformation());
                case DEVICE_PROVISIONING_INFORMATION:
                    return new NextStepInformation(mResponse.getDeviceProvisioningInformation());
                case NEXTSTEPS_NOT_SET:
                default:
                    // fall through
            }
        }
        return new NextStepInformation();
    }

    /**
     * A class that stores the information about next step in the check-in process.
     */
    public static final class NextStepInformation {

        //TODO: Consider to use a builder pattern to ensure at least one of the below fields is
        // not null value so that we can eliminate the need of two separate constructors.
        @Nullable
        private final NextCheckinInformation mNextCheckInInformation;
        @Nullable
        private final DeviceProvisioningInformation mDeviceProvisioningInformation;

        private NextStepInformation() {
            mNextCheckInInformation = null;
            mDeviceProvisioningInformation = null;
        }

        private NextStepInformation(@NonNull NextCheckinInformation information) {
            mNextCheckInInformation = information;
            mDeviceProvisioningInformation = null;
        }

        private NextStepInformation(@NonNull DeviceProvisioningInformation information) {
            mNextCheckInInformation = null;
            mDeviceProvisioningInformation = information;
        }

        public boolean isNextCheckInInformationAvailable() {
            return mNextCheckInInformation != null;
        }

        @Nullable
        public NextCheckinInformation getNextCheckInInformation() {
            return mNextCheckInInformation;
        }

        public boolean isDeviceProvisioningInformationAvailable() {
            return mDeviceProvisioningInformation != null;
        }

        @Nullable
        public DeviceProvisioningInformation getDeviceProvisioningInformation() {
            return mDeviceProvisioningInformation;
        }
    }
}

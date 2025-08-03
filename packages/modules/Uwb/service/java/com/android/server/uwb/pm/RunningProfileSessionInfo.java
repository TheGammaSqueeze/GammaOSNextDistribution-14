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

package com.android.server.uwb.pm;

import androidx.annotation.NonNull;

import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.UwbCapability;
import com.android.server.uwb.util.ObjectIdentifier;

import java.util.List;
import java.util.Optional;

/**
 * Provides the session information for this profile
 */
public class RunningProfileSessionInfo {
    /** see {@link ControleeInfo} required by the controlee */
    @NonNull
    public final Optional<ControleeInfo> controleeInfo;
    /** see {@link UwbCapability} */
    @NonNull
    public final UwbCapability uwbCapability;
    /** The possible ADF OIDs used in the responder device, required by initiator. */
    @NonNull
    public final Optional<List<ObjectIdentifier>> selectableOidsOfResponder;
    /**
     * The OID of ADF which was provisioned successfully for the current profile,
     * required by initiator.
     */
    @NonNull
    public final ObjectIdentifier oidOfProvisionedAdf;
    /** The UWB session ID for multicast case, required by controller */
    @NonNull
    public final Optional<Integer> sharedPrimarySessionId;
    /** The UWB session ID for multicast case, required by controller */
    @NonNull
    public final Optional<byte[]> sharedPrimarySessionKeyInfo;
    /** The secure blob can be loaded into the FiRa applet. */
    @NonNull
    public final Optional<byte[]> secureBlob;

    private RunningProfileSessionInfo(
            Optional<ControleeInfo> controleeInfo,
            UwbCapability uwbCapability,
            ObjectIdentifier oidOfProvisionedAdf,
            Optional<List<ObjectIdentifier>> selectableOidsOfResponder,
            Optional<Integer> sharedPrimarySessionId,
            Optional<byte[]> sharedPrimarySessionKeyInfo,
            Optional<byte[]> secureBlob) {
        this.controleeInfo = controleeInfo;
        this.uwbCapability = uwbCapability;
        this.oidOfProvisionedAdf = oidOfProvisionedAdf;
        this.selectableOidsOfResponder = selectableOidsOfResponder;
        this.sharedPrimarySessionId = sharedPrimarySessionId;
        this.sharedPrimarySessionKeyInfo = sharedPrimarySessionKeyInfo;
        this.secureBlob = secureBlob;
    }

    /** Builder for the {@link RunningProfileSessionInfo} */
    public static class Builder {
        private Optional<ControleeInfo> mControleeInfo;
        private UwbCapability mUwbCapability;
        private Optional<List<ObjectIdentifier>> mSelectableOidsOfResponder = Optional.empty();
        private ObjectIdentifier mOidOfProvisionedAdf;
        private Optional<Integer> mSharedPrimarySessionId = Optional.empty();
        private Optional<byte[]> mSecureBlob = Optional.empty();
        private Optional<byte[]> mSharedPrimarySessionKeyInfo = Optional.empty();

        /** The constructor {@link RunningProfileSessionInfo.Builder} */
        public Builder(@NonNull UwbCapability uwbCapability,
                @NonNull ObjectIdentifier oidOfProvisionedAdf) {
            this.mUwbCapability = uwbCapability;
            this.mOidOfProvisionedAdf = oidOfProvisionedAdf;
        }

        /** Sets the {@link ControleeInfo}. */
        @NonNull
        public Builder setControleeInfo(@NonNull ControleeInfo controleeInfo) {
            this.mControleeInfo = Optional.of(controleeInfo);
            return this;
        }

        /** Sets the possible ADF OIDs used in the responder device. */
        @NonNull
        public Builder setSelectableOidsOfResponder(
                @NonNull List<ObjectIdentifier> selectableOidsOfResponder) {
            this.mSelectableOidsOfResponder = Optional.of(selectableOidsOfResponder);
            return this;
        }

        /** Sets the UWB session ID and session key info for multicast case. */
        @NonNull
        public Builder setSharedPrimarySessionIdAndSessionKeyInfo(
                int sharedPrimarySessionId,
                @NonNull byte[] sharedPrimarySessionKeyInfo) {
            this.mSharedPrimarySessionId = Optional.of(sharedPrimarySessionId);
            this.mSharedPrimarySessionKeyInfo = Optional.of(sharedPrimarySessionKeyInfo);
            return this;
        }

        /** Sets the secure blob can be loaded into the FiRa applet. */
        @NonNull
        public Builder setSecureBlob(@NonNull byte[] secureBlob) {
            mSecureBlob = Optional.of(secureBlob);
            return this;
        }

        /** Builds the instance of {@link RunningProfileSessionInfo} */
        @NonNull
        public RunningProfileSessionInfo build() {
            return new RunningProfileSessionInfo(
                    mControleeInfo,
                    mUwbCapability,
                    mOidOfProvisionedAdf,
                    mSelectableOidsOfResponder,
                    mSharedPrimarySessionId,
                    mSharedPrimarySessionKeyInfo,
                    mSecureBlob);
        }
    }
}

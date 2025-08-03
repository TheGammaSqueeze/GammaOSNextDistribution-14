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

import androidx.annotation.Keep;

import com.android.devicelockcontroller.proto.DeviceLockFinalizeServiceGrpc;
import com.android.devicelockcontroller.proto.ReportDeviceProgramCompleteRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;
import com.android.devicelockcontroller.util.ThreadAsserts;

import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A client for {@link com.android.devicelockcontroller.proto.DeviceLockFinalizeServiceGrpc}.
 */
@Keep
public final class DeviceFinalizeClientImpl extends DeviceFinalizeClient {
    private final DeviceLockFinalizeServiceGrpc.DeviceLockFinalizeServiceBlockingStub mBlockingStub;

    public DeviceFinalizeClientImpl() {
        mBlockingStub = DeviceLockFinalizeServiceGrpc.newBlockingStub(
                        OkHttpChannelBuilder
                                .forAddress(sHostName, sPortNumber)
                                .build())
                .withInterceptors(new ApiKeyClientInterceptor(sApiKey));
    }

    /**
     * Reports that a device completed a Device Lock program.
     */
    @Override
    public ReportDeviceProgramCompleteResponse reportDeviceProgramComplete() {
        ThreadAsserts.assertWorkerThread("reportDeviceProgramComplete");
        try {
            mBlockingStub.reportDeviceProgramComplete(
                    ReportDeviceProgramCompleteRequest.newBuilder().setRegisteredDeviceIdentifier(
                            sRegisteredId).build());
            return new ReportDeviceProgramCompleteResponse();
        } catch (StatusRuntimeException e) {
            return new ReportDeviceProgramCompleteResponse(e.getStatus());
        }
    }
}

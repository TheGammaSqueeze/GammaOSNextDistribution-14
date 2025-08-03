/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.devicestate.cts;

import static android.server.wm.DeviceStateUtils.runWithControlDeviceStatePermission;

import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;

import androidx.annotation.NonNull;

/**
 * An implementation of {@link java.lang.AutoCloseable} that submits a request to override the
 * device state using the provided {@link DeviceStateRequest} and automatically cancels the request
 * on a call to {@link #close()}.
 */
public final class DeviceStateRequestSession implements AutoCloseable {
    @NonNull
    private final DeviceStateManager mDeviceStateManager;
    @NonNull
    private final DeviceStateRequest mRequest;
    @NonNull
    private final DeviceStateRequest.Callback mCallback;
    private final boolean mIsBaseStateRequest;

    public DeviceStateRequestSession(@NonNull DeviceStateManager manager,
            @NonNull DeviceStateRequest request, boolean isBaseStateRequest,
            @NonNull DeviceStateRequest.Callback callback) {
        mDeviceStateManager = manager;
        mRequest = request;
        mIsBaseStateRequest = isBaseStateRequest;
        mCallback = callback;

        submitRequest(request);
    }

    private void submitRequest(@NonNull DeviceStateRequest request) {
        try {
            if (mIsBaseStateRequest) {
                runWithControlDeviceStatePermission(() ->
                        mDeviceStateManager.requestBaseStateOverride(mRequest, Runnable::run,
                                mCallback));
            } else {
                runWithControlDeviceStatePermission(() ->
                        mDeviceStateManager.requestState(mRequest, Runnable::run, mCallback));
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void close() {
        try {
            if (mIsBaseStateRequest) {
                runWithControlDeviceStatePermission(mDeviceStateManager::cancelBaseStateOverride);
            } else {
                runWithControlDeviceStatePermission(mDeviceStateManager::cancelStateRequest);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

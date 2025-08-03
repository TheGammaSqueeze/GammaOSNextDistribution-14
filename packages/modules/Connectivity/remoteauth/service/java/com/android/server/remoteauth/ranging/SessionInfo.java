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

package com.android.server.remoteauth.ranging;

import static com.android.server.remoteauth.ranging.RangingCapabilities.RANGING_METHOD_UNKNOWN;

import com.android.internal.util.Preconditions;
import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;

/** Information about the {@link RangingSession}. */
public class SessionInfo {

    private final String mDeviceId;
    @RangingMethod private final int mRangingMethod;

    public String getDeviceId() {
        return mDeviceId;
    }

    @RangingMethod
    public int getRangingMethod() {
        return mRangingMethod;
    }

    private SessionInfo(String deviceId, @RangingMethod int rangingMethod) {
        mDeviceId = deviceId;
        mRangingMethod = rangingMethod;
    }

    @Override
    public String toString() {
        return "SessionInfo { "
                + "DeviceId = "
                + mDeviceId
                + "RangingMethod = "
                + mRangingMethod
                + " }";
    }

    /** Builder class for {@link SessionInfo}. */
    public static final class Builder {
        private String mDeviceId = "";
        @RangingMethod private int mRangingMethod = RANGING_METHOD_UNKNOWN;

        /** Sets the device id. */
        public Builder setDeviceId(String deviceId) {
            mDeviceId = deviceId;
            return this;
        }

        /** Sets the ranging method. */
        public Builder setRangingMethod(@RangingMethod int rangingMethod) {
            mRangingMethod = rangingMethod;
            return this;
        }

        /** Builds {@link SessionInfo}. */
        public SessionInfo build() {
            Preconditions.checkArgument(!mDeviceId.isEmpty(), "deviceId must not be empty.");
            Preconditions.checkArgument(
                    mRangingMethod != RANGING_METHOD_UNKNOWN, "Unknown rangingMethod");
            return new SessionInfo(mDeviceId, mRangingMethod);
        }
    }
}

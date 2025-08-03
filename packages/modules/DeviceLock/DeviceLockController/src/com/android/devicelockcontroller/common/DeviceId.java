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

package com.android.devicelockcontroller.common;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType;

/**
 * A data structure class that is used to represent a device unique identifier such as IMEI, MEID,
 * etc.
 */
public final class DeviceId {

    /** Type of the identifier */
    @DeviceIdType
    private final int mType;
    /** Number of the identifier */
    private final String mId;

    public DeviceId(int type, @NonNull String id) {
        mType = type;
        mId = id;
    }

    /**
     * @return Type of this unique identifier.
     */
    @DeviceIdType
    public int getType() {
        return mType;
    }

    /**
     * @return Number of this unique identifier.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Check if the input DeviceId equals to this DeviceId.
     *
     * @return true if the input id has same type and number as this one; false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DeviceId)) return false;
        DeviceId deviceId = (DeviceId) obj;
        return getType() == deviceId.getType() && TextUtils.equals(getId(), deviceId.getId());
    }

    @Override
    public int hashCode() {
        return getType() * getId().hashCode();
    }
}

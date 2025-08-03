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

package com.android.devicelockcontroller.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A group of {@link DevicePolicy}.
 */
final class DevicePolicyGroup {

    private final int mGroupTitleTextId;

    private final List<DevicePolicy> mDevicePolicyList;

    DevicePolicyGroup(int groupTitleTextId, List<DevicePolicy> devicePolicyList) {
        mGroupTitleTextId = groupTitleTextId;
        mDevicePolicyList = devicePolicyList;
    }

    int getGroupTitleTextId() {
        return mGroupTitleTextId;
    }

    List<DevicePolicy> getDevicePolicyList() {
        return mDevicePolicyList;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DevicePolicyGroup)) {
            return false;
        }
        DevicePolicyGroup that = (DevicePolicyGroup) obj;
        if (this.mGroupTitleTextId != that.mGroupTitleTextId) {
            return false;
        }
        if (this.mDevicePolicyList == that.mDevicePolicyList) {
            return true;
        }
        if (this.mDevicePolicyList == null || that.mDevicePolicyList == null
                || this.mDevicePolicyList.size() != that.mDevicePolicyList.size()) {
            return false;
        }
        for (int i = 0; i < this.mDevicePolicyList.size(); i++) {
            if (!(this.mDevicePolicyList.get(i).equals(that.mDevicePolicyList.get(i)))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGroupTitleTextId, mDevicePolicyList);
    }

    /**
     * Builder class for the {@link DevicePolicyGroup}.
     */
    static class Builder {

        private int mGroupTitleTextId;

        private final List<DevicePolicy> mDevicePolicyList = new ArrayList<>();

        Builder setTitleTextId(int groupTitleTextId) {
            mGroupTitleTextId = groupTitleTextId;
            return this;
        }

        private Builder addDevicePolicy(DevicePolicy devicePolicy) {
            mDevicePolicyList.add(devicePolicy);
            return this;
        }

        Builder addDevicePolicy(int drawableId, int textId) {
            addDevicePolicy(new DevicePolicy(drawableId, textId));
            return this;
        }

        DevicePolicyGroup build() {
            return new DevicePolicyGroup(mGroupTitleTextId, mDevicePolicyList);
        }
    }
}

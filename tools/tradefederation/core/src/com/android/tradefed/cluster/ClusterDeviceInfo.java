/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import com.android.tradefed.command.remote.DeviceDescriptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** A class to encapsulate cluster device info to be uploaded. */
public class ClusterDeviceInfo {
    private String mRunTarget;
    private String mGroupName;
    private DeviceDescriptor mDeviceDescriptor;
    private Map<String, String> mExtraInfo;

    private ClusterDeviceInfo(
            DeviceDescriptor deviceDescriptor,
            String runTarget,
            String groupName,
            Map<String, String> extraInfo) {
        mDeviceDescriptor = deviceDescriptor;
        mRunTarget = runTarget;
        mGroupName = groupName;
        mExtraInfo = new LinkedHashMap<>(extraInfo);
    }

    public String getRunTarget() {
        return mRunTarget;
    }

    public String getGroupName() {
        return mGroupName;
    }

    public DeviceDescriptor getDeviceDescriptor() {
        return mDeviceDescriptor;
    }

    public Map<String, String> getExtraInfo() {
        return new LinkedHashMap<>(mExtraInfo);
    }

    public static class Builder {
        private DeviceDescriptor mDeviceDescriptor;
        private String mRunTarget;
        private String mGroupName;
        private Map<String, String> mExtraInfo = new LinkedHashMap<>();

        public Builder() {}

        public Builder setRunTarget(final String runTarget) {
            mRunTarget = runTarget;
            return this;
        }

        public Builder setGroupName(final String groupName) {
            mGroupName = groupName;
            return this;
        }

        public Builder setDeviceDescriptor(final DeviceDescriptor deviceDescriptor) {
            mDeviceDescriptor = deviceDescriptor;
            mExtraInfo.put("hardware_revision", deviceDescriptor.getHardwareRevision());
            return this;
        }

        public Builder addExtraInfo(final Map<String, String> extraInfo) {
            mExtraInfo.putAll(extraInfo);
            return this;
        }

        public ClusterDeviceInfo build() {
            final ClusterDeviceInfo deviceInfo =
                    new ClusterDeviceInfo(mDeviceDescriptor, mRunTarget, mGroupName, mExtraInfo);
            return deviceInfo;
        }
    }

    /**
     * Generates the JSON Object for this device info.
     *
     * @return JSONObject equivalent of this device info.
     * @throws JSONException
     */
    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("device_serial", ClusterHostUtil.getUniqueDeviceSerial(mDeviceDescriptor));
        json.put("run_target", mRunTarget);
        json.put("build_id", mDeviceDescriptor.getBuildId());
        json.put("product", mDeviceDescriptor.getProduct());
        json.put("product_variant", mDeviceDescriptor.getProductVariant());
        json.put("sdk_version", mDeviceDescriptor.getSdkVersion());
        json.put("battery_level", mDeviceDescriptor.getBatteryLevel());
        json.put("mac_address", mDeviceDescriptor.getMacAddress());
        json.put("sim_state", mDeviceDescriptor.getSimState());
        json.put("sim_operator", mDeviceDescriptor.getSimOperator());
        json.put("state", mDeviceDescriptor.getState());
        json.put("is_stub_device", mDeviceDescriptor.isStubDevice());
        json.put("preconfigured_ip", mDeviceDescriptor.getPreconfiguredIp());
        json.put(
                "preconfigured_device_num_offset",
                mDeviceDescriptor.getPreconfiguredDeviceNumOffset());
        json.put("group_name", mGroupName);
        JSONArray extraInfoKeyValuePairs = new JSONArray();
        for (Map.Entry<String, String> entry : mExtraInfo.entrySet()) {
            extraInfoKeyValuePairs.put(
                    new JSONObject().put("key", entry.getKey()).put("value", entry.getValue()));
        }
        json.put("extra_info", extraInfoKeyValuePairs);
        return json;
    }
}

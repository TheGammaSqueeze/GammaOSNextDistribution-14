/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.command;

import com.android.tradefed.device.ITestDevice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Represents the results of an allocation attempt for a command. */
public class DeviceAllocationResult {

    private Map<String, String> mNotAllocatedReason = new LinkedHashMap<>();
    private Map<String, ITestDevice> mAllocatedDevices = new LinkedHashMap<>();

    /** returns whether or not the allocation was successful. */
    public boolean wasAllocationSuccessful() {
        return !mAllocatedDevices.isEmpty();
    }

    /** Add devices that have been allocated. */
    public void addAllocatedDevices(Map<String, ITestDevice> devices) {
        mAllocatedDevices.putAll(devices);
    }

    /** Add the reasons for not being allocated for each device config. */
    public void addAllocationFailureReason(String deviceConfigName, Map<String, String> reasons) {
        mNotAllocatedReason.put(deviceConfigName, createReasonMessage(reasons));
    }

    /** Returns the map of allocated devices */
    public Map<String, ITestDevice> getAllocatedDevices() {
        return mAllocatedDevices;
    }

    public String formattedReason() {
        if (mNotAllocatedReason.size() == 1) {
            return mNotAllocatedReason.values().iterator().next().toString();
        }
        return mNotAllocatedReason.toString();
    }

    private String createReasonMessage(Map<String, String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (String serial : reasons.keySet()) {
            String reason = reasons.get(serial);
            if (reason == null) {
                reason = "No reason provided";
            }
            sb.append(String.format("device '%s': %s", serial, reason));
        }
        if (reasons.isEmpty()) {
            sb.append("No reason returned.");
        }
        return sb.toString();
    }
}

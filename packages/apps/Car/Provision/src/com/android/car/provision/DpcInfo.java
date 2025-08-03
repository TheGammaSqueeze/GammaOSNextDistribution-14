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
package com.android.car.provision;

import android.content.ComponentName;

import java.util.Objects;

/**
 * Info about a Device Policy Controller app.
 */
final class DpcInfo {

    public final String name;
    public final String packageName;
    private final String mLegacyActivityName;
    private final String mReceiverName;
    public final String checkSum;
    public final String downloadUrl;

    DpcInfo(String name, String packageName, String legacyActivityName,
            String receiverName, String checkSum, String downloadUrl) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
        this.mReceiverName = Objects.requireNonNull(receiverName, "receiverName cannot be null");
        this.mLegacyActivityName = legacyActivityName;
        this.checkSum = checkSum;
        this.downloadUrl = downloadUrl;
    }

    /***
     * Gets the name of the activity that launches the legacy workflow.
     */
    public ComponentName getLegacyActivityComponentName() {
        return new ComponentName(packageName, mLegacyActivityName);
    }

    /***
     * Gets the name of the admin receiver.
     */
    public ComponentName getAdminReceiverComponentName() {
        return new ComponentName(packageName, mReceiverName);
    }

    @Override
    public String toString() {
        return "DpcInfo[name=" + name + ", package=" + packageName
                + ", legacyWorkflowComponentName=" + getLegacyActivityComponentName()
                + ", adminComponentName=" + getAdminReceiverComponentName()
                + ", checkSum=" + checkSum
                + ", downloadUrl=" + downloadUrl
                + "]";
    }
}

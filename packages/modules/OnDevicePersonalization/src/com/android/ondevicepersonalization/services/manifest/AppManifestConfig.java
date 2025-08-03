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

package com.android.ondevicepersonalization.services.manifest;

/**
 * POJO representing OnDevicePersonalization manifest config.
 */
public class AppManifestConfig {
    private final String mDownloadUrl;
    private final String mServiceName;
    private final String mFcRemoteServerUrl;

    public AppManifestConfig(String downloadUrl, String serviceName, String fcRemoteServerUrl) {
        mDownloadUrl = downloadUrl;
        mServiceName = serviceName;
        mFcRemoteServerUrl = fcRemoteServerUrl;
    }

    /**
     * @return The download URL configured in manifest
     */
    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    /**
     * @return The service name configured in manifest
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * @return The federated compute service remote server url configured in manifest
     */
    public String getFcRemoteServerUrl() {
        return mFcRemoteServerUrl;
    }
}

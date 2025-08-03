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

package android.remoteauth;

import android.remoteauth.IDeviceDiscoveryListener;

/**
 * Interface for communicating with the RemoteAuthService.
 * These methods are all require MANAGE_REMOTE_AUTH signature permission.
 * @hide
 */
interface IRemoteAuthService {
    // This is protected by the MANAGE_REMOTE_AUTH signature permission.
    boolean isRemoteAuthSupported();

    // This is protected by the MANAGE_REMOTE_AUTH signature permission.
    boolean registerDiscoveryListener(in IDeviceDiscoveryListener deviceDiscoveryListener,
                                  int userId,
                                  int timeoutMs,
                                  String packageName,
                                  @nullable String attributionTag);

    // This is protected by the MANAGE_REMOTE_AUTH signature permission.
    void unregisterDiscoveryListener(in IDeviceDiscoveryListener deviceDiscoveryListener,
                                     int userId,
                                     String packageName,
                                     @nullable String attributionTag);
}
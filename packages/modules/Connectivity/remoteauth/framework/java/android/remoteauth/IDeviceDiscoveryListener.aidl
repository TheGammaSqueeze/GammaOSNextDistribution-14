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

import android.remoteauth.RemoteDevice;

/**
 * Binder callback for DeviceDiscoveryCallback.
 *
 * {@hide}
 */
oneway interface IDeviceDiscoveryListener {
        /** Reports a {@link RemoteDevice} being discovered. */
        void onDiscovered(in RemoteDevice remoteDevice);

        /** Reports a {@link RemoteDevice} is no longer within range. */
        void onLost(in RemoteDevice remoteDevice);

        /** Reports a timeout of {@link RemoteDevice} was reached. */
        void onTimeout();
}

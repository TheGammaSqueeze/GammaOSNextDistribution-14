/*
 * Copyright (C) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.nearby;

import android.nearby.NearbyDeviceParcelable;

/**
 * Binder callback for ScanCallback.
 *
 * {@hide}
 */
oneway interface IScanListener {
        /** Reports a {@link NearbyDevice} being discovered. */
        void onDiscovered(in NearbyDeviceParcelable nearbyDeviceParcelable);

        /** Reports a {@link NearbyDevice} information(distance, packet, and etc) changed. */
        void onUpdated(in NearbyDeviceParcelable nearbyDeviceParcelable);

        /** Reports a {@link NearbyDevice} is no longer within range. */
        void onLost(in NearbyDeviceParcelable nearbyDeviceParcelable);

        /** Reports when there is an error during scanning. */
        void onError(in int errorCode);
}

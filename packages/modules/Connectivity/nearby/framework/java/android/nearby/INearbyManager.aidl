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

import android.nearby.IBroadcastListener;
import android.nearby.IScanListener;
import android.nearby.BroadcastRequestParcelable;
import android.nearby.ScanRequest;
import android.nearby.PoweredOffFindingEphemeralId;
import android.nearby.aidl.IOffloadCallback;

/**
 * Interface for communicating with the nearby services.
 *
 * @hide
 */
interface INearbyManager {

    int registerScanListener(in ScanRequest scanRequest, in IScanListener listener,
            String packageName, @nullable String attributionTag);

    void unregisterScanListener(in IScanListener listener, String packageName, @nullable String attributionTag);

    void startBroadcast(in BroadcastRequestParcelable broadcastRequest,
            in IBroadcastListener callback, String packageName, @nullable String attributionTag);

    void stopBroadcast(in IBroadcastListener callback, String packageName, @nullable String attributionTag);

    void queryOffloadCapability(in IOffloadCallback callback) ;

    void setPoweredOffFindingEphemeralIds(in List<PoweredOffFindingEphemeralId> eids);

    void setPoweredOffModeEnabled(boolean enabled);

    boolean getPoweredOffModeEnabled();
}

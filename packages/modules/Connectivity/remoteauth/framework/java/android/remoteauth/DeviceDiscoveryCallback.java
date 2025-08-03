/*
 * Copyright 2023 The Android Open Source Project
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

import android.annotation.NonNull;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Reports newly discovered remote devices.
 *
 * @hide
 */
// TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
public interface DeviceDiscoveryCallback {
    /** The device is no longer seen in the discovery process. */
    int STATE_LOST = 0;
    /** The device is seen in the discovery process */
    int STATE_SEEN = 1;
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_LOST, STATE_SEEN})
    @interface State {}

    /**
     * Invoked for every change in remote device state.
     *
     * @param device remote device
     * @param state indicates if found or lost
     */
    void onDeviceUpdate(@NonNull RemoteDevice device, @State int state);

    /** Invoked when discovery is stopped due to timeout. */
    void onTimeout();
}

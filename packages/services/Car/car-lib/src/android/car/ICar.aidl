/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car;

import android.car.ICarResultReceiver;

import com.android.car.internal.ICarServiceHelper;

/** @hide */
interface ICar {
    /**
     * Helper binder is for ICarServiceHelper. It is for the communication from CarService ->
     * CarServiceHelperService.
     * Receiver binder would set ICarSystemServerClient binder for CarServiceHelperService.
     */
    oneway void setSystemServerConnections(in ICarServiceHelper helper,
        in ICarResultReceiver receiver) = 0;

    // Rest of the calls are used for Apps to CarService communication
    @nullable IBinder getCarService(in String serviceName) = 11;
    int getCarConnectionType() = 12;
    boolean isFeatureEnabled(in String featureName) = 13;
    int enableFeature(in String featureName) = 14;
    int disableFeature(in String featureName) = 15;
    List<String> getAllEnabledFeatures() = 16;
    List<String> getAllPendingDisabledFeatures() = 17;
    List<String> getAllPendingEnabledFeatures() = 18;
    /**
     * Get class name for experimental feature. Class should have constructor taking (Car, IBinder)
     * and should inherit CarManagerBase.
     */
    @nullable String getCarManagerClassForFeature(in String featureName) = 19;
}

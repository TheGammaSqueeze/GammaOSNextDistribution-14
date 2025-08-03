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

package android.adservices.ondevicepersonalization.aidl;

import android.content.ComponentName;
import android.adservices.ondevicepersonalization.CallerMetadata;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.PersistableBundle;

/** @hide */
interface IOnDevicePersonalizationManagingService {
    String getVersion();
    void execute(
        in String callingPackageName,
        in ComponentName handler,
        in Bundle wrappedParams,
        in CallerMetadata metadata,
        in IExecuteCallback callback);

    void requestSurfacePackage(
        in String slotResultToken,
        in IBinder hostToken,
        int displayId,
        int width,
        int height,
        in CallerMetadata metadata,
        in IRequestSurfacePackageCallback callback);

    // TODO(b/301732670): Move to a new service.
    void registerMeasurementEvent(
        in int measurementEventType,
        in Bundle params,
        in CallerMetadata metadata,
        in IRegisterMeasurementEventCallback callback);
}

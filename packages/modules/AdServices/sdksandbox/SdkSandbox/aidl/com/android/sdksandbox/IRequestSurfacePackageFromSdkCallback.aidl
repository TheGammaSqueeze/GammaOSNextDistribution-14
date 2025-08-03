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

package com.android.sdksandbox;

import android.os.Bundle;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.app.sdksandbox.SandboxLatencyInfo;

/** @hide */
oneway interface IRequestSurfacePackageFromSdkCallback {
    const int SURFACE_PACKAGE_INTERNAL_ERROR = 1;

    // TODO(b/242571399): Convert sandboxLatencies to Parcelable Class
    void onSurfacePackageReady(in SurfacePackage surfacePackage, int surfacePackageId, in Bundle params, in SandboxLatencyInfo sandboxLatencyInfo);
    void onSurfacePackageError(int errorCode, String errorMessage, in SandboxLatencyInfo sandboxLatencyInfo);
}

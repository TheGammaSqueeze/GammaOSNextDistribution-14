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

package android.app.sdksandbox.testutils;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;

import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.RequestSurfacePackageException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;

public class FakeRequestSurfacePackageCallbackBinder extends IRequestSurfacePackageCallback.Stub {
    private final FakeRequestSurfacePackageCallback mFakeRequestSurfacePackageCallback;

    public FakeRequestSurfacePackageCallbackBinder(
            FakeRequestSurfacePackageCallback fakeRequestSurfacePackageCallback) {
        mFakeRequestSurfacePackageCallback = fakeRequestSurfacePackageCallback;
    }

    public FakeRequestSurfacePackageCallbackBinder() {
        this(new FakeRequestSurfacePackageCallback());
    }

    @Override
    public void onSurfacePackageError(
            int errorCode, String errorMsg, SandboxLatencyInfo sandboxLatencyInfo) {
        mFakeRequestSurfacePackageCallback.onError(
                new RequestSurfacePackageException(errorCode, errorMsg));
    }

    @Override
    public void onSurfacePackageReady(
            SurfaceControlViewHost.SurfacePackage surfacePackage,
            int surfacePackageId,
            Bundle params,
            SandboxLatencyInfo sandboxLatencyInfo) {
        params.putParcelable(EXTRA_SURFACE_PACKAGE, surfacePackage);
        mFakeRequestSurfacePackageCallback.onResult(params);
    }

    public boolean isRequestSurfacePackageSuccessful() {
        return mFakeRequestSurfacePackageCallback.isRequestSurfacePackageSuccessful();
    }

    public int getSurfacePackageErrorCode() {
        return mFakeRequestSurfacePackageCallback.getSurfacePackageErrorCode();
    }

    public String getSurfacePackageErrorMsg() {
        return mFakeRequestSurfacePackageCallback.getSurfacePackageErrorMsg();
    }
}

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

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.RequestSurfacePackageException;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.view.SurfaceControlViewHost;

public class FakeRequestSurfacePackageCallback
        implements OutcomeReceiver<Bundle, RequestSurfacePackageException> {
    private final WaitableCountDownLatch mSurfacePackageLatch = new WaitableCountDownLatch(5);

    private boolean mSurfacePackageSuccess;

    private RequestSurfacePackageException mSurfacePackageException;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    @Override
    public void onError(RequestSurfacePackageException exception) {
        mSurfacePackageSuccess = false;
        mSurfacePackageException = exception;
        mSurfacePackageLatch.countDown();
    }

    @Override
    public void onResult(Bundle response) {
        mSurfacePackageSuccess = true;
        mSurfacePackage =
                response.getParcelable(
                        EXTRA_SURFACE_PACKAGE, SurfaceControlViewHost.SurfacePackage.class);
        mSurfacePackageLatch.countDown();
    }

    public boolean isRequestSurfacePackageSuccessful() {
        mSurfacePackageLatch.waitForLatch();
        return mSurfacePackageSuccess;
    }

    public Bundle getExtraErrorInformation() {
        mSurfacePackageLatch.waitForLatch();
        assertThat(mSurfacePackageSuccess).isFalse();
        return mSurfacePackageException.getExtraErrorInformation();
    }

    public SurfaceControlViewHost.SurfacePackage getSurfacePackage() {
        mSurfacePackageLatch.waitForLatch();
        assertThat(mSurfacePackageSuccess).isTrue();
        return mSurfacePackage;
    }

    public int getSurfacePackageErrorCode() {
        mSurfacePackageLatch.waitForLatch();
        assertThat(mSurfacePackageSuccess).isFalse();
        return mSurfacePackageException.getRequestSurfacePackageErrorCode();
    }

    public String getSurfacePackageErrorMsg() {
        mSurfacePackageLatch.waitForLatch();
        assertThat(mSurfacePackageSuccess).isFalse();
        return mSurfacePackageException.getMessage();
    }

    public RequestSurfacePackageException getSurfacePackageException() {
        mSurfacePackageLatch.waitForLatch();
        assertThat(mSurfacePackageSuccess).isFalse();
        return mSurfacePackageException;
    }
}

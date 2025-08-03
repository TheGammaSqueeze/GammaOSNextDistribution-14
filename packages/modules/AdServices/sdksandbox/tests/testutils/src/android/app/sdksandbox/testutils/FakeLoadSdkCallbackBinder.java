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

import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;

public class FakeLoadSdkCallbackBinder extends ILoadSdkCallback.Stub {
    private final FakeLoadSdkCallback mFakeLoadSdkCallback;

    public FakeLoadSdkCallbackBinder(FakeLoadSdkCallback fakeLoadSdkCallback) {
        mFakeLoadSdkCallback = fakeLoadSdkCallback;
    }

    public FakeLoadSdkCallbackBinder() {
        this(new FakeLoadSdkCallback());
    }

    @Override
    public void onLoadSdkSuccess(SandboxedSdk sandboxedSdk, SandboxLatencyInfo sandboxLatencyInfo) {
        mFakeLoadSdkCallback.onResult(sandboxedSdk);
    }

    @Override
    public void onLoadSdkFailure(
            LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
        mFakeLoadSdkCallback.onError(exception);
    }

    public void assertLoadSdkIsSuccessful() {
        mFakeLoadSdkCallback.assertLoadSdkIsSuccessful();
    }

    public void assertLoadSdkIsUnsuccessful() {
        mFakeLoadSdkCallback.assertLoadSdkIsUnsuccessful();
    }

    public int getLoadSdkErrorCode() {
        return mFakeLoadSdkCallback.getLoadSdkErrorCode();
    }

    public String getLoadSdkErrorMsg() {
        return mFakeLoadSdkCallback.getLoadSdkErrorMsg();
    }

    public LoadSdkException getLoadSdkException() {
        return mFakeLoadSdkCallback.getLoadSdkException();
    }
}

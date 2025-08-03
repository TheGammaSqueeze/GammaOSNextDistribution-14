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

import android.app.sdksandbox.ISharedPreferencesSyncCallback;

public class FakeSharedPreferencesSyncCallback extends ISharedPreferencesSyncCallback.Stub {
    boolean mOnSandboxStartCalled = false;
    boolean mOnErrorCalled = false;
    private WaitableCountDownLatch mSyncDataLatch = new WaitableCountDownLatch(5);
    private int mErrorCode;
    private String mErrorMsg;

    @Override
    public void onSandboxStart() {
        mOnSandboxStartCalled = true;
        mSyncDataLatch.countDown();
    }

    @Override
    public void onError(int errorCode, String errorMsg) {
        mOnErrorCalled = true;
        mErrorCode = errorCode;
        mErrorMsg = errorMsg;
        mSyncDataLatch.countDown();
    }

    public boolean hasSandboxStarted() {
        mSyncDataLatch.waitForLatch();
        return mOnSandboxStartCalled;
    }

    public boolean hasError() {
        mSyncDataLatch.waitForLatch();
        return mOnErrorCalled;
    }

    public int getErrorCode() {
        mSyncDataLatch.waitForLatch();
        return mErrorCode;
    }

    public String getErrorMsg() {
        mSyncDataLatch.waitForLatch();
        return mErrorMsg;
    }

    public void resetLatch() {
        mSyncDataLatch = new WaitableCountDownLatch(5);
    }

}

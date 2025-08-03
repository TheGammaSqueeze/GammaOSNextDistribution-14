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

import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.android.sdksandbox.IComputeSdkStorageCallback;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.IUnloadSdkInSandboxCallback;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeSdkSandboxService extends ISdkSandboxService.Stub {
    private final ISdkSandboxManagerToSdkSandboxCallback mManagerToSdkCallback;
    private final CountDownLatch mLatch;

    private long mTimeSystemServerCallFinished = -1;
    private long mTimeSandboxReceivedCallFromSystemServer = -1;
    private long mTimeSandboxCalledSdk = -1;
    private long mTimeSdkCallCompleted = -1;
    private long mTimeSandboxCalledSystemServer = -1;

    private ILoadSdkInSandboxCallback mLoadSdkInSandboxCallback;
    private IRequestSurfacePackageFromSdkCallback mRequestSurfacePackageFromSdkCallback = null;
    private IUnloadSdkInSandboxCallback mUnloadSdkInSandboxCallback = null;
    private IComputeSdkStorageCallback mComputeSdkStorageCallback = null;
    private ApplicationInfo mCustomizedInfo;

    private boolean mSurfacePackageRequested = false;
    private int mInitializationCount = 0;

    public boolean dieOnLoad = false;

    public boolean failInitialization = false;

    private SharedPreferencesUpdate mLastSyncUpdate = null;


    public FakeSdkSandboxService() {
        mManagerToSdkCallback = new FakeManagerToSdkCallback();
        mLatch = new CountDownLatch(5);
    }

    public void setTimeValues(
            long timeSystemServerCallFinished,
            long timeSandboxReceivedCallFromSystemServer,
            long timeSandboxCalledSdk,
            long timeSdkCallCompleted,
            long timeSandboxCalledSystemServer) {
        mTimeSystemServerCallFinished = timeSystemServerCallFinished;
        mTimeSandboxReceivedCallFromSystemServer = timeSandboxReceivedCallFromSystemServer;
        mTimeSandboxCalledSdk = timeSandboxCalledSdk;
        mTimeSdkCallCompleted = timeSdkCallCompleted;
        mTimeSandboxCalledSystemServer = timeSandboxCalledSystemServer;
    }

    @Override
    public void initialize(
            ISdkToServiceCallback sdkToServiceCallback, boolean isCustomizedSdkContextEnabled)
            throws IllegalStateException {
        if (failInitialization) {
            throw new IllegalStateException();
        }
        mInitializationCount++;
    }

    @Override
    public void loadSdk(
            String callingPackageName,
            ApplicationInfo info,
            String sdkName,
            String sdkProviderClass,
            ApplicationInfo customizedInfo,
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo)
            throws DeadObjectException {
        if (dieOnLoad) {
            throw new DeadObjectException();
        }
        mLoadSdkInSandboxCallback = callback;
        mCustomizedInfo = customizedInfo;
    }

    @Override
    public void unloadSdk(
            String sdkName,
            IUnloadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        mUnloadSdkInSandboxCallback = callback;
    }

    @Override
    public void syncDataFromClient(SharedPreferencesUpdate update) {
        mLastSyncUpdate = update;
    }

    @Override
    public void computeSdkStorage(
            List<String> cePackagePaths,
            List<String> dePackagePaths,
            IComputeSdkStorageCallback callback) {
        mLatch.countDown();
        mComputeSdkStorageCallback = callback;
    }

    @Nullable
    public Bundle getLastSyncData() {
        return mLastSyncUpdate.getData();
    }

    @Nullable
    public SharedPreferencesUpdate getLastSyncUpdate() {
        return mLastSyncUpdate;
    }

    @Override
    public void notifySdkSandboxClientImportanceChange(boolean isForeground) {}

    public int getInitializationCount() {
        return mInitializationCount;
    }

    public void sendLoadSdkSuccessful() throws RemoteException {
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mTimeSystemServerCallFinished);
        mLoadSdkInSandboxCallback.onLoadSdkSuccess(
                new SandboxedSdk(new Binder()), mManagerToSdkCallback, sandboxLatencyInfo);
    }

    public void sendLoadSdkSuccessfulWithSandboxLatencies(SandboxLatencyInfo sandboxLatencyInfo)
            throws RemoteException {
        setSandboxLatencyTimestamps(sandboxLatencyInfo);
        mLoadSdkInSandboxCallback.onLoadSdkSuccess(
                new SandboxedSdk(new Binder()), mManagerToSdkCallback, sandboxLatencyInfo);
    }

    public boolean isSdkUnloaded() {
        return mUnloadSdkInSandboxCallback != null;
    }

    public void sendStorageInfoToSystemServer() throws Exception {
        mLatch.await(5, TimeUnit.SECONDS);
        mComputeSdkStorageCallback.onStorageInfoComputed(0, 0);
    }

    public void sendLoadSdkError() throws Exception {
        Class<?> clz = Class.forName("android.app.sdksandbox.LoadSdkException");
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mTimeSystemServerCallFinished);
        LoadSdkException exception =
                (LoadSdkException)
                        clz.getConstructor(Integer.TYPE, String.class)
                                .newInstance(
                                        SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                                        "Internal error");
        mLoadSdkInSandboxCallback.onLoadSdkError(exception, sandboxLatencyInfo);
    }

    public void sendSurfacePackageReady(SandboxLatencyInfo sandboxLatencyInfo)
            throws RemoteException {
        if (mSurfacePackageRequested) {
            mRequestSurfacePackageFromSdkCallback.onSurfacePackageReady(
                    /*surfacePackage=*/ null,
                    /*surfacePackageId=*/ 1,
                    /*params=*/ new Bundle(),
                    sandboxLatencyInfo);
        }
    }

    public void sendUnloadSdkSuccess(SandboxLatencyInfo sandboxLatencyInfo) throws Exception {
        setSandboxLatencyTimestamps(sandboxLatencyInfo);
        mUnloadSdkInSandboxCallback.onUnloadSdk(sandboxLatencyInfo);
    }

    // TODO(b/242684679): Use iRequestSurfacePackageFromSdkCallback instead of fake callback
    public void sendSurfacePackageError(
            int errorCode, String errorMsg, FakeRequestSurfacePackageCallbackBinder callback)
            throws RemoteException {
        callback.onSurfacePackageError(errorCode, errorMsg, new SandboxLatencyInfo());
    }

    public ApplicationInfo getCustomizedInfo() {
        return mCustomizedInfo;
    }

    private void setSandboxLatencyTimestamps(SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mTimeSystemServerCallFinished);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                mTimeSandboxReceivedCallFromSystemServer);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(mTimeSandboxCalledSdk);
        sandboxLatencyInfo.setTimeSdkCallCompleted(mTimeSdkCallCompleted);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mTimeSandboxCalledSystemServer);
    }

    private class FakeManagerToSdkCallback extends ISdkSandboxManagerToSdkSandboxCallback.Stub {
        @Override
        public void onSurfacePackageRequested(
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                Bundle extraParams,
                SandboxLatencyInfo sandboxLatencyInfo,
                IRequestSurfacePackageFromSdkCallback iRequestSurfacePackageFromSdkCallback) {
            mSurfacePackageRequested = true;
            mRequestSurfacePackageFromSdkCallback = iRequestSurfacePackageFromSdkCallback;
        }
    }
}

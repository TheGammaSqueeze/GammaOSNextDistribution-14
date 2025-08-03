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

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LogUtil;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;

import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Random;

/**
 * A holder for loaded code.
 */
class SandboxedSdkHolder {

    private static final String TAG = "SdkSandbox";

    private boolean mInitialized = false;
    private SandboxedSdkProvider mSdk;
    private Context mContext;
    private SandboxedSdk mSandboxedSdk;
    private ILoadSdkInSandboxCallback mLoadSdkInSandboxCallback;
    private SdkSandboxServiceImpl.SdkHolderToSdkSandboxServiceCallback
            mHolderToSdkSandboxServiceCallback;

    private DisplayManager mDisplayManager;
    private final Random mRandom = new SecureRandom();
    private final SparseArray<SurfaceControlViewHost.SurfacePackage> mSurfacePackages =
            new SparseArray<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SdkSandboxServiceImpl.Injector mInjector;

    void init(
            Bundle params,
            ILoadSdkInSandboxCallback loadSdkInSandboxCallback,
            String sdkProviderClassName,
            ClassLoader loader,
            SandboxedSdkContext sandboxedSdkContext,
            SdkSandboxServiceImpl.Injector injector,
            SandboxLatencyInfo sandboxLatencyInfo,
            SdkSandboxServiceImpl.SdkHolderToSdkSandboxServiceCallback holderToServiceCallback) {
        if (mInitialized) {
            throw new IllegalStateException("Already initialized!");
        }
        mInitialized = true;
        mContext = sandboxedSdkContext.getBaseContext();
        mLoadSdkInSandboxCallback = loadSdkInSandboxCallback;
        mHolderToSdkSandboxServiceCallback = holderToServiceCallback;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mInjector = injector;
        /**
         * The code inside the try block is *owned* by the sandbox, and latency is measured
         * separately to the code that is owned by the SDK
         */
        try {
            Class<?> clz = Class.forName(sdkProviderClassName, true, loader);
            mSdk = (SandboxedSdkProvider) clz.getConstructor().newInstance();
            mSdk.attachContext(sandboxedSdkContext);
        } catch (ClassNotFoundException e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Could not find class: " + sdkProviderClassName),
                    sandboxLatencyInfo);
        } catch (Exception e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Could not instantiate SandboxedSdkProvider: " + e),
                    sandboxLatencyInfo);
        } catch (Throwable e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Error thrown during init: " + e),
                    sandboxLatencyInfo);
        }

        mHandler.post(
                () -> {
                    /** The code inside the try block runs in SDK and is measured latency of SDK */
                    try {
                        sandboxLatencyInfo.setTimeSandboxCalledSdk(mInjector.elapsedRealtime());
                        mSandboxedSdk = mSdk.onLoadSdk(params);
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.elapsedRealtime());
                        sendLoadSdkSuccess(mSandboxedSdk, sandboxLatencyInfo);
                    } catch (LoadSdkException exception) {
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.elapsedRealtime());
                        sandboxLatencyInfo.setSandboxStatus(
                                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);
                        sendLoadSdkError(exception, sandboxLatencyInfo);
                    } catch (RuntimeException exception) {
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.elapsedRealtime());
                        sandboxLatencyInfo.setSandboxStatus(
                                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);
                        sendLoadSdkError(
                                new LoadSdkException(exception, new Bundle()), sandboxLatencyInfo);
                    }
                });
    }

    void unloadSdk() {
        mHandler.post(() -> mSdk.beforeUnloadSdk());
    }

    void dump(PrintWriter writer) {
        writer.print("mInitialized: " + mInitialized);
        final String sdkClass = mSdk == null ? "null" : mSdk.getClass().getName();
        writer.println(" mSdk class: " + sdkClass);
    }

    private void sendLoadSdkSuccess(
            SandboxedSdk sandboxedSdk, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.elapsedRealtime());
        try {
            mHolderToSdkSandboxServiceCallback.onSuccess();
            mLoadSdkInSandboxCallback.onLoadSdkSuccess(
                    sandboxedSdk, new SdkSandboxCallbackImpl(), sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkSuccess: " + e);
        }
    }

    private void sendSurfacePackageError(
            String errorMessage,
            IRequestSurfacePackageFromSdkCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.elapsedRealtime());
        try {
            callback.onSurfacePackageError(
                    IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR,
                    errorMessage,
                    sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onSurfacePackageError: " + e);
        }
    }

    private void sendLoadSdkError(
            LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.elapsedRealtime());
        try {
            mLoadSdkInSandboxCallback.onLoadSdkError(exception, sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkError: " + e);
        }
    }

    private int allocateSurfacePackageId(SurfaceControlViewHost.SurfacePackage surfacePackage) {
        synchronized (mSurfacePackages) {
            for (int i = 0; i < 32; i++) {
                int id = mRandom.nextInt();
                if (!mSurfacePackages.contains(id)) {
                    mSurfacePackages.put(id, surfacePackage);
                    return id;
                }
            }
            throw new IllegalStateException("Could not allocate surfacePackageId");
        }
    }

    private class SdkSandboxCallbackImpl
            extends ISdkSandboxManagerToSdkSandboxCallback.Stub {

        @Override
        public void onSurfacePackageRequested(
                IBinder token,
                int displayId,
                int width,
                int height,
                Bundle params,
                SandboxLatencyInfo sandboxLatencyInfo,
                IRequestSurfacePackageFromSdkCallback callback) {
            sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                    mInjector.elapsedRealtime());

            LogUtil.d(TAG, "onSurfacePackageRequested received");

            try {
                Context displayContext = mContext.createDisplayContext(
                        mDisplayManager.getDisplay(displayId));
                // TODO(b/209009304): Support other window contexts?
                Context windowContext = displayContext.createWindowContext(
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, null);
                // Creating a SurfaceControlViewHost needs to done on the handler thread.
                mHandler.post(
                        () -> {
                            LogUtil.d(TAG, "Creating SurfaceControlViewHost on handler thread");
                            final View view;
                            sandboxLatencyInfo.setTimeSandboxCalledSdk(mInjector.elapsedRealtime());
                            try {
                                view = mSdk.getView(windowContext, params, width, height);
                            } catch (Throwable e) {
                                sandboxLatencyInfo.setTimeSdkCallCompleted(
                                        mInjector.elapsedRealtime());
                                sandboxLatencyInfo.setSandboxStatus(
                                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);
                                sendSurfacePackageError(
                                        "Error thrown while getting surface package from SDK: " + e,
                                        callback,
                                        sandboxLatencyInfo);
                                return;
                            }
                            sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.elapsedRealtime());
                            try {
                                SurfaceControlViewHost host =
                                        new SurfaceControlViewHost(
                                                windowContext,
                                                mDisplayManager.getDisplay(displayId),
                                                token);
                                LogUtil.d(TAG, "SurfaceControlViewHost created");
                                host.setView(view, width, height);
                                LogUtil.d(TAG, "View from SDK set to SurfaceControlViewHost");
                                SurfaceControlViewHost.SurfacePackage surfacePackage =
                                        host.getSurfacePackage();
                                int surfacePackageId = allocateSurfacePackageId(surfacePackage);

                                sandboxLatencyInfo.setTimeSandboxCalledSystemServer(
                                        mInjector.elapsedRealtime());

                                callback.onSurfacePackageReady(
                                        surfacePackage,
                                        surfacePackageId,
                                        params,
                                        sandboxLatencyInfo);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Could not send onSurfacePackageReady", e);
                            } catch (Throwable e) {
                                sandboxLatencyInfo.setSandboxStatus(
                                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
                                sendSurfacePackageError(
                                        "Error thrown while getting surface package: " + e,
                                        callback,
                                        sandboxLatencyInfo);
                            }
                        });
            } catch (Throwable e) {
                sandboxLatencyInfo.setSandboxStatus(
                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
                sendSurfacePackageError(
                        "Error thrown while getting surface package: " + e,
                        callback,
                        sandboxLatencyInfo);
            }
        }

    }
}

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

package com.android.server.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE;


import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.IUnloadSdkCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LogUtil;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControlViewHost;

import com.android.internal.annotations.GuardedBy;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.IUnloadSdkInSandboxCallback;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.sdksandbox.helpers.PackageManagerHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the lifecycle of a single request to load an SDK for a specific app.
 *
 * <p>A new instance of this class must be created for every load request of an SDK. This class also
 * maintains a link to the remote SDK loaded in the sandbox if any, and communicates with it.
 */
class LoadSdkSession {

    private static final String TAG = "SdkSandboxManager";
    private static final String PROPERTY_SDK_PROVIDER_CLASS_NAME =
            "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";

    /** @hide */
    @IntDef(value = {LOAD_PENDING, LOADED, LOAD_FAILED, UNLOADED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LoadStatus {}

    /**
     * Represents the initial state of the SDK, when a request to load it has arrived but not yet
     * been completed.
     *
     * <p>Once the state of an SDK transitions out of LOAD_PENDING, it cannot be reset to
     * LOAD_PENDING as this state is the representation of a specific load request for an SDK.
     *
     * <ul>
     *   <li>LOAD_PENDING --> onLoadSdkSuccess() --> LOADED
     *   <li>LOAD_PENDING --> onLoadSdkError() --> LOAD_FAILED
     *   <li>LOAD_PENDING --> unload() --> IllegalArgumentException
     *   <li>LOAD_PENDING --> onSandboxDeath() --> LOAD_FAILED
     * </ul>
     */
    public static final int LOAD_PENDING = 1;

    /**
     * Represents the state when the SDK has been successfully loaded into the sandbox.
     *
     * <ul>
     *   <li>LOADED --> load() --> IllegalArgumentException
     *   <li>LOADED --> unload() --> UNLOADED
     *   <li>LOADED --> onSandboxDeath() --> UNLOADED
     * </ul>
     */
    public static final int LOADED = 2;

    /**
     * Represents the state when the SDK has failed to load into the sandbox.
     *
     * <p>The state can be LOAD_FAILED if the sandbox could not properly initialize the SDK, SDK is
     * invalid, the sandbox died while in the middle of loading etc. If the SDK load failed, this
     * same session cannot be used to load the SDK again.
     *
     * <ul>
     *   <li>LOAD_FAILED --> load() --> IllegalArgumentException
     *   <li>LOAD_FAILED --> unload() --> LOAD_FAILED
     *   <li>LOAD_FAILED --> onSandboxDeath() --> LOAD_FAILED
     * </ul>
     */
    public static final int LOAD_FAILED = 3;

    /**
     * Represents the state when the SDK has either been unloaded from the sandbox, or the sandbox
     * has died.
     *
     * <ul>
     *   <li>UNLOADED --> load() --> IllegalArgumentException
     *   <li>UNLOADED --> unload() --> UNLOADED
     *   <li>UNLOADED --> onSandboxDeath() --> UNLOADED
     * </ul>
     */
    public static final int UNLOADED = 4;

    private final Object mLock = new Object();

    private final Context mContext;
    private final SdkSandboxManagerService mSdkSandboxManagerService;
    private final SdkSandboxManagerService.Injector mInjector;

    final String mSdkName;
    final CallingInfo mCallingInfo;

    // The params used to load this SDK.
    private final Bundle mLoadParams;
    // The callback used to load this SDK.
    private final ILoadSdkCallback mLoadCallback;

    final SdkProviderInfo mSdkProviderInfo;

    /**
     * The initial status is LOAD_PENDING. Once the loading is complete, the status is set to LOADED
     * or LOAD_FAILED depending on the success of loading. If the SDK is unloaded at any point or
     * the sandbox dies, the status is set to UNLOADED.
     *
     * <p>The status cannot be reset to LOAD_PENDING as this class is meant to represent a single
     * load request.
     */
    @GuardedBy("mLock")
    @LoadStatus
    private int mStatus = LOAD_PENDING;

    // The sandbox in which this SDK is supposed to be loaded.
    @GuardedBy("mLock")
    private ISdkSandboxService mSandboxService = null;

    // Used for communication with the remotely loaded SDK in the sandbox.
    private final RemoteSdkLink mRemoteSdkLink;

    // Maintain all surface package requests whose callbacks have not been invoked yet.
    @GuardedBy("mLock")
    private final ArraySet<IRequestSurfacePackageCallback> mPendingRequestSurfacePackageCallbacks =
            new ArraySet<>();

    LoadSdkSession(
            Context context,
            SdkSandboxManagerService service,
            SdkSandboxManagerService.Injector injector,
            String sdkName,
            CallingInfo callingInfo,
            Bundle loadParams,
            ILoadSdkCallback loadCallback)
            throws PackageManager.NameNotFoundException {
        mContext = context;
        mSdkSandboxManagerService = service;
        mInjector = injector;
        mSdkName = sdkName;
        mCallingInfo = callingInfo;
        mLoadParams = loadParams;
        mLoadCallback = loadCallback;

        mSdkProviderInfo = createSdkProviderInfo();
        mRemoteSdkLink = new RemoteSdkLink();
    }

    @LoadStatus
    int getStatus() {
        synchronized (mLock) {
            return mStatus;
        }
    }

    @Nullable
    SandboxedSdk getSandboxedSdk() {
        return mRemoteSdkLink.mSandboxedSdk;
    }

    // Asks the given sandbox service to load this SDK.
    void load(
            ISdkSandboxService service,
            ApplicationInfo customizedInfo,
            SandboxLatencyInfo sandboxLatencyInfo) {
        // TODO(b/258679084): If a second load request comes here, while the first is pending, it
        // will go through. SdkSandboxManagerService already has a check for this, but we should
        // have it here as well.
        synchronized (mLock) {
            if (getStatus() != LOAD_PENDING) {
                // If the status is not the initial pending load, that means that this load request
                // had already been performed before and completed (either successfully or
                // unsuccessfully). Therefore, do not invoke any callback here.
                throw new IllegalArgumentException("Invalid request to load SDK " + mSdkName);
            }
            mSandboxService = service;
        }

        if (service == null) {
            handleLoadFailure(
                    new LoadSdkException(
                            SDK_SANDBOX_PROCESS_NOT_AVAILABLE, "Sandbox is not available"),
                    sandboxLatencyInfo);
        }

        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        try {
            service.loadSdk(
                    mCallingInfo.getPackageName(),
                    mSdkProviderInfo.getApplicationInfo(),
                    mSdkProviderInfo.getSdkInfo().getName(),
                    mSdkProviderInfo.getSdkProviderClassName(),
                    customizedInfo,
                    mLoadParams,
                    mRemoteSdkLink,
                    sandboxLatencyInfo);
        } catch (DeadObjectException e) {
            handleLoadFailure(
                    new LoadSdkException(
                            SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                            "Failed to load SDK as sandbox is dead"),
                    sandboxLatencyInfo);
        } catch (RemoteException e) {
            String errorMsg = "Failed to load sdk";
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
            handleLoadFailure(
                    new LoadSdkException(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, errorMsg),
                    sandboxLatencyInfo);
        }
    }

    void handleLoadSuccess(SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());

        synchronized (mLock) {
            if (getStatus() == LOAD_PENDING) {
                mStatus = LOADED;
            } else {
                // If the SDK is not pending a load, something has happened to it - for example, the
                // sandbox might have died and the status is now LOAD_FAILED. Either way, since it
                // is not waiting to be loaded, the LoadSdkCallback would have already been invoked.
                // Just log and return.
                LogUtil.d(
                        TAG,
                        "Could not successfully load "
                                + mSdkName
                                + " as its status is "
                                + getStatus());
                return;
            }
        }
        try {
            mLoadCallback.onLoadSdkSuccess(getSandboxedSdk(), sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send onLoadSdkSuccess", e);
        }
    }

    void handleLoadFailure(LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());

        synchronized (mLock) {
            if (getStatus() == LOAD_PENDING) {
                mStatus = LOAD_FAILED;
            } else {
                // If the SDK is not pending a load, something has happened to it - for example, the
                // sandbox might have died and the status is now LOAD_FAILED. Either way, since it
                // is not waiting to be loaded, the LoadSdkCallback would have already been invoked.
                // Just log and return.
                LogUtil.d(
                        TAG,
                        "Could not complete load failure for "
                                + mSdkName
                                + " as its status is "
                                + getStatus());
                return;
            }
        }
        try {
            mLoadCallback.onLoadSdkFailure(exception, sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send onLoadSdkFailure", e);
        }
    }

    void unload(SandboxLatencyInfo sandboxLatencyInfo, IUnloadSdkCallback callback) {
        // TODO(b/312444990): log latency in cases the method call fails.
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        IUnloadSdkInSandboxCallback unloadInSandboxCallback =
                new IUnloadSdkInSandboxCallback.Stub() {
                    @Override
                    public void onUnloadSdk(SandboxLatencyInfo sandboxLatencyInfo) {
                        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                                mInjector.elapsedRealtime());
                        try {
                            callback.onUnloadSdk(sandboxLatencyInfo);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not send onUnloadSdk");
                        }
                    }
                };

        ISdkSandboxService service = null;
        synchronized (mLock) {
            switch (getStatus()) {
                case LOAD_PENDING:
                    // If load is pending, unloading should fail.
                    throw new IllegalArgumentException(
                            "SDK "
                                    + mSdkName
                                    + " is currently being loaded for "
                                    + mCallingInfo
                                    + " - wait till onLoadSdkSuccess() to unload");
                case LOADED:
                    // Set status as unloaded right away, so that it is treated as unloaded even if
                    // the actual unloading hasn't completed.
                    mStatus = UNLOADED;
                    break;
                default:
                    // Unloading an SDK that is not loaded is a no-op, return. Don't throw any
                    // exception here since the sandbox can die at any time and the SDK becomes
                    // unloaded.
                    Log.i(TAG, "SDK " + mSdkName + " is not loaded for " + mCallingInfo);
                    return;
            }

            service = mSandboxService;
        }

        if (service == null) {
            // Sandbox could have died, just ignore.
            Log.i(TAG, "Cannot unload SDK " + mSdkName + " - could not find sandbox service");
            return;
        }

        try {
            service.unloadSdk(mSdkName, unloadInSandboxCallback, sandboxLatencyInfo);
        } catch (DeadObjectException e) {
            Log.i(
                    TAG,
                    "Sdk sandbox for " + mCallingInfo + " is dead, cannot unload SDK " + mSdkName);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to unload SDK: ", e);
        }
    }

    void requestSurfacePackage(
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            SandboxLatencyInfo sandboxLatencyInfo,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        synchronized (mLock) {
            mPendingRequestSurfacePackageCallbacks.add(callback);

            if (getStatus() != LOADED) {
                sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
                sandboxLatencyInfo.setSandboxStatus(
                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
                handleSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                        "SDK " + mSdkName + " is not loaded",
                        sandboxLatencyInfo,
                        callback);
                return;
            }
        }
        mRemoteSdkLink.requestSurfacePackage(
                hostToken, displayId, width, height, sandboxLatencyInfo, params, callback);
    }

    void handleSurfacePackageReady(
            SurfaceControlViewHost.SurfacePackage surfacePackage,
            int surfacePackageId,
            Bundle params,
            SandboxLatencyInfo sandboxLatencyInfo,
            IRequestSurfacePackageCallback callback) {
        synchronized (mLock) {
            mPendingRequestSurfacePackageCallbacks.remove(callback);
        }
        sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());
        try {
            callback.onSurfacePackageReady(
                    surfacePackage, surfacePackageId, params, sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send onSurfacePackageReady callback", e);
        }
    }

    void handleSurfacePackageError(
            int errorCode,
            String errorMsg,
            SandboxLatencyInfo sandboxLatencyInfo,
            IRequestSurfacePackageCallback callback) {
        synchronized (mLock) {
            mPendingRequestSurfacePackageCallbacks.remove(callback);
        }
        sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());
        try {
            callback.onSurfacePackageError(errorCode, errorMsg, sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send onSurfacePackageError", e);
        }
    }

    void onSandboxDeath() {
        synchronized (mLock) {
            mSandboxService = null;

            // If load status was pending, then the callback need to be notified.
            if (getStatus() == LOAD_PENDING) {
                handleLoadFailure(
                        new LoadSdkException(
                                SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                                "Could not load SDK, sandbox has died"),
                        new SandboxLatencyInfo());
            }

            // Clear all pending request surface package callbacks.
            notifyPendingRequestSurfacePackageCallbacksLocked();

            // Set status to unloaded on sandbox death.
            if (getStatus() == LOADED) {
                mStatus = UNLOADED;
            }
        }
    }

    @GuardedBy("mLock")
    private void notifyPendingRequestSurfacePackageCallbacksLocked() {
        for (int i = 0; i < mPendingRequestSurfacePackageCallbacks.size(); i++) {
            IRequestSurfacePackageCallback callback =
                    mPendingRequestSurfacePackageCallbacks.valueAt(i);
            handleSurfacePackageError(
                    REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                    "Sandbox died - could not request surface package",
                    new SandboxLatencyInfo(),
                    callback);
        }
        mPendingRequestSurfacePackageCallbacks.clear();
    }

    /**
     * A callback object to establish a link between the manager service and the remote SDK being
     * loaded in SdkSandbox.
     *
     * <p>Overview of communication:
     *
     * <ol>
     *   <li>RemoteSdk to ManagerService: {@link RemoteSdkLink} extends {@link
     *       ILoadSdkInSandboxCallback} interface. We pass on this object to {@link
     *       ISdkSandboxService} so that remote SDK can call back into ManagerService.
     *   <li>ManagerService to RemoteSdk: When the SDK is loaded for the first time and remote SDK
     *       calls back with successful result, it also sends reference to {@link
     *       ISdkSandboxManagerToSdkSandboxCallback} callback object. ManagerService uses this to
     *       callback into the remote SDK.
     * </ol>
     *
     * <p>We maintain a link for each unique {app, remoteSdk} pair, which is identified with {@code
     * sdkName}.
     */
    private class RemoteSdkLink extends ILoadSdkInSandboxCallback.Stub {
        @Nullable private volatile SandboxedSdk mSandboxedSdk;

        @GuardedBy("this")
        @Nullable
        private ISdkSandboxManagerToSdkSandboxCallback mManagerToSdkCallback;

        @Override
        public void onLoadSdkSuccess(
                SandboxedSdk sandboxedSdk,
                ISdkSandboxManagerToSdkSandboxCallback callback,
                SandboxLatencyInfo sandboxLatencyInfo) {
            sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                    mInjector.elapsedRealtime());

            synchronized (this) {
                // Keep reference to callback so that manager service can
                // callback to remote SDK loaded.
                mManagerToSdkCallback = callback;

                // Attach the SharedLibraryInfo for the loaded SDK to the SandboxedSdk.
                sandboxedSdk.attachSharedLibraryInfo(mSdkProviderInfo.getSdkInfo());

                // Keep reference to SandboxedSdk so that manager service can
                // keep log of all loaded SDKs and their binders for communication.
                mSandboxedSdk = sandboxedSdk;
            }

            handleLoadSuccess(sandboxLatencyInfo);
        }

        @Override
        public void onLoadSdkError(
                LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
            sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                    mInjector.elapsedRealtime());

            if (exception.getLoadSdkErrorCode()
                    == ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR) {
                mSdkSandboxManagerService.handleFailedSandboxInitialization(mCallingInfo);
            }
            handleLoadFailure(
                    updateLoadSdkErrorCode(exception),
                    sandboxLatencyInfo);
        }

        private LoadSdkException updateLoadSdkErrorCode(LoadSdkException exception) {
            @SdkSandboxManager.LoadSdkErrorCode
            int newErrorCode = toSdkSandboxManagerLoadSdkErrorCode(exception.getLoadSdkErrorCode());
            return new LoadSdkException(
                    newErrorCode,
                    exception.getMessage(),
                    exception.getCause(),
                    exception.getExtraInformation());
        }

        @SdkSandboxManager.LoadSdkErrorCode
        private int toSdkSandboxManagerLoadSdkErrorCode(int sdkSandboxErrorCode) {
            switch (sdkSandboxErrorCode) {
                case ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED:
                    return SdkSandboxManager.LOAD_SDK_ALREADY_LOADED;
                case ILoadSdkInSandboxCallback.LOAD_SDK_NOT_FOUND:
                    return SdkSandboxManager.LOAD_SDK_NOT_FOUND;
                case ILoadSdkInSandboxCallback.LOAD_SDK_PROVIDER_INIT_ERROR:
                case ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR:
                case ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR:
                    return SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
                case SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR:
                    return sdkSandboxErrorCode;
                default:
                    Log.e(
                            TAG,
                            "Error code "
                                    + sdkSandboxErrorCode
                                    + " has no mapping to the SdkSandboxManager error codes");
                    return SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
            }
        }

        public void requestSurfacePackage(
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                SandboxLatencyInfo sandboxLatencyInfo,
                Bundle params,
                IRequestSurfacePackageCallback callback) {
            sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
            try {
                synchronized (this) {
                    mManagerToSdkCallback.onSurfacePackageRequested(
                            hostToken,
                            displayId,
                            width,
                            height,
                            params,
                            sandboxLatencyInfo,
                            new IRequestSurfacePackageFromSdkCallback.Stub() {
                                @Override
                                public void onSurfacePackageReady(
                                        SurfaceControlViewHost.SurfacePackage surfacePackage,
                                        int surfacePackageId,
                                        Bundle params,
                                        SandboxLatencyInfo sandboxLatencyInfo) {
                                    sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                                            mInjector.elapsedRealtime());

                                    LogUtil.d(TAG, "onSurfacePackageReady received");

                                    handleSurfacePackageReady(
                                            surfacePackage,
                                            surfacePackageId,
                                            params,
                                            sandboxLatencyInfo,
                                            callback);
                                }

                                @Override
                                public void onSurfacePackageError(
                                        int errorCode,
                                        String errorMsg,
                                        SandboxLatencyInfo sandboxLatencyInfo) {
                                    sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                                            mInjector.elapsedRealtime());

                                    int sdkSandboxManagerErrorCode =
                                            toSdkSandboxManagerRequestSurfacePackageErrorCode(
                                                    errorCode);

                                    handleSurfacePackageError(
                                            sdkSandboxManagerErrorCode,
                                            errorMsg,
                                            sandboxLatencyInfo,
                                            callback);
                                }
                            });
                }
            } catch (DeadObjectException e) {
                LogUtil.d(
                        TAG,
                        mCallingInfo
                                + " requested surface package from SDK "
                                + mSdkName
                                + " but sandbox is not alive");
                handleSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                        "SDK " + mSdkName + " is not loaded",
                        sandboxLatencyInfo,
                        callback);
            } catch (RemoteException e) {
                String errorMsg = "Failed to requestSurfacePackage";
                Log.w(TAG, errorMsg, e);
                handleSurfacePackageError(
                        SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                        errorMsg + ": " + e,
                        sandboxLatencyInfo,
                        callback);
            }
        }

        @SdkSandboxManager.RequestSurfacePackageErrorCode
        private int toSdkSandboxManagerRequestSurfacePackageErrorCode(int sdkSandboxErrorCode) {
            if (sdkSandboxErrorCode
                    == IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR) {
                return SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
            }
            Log.e(
                    TAG,
                    "Error code"
                            + sdkSandboxErrorCode
                            + "has no mapping to the SdkSandboxManager error codes");
            return SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
        }
    }

    private void logLatencyMetricsForCallback(
            long timeSystemServerReceivedCallFromSandbox,
            int method,
            SandboxLatencyInfo sandboxLatencyInfo) {
        final int appUid = mCallingInfo.getUid();

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                sandboxLatencyInfo.getSystemServerToSandboxLatency(),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                appUid);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                sandboxLatencyInfo.getSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                appUid);

        final int latencySdk = sandboxLatencyInfo.getSdkLatency();
        if (latencySdk != -1) {
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    method,
                    latencySdk,
                    sandboxLatencyInfo.isSuccessfulAtSdk(),
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                    appUid);
        }

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromSandbox
                                - sandboxLatencyInfo.getTimeSandboxCalledSystemServer()),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                appUid);
    }

    private SdkProviderInfo createSdkProviderInfo() throws PackageManager.NameNotFoundException {
        PackageManagerHelper packageManagerHelper =
                new PackageManagerHelper(mContext, mCallingInfo.getUid());
        SharedLibraryInfo sharedLibrary =
                packageManagerHelper.getSdkSharedLibraryInfoForSdk(
                        mCallingInfo.getPackageName(), mSdkName);

        String sdkProviderClassName;
        try {
            sdkProviderClassName =
                    packageManagerHelper
                            .getProperty(
                                    PROPERTY_SDK_PROVIDER_CLASS_NAME,
                                    sharedLibrary.getDeclaringPackage().getPackageName())
                            .getString();
        } catch (PackageManager.NameNotFoundException e) {
            throw new PackageManager.NameNotFoundException(
                    PROPERTY_SDK_PROVIDER_CLASS_NAME + " property");
        }

        ApplicationInfo applicationInfo =
                packageManagerHelper.getApplicationInfoForSharedLibrary(
                        sharedLibrary,
                        PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                | PackageManager.MATCH_ANY_USER);

        return new SdkProviderInfo(applicationInfo, sharedLibrary, sdkProviderClassName);
    }

    ApplicationInfo getApplicationInfo() {
        return mSdkProviderInfo.getApplicationInfo();
    }

    /** Class which retrieves and stores the sdkName, sdkProviderClassName, and ApplicationInfo */
    static class SdkProviderInfo {

        private final ApplicationInfo mApplicationInfo;
        private final SharedLibraryInfo mSdkInfo;
        private final String mSdkProviderClassName;

        private SdkProviderInfo(
                ApplicationInfo applicationInfo,
                SharedLibraryInfo sdkInfo,
                String sdkProviderClassName) {
            mApplicationInfo = applicationInfo;
            mSdkInfo = sdkInfo;
            mSdkProviderClassName = sdkProviderClassName;
        }

        public SharedLibraryInfo getSdkInfo() {
            return mSdkInfo;
        }

        public String getSdkProviderClassName() {
            return mSdkProviderClassName;
        }

        public ApplicationInfo getApplicationInfo() {
            return mApplicationInfo;
        }
    }
}

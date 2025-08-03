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

package com.android.server.devicelock;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.devicelock.ParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Slog;

import com.android.devicelockcontroller.IDeviceLockControllerService;
import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * This class is used to establish a connection (bind) to the Device Lock Controller APK.
 */
final class DeviceLockControllerConnectorImpl implements DeviceLockControllerConnector {
    private final Object mLock = new Object();

    private static final String TAG = "DeviceLockControllerConnectorImpl";

    @GuardedBy("mLock")
    private IDeviceLockControllerService mDeviceLockControllerService;

    @GuardedBy("mLock")
    private ServiceConnection mServiceConnection;

    private final Context mContext;
    private final ComponentName mComponentName;
    private final UserHandle mUserHandle;
    private final Handler mHandler;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private static final long INACTIVITY_TIMEOUT_MILLIS = 1_000 * 60 * 1; // One minute.
    private static final long API_CALL_TIMEOUT_MILLIS = 1_000 * 10;       // Ten seconds.

    // The following hash set is used for API timeout detection. We do oneway calls into the
    // device lock controller service, and the service is supposed to reply with another one way
    // call. Once we call into the device lock controller service, we add the callback to this
    // hash map and remove it once the remote invocation from the controller is received by
    // the system service or a timeout occurred. In this way, we guarantee that the callback
    // will be always invoked (and it's only invoked once).
    @GuardedBy("mPendingCallbacks")
    private final ArraySet<OutcomeReceiver> mPendingCallbacks = new ArraySet<>();

    private final Runnable mUnbindDeviceLockControllerService = () -> {
        Slog.i(TAG, "Unbinding DeviceLockControllerService");
        unbind();
    };

    private <Result> void callControllerApi(Callable<Void> body,
            OutcomeReceiver<Result, Exception> callback) {
        Runnable r = () -> {
            Exception exception = null;

            mHandler.removeCallbacks(mUnbindDeviceLockControllerService);
            mHandler.postDelayed(mUnbindDeviceLockControllerService, INACTIVITY_TIMEOUT_MILLIS);

            synchronized (mLock) {
                // First, bind if not already bound.
                if (bindLocked()) {
                    while (mDeviceLockControllerService == null) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            // Nothing to do, wait again if mService is still null.
                        }
                    }

                    try {
                        synchronized (mPendingCallbacks) {
                            mPendingCallbacks.add(callback);
                        }
                        body.call();
                        // Start timeout for this call.
                        mHandler.postDelayed(() -> {
                            boolean removed;
                            synchronized (mPendingCallbacks) {
                                removed = mPendingCallbacks.remove(callback);
                            }
                            if (removed) {
                                // We hit a timeout, execute the callback.
                                mHandler.post(() -> callback.onError(new TimeoutException()));
                            }
                        }, API_CALL_TIMEOUT_MILLIS);
                    } catch (Exception e) {
                        synchronized (mPendingCallbacks) {
                            mPendingCallbacks.remove(callback);
                        }
                        exception = e;
                    }
                } else {
                    exception = new Exception("Failed to bind to service");
                }
            }

            if (exception != null) {
                final Exception finalException = exception;
                mHandler.post(() -> callback.onError(finalException));
                return;
            }
        };

        mExecutorService.execute(r);
    }

    private boolean hasApiCallTimedOut(OutcomeReceiver callback) {
        boolean removed;
        synchronized (mPendingCallbacks) {
            removed = mPendingCallbacks.remove(callback);
        }
        // if this callback was already been removed by the timeout and somehow this callback
        // arrived late. We already replied with a timeout error, ignore the result.

        return !removed;
    }

    private RemoteCallback.OnResultListener checkTimeout(OutcomeReceiver callback,
            RemoteCallback.OnResultListener listener) {
        return (@Nullable Bundle bundle) -> {
            if (hasApiCallTimedOut(callback)) {
                return;
            }
            listener.onResult(bundle);
        };
    }

    private class DeviceLockControllerServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mLock) {
                if (mServiceConnection == null) {
                    Slog.w(TAG, "Connected: " + mComponentName.flattenToShortString()
                            + " but not bound, ignore.");
                    return;
                }

                Slog.i(TAG, "Connected to " + mComponentName.flattenToShortString());

                mDeviceLockControllerService =
                        IDeviceLockControllerService.Stub.asInterface(service);

                mLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Slog.i(TAG, "Disconnected from " + mComponentName.flattenToShortString());
            // Service has crashed or been killed. The binding is still valid, however
            // we unbind here so we can bind again an restart the service when needed.
            // Otherwise, Activity Manager would restart it after some back-off, and trying
            // to use the service in this timeframe would result in a DeadObjectException.
            unbind();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            // Activity Manager gave up.
            synchronized (mLock) {
                if (mServiceConnection == null) {
                    // Callback came in late
                    Slog.w(TAG, "Binding died: " + mComponentName.flattenToShortString()
                            + " but not bound, ignore.");
                    return;
                }

                Slog.w(TAG, "Binding died " + mComponentName.flattenToShortString());

                // We just unbind here; any API calls would cause the binding to be recreated
                // when needed.
                unbindLocked();
            }
        }
    }

    /**
     * Create a new connector to the Device Lock Controller service.
     *
     * @param context       the context for this call.
     * @param componentName Device Lock Controller service component name.
     */
    DeviceLockControllerConnectorImpl(@NonNull Context context,
            @NonNull ComponentName componentName, @NonNull UserHandle userHandle) {
        mContext = context;
        mComponentName = componentName;
        mUserHandle = userHandle;

        HandlerThread handlerThread =
                new HandlerThread("DeviceLockControllerConnectorHandlerThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    @GuardedBy("mLock")
    private boolean bindLocked() {
        if (mServiceConnection != null) {
            // Already bound, ignore and return success.
            return true;
        }

        mServiceConnection = new DeviceLockControllerServiceConnection();

        final Intent service = new Intent().setComponent(mComponentName);
        final boolean bound = mContext.bindServiceAsUser(service, mServiceConnection,
                Context.BIND_AUTO_CREATE, mUserHandle);

        if (bound) {
            Slog.i(TAG, "Binding " + mComponentName.flattenToShortString());
        } else {
            // As per bindService() documentation, we still need to call unbindService()
            // if binding fails.
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
            Slog.e(TAG, "Binding " + mComponentName.flattenToShortString() + " failed.");
        }

        return bound;
    }

    @GuardedBy("mLock")
    private void unbindLocked() {
        if (mServiceConnection == null) {
            return;
        }

        Slog.i(TAG, "Unbinding " + mComponentName.flattenToShortString());

        mContext.unbindService(mServiceConnection);

        mDeviceLockControllerService = null;
        mServiceConnection = null;
    }

    @Override
    public void unbind() {
        synchronized (mLock) {
            unbindLocked();
        }
    }

    /**
     * Report an exception (if any) using the callback.
     *
     * @param callback Callback used to report the exception.
     * @param bundle Bundle where to look for a parcelable exception.
     *
     * @return true if an exception was reported.
     */
    private boolean maybeReportException(@NonNull OutcomeReceiver<?, Exception> callback,
            @NonNull Bundle bundle) {
        bundle.setClassLoader(this.getClass().getClassLoader());
        final ParcelableException parcelableException =
                bundle.getSerializable(IDeviceLockControllerService.KEY_PARCELABLE_EXCEPTION,
                        ParcelableException.class);
        if (parcelableException != null) {
            mHandler.post(() -> callback.onError(parcelableException));
            return true;
        }

        return false;
    }

    @Override
    public void lockDevice(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.lockDevice(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void unlockDevice(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.unlockDevice(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void isDeviceLocked(OutcomeReceiver<Boolean, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            final boolean isLocked =
                    result.getBoolean(IDeviceLockControllerService.KEY_RESULT);
            mHandler.post(() -> callback.onResult(isLocked));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.isDeviceLocked(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void getDeviceId(OutcomeReceiver<String, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            final String deviceId =
                    result.getString(IDeviceLockControllerService.KEY_RESULT);
            if (TextUtils.isEmpty(deviceId)) { // If the deviceId is null or empty
                mHandler.post(() -> callback.onError(new IllegalStateException(
                        "No registered Device ID found")));
            } else {
                mHandler.post(() -> callback.onResult(deviceId));
            }
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.getDeviceIdentifier(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void clearDeviceRestrictions(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.clearDeviceRestrictions(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void onUserSwitching(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.onUserSwitching(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void onUserUnlocked(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.onUserUnlocked(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void onUserSetupCompleted(OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.onUserSetupCompleted(remoteCallback);
                return null;
            }
        }, callback);
    }

    @Override
    public void onAppCrashed(boolean isKiosk, OutcomeReceiver<Void, Exception> callback) {
        RemoteCallback remoteCallback = new RemoteCallback(checkTimeout(callback, result -> {
            if (maybeReportException(callback, result)) {
                return;
            }

            mHandler.post(() -> callback.onResult(null));
        }));

        callControllerApi(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in callControllerApi (error prone).
            public Void call() throws Exception {
                mDeviceLockControllerService.onAppCrashed(isKiosk, remoteCallback);
                return null;
            }
        }, callback);
    }
}

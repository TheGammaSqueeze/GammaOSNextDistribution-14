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

package android.security.rkp.service;

import static android.annotation.SystemApi.Client.SYSTEM_SERVER;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.OperationCanceledException;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.rkpdapp.IGetKeyCallback;
import com.android.rkpdapp.IGetRegistrationCallback;
import com.android.rkpdapp.IRegistration;
import com.android.rkpdapp.IRemoteProvisioning;
import com.android.rkpdapp.IStoreUpgradedKeyCallback;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Proxy object for calling into com.android.rkpdapp, which is responsible for remote key
 * provisioning. System server cannot call into rkpdapp directly, as it is contained in a mainline
 * module, and as such cannot expose stable AIDL. Instead, this wrapper code is exposed as a
 * stable SYSTEM_SERVER API. The AIDL details are hidden by this class.
 *
 * @hide
 */
@SystemApi(client = SYSTEM_SERVER)
public class RegistrationProxy {
    static final String TAG = "RegistrationProxy";
    IRegistration mBinder;

    /** Deals with the {@code ServiceConnection} lifetime for the rkpd bound service. */
    private static class IRemoteProvisioningConnection implements ServiceConnection {
        @GuardedBy("this")
        IRemoteProvisioning mRemoteProvisioningService;
        RemoteException mRemoteException;
        CountDownLatch mLatch = new CountDownLatch(1);

        IRemoteProvisioningConnection(Context context) throws RemoteException {
            final String serviceName = IRemoteProvisioning.class.getName();
            final Intent intent = new Intent(serviceName);

            // Look for the bound service hosted by a system package. There should only ever be one.
            final ResolveInfoFlags flags = ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY);
            final List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(
                    intent, flags);
            if (resolveInfos == null || resolveInfos.size() == 0) {
                throw new IllegalStateException(
                        "No system services were found hosting " + serviceName);
            }
            if (resolveInfos.size() > 1) {
                throw new IllegalStateException(
                        "Multiple system packages found hosting " + serviceName + ": "
                                + resolveInfos.stream().map(
                                    r -> r.serviceInfo.applicationInfo.packageName).collect(
                                        Collectors.joining(", ")));
            }

            // At this point, we're sure there's one system service hosting the binder, so bind it
            final ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
            intent.setComponent(
                    new ComponentName(serviceInfo.applicationInfo.packageName, serviceInfo.name));
            if (!context.bindServiceAsUser(intent, this, Context.BIND_AUTO_CREATE,
                    UserHandle.SYSTEM)) {
                throw new RemoteException("Failed to bind to IRemoteProvisioning service");
            }
        }

        public IRemoteProvisioning waitForRemoteProvisioningService(Duration bindTimeout)
                throws RemoteException, TimeoutException {
            try {
                if (!mLatch.await(bindTimeout.toMillis(), MILLISECONDS)) {
                    throw new TimeoutException("Timed out waiting on service connection to rkpd");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Wait for binder was interrupted", e);
            }
            synchronized (this) {
                if (mRemoteException != null) {
                    throw mRemoteException;
                }
                return mRemoteProvisioningService;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "onServiceConnected: " + name.getClassName());
            synchronized (this) {
                mRemoteException = null;
                mRemoteProvisioningService = IRemoteProvisioning.Stub.asInterface(binder);
            }
            mLatch.countDown();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.i(TAG, "onNullBinding: " + name.getClassName());
            mRemoteException = new RemoteException("Received null binding from rkpd service.");
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected: " + name.getClassName());
            synchronized (this) {
                mRemoteException = new RemoteException("rkpd service disconnected");
                mRemoteProvisioningService = null;
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.i(TAG, "onBindingDied: " + name.getClassName());
            synchronized (this) {
                mRemoteException = new RemoteException("rkpd service binding died");
                mRemoteProvisioningService = null;
            }
        }
    }

    /**
     * Factory method for creating a registration for an IRemotelyProvisionedComponent.
     * RegistrationProxy objects may not be directly constructed via new.
     *
     * @param context The application Context
     * @param callerUid The UID of the caller into system server for whom we need to fetch a
     *                  registration. Remotely provisioned keys are partitioned by client UID,
     *                  so each UID passed here will result in a unique registration that returns
     *                  keys that are only for the given client UID.
     * @param irpcName The name of the IRemotelyProvisionedComponent HAL for which a registration
     *                 is requested.
     * @param bindTimeout How long to wait for the underlying service binding. If the service is
     *                    not available within this time limit, the receiver is notified with a
     *                    TimeoutException.
     * @param executor Callbacks to the receiver are performed using this executor.
     * @param receiver Asynchronously receives either a registration or some exception indicating
     *                 why the registration could not be created.
     */
    public static void createAsync(@NonNull Context context, int callerUid,
            @NonNull String irpcName, @NonNull Duration bindTimeout,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<RegistrationProxy, Exception> receiver) {
        try {
            // The connection object is used to get exactly one IRegistration binder. Once we
            // get it, we unbind the connection. This allows the bound service to be terminated
            // under memory pressure.
            final IRemoteProvisioningConnection connection = new IRemoteProvisioningConnection(
                    context);
            IGetRegistrationCallback.Stub callbackHandler = new IGetRegistrationCallback.Stub() {
                @Override
                public void onSuccess(IRegistration registration) {
                    Log.i(TAG, "IGetRegistrationCallback.onSuccess");
                    context.unbindService(connection);
                    executor.execute(() -> receiver.onResult(new RegistrationProxy(registration)));
                }

                @Override
                public void onCancel() {
                    Log.i(TAG, "IGetRegistrationCallback.onCancel");
                    context.unbindService(connection);
                }

                @Override
                public void onError(String error) {
                    Log.i(TAG, "IGetRegistrationCallback.onError:" + error);
                    context.unbindService(connection);
                    executor.execute(() -> receiver.onError(new RemoteException(error)));
                }
            };

            final IRemoteProvisioning remoteProvisioningService =
                    connection.waitForRemoteProvisioningService(bindTimeout);
            remoteProvisioningService.getRegistration(callerUid, irpcName, callbackHandler);
        } catch (Exception e) {
            Log.e(TAG, "Error getting registration", e);
            executor.execute(() -> receiver.onError(e));
        }
    }

    private RegistrationProxy(IRegistration binder) {
        mBinder = binder;
    }

    /**
     * Begins an async operation to fetch a key from rkp. The receiver will be notified on
     * completion or error. Cancellation is reported as an error, passing OperationCanceledException
     * to the receiver.
     * @param keyId An arbitrary, caller-chosen identifier for the key. If a key has previously
     *              been assigned this id, then that key will be returned. Else, an unassigned key
     *              is chosen and keyId is assigned to that key.
     * @param cancellationSignal Signal object used to indicate to the asynchronous code that a
     *                           pending call should be cancelled.
     * @param executor The executor on which to call receiver.
     * @param receiver Asynchronously receives a RemotelyProvisionedKey on success, else an
     *                 exception is received.
     */
    public void getKeyAsync(int keyId,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<RemotelyProvisionedKey, Exception> receiver) {
        final AtomicBoolean operationComplete = new AtomicBoolean(false);

        final var callback = new IGetKeyCallback.Stub() {
            @Override
            public void onSuccess(com.android.rkpdapp.RemotelyProvisionedKey key) {
                if (operationComplete.compareAndSet(false, true)) {
                    executor.execute(() -> receiver.onResult(new RemotelyProvisionedKey(key)));
                } else {
                    Log.w(TAG, "Ignoring extra success for " + this);
                }
            }

            @Override
            public void onProvisioningNeeded() {
                Log.i(TAG, "Provisioning required before keys are available for " + this);
            }

            @Override
            public void onCancel() {
                if (operationComplete.compareAndSet(false, true)) {
                    executor.execute(() -> receiver.onError(new OperationCanceledException()));
                } else {
                    Log.w(TAG, "Ignoring extra cancel for " + this);
                }
            }

            @Override
            public void onError(byte error, String description) {
                if (operationComplete.compareAndSet(false, true)) {
                    executor.execute(() -> receiver.onError(
                            new RkpProxyException(convertGetKeyError(error), description)));
                } else {
                    Log.w(TAG, "Ignoring extra error (" + error + ") for " + this);
                }
            }
        };

        cancellationSignal.setOnCancelListener(() -> {
            if (operationComplete.get()) {
                Log.w(TAG,
                        "Ignoring cancel call after operation complete for " + callback.hashCode());
            } else {
                try {
                    Log.i(TAG, "Attempting to cancel getKeyAsync for " + callback.hashCode());
                    mBinder.cancelGetKey(callback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error cancelling getKey operation", e);
                }
            }
        });

        try {
            Log.i(TAG, "getKeyAsync operation started with callback " + callback.hashCode());
            mBinder.getKey(keyId, callback);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Begins an async operation to store an upgraded key blob in the rkpd database. This is part
     * of the anti-rollback protections for keys. If a system has a security patch applied, then
     * key blobs may need to be upgraded so that the keys cannot be used if the system is rolled
     * back to the vulnerable version of code.
     *
     * The anti-rollback mechanism requires the consumer of the key blob (e.g. KeyMint) to return
     * an error indicating that the key needs to be upgraded. The client (e.g. keystore2) is then
     * responsible for calling the upgrade method, then asking rkpd to store the upgraded blob.
     * This way, if the system is ever downgraded, the key blobs can no longer be used.
     *
     * @param oldKeyBlob The key to be upgraded.
     * @param newKeyBlob The new key, replacing oldKeyBlob in the database.
     * @param executor The executor on which to call receiver.
     * @param receiver Asynchronously receives success callback, else an exception is received.
     */
    public void storeUpgradedKeyAsync(@NonNull byte[] oldKeyBlob, @NonNull byte[] newKeyBlob,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> receiver) {
        final var callback = new IStoreUpgradedKeyCallback.Stub() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "upgrade key succeeded for callback " + hashCode());
                executor.execute(() -> receiver.onResult(null));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "upgrade key failed: " + error + ", callback: " + hashCode());
                executor.execute(() -> receiver.onError(new RemoteException(error)));
            }
        };

        try {
            Log.i(TAG, "storeUpgradedKeyAsync operation started with callback "
                    + callback.hashCode());
            mBinder.storeUpgradedKeyAsync(oldKeyBlob, newKeyBlob, callback);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /** Converts an IGetKeyCallback.Error code into an RkpProxyException error code. */
    private static int convertGetKeyError(byte error) {
        switch (error) {
            case IGetKeyCallback.Error.ERROR_REQUIRES_SECURITY_PATCH:
                return RkpProxyException.ERROR_REQUIRES_SECURITY_PATCH;
            case IGetKeyCallback.Error.ERROR_PENDING_INTERNET_CONNECTIVITY:
                return RkpProxyException.ERROR_PENDING_INTERNET_CONNECTIVITY;
            case IGetKeyCallback.Error.ERROR_PERMANENT:
                return RkpProxyException.ERROR_PERMANENT;
            case IGetKeyCallback.Error.ERROR_UNKNOWN:
                return RkpProxyException.ERROR_UNKNOWN;
            default:
                Log.e(TAG, "Undefined error from rkpd: " + error);
                return RkpProxyException.ERROR_UNKNOWN;
        }
    }
}

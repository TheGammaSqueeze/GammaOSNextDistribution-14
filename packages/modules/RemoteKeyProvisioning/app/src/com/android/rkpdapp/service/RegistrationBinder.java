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

package com.android.rkpdapp.service;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.IGetKeyCallback;
import com.android.rkpdapp.IRegistration;
import com.android.rkpdapp.IStoreUpgradedKeyCallback;
import com.android.rkpdapp.RemotelyProvisionedKey;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.metrics.ProvisioningAttempt;
import com.android.rkpdapp.metrics.RkpdClientOperation;
import com.android.rkpdapp.provisioner.Provisioner;
import com.android.rkpdapp.utils.Settings;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import co.nstant.in.cbor.CborException;

/**
 * Implementation of com.android.rkpdapp.IRegistration, which fetches keys for a (caller UID,
 * IRemotelyProvisionedComponent) tuple.
 */
public final class RegistrationBinder extends IRegistration.Stub {
    // The minimum amount of time that the registration will consider a key valid. If a key expires
    // before this time elapses, then the key is considered too stale and will not be used.
    public static final Duration MIN_KEY_LIFETIME = Duration.ofHours(1);

    static final String TAG = "RkpdRegistrationBinder";

    private final Context mContext;
    private final int mClientUid;
    private final SystemInterface mSystemInterface;
    private final ProvisionedKeyDao mProvisionedKeyDao;
    private final ServerInterface mRkpServer;
    private final Provisioner mProvisioner;
    private final ExecutorService mThreadPool;
    private final Object mTasksLock = new Object();
    @GuardedBy("mTasksLock")
    private final HashMap<IBinder, Future<?>> mTasks = new HashMap<>();

    public RegistrationBinder(Context context, int clientUid, SystemInterface systemInterface,
            ProvisionedKeyDao provisionedKeyDao, ServerInterface rkpServer,
            Provisioner provisioner, ExecutorService threadPool) {
        mContext = context;
        mClientUid = clientUid;
        mSystemInterface = systemInterface;
        mProvisionedKeyDao = provisionedKeyDao;
        mRkpServer = rkpServer;
        mProvisioner = provisioner;
        mThreadPool = threadPool;
    }

    private void getKeyWorker(int keyId, IGetKeyCallback callback)
            throws CborException, InterruptedException, RkpdException {
        Log.i(TAG, "Key requested for : " + mSystemInterface.getServiceName() + ", clientUid: "
                + mClientUid + ", keyId: " + keyId + ", callback: "
                + callback.asBinder().hashCode());
        // Use reduced look-ahead to get rid of soon-to-be expired keys, because the periodic
        // provisioner should be ensuring that old keys are already expired. However, in the
        // edge case that periodic provisioning didn't work, we want to allow slightly "more stale"
        // keys to be used. This reduces window of time in which key attestation is not available
        // (e.g. if there is a provisioning server outage). Note that we must have some look-ahead,
        // rather than using "now", else we might return a key that expires so soon that the caller
        // can never successfully use it.
        final Instant minExpiry = Instant.now().plus(MIN_KEY_LIFETIME);
        mProvisionedKeyDao.deleteExpiringKeys(minExpiry);

        ProvisionedKey assignedKey = mProvisionedKeyDao.getKeyForClientAndIrpc(
                mSystemInterface.getServiceName(), mClientUid, keyId);

        if (assignedKey == null) {
            assignedKey = tryToAssignKey(minExpiry, keyId);
        }

        if (assignedKey == null) {
            // Since provisionKeys goes over the network, this represents our last chancel to cancel
            // before we go off and hit the network. It's not worth checking for interruption prior
            // to this, because none of the prior work is long-running.
            checkForCancel();

            Log.i(TAG, "No keys are available, kicking off provisioning");
            checkedCallback(callback::onProvisioningNeeded);
            try (ProvisioningAttempt metrics = ProvisioningAttempt.createOutOfKeysAttemptMetrics(
                    mContext, mSystemInterface.getServiceName())) {
                fetchGeekAndProvisionKeys(metrics);
            }
            assignedKey = tryToAssignKey(minExpiry, keyId);
        }

        // Now that we've gotten back from our network round-trip, it's possible an interrupt came
        // in, so deal with it. However, it's most likely that an InterruptedException came from
        // the SDK while we were sitting on the socket down in Provisioner.provisionKeys.
        checkForCancel();

        if (assignedKey == null) {
            // This can happen if provisioning is disabled on the device for some reason,
            // or if we're not connected to the internet.
            Log.e(TAG, "Unable to provision keys");
            checkedCallback(() -> callback.onError(IGetKeyCallback.Error.ERROR_UNKNOWN,
                    "Provisioning failed, no keys available"));
        } else {
            Log.i(TAG, "Key successfully assigned to client");
            RemotelyProvisionedKey key = new RemotelyProvisionedKey();
            key.keyBlob = assignedKey.keyBlob;
            key.encodedCertChain = assignedKey.certificateChain;
            checkedCallback(() -> callback.onSuccess(key));
        }
    }

    private void fetchGeekAndProvisionKeys(ProvisioningAttempt metrics)
            throws CborException, RkpdException, InterruptedException {
        GeekResponse response = mRkpServer.fetchGeekAndUpdate(metrics);
        if (response.numExtraAttestationKeys == 0) {
            Log.v(TAG, "Provisioning disabled.");
            metrics.setEnablement(ProvisioningAttempt.Enablement.DISABLED);
            metrics.setStatus(ProvisioningAttempt.Status.PROVISIONING_DISABLED);
            return;
        }
        mProvisioner.provisionKeys(metrics, mSystemInterface, response);
    }

    private ProvisionedKey tryToAssignKey(Instant minExpiry, int keyId) {
        // Since we're going to be assigning a fresh key to the app, we ideally want a key that's
        // longer-lived than the minimum. We use the server-configured expiration, which is normally
        // days, as the preferred lifetime for a key. However, if we cannot find a key that is valid
        // for that long, we'll settle for a shorter-lived key.
        Instant[] expirations = new Instant[]{
                Instant.now().plus(Settings.getExpiringBy(mContext)),
                minExpiry
        };
        Arrays.sort(expirations, Collections.reverseOrder());
        for (Instant expiry : expirations) {
            Log.i(TAG, "No key assigned, looking for an available key with expiry of " + expiry);
            ProvisionedKey key = mProvisionedKeyDao.getOrAssignKey(
                    mSystemInterface.getServiceName(),
                    expiry, mClientUid, keyId);
            if (key != null) {
                provisionKeysOnKeyConsumed();
                return key;
            }
        }
        return null;
    }

    private void provisionKeysOnKeyConsumed() {
        try (ProvisioningAttempt metrics = ProvisioningAttempt.createKeyConsumedAttemptMetrics(
                mContext, mSystemInterface.getServiceName())) {
            if (!mProvisioner.isProvisioningNeeded(metrics, mSystemInterface.getServiceName())) {
                metrics.setStatus(ProvisioningAttempt.Status.NO_PROVISIONING_NEEDED);
                return;
            }

            mThreadPool.execute(() -> {
                try {
                    fetchGeekAndProvisionKeys(metrics);
                } catch (CborException | RkpdException | InterruptedException e) {
                    Log.e(TAG, "Error provisioning keys", e);
                }
            });
        }
    }

    private void checkForCancel() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

    private interface CallbackWrapper {
        void run() throws RemoteException;
    }

    private void checkedCallback(CallbackWrapper wrapper) {
        try {
            wrapper.run();
        } catch (RemoteException e) {
            // This should only ever happen if there's a binder issue invoking the callback
            Log.e(TAG, "Error performing client callback", e);
        }
    }

    @Override
    public void getKey(int keyId, IGetKeyCallback callback) {
        synchronized (mTasksLock) {
            if (mTasks.containsKey(callback.asBinder())) {
                throw new IllegalArgumentException("Callback " + callback.asBinder().hashCode()
                        + " is already associated with a getKey operation that is in-progress");
            }

            mTasks.put(callback.asBinder(),
                    mThreadPool.submit(() -> getKeyThreadWorker(keyId, callback)));
        }
    }

    private void getKeyThreadWorker(int keyId, IGetKeyCallback callback) {
        // We don't use a try-with-resources here because the metric may need to be updated
        // inside an exception handler, but close would have been called prior to that. Therefore,
        // we explicitly close the metric explicitly in the "finally" block, after all handlers
        // have had a chance to run.
        RkpdClientOperation metric = RkpdClientOperation.getKey(mClientUid,
                mSystemInterface.getServiceName());
        try {
            getKeyWorker(keyId, callback);
            metric.setResult(RkpdClientOperation.Result.SUCCESS);
        } catch (InterruptedException e) {
            Log.i(TAG, "getKey was interrupted");
            metric.setResult(RkpdClientOperation.Result.CANCELED);
            checkedCallback(callback::onCancel);
        } catch (RkpdException e) {
            Log.e(TAG, "RKPD failed to provision keys", e);
            final byte mappedError = mapToGetKeyError(e, metric);
            checkedCallback(
                    () -> callback.onError(mappedError, e.getMessage()));
        } catch (Exception e) {
            // Do our best to inform the callback when the unexpected happens. Otherwise,
            // the caller is going to wait until they timeout without knowing something like
            // a RuntimeException occurred.
            Log.e(TAG, "Unexpected error provisioning keys", e);
            checkedCallback(() -> callback.onError(IGetKeyCallback.Error.ERROR_UNKNOWN,
                    e.getMessage()));
        } finally {
            metric.close();
            synchronized (mTasksLock) {
                mTasks.remove(callback.asBinder());
            }
        }
    }

    /** Maps an RkpdException into an IGetKeyCallback.Error value. */
    private byte mapToGetKeyError(RkpdException e, RkpdClientOperation metric) {
        switch (e.getErrorCode()) {
            case NO_NETWORK_CONNECTIVITY:
                metric.setResult(RkpdClientOperation.Result.ERROR_PENDING_INTERNET_CONNECTIVITY);
                return IGetKeyCallback.Error.ERROR_PENDING_INTERNET_CONNECTIVITY;

            case DEVICE_NOT_REGISTERED:
                metric.setResult(RkpdClientOperation.Result.ERROR_PERMANENT);
                return IGetKeyCallback.Error.ERROR_PERMANENT;

            case INTERNAL_ERROR:
                metric.setResult(RkpdClientOperation.Result.ERROR_INTERNAL);
                return IGetKeyCallback.Error.ERROR_UNKNOWN;

            case NETWORK_COMMUNICATION_ERROR:
            case HTTP_CLIENT_ERROR:
            case HTTP_SERVER_ERROR:
            case HTTP_UNKNOWN_ERROR:
            default:
                return IGetKeyCallback.Error.ERROR_UNKNOWN;
        }
    }

    @Override
    public void cancelGetKey(IGetKeyCallback callback) throws RemoteException {
        Log.i(TAG, "cancelGetKey(" + callback.asBinder().hashCode() + ")");
        synchronized (mTasksLock) {
            try (RkpdClientOperation metric = RkpdClientOperation.cancelGetKey(mClientUid,
                    mSystemInterface.getServiceName())) {
                Future<?> task = mTasks.get(callback.asBinder());

                if (task == null) {
                    Log.w(TAG, "callback not found, task may have already completed");
                } else if (task.isDone()) {
                    Log.w(TAG, "task already completed, not cancelling");
                } else if (task.isCancelled()) {
                    Log.w(TAG, "task already cancelled, cannot cancel it any further");
                } else {
                    task.cancel(true);
                }
                metric.setResult(RkpdClientOperation.Result.SUCCESS);
            }
        }
    }

    @Override
    public void storeUpgradedKeyAsync(byte[] oldKeyBlob, byte[] newKeyBlob,
            IStoreUpgradedKeyCallback callback) throws RemoteException {
        Log.i(TAG, "storeUpgradedKeyAsync");
        mThreadPool.execute(() -> {
            try (RkpdClientOperation metric = RkpdClientOperation.storeUpgradedKey(
                    mClientUid, mSystemInterface.getServiceName())) {
                int keysUpgraded = mProvisionedKeyDao.upgradeKeyBlob(mClientUid, oldKeyBlob,
                        newKeyBlob);
                if (keysUpgraded == 1) {
                    metric.setResult(RkpdClientOperation.Result.SUCCESS);
                    checkedCallback(callback::onSuccess);
                } else if (keysUpgraded == 0) {
                    metric.setResult(RkpdClientOperation.Result.ERROR_KEY_NOT_FOUND);
                    checkedCallback(() -> callback.onError("No keys matching oldKeyBlob found"));
                } else {
                    metric.setResult(RkpdClientOperation.Result.ERROR_INTERNAL);
                    Log.e(TAG, "Multiple keys matched the upgrade (" + keysUpgraded
                            + "). This should be impossible!");
                    checkedCallback(() -> callback.onError("Internal error"));
                }
            } catch (Exception e) {
                checkedCallback(() -> callback.onError(e.getMessage()));
            }
        });
    }
}

/*
 * Copyright 2023 The Android Open Source Project
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

package android.remoteauth;

import static android.remoteauth.DeviceDiscoveryCallback.STATE_LOST;
import static android.remoteauth.DeviceDiscoveryCallback.STATE_SEEN;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemService;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

/**
 * A system service providing a way to perform remote authentication-related operations such as
 * discovering, registering and authenticating via remote authenticator.
 *
 * <p>To get a {@link RemoteAuthManager} instance, call the <code>
 * Context.getSystemService(Context.REMOTE_AUTH_SERVICE)</code>.
 *
 * @hide
 */
// TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
// TODO(b/290092977): Change to Context.REMOTE_AUTH_SERVICE after aosp/2681375
// is automerges from aosp-main to udc-mainline-prod
@SystemService(RemoteAuthManager.REMOTE_AUTH_SERVICE)
public class RemoteAuthManager {
    private static final String TAG = "RemoteAuthManager";

    /** @hide */
    public static final String REMOTE_AUTH_SERVICE = "remote_auth";

    private final Context mContext;
    private final IRemoteAuthService mService;

    @GuardedBy("mDiscoveryListeners")
    private final WeakHashMap<
                    DeviceDiscoveryCallback, WeakReference<DeviceDiscoveryListenerTransport>>
            mDiscoveryListeners = new WeakHashMap<>();

    /** @hide */
    public RemoteAuthManager(@NonNull Context context, @NonNull IRemoteAuthService service) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(service);
        mContext = context;
        mService = service;
    }

    /**
     * Returns if this device can be enrolled in the feature.
     *
     * @return true if this device can be enrolled
     * @hide
     */
    // TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
    // TODO(b/297301535): @RequiresPermission(MANAGE_REMOTE_AUTH)
    public boolean isRemoteAuthSupported() {
        try {
            return mService.isRemoteAuthSupported();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Starts remote authenticator discovery process with timeout. Devices that are capable to
     * operate as remote authenticators are reported via callback. The discovery stops by calling
     * stopDiscovery or after a timeout.
     *
     * @param timeoutMs the duration in milliseconds after which discovery will stop automatically
     * @param executor the callback will be executed in the executor thread
     * @param callback to be used by the caller to get notifications about remote devices
     * @return {@code true} if discovery began successfully, {@code false} otherwise
     * @hide
     */
    // TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
    // TODO(b/297301535): @RequiresPermission(MANAGE_REMOTE_AUTH)
    public boolean startDiscovery(
            int timeoutMs,
            @CallbackExecutor @NonNull Executor executor,
            @NonNull DeviceDiscoveryCallback callback) {
        try {
            Preconditions.checkNotNull(callback, "invalid null callback");
            Preconditions.checkArgument(timeoutMs > 0, "invalid timeoutMs, must be > 0");
            Preconditions.checkNotNull(executor, "invalid null executor");
            DeviceDiscoveryListenerTransport transport;
            synchronized (mDiscoveryListeners) {
                WeakReference<DeviceDiscoveryListenerTransport> reference =
                        mDiscoveryListeners.get(callback);
                transport = (reference != null) ? reference.get() : null;
                if (transport == null) {
                    transport =
                            new DeviceDiscoveryListenerTransport(
                                    callback, mContext.getUser().getIdentifier(), executor);
                }

                boolean result =
                        mService.registerDiscoveryListener(
                                transport,
                                mContext.getUser().getIdentifier(),
                                timeoutMs,
                                mContext.getPackageName(),
                                mContext.getAttributionTag());
                if (result) {
                    mDiscoveryListeners.put(callback, new WeakReference<>(transport));
                    return true;
                }
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return false;
    }

    /**
     * Removes this listener from device discovery notifications. The given callback is guaranteed
     * not to receive any invocations that happen after this method is invoked.
     *
     * @param callback the callback for the previously started discovery to be ended
     * @hide
     */
    // Suppressed lint: Registration methods should have overload that accepts delivery Executor.
    // Already have executor in startDiscovery() method.
    @SuppressLint("ExecutorRegistration")
    // TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
    // TODO(b/297301535): @RequiresPermission(MANAGE_REMOTE_AUTH)
    public void stopDiscovery(@NonNull DeviceDiscoveryCallback callback) {
        Preconditions.checkNotNull(callback, "invalid null scanCallback");
        try {
            DeviceDiscoveryListenerTransport transport;
            synchronized (mDiscoveryListeners) {
                WeakReference<DeviceDiscoveryListenerTransport> reference =
                        mDiscoveryListeners.remove(callback);
                transport = (reference != null) ? reference.get() : null;
            }
            if (transport != null) {
                mService.unregisterDiscoveryListener(
                        transport,
                        transport.getUserId(),
                        mContext.getPackageName(),
                        mContext.getAttributionTag());
            } else {
                Log.d(
                        TAG,
                        "Cannot stop discovery with this callback "
                                + "because it is not registered.");
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private class DeviceDiscoveryListenerTransport extends IDeviceDiscoveryListener.Stub {

        private volatile @NonNull DeviceDiscoveryCallback mDeviceDiscoveryCallback;
        private Executor mExecutor;
        private @UserIdInt int mUserId;

        DeviceDiscoveryListenerTransport(
                DeviceDiscoveryCallback deviceDiscoveryCallback,
                @UserIdInt int userId,
                @CallbackExecutor Executor executor) {
            Preconditions.checkNotNull(deviceDiscoveryCallback, "invalid null callback");
            mDeviceDiscoveryCallback = deviceDiscoveryCallback;
            mUserId = userId;
            mExecutor = executor;
        }

        @UserIdInt
        int getUserId() {
            return mUserId;
        }

        @Override
        public void onDiscovered(RemoteDevice remoteDevice) throws RemoteException {
            if (remoteDevice == null) {
                Log.w(TAG, "onDiscovered is called with null device");
                return;
            }
            Log.i(TAG, "Notifying the caller about discovered: " + remoteDevice);
            mExecutor.execute(
                    () -> {
                        mDeviceDiscoveryCallback.onDeviceUpdate(remoteDevice, STATE_SEEN);
                    });
        }

        @Override
        public void onLost(RemoteDevice remoteDevice) throws RemoteException {
            if (remoteDevice == null) {
                Log.w(TAG, "onLost is called with null device");
                return;
            }
            Log.i(TAG, "Notifying the caller about lost: " + remoteDevice);
            mExecutor.execute(
                    () -> {
                        mDeviceDiscoveryCallback.onDeviceUpdate(remoteDevice, STATE_LOST);
                    });
        }

        @Override
        public void onTimeout() {
            Log.i(TAG, "Notifying the caller about discovery timeout");
            mExecutor.execute(
                    () -> {
                        mDeviceDiscoveryCallback.onTimeout();
                    });
            synchronized (mDiscoveryListeners) {
                mDiscoveryListeners.remove(mDeviceDiscoveryCallback);
            }
            mDeviceDiscoveryCallback = null;
        }
    }
}

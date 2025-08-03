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

package android.car.occupantconnection;


import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager.AppState;
import android.car.CarRemoteDeviceManager.OccupantZoneState;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * API for communication between different endpoints in the occupant zones in the car.
 * <p>
 * Unless specified explicitly, a client means an app that uses this API and runs as a
 * foreground user in an occupant zone, while a peer client means an app that has the same package
 * name as the caller app and runs as another foreground user (in another occupant zone or even
 * another Android system).
 * An endpoint means a component (such as a Fragment or an Activity) that has an instance of
 * {@link CarOccupantConnectionManager}.
 * <p>
 * Communication between apps with different package names is not supported.
 * <p>
 * A common use case of this API is like:
 * <pre>
 *     ==========================================        =========================================
 *     =        client1 (occupantZone1)         =        =        client2 (occupantZone2)        =
 *     =                                        =        =                                       =
 *     =    ************     ************       =        =    ************      ************     =
 *     =    * sender1A *     * sender1B *       =        =    * sender2A *      * sender2B *     =
 *     =    ************     ************       =        =    ************      ************     =
 *     =                                        =        =                                       =
 *     =    ****************************        =        =    ****************************       =
 *     =    *     ReceiverService1     *        =        =    *     ReceiverService2     *       =
 *     =    ****************************        =        =    ****************************       =
 *     =                                        =        =                                       =
 *     =    **************    **************    =        =    **************   **************    =
 *     =    * receiver1A *    * receiver1B *    =        =    * receiver2A *   * receiver2B *    =
 *     =    **************    **************    =        =    **************   **************    =
 *     ==========================================        =========================================
 *
 *                 ****** Payload *****
 *                 * ID: "receiver2A" *
 *                 * value: "123"     *
 *                 ********************                        Payload     |---> receiver2A
 *     sender1A -------------------------->ReceiverService2--------------->|
 *                                                                         |.... receiver2B
 * </pre>
 * <ul>
 *   <li> Client1 and client2 must have the same package name. Client1 runs in occupantZone1
 *        while client2 runs in occupantZone2. Sender1A (an endpoint in client1) wants to
 *        send a {@link Payload} to receiver2A (an endpoint in client2).
 *   <li> Pre-connection:
 *     <ul>
 *       <li> The client app inherits {@link AbstractReceiverService} and declares the service in
 *            its manifest file.
 *     </ul>
 *   <li> Establish connection:
 *     <ul>
 *       <li> Sender1A monitors occupantZone2 by calling {@link
 *            android.car.CarRemoteDeviceManager#registerStateCallback}.
 *       <li> Sender1A waits until the {@link OccupantZoneState} of occupantZone2 becomes
 *            {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_CONNECTION_READY} and
 *            the {@link AppState} of client2 becomes {@link
 *            android.car.CarRemoteDeviceManager#FLAG_CLIENT_INSTALLED}, then requests a connection
 *            to occupantZone2 by calling {@link #requestConnection}. If UI is needed to establish
 *            the connection, sender1A must wait until {@link
 *            android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED} and {@link
 *            android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND}).
 *       <li> ReceiverService2 is started and bound by car service ({@link
 *            com.android.car.occupantconnection.CarOccupantConnectionService} automatically.
 *            ReceiverService2 is notified via {@link
 *            AbstractReceiverService#onConnectionInitiated}.
 *       <li> ReceiverService2 accepts the connection by calling {@link
 *            AbstractReceiverService#acceptConnection}.
 *       <li> Then the one-way connection is established. Sender1A is notified via {@link
 *            ConnectionRequestCallback#onConnected}, and ReceiverService2 is notified via
 *            {@link AbstractReceiverService#onConnected}.
 *     </ul>
 *   <li> Send Payload:
 *     <ul>
 *       <li> Sender1A sends a Payload to occupantZone2 by calling {@link #sendPayload}. To indicate
 *            that the Payload is sent to receiver2A, Sender1A puts receiver2A's ID ("receiver2A")
 *            into the Payload.
 *       <li> ReceiverService2 is notified for the Payload via {@link
 *            AbstractReceiverService#onPayloadReceived}.
 *            In this method, ReceiverService2 can forward the Payload to client2's receiver
 *            endpoints (if any), or cache the Payload and forward it later once a new receiver
 *            endpoint is registered.
 *     </ul>
 *   <li> Register receiver:
 *     <ul>
 *       <li> Receiver2A calls {@link #registerReceiver} with ID "receiver2A". Then
 *            ReceiverService2 is notified via {@link AbstractReceiverService#onReceiverRegistered}.
 *            In that method, ReceiverService2 parses the Payload and finds that the Payload should
 *            be sent to the endpoint with ID "receiver2A", then invokes {@link
 *            AbstractReceiverService#forwardPayload} to forward the cached Payload to receiver2A.
 *            <p>
 *            Note: this step can be done before "Establish connection". In this case,
 *            ReceiverService2 will be started and bound by car service early.
 *            Once sender1A sends a Payload to occupantZone2, ReceiverService2 will be notified
 *            via {@link AbstractReceiverService#onReceiverRegistered}. In that method,
 *            ReceiverService2 can forward the Payload to Receiver2A without caching.
 *       <li> Receiver2A is notified for the Payload via {@link PayloadCallback#onPayloadReceived}.
 *     </ul>
 *   <li> Terminate the connection:
 *   <ul>
 *     <li> Sender1A terminates the connection to occupantZone2:
 *          Once sender1A no longer needs to send Payload to occupantZone2, it terminates the
 *          connection by calling {@link #disconnect}. Then sender1A is notified via
 *          {@link ConnectionRequestCallback#onDisconnected}, and ReceiverService2 is notified via
 *          {@link AbstractReceiverService#onDisconnected}.
 *     <li> Unregister receiver2A:
 *          Once receiver2A no longer needs to receive Payload from any other occupant zones,
 *          it calls {@link #unregisterReceiver}.
 *    <li> Unbound and destroy ReceiverService2:
 *         Since all the senders have disconnected from occupantZone2 and there is no receiver
 *         registered in occupantZone2, ReceiverService2 will be unbound and destroyed
 *         automatically.
 *   </ul>
 *   <li> Sender1A stops monitoring other occupant zones by calling {@link
 *        android.car.CarRemoteDeviceManager#unregisterStateCallback}. This step can
 *        be done before or after "Terminate the connection".
 * </ul>
 * <p>
 * For a given {@link android.car.Car} instance, the CarOccupantConnectionManager is a singleton.
 * However, the client app may create multiple {@link android.car.Car} instances thus create
 * multiple CarOccupantConnectionManager instances. These CarOccupantConnectionManager instances
 * are treated as the same instance for the client app. For example:
 * <ul>
 *   <li> Sender1A creates a CarOccupantConnectionManager instance (managerA), while sender1B
 *        creates a different CarOccupantConnectionManager instance (managerB). Then sender1A uses
 *        managerA to request a connection to occupantZone2. Once connected, sender1B can use
 *        managerB to send Payload to occupantZone2 without requesting a new connection.
 *        To know whether it is connected to occupantZone2, sender1B can call {@link #isConnected}.
 *   <li> Besides, sender1B can terminate the connection by calling managerB#disconnect(), despite
 *        that the connection was requested by sender1A. Once the connection is terminated, sender1A
 *        will be notified via {@link ConnectionRequestCallback#onDisconnected}, and sender1B will
 *        not be notified since it didn't register register the {@link ConnectionRequestCallback}.
 * </ul>
 *
 * @hide
 */
@SystemApi
public final class CarOccupantConnectionManager extends CarManagerBase {

    private static final String TAG = CarOccupantConnectionManager.class.getSimpleName();

    /** The connection request has no error. */
    public static final int CONNECTION_ERROR_NONE = 0;

    /** The connection request failed because of an error of unidentified cause. */
    public static final int CONNECTION_ERROR_UNKNOWN = 1;

    /**
     * The connection request failed because the peer occupant zone was not ready for connection.
     * To avoid this error, the caller endpoint should ensure that the state of the peer occupant
     * zone is {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_CONNECTION_READY} before
     * requesting a connection to it.
     */
    public static final int CONNECTION_ERROR_NOT_READY = 2;

    /**
     * The connection request failed because the peer app was not installed. To avoid this error,
     * the caller endpoint should ensure that the state of the peer app is {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_INSTALLED} before requesting a connection to
     * it.
     */
    public static final int CONNECTION_ERROR_PEER_APP_NOT_INSTALLED = 3;

    /**
     * The connection request failed because its long version code ({@link
     * PackageInfo#getLongVersionCode}) didn't match the peer app's long version code.
     */
    public static final int CONNECTION_ERROR_LONG_VERSION_NOT_MATCH = 4;

    /**
     * The connection request failed because its signing info ({@link PackageInfo#signingInfo}
     * didn't match the peer app's signing info.
     */
    public static final int CONNECTION_ERROR_SIGNATURE_NOT_MATCH = 5;

    /** The connection request failed because the user rejected it. */
    public static final int CONNECTION_ERROR_USER_REJECTED = 6;

    /**
     * The maximum value of predefined connection error code. If the client app wants to pass a
     * custom value in {@link AbstractReceiverService#rejectConnection}, the custom value must be
     * larger than this value, otherwise the sender client might get the wrong connection error code
     * when its connection request fails.
     */
    public static final int CONNECTION_ERROR_PREDEFINED_MAXIMUM_VALUE = 10000;

    /**
     * Flags for the error type of connection request.
     *
     * @hide
     */
    @IntDef(flag = false, prefix = {"CONNECTION_ERROR_"}, value = {
            CONNECTION_ERROR_NONE,
            CONNECTION_ERROR_UNKNOWN,
            CONNECTION_ERROR_NOT_READY,
            CONNECTION_ERROR_PEER_APP_NOT_INSTALLED,
            CONNECTION_ERROR_LONG_VERSION_NOT_MATCH,
            CONNECTION_ERROR_SIGNATURE_NOT_MATCH,
            CONNECTION_ERROR_USER_REJECTED,
            CONNECTION_ERROR_PREDEFINED_MAXIMUM_VALUE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionError {
    }

    /**
     * A callback for lifecycle events of a connection request. When the endpoint (sender) calls
     * {@link #requestConnection} to connect to its peer client, it will be notified for the events.
     * The sender may call {@link #cancelConnection} if none of the events are triggered for a
     * long time.
     */
    public interface ConnectionRequestCallback {
        /**
         * Invoked when the one-way connection has been established.
         * <p>
         * In order to establish the connection, the receiver {@link AbstractReceiverService}
         * must accept the connection, and the sender must not cancel the request before the
         * connection is established.
         * Once the connection is established, the sender can send {@link Payload} to the
         * receiver client.
         */
        void onConnected(@NonNull OccupantZoneInfo receiverZone);

        /**
         * Invoked when there was an error when establishing the connection. For example, the
         * receiver client is not ready for connection, or the receiver client rejected the
         * connection request.
         *
         * @param connectionError could be any value of {@link ConnectionError}, or an app-defined
         *                        value
         */
        void onFailed(@NonNull OccupantZoneInfo receiverZone, int connectionError);

        /**
         * Invoked when the connection is terminated. For example, the receiver {@link
         * AbstractReceiverService} is unbound and destroyed, is crashed, or the receiver client
         * has become unreachable.
         * <p>
         * Once disconnected, the sender can no longer send {@link Payload} to the receiver
         * client.
         */
        void onDisconnected(@NonNull OccupantZoneInfo receiverZone);
    }

    /** A callback to receive a {@link Payload}. */
    public interface PayloadCallback {
        /**
         * Invoked when the receiver endpoint has received a {@link Payload} from {@code
         * senderZone}.
         */
        void onPayloadReceived(@NonNull OccupantZoneInfo senderZone,
                @NonNull Payload payload);
    }

    /** An exception to indicate that it failed to send the {@link Payload}. */
    public static final class PayloadTransferException extends Exception {
    }

    private final ICarOccupantConnection mService;

    private final Object mLock = new Object();

    private final String mPackageName;

    /**
     * A map of connection requests. The key is the zone ID of the receiver occupant zone, and
     * the value is the callback and associated executor.
     */
    @GuardedBy("mLock")
    private final SparseArray<Pair<ConnectionRequestCallback, Executor>>
            mConnectionRequestMap = new SparseArray<>();

    private final IConnectionRequestCallback mBinderConnectionRequestCallback =
            new IConnectionRequestCallback.Stub() {
                @Override
                public void onConnected(OccupantZoneInfo receiverZone) {
                    synchronized (mLock) {
                        Pair<ConnectionRequestCallback, Executor> pair =
                                mConnectionRequestMap.get(receiverZone.zoneId);
                        if (pair == null) {
                            Slog.e(TAG, "onConnected: no pending connection request");
                            return;
                        }
                        // Notify the sender of success.
                        ConnectionRequestCallback callback = pair.first;
                        Executor executor = pair.second;
                        long token = Binder.clearCallingIdentity();
                        try {
                            executor.execute(() -> callback.onConnected(receiverZone));
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }

                        // Unlike other onFoo() methods, we shouldn't remove the callback here
                        // because we need to invoke it once it is disconnected.
                    }
                }

                @Override
                public void onFailed(OccupantZoneInfo receiverZone, int connectionError) {
                    synchronized (mLock) {
                        Pair<ConnectionRequestCallback, Executor> pair =
                                mConnectionRequestMap.get(receiverZone.zoneId);
                        if (pair == null) {
                            Slog.e(TAG, "onFailed: no pending connection request");
                            return;
                        }
                        // Notify the sender of failure.
                        ConnectionRequestCallback callback = pair.first;
                        Executor executor = pair.second;
                        long token = Binder.clearCallingIdentity();
                        try {
                            executor.execute(
                                    () -> callback.onFailed(receiverZone, connectionError));
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }

                        mConnectionRequestMap.remove(receiverZone.zoneId);
                    }
                }

                @Override
                public void onDisconnected(OccupantZoneInfo receiverZone) {
                    synchronized (mLock) {
                        Pair<ConnectionRequestCallback, Executor> pair =
                                mConnectionRequestMap.get(receiverZone.zoneId);
                        if (pair == null) {
                            Slog.e(TAG, "onDisconnected: no pending connection request");
                            return;
                        }
                        // Notify the sender of disconnection.
                        ConnectionRequestCallback callback = pair.first;
                        Executor executor = pair.second;
                        long token = Binder.clearCallingIdentity();
                        try {
                            executor.execute(() -> callback.onDisconnected(receiverZone));
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }

                        mConnectionRequestMap.remove(receiverZone.zoneId);
                    }
                }
            };

    /**
     * A map of registered receivers. The key is the endpointId of the receiver, the value is
     * the associated callback and the Executor of the callback.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, Pair<PayloadCallback, Executor>> mReceiverPayloadCallbackMap =
            new ArrayMap<>();

    private final IPayloadCallback mBinderPayloadCallback = new IPayloadCallback.Stub() {
        @Override
        public void onPayloadReceived(OccupantZoneInfo senderZone, String receiverEndpointId,
                Payload payload) {
            Pair<PayloadCallback, Executor> pair;
            synchronized (mLock) {
                pair = mReceiverPayloadCallbackMap.get(receiverEndpointId);
                if (pair == null) {
                    // This should never happen, but let's be cautious.
                    Slog.e(TAG, "Couldn't find receiver " + receiverEndpointId);
                    return;
                }
            }
            PayloadCallback callback = pair.first;
            Executor executor = pair.second;
            long token = Binder.clearCallingIdentity();
            try {
                executor.execute(() -> callback.onPayloadReceived(senderZone, payload));
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    /** @hide */
    public CarOccupantConnectionManager(Car car, IBinder service) {
        super(car);
        mService = ICarOccupantConnection.Stub.asInterface(service);
        mPackageName = mCar.getContext().getPackageName();
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (mLock) {
            mConnectionRequestMap.clear();
            mReceiverPayloadCallbackMap.clear();
        }
    }

    /**
     * Registers a {@link PayloadCallback} to receive {@link Payload}. If the {@link
     * AbstractReceiverService} in the caller app was not started yet, it will be started and
     * bound by car service automatically.
     * <p>
     * The caller endpoint must call {@link #unregisterReceiver} before it is destroyed.
     *
     * @param receiverEndpointId the ID of this receiver endpoint. Since there might be multiple
     *                           receiver endpoints in the client app, the ID can be used by the
     *                           client app ({@link AbstractReceiverService}) to decide which
     *                           endpoint(s) to dispatch the Payload to. The client app can use any
     *                           String as the ID, as long as it is unique among the client app.
     * @param executor           the Executor to run the callback
     * @param callback           the callback notified when this endpoint receives a Payload
     * @throws IllegalStateException if the {@code receiverEndpointId} had a {@link PayloadCallback}
     *                               registered
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void registerReceiver(@NonNull String receiverEndpointId,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull PayloadCallback callback) {
        Objects.requireNonNull(receiverEndpointId, "receiverEndpointId cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (mLock) {
            try {
                mService.registerReceiver(mPackageName, receiverEndpointId, mBinderPayloadCallback);
                // Save the callback only after the remote call succeeded.
                mReceiverPayloadCallbackMap.put(receiverEndpointId, new Pair<>(callback, executor));
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to register receiver: " + receiverEndpointId);
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Unregisters the existing {@link PayloadCallback} for {@code receiverEndpointId}.
     * <p>
     * This method can be called after calling {@link #registerReceiver} once the receiver
     * endpoint no longer needs to receive Payload, or becomes inactive.
     * This method must be called before the receiver endpoint is destroyed. Failing to call this
     * method might cause the AbstractReceiverService to persist.
     *
     * @throws IllegalStateException if the {@code receiverEndpointId} had no {@link
     *                               PayloadCallback} registered
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void unregisterReceiver(@NonNull String receiverEndpointId) {
        Objects.requireNonNull(receiverEndpointId, "receiverEndpointId cannot be null");
        synchronized (mLock) {
            try {
                mService.unregisterReceiver(mPackageName, receiverEndpointId);
                // Remove the callback after the remote call succeeded.
                mReceiverPayloadCallbackMap.remove(receiverEndpointId);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to unregister receiver: " + receiverEndpointId);
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Sends a request to connect to the receiver client in {@code receiverZone}. The {@link
     * AbstractReceiverService} in the receiver client will be started and bound automatically if it
     * was not started yet.
     * <p>
     * This method should only be called when the state of the {@code receiverZone} contains
     * {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_CONNECTION_READY} (and
     * {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED} and {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND} if UI is needed to
     * establish the connection). Otherwise, errors may occur.
     * <p>
     * For security, it is highly recommended that the sender not request a connection to the
     * receiver client if the state of the receiver client doesn't contain
     * {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_SAME_LONG_VERSION} or
     * {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_SAME_SIGNATURE}. If the sender still
     * wants to request the connection in the case above, it should call
     * {@link android.car.CarRemoteDeviceManager#getEndpointPackageInfo} to get the receiver's
     * {@link android.content.pm.PackageInfo} and check if it's valid before requesting the
     * connection.
     * <p>
     * The caller may call {@link #cancelConnection} to cancel the request.
     * <p>
     * The connection is one-way. In other words, the receiver can't send {@link Payload} to the
     * sender. If the receiver wants to send {@link Payload}, it must call this method to become
     * a sender.
     * <p>
     * The caller must not request another connection to the same {@code receiverZone} if there
     * is an established connection or pending connection (a connection request that has not been
     * responded yet) to {@code receiverZone}.
     * The caller must call {@link #disconnect} before it is destroyed.
     *
     * @param receiverZone the occupant zone to connect to
     * @param executor     the Executor to run the callback
     * @param callback     the callback notified for the request result
     * @throws IllegalStateException if there is an established connection or pending connection to
     *                               {@code receiverZone}
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void requestConnection(@NonNull OccupantZoneInfo receiverZone,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ConnectionRequestCallback callback) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (mLock) {
            Preconditions.checkState(!mConnectionRequestMap.contains(receiverZone.zoneId),
                    "Already requested a connection to " + receiverZone);
            try {
                mService.requestConnection(mPackageName, receiverZone,
                        mBinderConnectionRequestCallback);
                mConnectionRequestMap.put(receiverZone.zoneId, new Pair<>(callback, executor));
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to request connection");
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Cancels the pending connection request to the peer client in {@code receiverZone}.
     * <p>
     * The caller endpoint may call this method when it has requested a connection, but hasn't
     * received any response for a long time, or the user wants to cancel the request explicitly.
     * In other words, this method should be called after {@link #requestConnection}, and before
     * any events in the {@link ConnectionRequestCallback} is triggered.
     *
     * @throws IllegalStateException if this {@link CarOccupantConnectionManager} has no pending
     *                               connection request to {@code receiverZone}
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void cancelConnection(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        synchronized (mLock) {
            Preconditions.checkState(mConnectionRequestMap.contains(receiverZone.zoneId),
                    "This manager instance has no connection request to " + receiverZone);
            try {
                mService.cancelConnection(mPackageName, receiverZone);
                mConnectionRequestMap.remove(receiverZone.zoneId);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to cancel connection");
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Sends the {@code payload} to the peer client in {@code receiverZone}.
     * <p>
     * Different sender endpoints in the same client app are treated as the same sender. If the
     * sender endpoints need to differentiate themselves, they can put the identity info into the
     * payload.
     *
     * @throws IllegalStateException    if it was not connected to the peer client in
     *                                  {@code receiverZone}
     * @throws PayloadTransferException if the payload was not sent. For example, this method is
     *                                  called when the connection is not established or has been
     *                                  terminated, or an internal error occurred.
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void sendPayload(@NonNull OccupantZoneInfo receiverZone, @NonNull Payload payload)
            throws PayloadTransferException {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        try {
            mService.sendPayload(mPackageName, receiverZone, payload);
        } catch (IllegalStateException e) {
            Slog.e(TAG, "Failed to send Payload to " + receiverZone);
            throw new PayloadTransferException();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to send Payload to " + receiverZone);
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Disconnects from the peer client in {@code receiverZone}.
     * <p>
     * This method can be called as soon as the caller app no longer needs to send {@link Payload}
     * to {@code receiverZone}. If there are multiple sender endpoints in the client app reuse the
     * same connection, this method should be called when all sender endpoints no longer need to
     * send Payload to {@code receiverZone}.
     * <p>
     * This method must be called before the caller is destroyed. Failing to call this method might
     * cause the {@link AbstractReceiverService} in the peer client to persist.
     *
     * @throws IllegalStateException if it was not connected to the peer client in
     *                               {@code receiverZone}
     */
    @SuppressWarnings("[NotCloseable]")
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void disconnect(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        try {
            mService.disconnect(mPackageName, receiverZone);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to disconnect");
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns whether it is connected to its peer client in {@code receiverZone}. When it is
     * connected, it can send {@link Payload} to the peer client.
     * <p>
     * Note: the connection is one-way. The peer client can not send {@link Payload} to this client
     * unless the peer client is also connected to this client.
     */
    @SuppressWarnings("[NotCloseable]")
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public boolean isConnected(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        try {
            return mService.isConnected(mPackageName, receiverZone);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get connection state");
            return handleRemoteExceptionFromCarService(e, false);
        }
    }
}

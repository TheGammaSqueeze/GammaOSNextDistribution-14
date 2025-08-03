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

import static android.car.Car.CAR_INTENT_ACTION_RECEIVER_SERVICE;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_LONG_VERSION_NOT_MATCH;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_SIGNATURE_NOT_MATCH;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.app.Service;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.builtin.util.Slogf;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.car.internal.util.BinderKeyValueContainer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Set;

/**
 * A service used to respond to connection requests from peer clients on other occupant zones,
 * receive {@link Payload} from peer clients, cache the received Payload, and dispatch it to the
 * receiver endpoints in this client.
 * <p>
 * The client app must extend this service to receive Payload from peer clients. When declaring
 * this service in the manifest file, the client must add an intent filter with action
 * {@value android.car.Car#CAR_INTENT_ACTION_RECEIVER_SERVICE} for this service, and require
 * {@code android.car.occupantconnection.permission.BIND_RECEIVER_SERVICE}. For example:
 * <pre>{@code
 * <service android:name=".MyReceiverService"
 *          android:permission="android.car.occupantconnection.permission.BIND_RECEIVER_SERVICE"
 *          android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.car.intent.action.RECEIVER_SERVICE" />
 *     </intent-filter>
 * </service>}
 * </pre>
 * <p>
 * This service runs on the main thread of the client app, and is a singleton for the client app.
 * The lifecycle of this service is managed by car service ({@link
 * com.android.car.occupantconnection.CarOccupantConnectionService}).
 * <p>
 * This service can be bound by car service in two ways:
 * <ul>
 *   <li> A sender endpoint in the peer client calls {@link
 *        CarOccupantConnectionManager#requestConnection} to connect to this client.
 *   <li> A receiver endpoint in this client calls {@link
 *        CarOccupantConnectionManager#registerReceiver}.
 * </ul>
 * <p>
 * Once all the senders have disconnected from this client and there is no receiver endpoints
 * registered in this client, this service will be unbound by car service automatically.
 * <p>
 * When this service is crashed, all connections to this client will be terminated. As a result,
 * all senders that were connected to this client will be notified via {@link
 * CarOccupantConnectionManager.ConnectionRequestCallback#onDisconnected}. In addition, the cached
 * Payload will be lost, if any. The senders are responsible for resending the Payload if needed.
 *
 * @hide
 */
@SystemApi
public abstract class AbstractReceiverService extends Service {

    private static final String TAG = AbstractReceiverService.class.getSimpleName();
    private static final String INDENTATION_2 = "  ";
    private static final String INDENTATION_4 = "    ";

    /**
     * A map of receiver endpoints in this client. The key is the ID of the endpoint, the value is
     * the associated payload callback.
     * <p>
     * Although it is unusual, the process that registered the payload callback (process1) might be
     * different from the process that this service is running (process2). When process1 is dead,
     * if this service invokes the dead callback, a DeadObjectException will be thrown.
     * To avoid that, the callbacks are stored in this BinderKeyValueContainer so that dead
     * callbacks can be removed automatically.
     */
    private final BinderKeyValueContainer<String, IPayloadCallback> mReceiverEndpointMap =
            new BinderKeyValueContainer<>();

    private IBackendConnectionResponder mBackendConnectionResponder;
    private long mMyVersionCode;

    private final IBackendReceiver.Stub mBackendReceiver = new IBackendReceiver.Stub() {
        @Override
        public void registerReceiver(String receiverEndpointId, IPayloadCallback callback) {
            mReceiverEndpointMap.put(receiverEndpointId, callback);
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onReceiverRegistered(receiverEndpointId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void unregisterReceiver(String receiverEndpointId) {
            mReceiverEndpointMap.remove(receiverEndpointId);
        }

        @Override
        public void registerBackendConnectionResponder(IBackendConnectionResponder responder) {
            mBackendConnectionResponder = responder;
        }

        @Override
        public void onPayloadReceived(OccupantZoneInfo senderZone, Payload payload) {
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onPayloadReceived(senderZone, payload);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onConnectionInitiated(OccupantZoneInfo senderZone, long senderVersion,
                SigningInfo senderSigningInfo) {
            if (!isSenderCompatible(senderVersion)) {
                Slogf.w(TAG, "Reject the connection request from %s because its long version"
                                + " code %d doesn't match the receiver's %d ", senderZone,
                        senderVersion, mMyVersionCode);
                AbstractReceiverService.this.rejectConnection(senderZone,
                        CONNECTION_ERROR_LONG_VERSION_NOT_MATCH);
                return;
            }
            if (!isSenderAuthorized(senderSigningInfo)) {
                Slogf.w(TAG, "Reject the connection request from %s because its SigningInfo"
                        + " doesn't match", senderZone);
                AbstractReceiverService.this.rejectConnection(senderZone,
                        CONNECTION_ERROR_SIGNATURE_NOT_MATCH);
                return;
            }
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onConnectionInitiated(senderZone);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onConnected(OccupantZoneInfo senderZone) {
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onConnected(senderZone);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onConnectionCanceled(OccupantZoneInfo senderZone) {
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onConnectionCanceled(senderZone);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onDisconnected(OccupantZoneInfo senderZone) {
            long token = Binder.clearCallingIdentity();
            try {
                AbstractReceiverService.this.onDisconnected(senderZone);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo myInfo = getPackageManager().getPackageInfo(getPackageName(),
                    GET_SIGNING_CERTIFICATES);
            mMyVersionCode = myInfo.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Couldn't find the PackageInfo of " + getPackageName(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * To prevent the client app overriding this method improperly, this method is {@code final}.
     * If the client app needs to bind to this service, it should override {@link
     * #onLocalServiceBind}.
     */
    @Nullable
    @Override
    public final IBinder onBind(@NonNull Intent intent) {
        if (CAR_INTENT_ACTION_RECEIVER_SERVICE.equals(intent.getAction())) {
            return mBackendReceiver.asBinder();
        }
        return onLocalServiceBind(intent);
    }

    /**
     * Returns the communication channel to this service. If the client app needs to bind to this
     * service and get a communication channel to this service, it should override this method
     * instead of {@link #onBind}.
     */
    @Nullable
    public IBinder onLocalServiceBind(@NonNull Intent intent) {
        return null;
    }

    /**
     * Invoked when this service has received {@code payload} from its peer client on
     * {@code senderZone}.
     * <p>
     * The inheritance of this service should override this method to
     * <ul>
     *   <li> forward the {@code payload} to the corresponding receiver endpoint(s), if any, and/or
     *   <li> cache the {@code payload}, then dispatch it when a new receiver endpoint is
     *        registered. The inheritance should clear the cache once it is no longer needed.
     * </ul>
     */
    public abstract void onPayloadReceived(@NonNull OccupantZoneInfo senderZone,
            @NonNull Payload payload);

    /**
     * Invoked when a receiver endpoint is registered.
     * <p>
     * The inheritance of this service can override this method to forward the cached Payload
     * (if any) to the newly registered endpoint. The inheritance of this service doesn't need to
     * override this method if it never caches the Payload.
     *
     * @param receiverEndpointId the ID of the newly registered endpoint
     */
    public void onReceiverRegistered(@NonNull String receiverEndpointId) {
    }

    /**
     * Returns whether the long version code ({@link PackageInfo#getLongVersionCode}) of the sender
     * app is compatible with the receiver app's. If it doesn't match, this service will reject the
     * connection request from the sender.
     * <p>
     * The default implementation checks whether the version codes are identical. This is fine if
     * all the peer clients run on the same Android instance, since PackageManager doesn't allow to
     * install two different apps with the same package name - even for different users.
     * However, if the peer clients run on different Android instances, and the app wants to support
     * connection between them even if they have different versions, the app will need to override
     * this method.
     */
    @SuppressLint("OnNameExpected")
    public boolean isSenderCompatible(long senderVersion) {
        return mMyVersionCode == senderVersion;
    }

    /**
     * Returns whether the signing info ({@link PackageInfo#signingInfo} of the sender app is
     * authorized. If it is not authorized, this service will reject the connection request from
     * the sender.
     * <p>
     * The default implementation simply returns {@code true}. This is fine if all the peer clients
     * run on the same Android instance, since PackageManager doesn't allow to install two different
     * apps with the same package name - even for different users.
     * However, if the peer clients run on different Android instances, the app must override this
     * method for security.
     */
    @SuppressLint("OnNameExpected")
    public boolean isSenderAuthorized(@NonNull SigningInfo senderSigningInfo) {
        return true;
    }

    /**
     * Invoked when the sender client in {@code senderZone} has requested a connection to this
     * client.
     * <p>
     * If user confirmation is needed to establish the connection, the inheritance can override
     * this method to launch a permission activity, and call {@link #acceptConnection} or
     * {@link #rejectConnection} based on the result. For driving safety, the permission activity
     * must be distraction optimized. Alternatively, the permission can be granted during device
     * setup.
     */
    public abstract void onConnectionInitiated(@NonNull OccupantZoneInfo senderZone);

    /**
     * Invoked when the one-way connection has been established.
     * <p>
     * In order to establish the connection, the inheritance of this service must call
     * {@link #acceptConnection}, and the sender must NOT call {@link
     * CarOccupantConnectionManager#cancelConnection} before the connection is established.
     * <p>
     * Once the connection is established, the sender can send {@link Payload} to this client.
     */
    public void onConnected(@NonNull OccupantZoneInfo senderZone) {
    }

    /**
     * Invoked when the sender has canceled the pending connection request, or has become
     * unreachable after sending the connection request.
     */
    public void onConnectionCanceled(@NonNull OccupantZoneInfo senderZone) {
    }

    /**
     * Invoked when the connection is terminated. For example, the sender on {@code senderZone}
     * has called {@link CarOccupantConnectionManager#disconnect}, or the sender has become
     * unreachable.
     * <p>
     * When disconnected, the sender can no longer send {@link Payload} to this client.
     */
    public void onDisconnected(@NonNull OccupantZoneInfo senderZone) {
    }

    /** Accepts the connection request from {@code senderZone}. */
    public final void acceptConnection(@NonNull OccupantZoneInfo senderZone) {
        try {
            mBackendConnectionResponder.acceptConnection(senderZone);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Rejects the connection request from {@code senderZone}.
     *
     * @param rejectionReason the reason for rejection. It could be a predefined value (
     *        {@link CarOccupantConnectionManager#CONNECTION_ERROR_LONG_VERSION_NOT_MATCH},
     *        {@link CarOccupantConnectionManager#CONNECTION_ERROR_SIGNATURE_NOT_MATCH},
     *        {@link CarOccupantConnectionManager#CONNECTION_ERROR_USER_REJECTED}), or app-defined
     *        value that is larger than {@link
     *        CarOccupantConnectionManager#CONNECTION_ERROR_PREDEFINED_MAXIMUM_VALUE}.
     */
    public final void rejectConnection(@NonNull OccupantZoneInfo senderZone, int rejectionReason) {
        try {
            mBackendConnectionResponder.rejectConnection(senderZone, rejectionReason);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Forwards the {@code payload} to the given receiver endpoint in this client.
     * <p>
     * Note: different receiver endpoints in the same client app are identified by their IDs,
     * while different sender endpoints in the same client app are treated as the same sender.
     * If the senders need to differentiate themselves, they can put the identity info into the
     * {@code payload} it sends.
     *
     * @param senderZone         the occupant zone that the Payload was sent from
     * @param receiverEndpointId the ID of the receiver endpoint
     * @param payload            the Payload
     * @return whether the Payload has been forwarded to the receiver endpoint
     */
    public final boolean forwardPayload(@NonNull OccupantZoneInfo senderZone,
            @NonNull String receiverEndpointId,
            @NonNull Payload payload) {
        IPayloadCallback callback = mReceiverEndpointMap.get(receiverEndpointId);
        if (callback == null) {
            Slogf.e(TAG, "The receiver endpoint has been unregistered: %s", receiverEndpointId);
            return false;
        }
        try {
            callback.onPayloadReceived(senderZone, receiverEndpointId, payload);
            return true;
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Returns an unmodifiable set containing all the IDs of the receiver endpoints. Returns an
     * empty set if there is no receiver endpoint registered.
     */
    @NonNull
    public final Set<String> getAllReceiverEndpoints() {
        return mReceiverEndpointMap.keySet();
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void dump(@Nullable FileDescriptor fd, @NonNull PrintWriter writer,
            @Nullable String[] args) {
        writer.println("*AbstractReceiverService*");
        writer.printf("%smReceiverEndpointMap:\n", INDENTATION_2);
        for (int i = 0; i < mReceiverEndpointMap.size(); i++) {
            String id = mReceiverEndpointMap.keyAt(i);
            IPayloadCallback callback = mReceiverEndpointMap.valueAt(i);
            writer.printf("%s%s, callback:%s\n", INDENTATION_4, id, callback);
        }
        writer.printf("%smBackendConnectionResponder:%s\n", INDENTATION_2,
                mBackendConnectionResponder);
        writer.printf("%smBackendReceiver:%s\n", INDENTATION_2, mBackendReceiver);
    }
}

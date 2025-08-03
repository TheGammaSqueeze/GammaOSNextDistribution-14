/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.remoteauth;

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.util.Preconditions;
import com.android.server.remoteauth.connectivity.Connection;
import com.android.server.remoteauth.connectivity.ConnectionException;
import com.android.server.remoteauth.connectivity.ConnectionInfo;
import com.android.server.remoteauth.connectivity.ConnectivityManager;
import com.android.server.remoteauth.connectivity.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of remote devices {@link ConnectionInfo} and {@link Connection}.
 *
 * <p>Allows mapping between {@link ConnectionInfo#getConnectionId()} to {@link ConnectionInfo} and
 * {@link Connection}
 */
public class RemoteAuthConnectionCache {
    public static final String TAG = "RemoteAuthConCache";
    private final Map<Integer, ConnectionInfo> mConnectionInfoMap = new ConcurrentHashMap<>();
    private final Map<Integer, Connection> mConnectionMap = new ConcurrentHashMap<>();

    private final ConnectivityManager mConnectivityManager;

    public RemoteAuthConnectionCache(@NonNull ConnectivityManager connectivityManager) {
        Preconditions.checkNotNull(connectivityManager);
        this.mConnectivityManager = connectivityManager;
    }

    /** Returns the {@link ConnectivityManager}. */
    ConnectivityManager getConnectivityManager() {
        return mConnectivityManager;
    }

    /**
     * Associates the connectionId with {@link ConnectionInfo}. Updates association with new value
     * if already exists
     *
     * @param connectionInfo of the remote device
     */
    public void setConnectionInfo(@NonNull ConnectionInfo connectionInfo) {
        Preconditions.checkNotNull(connectionInfo);
        mConnectionInfoMap.put(connectionInfo.getConnectionId(), connectionInfo);
    }

    /** Returns {@link ConnectionInfo} associated with connectionId. */
    public ConnectionInfo getConnectionInfo(int connectionId) {
        return mConnectionInfoMap.get(connectionId);
    }

    /**
     * Associates the connectionId with {@link Connection}. Updates association with new value if
     * already exists
     *
     * @param connection to the remote device
     */
    public void setConnection(@NonNull Connection connection) {
        Preconditions.checkNotNull(connection);
        mConnectionMap.put(connection.getConnectionInfo().getConnectionId(), connection);
    }

    /**
     * Returns {@link Connection} associated with connectionId. Uses {@link ConnectivityManager} to
     * create and associate with new {@link Connection}, if mapping doesn't exist
     *
     * @param connectionId of the remote device
     */
    public Connection getConnection(int connectionId) {
        return mConnectionMap.computeIfAbsent(
                connectionId,
                id -> {
                    ConnectionInfo connectionInfo = getConnectionInfo(id);
                    if (null == connectionInfo) {
                        // TODO: Try accessing DB to fetch by connectionId
                        Log.e(TAG, String.format("Unknown connectionId: %d", connectionId));
                        return null;
                    }
                    try {
                        Connection connection =
                                mConnectivityManager.connect(
                                        connectionInfo,
                                        new EventListener() {
                                            @Override
                                            public void onDisconnect(
                                                    @NonNull ConnectionInfo connectionInfo) {
                                                removeConnection(connectionInfo.getConnectionId());
                                                Log.i(
                                                        TAG,
                                                        String.format(
                                                                "Disconnected from: %d",
                                                                connectionInfo.getConnectionId()));
                                            }
                                        });
                        if (null == connection) {
                            Log.e(TAG, String.format("Failed to connect: %d", connectionId));
                            return null;
                        }
                        return connection;
                    } catch (ConnectionException e) {
                        Log.e(
                                TAG,
                                String.format("Failed to create connection to %d.", connectionId),
                                e);
                        return null;
                    }
                });
    }

    /**
     * Removes {@link Connection} from cache.
     *
     * @param connectionId of the remote device
     */
    public void removeConnection(int connectionId) {
        if (null != mConnectionMap.remove(connectionId)) {
            Log.i(
                    TAG,
                    String.format("Connection associated with id: %d was removed", connectionId));
        }
    }
}

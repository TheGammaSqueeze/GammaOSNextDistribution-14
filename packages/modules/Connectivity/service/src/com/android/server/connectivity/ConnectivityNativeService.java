/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.connectivity.aidl.ConnectivityNative;
import android.os.Binder;
import android.os.Process;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.BpfBitmap;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.PermissionUtils;

import java.util.ArrayList;

/**
 * @hide
 */
public class ConnectivityNativeService extends ConnectivityNative.Stub {
    public static final String SERVICE_NAME = "connectivity_native";

    private static final String TAG = ConnectivityNativeService.class.getSimpleName();

    private static final String BLOCKED_PORTS_MAP_PATH =
            "/sys/fs/bpf/net_shared/map_block_blocked_ports_map";

    private final Context mContext;

    // BPF map for port blocking. Exactly 65536 entries long, with one entry per port number
    @Nullable
    private final BpfBitmap mBpfBlockedPortsMap;

    /**
     * Dependencies of ConnectivityNativeService, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** Get BPF maps. */
        @Nullable public BpfBitmap getBlockPortsMap() {
            try {
                return new BpfBitmap(BLOCKED_PORTS_MAP_PATH);
            } catch (ErrnoException e) {
                throw new UnsupportedOperationException("Failed to create blocked ports map: "
                        + e);
            }
        }
    }

    private void enforceBlockPortPermission() {
        final int uid = Binder.getCallingUid();
        if (uid == Process.ROOT_UID || uid == Process.PHONE_UID) return;
        PermissionUtils.enforceNetworkStackPermission(mContext);
    }

    private void ensureValidPortNumber(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port number " + port);
        }
    }

    public ConnectivityNativeService(final Context context) {
        this(context, new Dependencies());
    }

    @VisibleForTesting
    protected ConnectivityNativeService(final Context context, @NonNull Dependencies deps) {
        mContext = context;
        mBpfBlockedPortsMap = deps.getBlockPortsMap();
    }

    @Override
    public void blockPortForBind(int port) {
        enforceBlockPortPermission();
        ensureValidPortNumber(port);
        try {
            mBpfBlockedPortsMap.set(port);
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno, e.getMessage());
        }
    }

    @Override
    public void unblockPortForBind(int port) {
        enforceBlockPortPermission();
        ensureValidPortNumber(port);
        try {
            mBpfBlockedPortsMap.unset(port);
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno,
                    "Could not unset bitmap value for (port: " + port + "): " + e);
        }
    }

    @Override
    public void unblockAllPortsForBind() {
        enforceBlockPortPermission();
        try {
            mBpfBlockedPortsMap.clear();
        } catch (ErrnoException e) {
            throw new ServiceSpecificException(e.errno, "Could not clear map: " + e);
        }
    }

    @Override
    public int[] getPortsBlockedForBind() {
        enforceBlockPortPermission();

        ArrayList<Integer> portMap = new ArrayList<Integer>();
        for (int i = 0; i <= 65535; i++) {
            try {
                if (mBpfBlockedPortsMap.get(i)) portMap.add(i);
            } catch (ErrnoException e) {
                Log.e(TAG, "Failed to get index " + i, e);
            }
        }
        return CollectionUtils.toIntArray(portMap);
    }

    @Override
    public int getInterfaceVersion() {
        return this.VERSION;
    }

    @Override
    public String getInterfaceHash() {
        return this.HASH;
    }
}

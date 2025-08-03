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

package com.android.server.connectivity.mdns;

import android.annotation.Nullable;
import android.net.Network;

import java.util.Objects;

/**
 * A class that identifies a socket.
 *
 * <p> A socket is typically created with an associated network. However, tethering interfaces do
 * not have an associated network, only an interface index. This means that the socket cannot be
 * identified in some places. Therefore, this class is necessary for identifying a socket. It
 * includes both the network and interface index.
 */
public class SocketKey {
    @Nullable
    private final Network mNetwork;
    private final int mInterfaceIndex;

    SocketKey(int interfaceIndex) {
        this(null /* network */, interfaceIndex);
    }

    SocketKey(@Nullable Network network, int interfaceIndex) {
        mNetwork = network;
        mInterfaceIndex = interfaceIndex;
    }

    @Nullable
    public Network getNetwork() {
        return mNetwork;
    }

    public int getInterfaceIndex() {
        return mInterfaceIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNetwork, mInterfaceIndex);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof SocketKey)) {
            return false;
        }
        return Objects.equals(mNetwork, ((SocketKey) other).mNetwork)
                && mInterfaceIndex == ((SocketKey) other).mInterfaceIndex;
    }

    @Override
    public String toString() {
        return "SocketKey{ network=" + mNetwork + " interfaceIndex=" + mInterfaceIndex + " }";
    }
}

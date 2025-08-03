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

package com.android.server.connectivity.mdns;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;

/** A wrapper class of {@link NetworkInterface} to be mocked in unit tests. */
public class NetworkInterfaceWrapper {
    private final NetworkInterface networkInterface;

    public NetworkInterfaceWrapper(NetworkInterface networkInterface) {
        this.networkInterface = networkInterface;
    }

    public NetworkInterface getNetworkInterface() {
        return networkInterface;
    }

    public boolean isUp() throws SocketException {
        return networkInterface.isUp();
    }

    public boolean isLoopback() throws SocketException {
        return networkInterface.isLoopback();
    }

    public boolean isPointToPoint() throws SocketException {
        return networkInterface.isPointToPoint();
    }

    public boolean isVirtual() {
        return networkInterface.isVirtual();
    }

    public boolean supportsMulticast() throws SocketException {
        return networkInterface.supportsMulticast();
    }

    public List<InterfaceAddress> getInterfaceAddresses() {
        return networkInterface.getInterfaceAddresses();
    }

    public int getIndex() {
        return networkInterface.getIndex();
    }

    @Override
    public String toString() {
        return networkInterface.toString();
    }
}
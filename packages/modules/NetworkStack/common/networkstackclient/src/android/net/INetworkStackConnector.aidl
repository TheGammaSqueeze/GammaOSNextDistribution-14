/**
 * Copyright (c) 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing perNmissions and
 * limitations under the License.
 */
package android.net;

import android.net.IIpMemoryStoreCallbacks;
import android.net.INetworkMonitorCallbacks;
import android.net.INetworkStackStatusCallback;
import android.net.Network;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.ip.IIpClientCallbacks;

/** @hide */
oneway interface INetworkStackConnector {
    void makeDhcpServer(in String ifName, in DhcpServingParamsParcel params,
        in IDhcpServerCallbacks cb);
    void makeNetworkMonitor(in Network network, String name, in INetworkMonitorCallbacks cb);
    void makeIpClient(in String ifName, in IIpClientCallbacks callbacks);
    void fetchIpMemoryStore(in IIpMemoryStoreCallbacks cb);
    /**
     * Mark a UID as test UID, allowing it to use the TestNetworkStackService.
     *
     * TestNetworkStackService is a binder service identical to NetworkStackService, but only
     * available on userdebug builds, and only usable by the test UID. It does not require the
     * MAINLINE_NETWORK_STACK signature permission like NetworkStackService does, so it allows the
     * test UID to use other methods in this interface even though it would otherwise not have
     * permission to.
     *
     * This method must be called as root and can only be used on debuggable builds. It only affects
     * the NetworkStack until it is restarted.
     * Callers should pass in -1 to reset after use.
     *
     * @param cb Callback that will be called with a status of 0 if the call succeeds. Calls without
     *           sufficient permissions may be dropped without generating a callback.
     */
    oneway void allowTestUid(int uid, in INetworkStackStatusCallback cb);
}

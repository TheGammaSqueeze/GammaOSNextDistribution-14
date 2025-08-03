/*
 *    Copyright (c) 2023, The OpenThread Authors.
 *    All rights reserved.
 *
 *    Redistribution and use in source and binary forms, with or without
 *    modification, are permitted provided that the following conditions are met:
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *    POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server.thread.openthread;

import com.android.server.thread.openthread.BackboneRouterState;
import com.android.server.thread.openthread.Ipv6AddressInfo;
import com.android.server.thread.openthread.OtDaemonState;

/** OpenThread daemon callbacks. */
oneway interface IOtDaemonCallback {
    /**
     * Called when any of the sate in {@link OtDaemonState} has been changed or this {@link
     * IOtDaemonCallback} object is registered with {#link IOtDaemon#registerStateCallback}.
     *
     * @param newState the new OpenThread state
     * @param listenerId the listenerId passed in {#link IOtDaemon#registerStateCallback} or
     *                   -1 when this callback is invoked proactively by OT daemon
     */
    void onStateChanged(in OtDaemonState newState, long listenerId);

    /**
     * Called when Thread interface address has been changed.
     *
     * @param addressInfo the IPv6 address which has been updated. This can be both unicast and
     *                    multicast addresses
     * @param isAdded {@code true} if this address is being added to the Thread interface;
     *                Otherwise, this address is being removed
     */
    void onAddressChanged(in Ipv6AddressInfo addressInfo, boolean isAdded);

    /**
     * Called when backbone router state or multicast forwarding listening addresses has been
     * changed.
     *
     * @param bbrState the backbone router state
     */
    void onBackboneRouterStateChanged(in BackboneRouterState bbrState);

    /**
     * Called when Thread enabled state has changed. Valid values are STATE_* defined in
     * {@link ThreadNetworkController}.
     *
     * @param enabled {@code true} if Thread is enabled, {@code false} if Thread is disabled.
     */
    void onThreadEnabledChanged(in int enabled);
}

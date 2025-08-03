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

import android.os.ParcelFileDescriptor;

import com.android.server.thread.openthread.BorderRouterConfigurationParcel;
import com.android.server.thread.openthread.IChannelMasksReceiver;
import com.android.server.thread.openthread.Ipv6AddressInfo;
import com.android.server.thread.openthread.IOtStatusReceiver;
import com.android.server.thread.openthread.IOtDaemonCallback;
import com.android.server.thread.openthread.INsdPublisher;

/**
 * The OpenThread daemon service which provides access to the core Thread stack for
 * system_server.
 */
oneway interface IOtDaemon {
    /**
     * The Thread tunnel interface name. This interface MUST be created before
     * starting this {@link IOtDaemon} service.
     */
    const String TUN_IF_NAME = "thread-wpan";

    /** Thread radio is disabled. */
    const int OT_STATE_DISABLED = 0;
    /** Thread radio is enabled. */
    const int OT_STATE_ENABLED = 1;
    /** Thread radio is being disabled. */
    const int OT_STATE_DISABLING = 2;

    enum ErrorCode {
        // Converts to ThreadNetworkException#ERROR_FAILED_PRECONDITION
        OT_ERROR_FAILED_PRECONDITION = -3,
        // Converts to ThreadNetworkException#ERROR_THREAD_DISABLED
        OT_ERROR_THREAD_DISABLED = -2,
        // Converts to ThreadNetworkException#ERROR_UNSUPPORTED_CHANNEL
        // TODO: Add this error code to OpenThread and make sure `otDatasetSetActiveTlvs()` returns
        // this error code when an unsupported channel is provided
        OT_ERROR_UNSUPPORTED_CHANNEL = -1,

        // The error code below MUST be consistent with openthread/include/openthread/error.h
        // TODO: add a unit test to make sure that values are always match

        OT_ERROR_NO_BUFS = 3,
        OT_ERROR_BUSY = 5,
        OT_ERROR_PARSE = 6,
        OT_ERROR_ABORT = 11,
        OT_ERROR_INVALID_STATE = 13,
        OT_ERROR_RESPONSE_TIMEOUT = 28,
        OT_ERROR_REASSEMBLY_TIMEOUT = 30,
        OT_ERROR_REJECTED = 37,
    }

    /**
     * Initializes this service with Thread tunnel interface FD.
     *
     * @param tunFd the Thread tunnel interface FD which can be used to transmit/receive
     *              packets to/from Thread PAN
     * @param enabled the Thead enabled state from Persistent Settings
     * @param nsdPublisher the INsdPublisher which can be used for mDNS advertisement/discovery
     *                    on AIL by {@link NsdManager}
     */
    void initialize(in ParcelFileDescriptor tunFd, in boolean enabled,
                    in INsdPublisher nsdPublisher);

    /**
     * Enables/disables Thread.
     *
     * When disables Thread, it will first detach from the network without erasing the
     * active dataset, and then disable Thread radios.
     *
     * If called with same Thread enabled state as current state, the method succeeds with
     * no-op.
     *
     * @sa android.net.thread.ThreadNetworkController#setThreadEnabled
     */
    void setThreadEnabled(in boolean enabled, in IOtStatusReceiver receiver);

    /**
     * Registers a callback to receive OpenThread daemon state changes.
     *
     * @param callback invoked immediately after this method or any time a state is changed
     * @param listenerId specifies the the ID which will be sent back in callbacks of {@link
     *                   IOtDaemonCallback}
     */
    void registerStateCallback(in IOtDaemonCallback callback, long listenerId);

    /**
     * Joins this device to the network specified by {@code activeOpDatasetTlvs}.
     *
     * @sa android.net.thread.ThreadNetworkController#join
     */
    void join(in byte[] activeOpDatasetTlvs, in IOtStatusReceiver receiver);

    /**
     * Leaves from the current network.
     *
     * 1. It returns success immediately if this device has already left or disabled
     * 2. Else if there is already an onging {@code join} request, no action will be taken but
     *    the {@code receiver} will be invoked after the previous request is completed
     * 3. Otherwise, OTBR sends Address Release Notification (i.e. ADDR_REL.ntf) to grcefully
     *    detach from the current network and it takes 1 second to finish
     * 4. The Operational Dataset will be removed from persistent storage
     *
     * @sa android.net.thread.ThreadNetworkController#leave
     */
    void leave(in IOtStatusReceiver receiver);

    /** Migrates to the new network specified by {@code pendingOpDatasetTlvs}.
     *
     * @sa android.net.thread.ThreadNetworkController#scheduleMigration
     */
    void scheduleMigration(
        in byte[] pendingOpDatasetTlvs, in IOtStatusReceiver receiver);

    /**
     * Sets the country code.
     *
     * @param countryCode 2 byte country code (as defined in ISO 3166) to set.
     * @param receiver the receiver to receive result of this operation
     */
    oneway void setCountryCode(in String countryCode, in IOtStatusReceiver receiver);

    /**
     * Configures the Border Router features.
     *
     * @param brConfig the border router's configuration
     * @param receiver the status receiver
     *
     */
    oneway void configureBorderRouter(
        in BorderRouterConfigurationParcel brConfig, in IOtStatusReceiver receiver);

    /**
     * Gets the supported and preferred channel masks.
     *
     * @param receiver the receiver to receive result of this operation
     */
    void getChannelMasks(in IChannelMasksReceiver receiver);

    // TODO: add Border Router APIs
}

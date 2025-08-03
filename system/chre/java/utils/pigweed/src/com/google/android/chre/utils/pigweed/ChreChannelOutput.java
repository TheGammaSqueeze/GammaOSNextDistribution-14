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
package com.google.android.chre.utils.pigweed;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppMessage;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.pigweed.pw_rpc.Channel;
import dev.pigweed.pw_rpc.ChannelOutputException;

/**
 * Implements the Channel.Output interface of Pigweed RPC and provides a layer of abstraction on top
 * of Pigweed RPC to make it more friendly to use with CHRE APIs.
 */
public class ChreChannelOutput implements Channel.Output {
    /**
     * Message type to use for RPC messages.
     */
    public static final int CHRE_MESSAGE_TYPE_RPC = 0x7FFFFFF5;

    // 1 denotes that a host endpoint is the client that created the channel.
    private static final int CHANNEL_ID_HOST_CLIENT = (1 << 16);

    private final ContextHubClient mClient;
    private final long mNanoappId;

    // Whether this output channel is no longer able to communicate with the nanoapp.
    private AtomicBoolean mAuthDenied = new AtomicBoolean(false);

    public ChreChannelOutput(ContextHubClient client, long nanoappId) {
        mClient = client;
        mNanoappId = nanoappId;
    }

    /**
     * This method MUST NOT be called directly from users of this class.
     */
    @Override
    public void send(byte[] packet) throws ChannelOutputException {
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(mNanoappId,
                CHRE_MESSAGE_TYPE_RPC, packet);
        if (mAuthDenied.get()
                || ContextHubTransaction.RESULT_SUCCESS != mClient.sendMessageToNanoApp(message)) {
            throw new ChannelOutputException();
        }
    }

    /**
     * @return Channel ID to use for all Channels that use this output to send
     * messages to a nanoapp.
     */
    public int getChannelId() {
        return (CHANNEL_ID_HOST_CLIENT | mClient.getId());
    }

    /**
     * Used to indicate whether the particular nanoapp cannot be communicated
     * with any more (e.g. due to permissions loss).
     */
    void setAuthDenied(boolean denied) {
        mAuthDenied.set(denied);
    }
}

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

import static android.hardware.location.ContextHubManager.AUTHORIZATION_DENIED;
import static android.hardware.location.ContextHubManager.AUTHORIZATION_GRANTED;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.NanoAppMessage;

import java.util.Objects;

import dev.pigweed.pw_rpc.Client;

/**
 * Implementation that can be used to ensure callbacks from the ContextHub APIs are properly handled
 * when using pw_rpc.
 */
public class ChreCallbackHandler extends ContextHubClientCallback {
    private final long mNanoappId;
    // Nullable.
    private final ContextHubClientCallback mCallback;
    private Client mRpcClient;
    private ChreChannelOutput mChannelOutput;

    /**
     * @param nanoappId ID of the RPC Server nanoapp
     * @param callback  The callbacks receiving messages and life-cycle events from nanoapps,
     *                  nullable.
     */
    public ChreCallbackHandler(long nanoappId, ContextHubClientCallback callback) {
        mNanoappId = nanoappId;
        mCallback = callback;
    }

    /**
     * Completes the initialization.
     *
     * @param rpcClient     The Pigweed RPC client, non null
     * @param channelOutput The ChannelOutput used by Pigweed, non null
     */
    public void lateInit(Client rpcClient, ChreChannelOutput channelOutput) {
        mRpcClient = Objects.requireNonNull(rpcClient);
        mChannelOutput = Objects.requireNonNull(channelOutput);
    }

    /**
     * This method passes the message to pigweed RPC for decoding.
     */
    @Override
    public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        if (mRpcClient != null && message.getNanoAppId() == mNanoappId) {
            mRpcClient.processPacket(message.getMessageBody());
        }
        if (mCallback != null) {
            mCallback.onMessageFromNanoApp(client, message);
        }
    }

    /**
     * This method ensures all outstanding RPCs are canceled.
     */
    @Override
    public void onHubReset(ContextHubClient client) {
        closeChannel();
        if (mCallback != null) {
            mCallback.onHubReset(client);
        }
    }

    /**
     * This method ensures all outstanding RPCs are canceled.
     */
    @Override
    public void onNanoAppAborted(ContextHubClient client, long nanoappId, int abortCode) {
        if (nanoappId == mNanoappId) {
            closeChannel();
        }
        if (mCallback != null) {
            mCallback.onNanoAppAborted(client, nanoappId, abortCode);
        }
    }

    @Override
    public void onNanoAppLoaded(ContextHubClient client, long nanoappId) {
        if (mCallback != null) {
            mCallback.onNanoAppLoaded(client, nanoappId);
        }
    }

    /**
     * This method ensures all outstanding RPCs are canceled.
     */
    @Override
    public void onNanoAppUnloaded(ContextHubClient client, long nanoappId) {
        if (nanoappId == mNanoappId) {
            closeChannel();
        }
        if (mCallback != null) {
            mCallback.onNanoAppUnloaded(client, nanoappId);
        }
    }

    @Override
    public void onNanoAppEnabled(ContextHubClient client, long nanoappId) {
        if (mCallback != null) {
            mCallback.onNanoAppEnabled(client, nanoappId);
        }
    }

    /**
     * This method ensures all outstanding RPCs are canceled.
     */
    @Override
    public void onNanoAppDisabled(ContextHubClient client, long nanoappId) {
        if (nanoappId == mNanoappId) {
            closeChannel();
        }
        if (mCallback != null) {
            mCallback.onNanoAppDisabled(client, nanoappId);
        }
    }

    /**
     * If the client is now unauthorized to communicate with the nanoapp, any future RPCs attempted
     * will fail until the client becomes authorized again.
     */
    @Override
    public void onClientAuthorizationChanged(ContextHubClient client, long nanoappId,
            int authorization) {
        if (mChannelOutput != null && nanoappId == mNanoappId) {
            if (authorization == AUTHORIZATION_DENIED) {
                mChannelOutput.setAuthDenied(true /* denied */);
                closeChannel();
            } else if (authorization == AUTHORIZATION_GRANTED) {
                mChannelOutput.setAuthDenied(false /* denied */);
            }
        }
        if (mCallback != null) {
            mCallback.onClientAuthorizationChanged(client, nanoappId, authorization);
        }
    }

    private void closeChannel() {
        if (mRpcClient != null && mChannelOutput != null) {
            mRpcClient.closeChannel(mChannelOutput.getChannelId());
            mChannelOutput = null;
        }
    }
}

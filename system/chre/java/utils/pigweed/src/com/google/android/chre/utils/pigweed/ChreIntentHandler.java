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

import android.content.Intent;
import android.hardware.location.ContextHubIntentEvent;
import android.hardware.location.ContextHubManager;
import android.util.Log;

import java.util.Objects;

import dev.pigweed.pw_rpc.Client;

/**
 * Handles RPC events in CHRE intent.
 */
public class ChreIntentHandler {
    private static final String TAG = "ChreIntentHandler";

    /**
     * Handles CHRE intents.
     *
     * @param intent        the intent, non null
     * @param nanoappId     ID of the RPC Server nanoapp
     * @param rpcClient     The Pigweed RPC client, non null
     * @param channelOutput The ChannelOutput used by Pigweed, non null
     */
    public static void handle(Intent intent, long nanoappId, Client rpcClient,
            ChreChannelOutput channelOutput) {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(rpcClient);
        Objects.requireNonNull(channelOutput);

        ContextHubIntentEvent event = ContextHubIntentEvent.fromIntent(intent);

        switch (event.getEventType()) {
            case ContextHubManager.EVENT_NANOAPP_LOADED:
                // Nothing to do.
                break;
            case ContextHubManager.EVENT_NANOAPP_UNLOADED:
                rpcClient.closeChannel(channelOutput.getChannelId());
                break;
            case ContextHubManager.EVENT_NANOAPP_ENABLED:
                // Nothing to do.
                break;
            case ContextHubManager.EVENT_NANOAPP_DISABLED:
                rpcClient.closeChannel(channelOutput.getChannelId());
                break;
            case ContextHubManager.EVENT_NANOAPP_ABORTED:
                rpcClient.closeChannel(channelOutput.getChannelId());
                break;
            case ContextHubManager.EVENT_NANOAPP_MESSAGE:
                if (event.getNanoAppId() == nanoappId) {
                    rpcClient.processPacket(event.getNanoAppMessage().getMessageBody());
                }
                break;
            case ContextHubManager.EVENT_HUB_RESET:
                rpcClient.closeChannel(channelOutput.getChannelId());
                break;
            case ContextHubManager.EVENT_CLIENT_AUTHORIZATION:
                if (event.getNanoAppId() == nanoappId) {
                    if (event.getClientAuthorizationState() == AUTHORIZATION_DENIED) {
                        channelOutput.setAuthDenied(true /* denied */);
                        rpcClient.closeChannel(channelOutput.getChannelId());
                    } else if (event.getClientAuthorizationState() == AUTHORIZATION_GRANTED) {
                        channelOutput.setAuthDenied(false /* denied */);
                    }
                }
                break;
            default:
                Log.e(TAG, "Unexpected event: " + event);
        }
    }
}

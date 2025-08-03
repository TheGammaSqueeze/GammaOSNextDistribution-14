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

import android.content.Intent;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppRpcService;
import android.hardware.location.NanoAppState;

import java.util.List;
import java.util.Objects;

import dev.pigweed.pw_rpc.Channel;
import dev.pigweed.pw_rpc.Client;
import dev.pigweed.pw_rpc.MethodClient;
import dev.pigweed.pw_rpc.Service;

/**
 * Pigweed RPC Client Helper.
 *
 * See https://g3doc.corp.google.com/location/lbs/contexthub/g3doc/nanoapps/pw_rpc_host.md
 */
public class ChreRpcClient {

    // Non null.
    private final Client mRpcClient;
    // Non null.
    private final Channel mChannel;
    // Non null.
    private final ChreChannelOutput mChannelOutput;
    private final long mServerNanoappId;
    // Non null.
    private final ContextHubClient mContextHubClient;
    private ChreIntentHandler mIntentHandler;

    /**
     * Creates a ContextHubClient and initializes the helper.
     *
     * Use this constructor for persistent clients using callbacks.
     *
     * @param manager         The context manager used to create a client, non null
     * @param info            Context hub info, non null
     * @param serverNanoappId The ID of the RPC server nanoapp
     * @param services        The list of services provided by the server, non null
     * @param callback        The callbacks receiving messages and life-cycle events from nanoapps,
     *                        nullable
     */
    public ChreRpcClient(ContextHubManager manager, ContextHubInfo info,
            long serverNanoappId, List<Service> services,
            ContextHubClientCallback callback) {
        Objects.requireNonNull(manager);
        Objects.requireNonNull(info);
        Objects.requireNonNull(services);
        ChreCallbackHandler callbackHandler = new ChreCallbackHandler(serverNanoappId, callback);
        mContextHubClient = manager.createClient(info, callbackHandler);
        mServerNanoappId = serverNanoappId;
        mChannelOutput = new ChreChannelOutput(mContextHubClient, serverNanoappId);
        mChannel = new Channel(mChannelOutput.getChannelId(), mChannelOutput);
        mRpcClient = Client.create(List.of(mChannel), services);
        callbackHandler.lateInit(mRpcClient, mChannelOutput);
    }

    /**
     * Initializes the helper
     *
     * Use this constructor for non-persistent clients using intents.
     *
     * handleIntent() must be called with any CHRE intent received by the BroadcastReceiver.
     *
     * @param contextHubClient The context hub client providing the RPC server nanoapp, non null
     * @param serverNanoappId  The ID of the RPC server nanoapp
     * @param services         The list of services provided by the server, non null
     */
    public ChreRpcClient(ContextHubClient contextHubClient, long serverNanoappId,
            List<Service> services) {
        mContextHubClient = Objects.requireNonNull(contextHubClient);
        Objects.requireNonNull(services);
        mServerNanoappId = serverNanoappId;
        mChannelOutput = new ChreChannelOutput(contextHubClient, serverNanoappId);
        mChannel = new Channel(mChannelOutput.getChannelId(), mChannelOutput);
        mRpcClient = Client.create(List.of(mChannel), services);
    }

    /**
     * Returns whether the state matches the server nanoapp and the service is provided.
     *
     * @param state           A nanoapp state
     * @param serverNanoappId The ID of the RPC server nanoapp
     * @param serviceId       ID of the service
     * @param serviceVersion  Version of the service
     * @return the state matches the server nanoapp and the service is provided
     */
    public static boolean hasService(NanoAppState state, long serverNanoappId, long serviceId,
            int serviceVersion) {
        if (state.getNanoAppId() != serverNanoappId) {
            return false;
        }

        for (NanoAppRpcService service : state.getRpcServices()) {
            if (service.getId() == serviceId) {
                return service.getVersion() == serviceVersion;
            }
        }

        return false;
    }

    /**
     * Handles CHRE intents.
     *
     * @param intent The CHRE intent, non null
     */
    public void handleIntent(Intent intent) {
        ChreIntentHandler.handle(intent, mServerNanoappId, mRpcClient, mChannelOutput);
    }

    /**
     * Returns the context hub client.
     */
    public ContextHubClient getContextHubClient() {
        return mContextHubClient;
    }

    /**
     * Shorthand for closing the underlying ContextHubClient.
     */
    public void close() {
        mContextHubClient.close();
    }

    /**
     * Returns a MethodClient.
     *
     * Use the client to invoke the service.
     *
     * @param methodName the method name as "package.Service.Method" or "package.Service/Method"
     * @return The MethodClient instance
     */
    public MethodClient getMethodClient(String methodName) {
        return mRpcClient.method(mChannel.id(), methodName);
    }
}

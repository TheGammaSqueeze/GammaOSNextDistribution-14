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

package com.android.tradefed.invoker.shard;

import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.internal.android.engprod.v1.DynamicTestTargetProviderGrpc;
import com.google.internal.android.engprod.v1.ProvideTestTargetRequest;
import com.google.internal.android.engprod.v1.ProvideTestTargetResponse;
import com.google.internal.android.engprod.v1.RequestTestTargetRequest;
import com.google.internal.android.engprod.v1.RequestTestTargetResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;

import java.io.IOException;
import java.util.concurrent.Executors;

/** {@inheritDoc} */
public class ConfigurableGrpcDynamicShardingClient implements IDynamicShardingClient {
    private ManagedChannel mChannel;
    private DynamicTestTargetProviderGrpc.DynamicTestTargetProviderBlockingStub mStub;
    private Credentials mCredentials;

    private ConfigurableGrpcDynamicShardingClient(
            ManagedChannel channel,
            DynamicTestTargetProviderGrpc.DynamicTestTargetProviderBlockingStub stub,
            Credentials creds) {
        mChannel = channel;
        mStub = stub;
        mCredentials = creds;
    }

    /** {@inheritDoc} */
    @Override
    public ProvideTestTargetResponse provideTestTarget(ProvideTestTargetRequest request) {
        return mStub.provideTestTarget(request);
    }

    /** {@inheritDoc} */
    @Override
    public RequestTestTargetResponse requestTestTarget(RequestTestTargetRequest request) {
        return mStub.requestTestTarget(request);
    }

    /**
     * Constructor for a configurable gRPC client to a dynamic sharding server.
     *
     * <p>Be sure to properly populate the connectionInfo object being passed in.
     */
    public ConfigurableGrpcDynamicShardingClient(IDynamicShardingConnectionInfo connectionInfo) {
        try {
            Credentials creds =
                    GoogleCredentials.getApplicationDefault()
                            .createScoped(connectionInfo.getAuthScopes());
            ManagedChannel channel =
                    ManagedChannelBuilder.forAddress(
                                    connectionInfo.getServerAddress(),
                                    connectionInfo.getServerPort())
                            .executor(Executors.newCachedThreadPool())
                            .maxInboundMessageSize(32 * 1024)
                            .build();
            DynamicTestTargetProviderGrpc.DynamicTestTargetProviderBlockingStub stub =
                    DynamicTestTargetProviderGrpc.newBlockingStub(channel)
                            .withCallCredentials(MoreCallCredentials.from(creds));

            mChannel = channel;
            mStub = stub;
            mCredentials = creds;
        } catch (IOException e) {
            throw new HarnessRuntimeException(e.getMessage(), InfraErrorIdentifier.UNDETERMINED);
        }
    }
}

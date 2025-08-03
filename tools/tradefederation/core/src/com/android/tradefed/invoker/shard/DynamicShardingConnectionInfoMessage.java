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

import com.proto.tradefed.feature.MultiPartResponse;
import com.proto.tradefed.feature.PartResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-holding class in order to make sending via the feature server easier.
 *
 * <p>WARNING: THIS CLASS NEEDS TO BE BACKWARD COMPATIBLE WITH ITSELF AT LEAST 2 WEEKS BACK. IT MUST
 * MESH WITH THE DEPLOYED VERSION WHICH CAN BE UP TO A COUPLE WEEKS OLD. This is because this class
 * handles serialization and deserialization for a feature, but it must work with the lab-deployed
 * version which is going to be an older version of this class.
 */
public class DynamicShardingConnectionInfoMessage implements IDynamicShardingConnectionInfo {
    private final String mServerName;
    private final Integer mServerPort;
    private final List<String> mAuthScopes;

    public static final String SERVER_NAME_KEY = "server_name";
    public static final String SERVER_PORT_KEY = "server_port";
    public static final String AUTH_SCOPES_KEY = "auth_scopes";

    DynamicShardingConnectionInfoMessage(
            String serverName, Integer serverPort, List<String> authScopes) {
        mServerName = serverName;
        mServerPort = serverPort;
        mAuthScopes = authScopes;
    }

    public static DynamicShardingConnectionInfoMessage fromConnectionInfo(
            IDynamicShardingConnectionInfo info) {
        return new DynamicShardingConnectionInfoMessage(
                info.getServerAddress(), info.getServerPort(), info.getAuthScopes());
    }

    public static DynamicShardingConnectionInfoMessage fromMultiPartResponse(
            MultiPartResponse response) {
        Optional<String> serverName = Optional.empty();
        Optional<Integer> serverPort = Optional.empty();
        List<String> authScopes = new ArrayList();

        for (PartResponse part : response.getResponsePartList()) {
            if (part.getKey().equals(AUTH_SCOPES_KEY)) {
                authScopes.add(part.getValue());
            }
            if (part.getKey().equals(SERVER_NAME_KEY)) {
                if (serverName.isPresent()) {
                    throw new HarnessRuntimeException(
                            "Malformed dynamic sharding connection info: server name was specified"
                                    + " more than once.",
                            InfraErrorIdentifier.UNDETERMINED);
                }
                serverName = Optional.of(part.getValue());
            }
            if (part.getKey().equals(SERVER_PORT_KEY)) {
                if (serverPort.isPresent()) {
                    throw new HarnessRuntimeException(
                            "Malformed dynamic sharding connection info: server port was specified"
                                    + " more than once.",
                            InfraErrorIdentifier.UNDETERMINED);
                }
                serverPort = Optional.of(Integer.parseInt(part.getValue()));
            }
        }

        if (!serverName.isPresent()) {
            throw new HarnessRuntimeException(
                    "Malformed dynamic sharding connection info: server name was not specified.",
                    InfraErrorIdentifier.UNDETERMINED);
        }

        if (!serverPort.isPresent()) {
            throw new HarnessRuntimeException(
                    "Malformed dynamic sharding connection info: server port was not specified.",
                    InfraErrorIdentifier.UNDETERMINED);
        }

        return new DynamicShardingConnectionInfoMessage(
                serverName.get(), serverPort.get(), authScopes);
    }

    public MultiPartResponse.Builder toResponseBuilder() {
        MultiPartResponse.Builder builder = MultiPartResponse.newBuilder();
        builder.addResponsePart(
                PartResponse.newBuilder().setKey(SERVER_NAME_KEY).setValue(mServerName));
        builder.addResponsePart(
                PartResponse.newBuilder().setKey(SERVER_PORT_KEY).setValue(mServerPort.toString()));
        for (String scope : mAuthScopes) {
            builder.addResponsePart(
                    PartResponse.newBuilder().setKey(AUTH_SCOPES_KEY).setValue(scope));
        }

        return builder;
    }

    @Override
    public String getServerAddress() {
        return mServerName;
    }

    @Override
    public Integer getServerPort() {
        return mServerPort;
    }

    @Override
    public List<String> getAuthScopes() {
        return mAuthScopes;
    }
}

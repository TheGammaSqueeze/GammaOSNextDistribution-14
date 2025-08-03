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
package com.android.tradefed.service;

import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.TradefedInformationGrpc;
import com.proto.tradefed.feature.TradefedInformationGrpc.TradefedInformationBlockingStub;

import java.util.Map;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

/** A grpc client to request feature execution from the server. */
public class TradefedFeatureClient implements AutoCloseable {

    private static final int MAX_MESSAGE_SIZE_BYTES = 33554432; // 32 MB
    private TradefedInformationBlockingStub mBlockingStub;
    private ManagedChannel mChannel;

    public TradefedFeatureClient() {
        mChannel =
                ManagedChannelBuilder.forAddress("localhost", TradefedFeatureServer.getPort())
                        .usePlaintext()
                        .maxInboundMessageSize(MAX_MESSAGE_SIZE_BYTES)
                        .build();
        mBlockingStub = TradefedInformationGrpc.newBlockingStub(mChannel);
    }

    /**
     * Request a feature to the main server to execute and return the response.
     *
     * @param featureName The name of the feature to trigger.
     * @param args The args to invoke the feature.
     * @return A {@link FeatureResponse}.
     */
    public FeatureResponse triggerFeature(String featureName, Map<String, String> args) {
        return triggerFeature(
                featureName, System.getenv(TradefedFeatureServer.SERVER_REFERENCE), args);
    }

    /**
     * Request a feature to the main server to execute and return the response.
     *
     * @param featureName The name of the feature to trigger.
     * @param args The args to invoke the feature.
     * @return A {@link FeatureResponse}.
     */
    private FeatureResponse triggerFeature(
            String featureName, String invocationReference, Map<String, String> args) {
        if (mChannel == null) {
            throw new IllegalStateException(
                    "Tradefed feature channel is null due to being uninitialized or closed.");
        }
        FeatureResponse response;
        try (CloseableTraceScope ignore =
                new CloseableTraceScope("triggerFeature:" + featureName)) {
            CLog.d("invoking feature '%s'", featureName);
            FeatureRequest.Builder request =
                    FeatureRequest.newBuilder().setName(featureName).putAllArgs(args);
            if (invocationReference != null) {
                request.setReferenceId(invocationReference);
            } else if (InvocationMetricLogger.getInvocationMetrics().containsKey(InvocationMetricKey.SERVER_REFERENCE.toString())) {
                CLog.d("Using reference id from metrics.");
                request.setReferenceId(InvocationMetricLogger.getInvocationMetrics().get(InvocationMetricKey.SERVER_REFERENCE.toString()));
            } else {
                CLog.w("Reference id is null.");
            }
            CLog.d("%s", request);
            response = mBlockingStub.triggerFeature(request.build());
        } catch (StatusRuntimeException e) {
            response =
                    FeatureResponse.newBuilder()
                            .setErrorInfo(
                                    ErrorInfo.newBuilder()
                                            .setErrorTrace(StreamUtil.getStackTrace(e))
                                            .build())
                            .build();
        }
        String message = String.format("Feature name: %s. response: %s", featureName, response);
        if (response.hasErrorInfo()) {
            StringBuilder callsite = new StringBuilder();
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                callsite.append(e.toString());
            }
            message += String.format(". Callsite: %s", callsite);
            CLog.w(message);
        } else {
            CLog.d(message);
        }
        return response;
    }

    @Override
    public void close() {
        if (mChannel != null) {
            mChannel.shutdown();
        }
    }
}

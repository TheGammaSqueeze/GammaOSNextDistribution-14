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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.internal.IRemoteScheduledListenersFeature;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.SystemUtil;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.TradefedInformationGrpc.TradefedInformationImplBase;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

/** A server that responds to requests for triggering features. */
public class TradefedFeatureServer extends TradefedInformationImplBase {

    public static final String SERVER_REFERENCE = "SERVER_REFERENCE";
    public static final String TEST_INFORMATION_OBJECT = "TEST_INFORMATION";
    public static final String TF_SERVICE_PORT = "TF_SERVICE_PORT";

    private static final int DEFAULT_PORT = 0;
    private static Integer sInternalPort = null;

    private Server mServer;

    private Map<String, IConfiguration> mRegisteredInvocation = new ConcurrentHashMap<>();
    private Map<String, ThreadGroup> mRegisteredGroup =
            new ConcurrentHashMap<String, ThreadGroup>();
    private Map<String, List<IScheduledInvocationListener>>
            mRegisteredScheduledInvocationListeners = new ConcurrentHashMap<>();

    /** Returns the port used by the server. */
    public static int getPort() {
        if (sInternalPort != null) {
            return sInternalPort;
        }
        return System.getenv(TF_SERVICE_PORT) != null
                ? Integer.parseInt(System.getenv(TF_SERVICE_PORT))
                : DEFAULT_PORT;
    }

    public TradefedFeatureServer() {
        this(ServerBuilder.forPort(getPort()));
    }

    @VisibleForTesting
    TradefedFeatureServer(ServerBuilder<?> serverBuilder) {
        mServer = serverBuilder.addService(this).build();
    }

    /** Start the grpc server to listen to requests. */
    public void start() {
        try {
            CLog.d("Starting feature server.");
            mServer.start();
            sInternalPort = mServer.getPort();
        } catch (IOException e) {
            if (SystemUtil.isLocalMode()) {
                CLog.w("TradefedFeatureServer already started: %s", e.getMessage());
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /** Stop the grpc server. */
    public void shutdown() throws InterruptedException {
        if (mServer != null) {
            CLog.d("Stopping feature server.");
            mServer.shutdown();
            mServer.awaitTermination();
        }
    }

    @Override
    public void triggerFeature(
            FeatureRequest request, StreamObserver<FeatureResponse> responseObserver) {
        FeatureResponse response;
        try {
            response = createResponse(request);
        } catch (RuntimeException exception) {
            response = FeatureResponse.newBuilder()
                .setErrorInfo(
                    ErrorInfo.newBuilder()
                            .setErrorTrace(StreamUtil.getStackTrace(exception)))
                    .build();
        }
        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }

    /** Register an invocation with a unique reference that can be queried */
    public String registerInvocation(
            IConfiguration config, ThreadGroup tg, List<IScheduledInvocationListener> listeners) {
        String referenceId = UUID.randomUUID().toString();
        mRegisteredInvocation.put(referenceId, config);
        mRegisteredGroup.put(referenceId, tg);
        mRegisteredScheduledInvocationListeners.put(referenceId, listeners);
        config.getConfigurationDescription().addMetadata(SERVER_REFERENCE, referenceId);
        return referenceId;
    }

    /** Unregister an invocation by its configuration. */
    public void unregisterInvocation(IConfiguration reference) {
        String referenceId =
                reference
                        .getConfigurationDescription()
                        .getAllMetaData()
                        .getUniqueMap()
                        .get(SERVER_REFERENCE);
        if (referenceId != null) {
            mRegisteredInvocation.remove(referenceId);
            mRegisteredGroup.remove(referenceId);
            mRegisteredScheduledInvocationListeners.remove(referenceId);
        }
    }

    private FeatureResponse createResponse(FeatureRequest request) {
        ServiceLoader<IRemoteFeature> serviceLoader = ServiceLoader.load(IRemoteFeature.class);
        for (IRemoteFeature feature : serviceLoader) {
            if (feature.getName().equals(request.getName())) {
                InvocationMetricLogger.setLocalGroup(
                        mRegisteredGroup.get(request.getReferenceId()));
                if (feature instanceof IConfigurationReceiver) {
                    ((IConfigurationReceiver) feature)
                            .setConfiguration(mRegisteredInvocation.get(request.getReferenceId()));
                }
                if (feature instanceof ITestInformationReceiver) {
                    if (mRegisteredInvocation.get(request.getReferenceId()) != null) {
                        ((ITestInformationReceiver) feature)
                                .setTestInformation(
                                        (TestInformation) mRegisteredInvocation
                                            .get(request.getReferenceId())
                                            .getConfigurationObject(TEST_INFORMATION_OBJECT));
                    }
                }
                if (feature instanceof IRemoteScheduledListenersFeature) {
                    List<IScheduledInvocationListener> listeners =
                            mRegisteredScheduledInvocationListeners.get(request.getReferenceId());
                    if (listeners != null) {
                        ((IRemoteScheduledListenersFeature) feature).setListeners(listeners);
                    }
                }
                try {
                    FeatureResponse rep = feature.execute(request);
                    if (rep == null) {
                        return FeatureResponse.newBuilder()
                                .setErrorInfo(
                                        ErrorInfo.newBuilder()
                                                .setErrorTrace(
                                                        String.format(
                                                                "Feature '%s' returned null"
                                                                        + " response.",
                                                                request.getName())))
                                .build();
                    }
                    return rep;
                } finally {
                    if (feature instanceof IConfigurationReceiver) {
                        ((IConfigurationReceiver) feature).setConfiguration(null);
                    }
                    InvocationMetricLogger.resetLocalGroup();
                }
            }
        }
        return FeatureResponse.newBuilder()
                .setErrorInfo(
                        ErrorInfo.newBuilder()
                                .setErrorTrace(
                                        String.format(
                                                "No feature matching the requested one '%s'",
                                                request.getName())))
                .build();
    }
}

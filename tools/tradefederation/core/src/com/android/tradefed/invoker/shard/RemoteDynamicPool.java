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
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.shard.token.ITokenRequest;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.ITestSuite;

import com.google.internal.android.engprod.v1.ProvideTestTargetRequest;
import com.google.internal.android.engprod.v1.ProvideTestTargetResponse;
import com.google.internal.android.engprod.v1.RequestTestTargetRequest;
import com.google.internal.android.engprod.v1.RequestTestTargetResponse;
import com.google.internal.android.engprod.v1.SerializedTestTarget;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Implementation of a pool of remote work queued tests */
public class RemoteDynamicPool implements ITestsPool {
    private IDynamicShardingClient mClient;
    private Map<String, ITestSuite> mModuleMapping;
    private Map<String, Integer> mAttemptNumberByTestTarget;
    private String mPoolId;
    private List<IRemoteTest> mQueuedTests = new ArrayList<>();
    private Clock mClock = Clock.systemUTC();

    public static RemoteDynamicPool newInstance(
            IDynamicShardingClient client, String poolId, Map<String, ITestSuite> moduleMapping) {
        return new RemoteDynamicPool(client, poolId, moduleMapping);
    }

    private RemoteDynamicPool(
            IDynamicShardingClient client, String poolId, Map<String, ITestSuite> moduleMapping) {
        mClient = client;
        mModuleMapping = moduleMapping;
        mPoolId = poolId;
        mAttemptNumberByTestTarget = new HashMap<>();
    }

    public int getAttemptNumber(ITestSuite test) {
        String testTargetName = test.getDirectModule().getId();
        return mAttemptNumberByTestTarget.get(testTargetName);
    }

    public void returnToRemotePool(ITestSuite test, int attemptNumber) {
        String testTargetName = test.getDirectModule().getId();
        SerializedTestTarget testTarget =
                SerializedTestTarget.newBuilder()
                        .setTargetName(testTargetName)
                        .setAttemptNumber(attemptNumber + 1)
                        .build();
        ProvideTestTargetRequest request =
                ProvideTestTargetRequest.newBuilder()
                        .setReferencePoolId(mPoolId)
                        .setUseOneShotSeeding(false)
                        .addTestTargets(testTarget)
                        .build();
        ProvideTestTargetResponse response = mClient.provideTestTarget(request);
    }

    @Override
    public IRemoteTest poll(TestInformation info, boolean reportNotExecuted) {
        if (mQueuedTests.isEmpty()) {
            // Ensure there are no carried over attempt numbers before polling
            // the server.
            mAttemptNumberByTestTarget.clear();

            RequestTestTargetRequest request =
                    RequestTestTargetRequest.newBuilder().setReferencePoolId(mPoolId).build();

            long startTime = mClock.millis();

            RequestTestTargetResponse response;

            try {
                response = mClient.requestTestTarget(request);
            } catch (Throwable e) {
                throw new HarnessRuntimeException(
                        e.getMessage(), InfraErrorIdentifier.SCHEDULING_ERROR);
            }

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DYNAMIC_SHARDING_REQUEST_LATENCY,
                    mClock.millis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DYNAMIC_SHARDING_REQUEST_COUNT, 1);

            CLog.v(String.format("Received test targets: %s", response.getTestTargetsList()));
            mQueuedTests.addAll(
                    response.getTestTargetsList().stream()
                            .map(x -> mModuleMapping.get(x.getTargetName()))
                            .collect(Collectors.toList()));
            response.getTestTargetsList().stream()
                    .forEach(
                            x -> {
                                mAttemptNumberByTestTarget.put(
                                        x.getTargetName(), x.getAttemptNumber());
                            });
            if (mQueuedTests.isEmpty()) {
                return null;
            } else {
                return mQueuedTests.remove(mQueuedTests.size() - 1);
            }
        } else {
            return mQueuedTests.remove(mQueuedTests.size() - 1);
        }
    }

    @Override
    public ITokenRequest pollRejectedTokenModule() {
        return null;
    }
}

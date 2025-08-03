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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.ITestSuite;

import com.google.common.base.Strings;
import com.google.internal.android.engprod.v1.ProvideTestTargetRequest;
import com.google.internal.android.engprod.v1.SerializedTestTarget;
import com.proto.tradefed.feature.FeatureResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/** Sharding strategy to allow work remote work queueing between multiple TF instances */
public class DynamicShardHelper extends StrictShardHelper {

    /** {@inheritDoc} */
    @Override
    public boolean shardConfig(
            IConfiguration config,
            TestInformation testInfo,
            IRescheduler rescheduler,
            ITestLogger logger) {
        // Check preconditions
        Integer shardCount = config.getCommandOptions().getShardCount();
        Integer shardIndex = config.getCommandOptions().getShardIndex();

        String invocationId = testInfo.getContext().getAttribute("invocation_id");
        String attemptId = testInfo.getContext().getAttribute("attempt_index");

        boolean shouldDelegate = false;

        // We should re-delegate this to strict sharding so it can delegate this case to local
        // sharding
        if (shardIndex == null) {
            shouldDelegate = true;
        }

        if (shardIndex != null && shardCount == null) {
            throw new HarnessRuntimeException(
                    "shard-count is null while shard-index is " + shardIndex,
                    InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
        }

        // redelegate to strict sharding
        if (Strings.isNullOrEmpty(attemptId)) {
            shouldDelegate = true;
        }

        // If we don't have sufficient information to properly key the pool, then fall
        // back to strict sharding.
        if (Strings.isNullOrEmpty(invocationId)) {
            CLog.d("No invocation_id specified, falling back to strict sharding.");
            shouldDelegate = true;
        }

        // Check if any of the tests are not ITestSuite instances
        // If not, make sure that intra-module sharding is off and delegate
        if (config.getTests().stream()
                .anyMatch(x -> !ITestSuite.class.isAssignableFrom(x.getClass()))) {
            CLog.d("Found non-ITestSuite tests, falling back to strict sharding");
            shouldDelegate = true;
        }

        if (shouldDelegate) {
            CLog.d(
                    "Setting option 'remote-dynamic-sharding' to false since precondition checks"
                            + " failed.");
            config.getCommandOptions().setShouldRemoteDynamicShard(false);
            return super.shardConfig(config, testInfo, rescheduler, logger);
        }

        // Initialize Dynamic Sharding client
        IDynamicShardingClient client = getClient();

        String poolId = String.format("invocation-%s", invocationId);

        List<ITestSuite> allModules = getAllModules(config, testInfo);

        Map<String, ITestSuite> moduleMapping = new HashMap<>();
        for (ITestSuite test : allModules) {
            moduleMapping.put(test.getDirectModule().getId(), test);
        }

        // if we're shard 0 populate the pool with the list of tests
        try {
            // Populate the pool
            List<SerializedTestTarget> targetsToUpload =
                    moduleMapping.keySet().stream()
                            .map(x -> SerializedTestTarget.newBuilder().setTargetName(x).build())
                            .collect(Collectors.toList());
            CLog.d("Uploading to pool %s test targets: %s", poolId, targetsToUpload);
            ProvideTestTargetRequest request =
                    ProvideTestTargetRequest.newBuilder()
                            .setReferencePoolId(poolId)
                            .setUseOneShotSeeding(true)
                            .addAllTestTargets(targetsToUpload)
                            .build();
            client.provideTestTarget(request);
        } catch (StatusRuntimeException e) {
            // If it is just the ALREADY_EXISTS error, that's ok; it just means
            // that one of the other shards got to it before this one.
            if (Status.fromThrowable(e).getCode() != Status.Code.ALREADY_EXISTS) {
                // rethrow if it isn't the error we were expecting.
                throw e;
            }
            // will only reach this point if the error code is ALREADY_EXISTS
            CLog.v("Another shard has already seeded the pool '%s'.", poolId);
        }

        // if we're any shard, create a test pool poller that polls the sharding server
        ITestsPool pool = RemoteDynamicPool.newInstance(client, poolId, moduleMapping);

        // For now this should disable the reporting of not executed tests since each
        // poller can only decrement this by 1 and this mode only uses one poller per
        // cluster command.
        // At some point we should probably have some way for pollers to register and
        // deregister from a pool in order to be able to tell how many pollers are still
        // listening to a pool.
        CountDownLatch tracker = new CountDownLatch(2);
        TestsPoolPoller poller = new TestsPoolPoller(pool, tracker);

        // set our main test to be the test pool poller.
        config.setTest(poller);

        // We cannot shuffle to get better average results
        return false;
    }

    private IDynamicShardingClient getClient() {
        TradefedFeatureClient featureClient = new TradefedFeatureClient();
        FeatureResponse resp =
                featureClient.triggerFeature(
                        "getDynamicShardingConnectionInfo", new HashMap<String, String>());
        if (resp.hasMultiPartResponse()) {
            DynamicShardingConnectionInfoMessage msg =
                    DynamicShardingConnectionInfoMessage.fromMultiPartResponse(
                            resp.getMultiPartResponse());
            return new ConfigurableGrpcDynamicShardingClient(msg);
        } else {
            CLog.v(
                    "Failed to get connection info from feature client, will attempt to load a"
                            + " client using the service loader");
            ServiceLoader<IDynamicShardingClient> serviceLoader =
                    ServiceLoader.load(IDynamicShardingClient.class);
            for (IDynamicShardingClient client : serviceLoader) {
                // the first (and should be only) implementation of this feature should be the
                // internal one
                if (IDynamicShardingConnectionInfo.class.isAssignableFrom(client.getClass())) {
                    // use the internal one to configure the generic one
                    return new ConfigurableGrpcDynamicShardingClient(
                            (IDynamicShardingConnectionInfo) client);
                }
            }
        }
        throw new HarnessRuntimeException(
                "Failed to retrieve dynamic sharding connection info, feature server problem?",
                InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
    }

    private List<ITestSuite> getAllModules(IConfiguration config, TestInformation testInfo) {
        List<ITestSuite> allTests = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof ITestSuite) {
                ITestSuite suite = (ITestSuite) test;
                // Disable intra-module-sharding when requesting dynamic sharding
                // as it's currently not supported together.
                if (suite.getIntraModuleSharding()) {
                    CLog.w(
                            "Disabling intra-module sharding because it is not supported with"
                                    + "dynamic sharding.");
                    suite.setIntraModuleSharding(false);
                }

                allTests.addAll(
                        suite.split(1000000, testInfo).stream()
                                .map(x -> (ITestSuite) x)
                                .collect(Collectors.toList()));
            } else {
                throw new HarnessRuntimeException(
                        "Test not an instance of ITestSuite, cannot execute this using dynamic"
                                + " sharding.",
                        InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
            }
        }
        return allTests;
    }
}

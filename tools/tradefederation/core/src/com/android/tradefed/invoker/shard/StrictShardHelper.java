/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ModuleMerger;
import com.android.tradefed.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sharding strategy to create strict shards that do not report together, */
public class StrictShardHelper extends ShardHelper {

    /** {@inheritDoc} */
    @Override
    public boolean shardConfig(
            IConfiguration config,
            TestInformation testInfo,
            IRescheduler rescheduler,
            ITestLogger logger) {
        // need to look up attempt id somewhere and make sure we only attempt this on attempt 0
        if (config.getCommandOptions().shouldRemoteDynamicShard()) {
            // We are using dynamic sharding
            DynamicShardHelper helper = new DynamicShardHelper();
            // TODO(murj) handle the case where dynamic sharding fails
            return helper.shardConfig(config, testInfo, rescheduler, logger);
        }

        Integer shardCount = config.getCommandOptions().getShardCount();
        Integer shardIndex = config.getCommandOptions().getShardIndex();
        boolean optimizeMainline = config.getCommandOptions().getOptimizeMainlineTest();

        if (shardIndex == null) {
            return super.shardConfig(config, testInfo, rescheduler, logger);
        }
        if (shardCount == null) {
            throw new RuntimeException("shard-count is null while shard-index is " + shardIndex);
        }
        // No sharding needed if shard-count=1
        if (shardCount == 1) {
            return false;
        }

        // Split tests in place, without actually sharding.
        List<IRemoteTest> listAllTests = getAllTests(config, shardCount, testInfo, logger);
        // We cannot shuffle to get better average results
        normalizeDistribution(listAllTests, shardCount);
        List<IRemoteTest> splitList;
        if (shardCount == 1) {
            // not sharded
            splitList = listAllTests;
        } else {
            splitList =
                    splitTests(
                                    listAllTests,
                                    shardCount,
                                    config.getCommandOptions().shouldUseEvenModuleSharding())
                            .get(shardIndex);
        }
        aggregateSuiteModules(splitList);
        if (optimizeMainline) {
            CLog.i("Reordering the test modules list for index: %s", shardIndex);
            reorderTestModules(splitList);
        }
        config.setTests(splitList);
        return false;
    }

    /**
     * Helper to re order the list full list of {@link IRemoteTest} for mainline.
     *
     * @param tests the {@link IRemoteTest} containing all the tests that need to run.
     */
    private void reorderTestModules(List<IRemoteTest> tests) {
        Collections.sort(tests, new Comparator<IRemoteTest>() {
            @Override
            public int compare(IRemoteTest o1, IRemoteTest o2) {
                String moduleId1 = ((ITestSuite)o1).getDirectModule().getId();
                String moduleId2 = ((ITestSuite)o2).getDirectModule().getId();
                return getMainlineId(moduleId1).compareTo(getMainlineId(moduleId2));
            }
        });
    }

    /**
     * Returns the parameterized mainline modules' name defined in the square brackets.
     *
     * @param id The module's name.
     * @throws RuntimeException if the module name doesn't match the pattern for mainline modules.
     */
    private String getMainlineId(String id) {
        // Pattern used to identify the parameterized mainline modules defined in the square
        // brackets.
        Pattern parameterizedMainlineRegex = Pattern.compile("\\[(.*(\\.apk|.apex|.apks))\\]$");
        Matcher m = parameterizedMainlineRegex.matcher(id);
        if (m.find()) {
            return m.group(1);
        }
        throw new HarnessRuntimeException(
                String.format(
                        "Module: %s doesn't match the pattern for mainline modules. The "
                                + "pattern should end with apk/apex/apks.",
                        id),
                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
    }

    /**
     * Helper to return the full list of {@link IRemoteTest} based on {@link IShardableTest} split.
     *
     * @param config the {@link IConfiguration} describing the invocation.
     * @param shardCount the shard count hint to be provided to some tests.
     * @param testInfo the {@link TestInformation} of the parent invocation.
     * @return the list of all {@link IRemoteTest}.
     */
    private List<IRemoteTest> getAllTests(
            IConfiguration config,
            Integer shardCount,
            TestInformation testInfo,
            ITestLogger logger) {
        List<IRemoteTest> allTests = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IShardableTest) {
                // Inject current information to help with sharding
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(testInfo.getBuildInfo());
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(testInfo.getDevice());
                }
                if (test instanceof IInvocationContextReceiver) {
                    ((IInvocationContextReceiver) test).setInvocationContext(testInfo.getContext());
                }
                if (test instanceof ITestLoggerReceiver) {
                    ((ITestLoggerReceiver) test).setTestLogger(logger);
                }

                // Handling of the ITestSuite is a special case, we do not allow pool of tests
                // since each shard needs to be independent.
                if (test instanceof ITestSuite) {
                    ((ITestSuite) test).setShouldMakeDynamicModule(false);
                }

                Collection<IRemoteTest> subTests =
                        ((IShardableTest) test).split(shardCount, testInfo);
                if (subTests == null) {
                    // test did not shard so we add it as is.
                    allTests.add(test);
                } else {
                    allTests.addAll(subTests);
                }
            } else {
                // if test is not shardable we add it as is.
                allTests.add(test);
            }
        }
        return allTests;
    }

    /**
     * Split the list of tests to run however the implementation see fit. Sharding needs to be
     * consistent. It is acceptable to return an empty list if no tests can be run in the shard.
     *
     * <p>Implement this in order to provide a test suite specific sharding. The default
     * implementation attempts to balance the number of IRemoteTest per shards as much as possible
     * as a first step, then use a minor criteria or run-hint to adjust the lists a bit more.
     *
     * @param fullList the initial full list of {@link IRemoteTest} containing all the tests that
     *     need to run.
     * @param shardCount the total number of shard that need to run.
     * @param useEvenModuleSharding whether to use a strategy that evenly distributes number of
     *     modules across shards
     * @return a list of list {@link IRemoteTest}s that have been assigned to each shard. The list
     *     size will be the shardCount.
     */
    @VisibleForTesting
    protected List<List<IRemoteTest>> splitTests(
            List<IRemoteTest> fullList, int shardCount, boolean useEvenModuleSharding) {
        List<List<IRemoteTest>> shards;
        if (useEvenModuleSharding) {
            CLog.d("Using the sharding strategy to distribute number of modules more evenly.");
            shards = shardList(fullList, shardCount);
        } else {
            shards = new ArrayList<>();
            // We are using Match.ceil to avoid the last shard having too much extra.
            int numPerShard = (int) Math.ceil(fullList.size() / (float) shardCount);

            boolean needsCorrection = false;
            float correctionRatio = 0f;
            if (fullList.size() > shardCount) {
                // In some cases because of the Math.ceil, some combination might run out of tests
                // before the last shard, in that case we populate a correction to rebalance the
                // tests.
                needsCorrection = (numPerShard * (shardCount - 1)) > fullList.size();
                correctionRatio = numPerShard - (fullList.size() / (float) shardCount);
            }
            // Recalculate the number of tests per shard with the correction taken into account.
            numPerShard = (int) Math.floor(numPerShard - correctionRatio);
            // Based of the parameters, distribute the tests across shards.
            shards = balancedDistrib(fullList, shardCount, numPerShard, needsCorrection);
        }
        // Do last minute rebalancing
        topBottom(shards, shardCount);
        return shards;
    }

    private List<List<IRemoteTest>> balancedDistrib(
            List<IRemoteTest> fullList, int shardCount, int numPerShard, boolean needsCorrection) {
        List<List<IRemoteTest>> shards = new ArrayList<>();
        List<IRemoteTest> correctionList = new ArrayList<>();
        int correctionSize = 0;

        // Generate all the shards
        for (int i = 0; i < shardCount; i++) {
            List<IRemoteTest> shardList;
            if (i >= fullList.size()) {
                // Return empty list when we don't have enough tests for all the shards.
                shardList = new ArrayList<IRemoteTest>();
                shards.add(shardList);
                continue;
            }

            if (i == shardCount - 1) {
                // last shard take everything remaining except the correction:
                if (needsCorrection) {
                    // We omit the size of the correction needed.
                    correctionSize = fullList.size() - (numPerShard + (i * numPerShard));
                    correctionList =
                            fullList.subList(fullList.size() - correctionSize, fullList.size());
                }
                shardList = fullList.subList(i * numPerShard, fullList.size() - correctionSize);
                shards.add(new ArrayList<>(shardList));
                continue;
            }
            shardList = fullList.subList(i * numPerShard, numPerShard + (i * numPerShard));
            shards.add(new ArrayList<>(shardList));
        }

        // If we have correction omitted tests, disperse them on each shard, at this point the
        // number of tests in correction is ensured to be bellow the number of shards.
        for (int i = 0; i < shardCount; i++) {
            if (i < correctionList.size()) {
                shards.get(i).add(correctionList.get(i));
            } else {
                break;
            }
        }
        return shards;
    }

    @VisibleForTesting
    static <T> List<List<T>> shardList(List<T> fullList, int shardCount) {
        int totalSize = fullList.size();
        int smallShardSize = totalSize / shardCount;
        int bigShardSize = smallShardSize + 1;
        int bigShardCount = totalSize % shardCount;

        // Correctness:
        // sum(shard sizes)
        // == smallShardSize * smallShardCount + bigShardSize * bigShardCount
        // == smallShardSize * (shardCount - bigShardCount) + bigShardSize * bigShardCount
        // == smallShardSize * (shardCount - bigShardCount) + (smallShardSize + 1) * bigShardCount
        // == smallShardSize * (shardCount - bigShardCount + bigShardCount) + bigShardCount
        // == smallShardSize * shardCount + bigShardCount
        // == floor(totalSize / shardCount) * shardCount + remainder(totalSize / shardCount)
        // == totalSize

        List<List<T>> shards = new ArrayList<>();
        int i = 0;
        for (; i < bigShardCount * bigShardSize; i += bigShardSize) {
            shards.add(fullList.subList(i, i + bigShardSize));
        }
        for (; i < totalSize; i += smallShardSize) {
            shards.add(fullList.subList(i, i + smallShardSize));
        }
        while (shards.size() < shardCount) {
            shards.add(new ArrayList<>());
        }
        return shards;
    }

    /**
     * Move around predictably the tests in order to have a better uniformization of the tests in
     * each shard.
     */
    private void normalizeDistribution(List<IRemoteTest> listAllTests, int shardCount) {
        final int numRound = shardCount;
        final int distance = shardCount - 1;
        for (int i = 0; i < numRound; i++) {
            for (int j = 0; j < listAllTests.size(); j = j + distance) {
                // Push the test at the end
                IRemoteTest push = listAllTests.remove(j);
                listAllTests.add(push);
            }
        }
    }

    /**
     * Special handling for suite from {@link ITestSuite}. We aggregate the tests in the same shard
     * in order to optimize target_preparation step.
     *
     * @param tests the {@link List} of {@link IRemoteTest} for that shard.
     */
    private void aggregateSuiteModules(List<IRemoteTest> tests) {
        List<IRemoteTest> dupList = new ArrayList<>(tests);
        for (int i = 0; i < dupList.size(); i++) {
            if (dupList.get(i) instanceof ITestSuite) {
                // We iterate the other tests to see if we can find another from the same module.
                for (int j = i + 1; j < dupList.size(); j++) {
                    // If the test was not already merged
                    if (tests.contains(dupList.get(j))) {
                        if (dupList.get(j) instanceof ITestSuite) {
                            if (ModuleMerger.arePartOfSameSuite(
                                    (ITestSuite) dupList.get(i), (ITestSuite) dupList.get(j))) {
                                ModuleMerger.mergeSplittedITestSuite(
                                        (ITestSuite) dupList.get(i), (ITestSuite) dupList.get(j));
                                tests.remove(dupList.get(j));
                            }
                        }
                    }
                }
            }
        }
    }

    private void topBottom(List<List<IRemoteTest>> allShards, int shardCount) {
        // We only attempt this when the number of shard is pretty high
        if (shardCount < 4) {
            return;
        }
        // Generate approximate RuntimeHint for each shard
        int index = 0;
        List<SortShardObj> shardTimes = new ArrayList<>();
        for (List<IRemoteTest> shard : allShards) {
            long aggTime = 0L;
            CLog.d("++++++++++++++++++ SHARD %s +++++++++++++++", index);
            for (IRemoteTest test : shard) {
                if (test instanceof IRuntimeHintProvider) {
                    aggTime += ((IRuntimeHintProvider) test).getRuntimeHint();
                }
            }
            CLog.d("Shard %s approximate time: %s", index, TimeUtil.formatElapsedTime(aggTime));
            shardTimes.add(new SortShardObj(index, aggTime));
            index++;
            CLog.d("+++++++++++++++++++++++++++++++++++++++++++");
        }

        Collections.sort(shardTimes);
        if ((shardTimes.get(0).mAggTime - shardTimes.get(shardTimes.size() - 1).mAggTime)
                < 60 * 60 * 1000L) {
            return;
        }

        // take 30% top shard (10 shard = top 3 shards)
        for (int i = 0; i < (shardCount * 0.3); i++) {
            CLog.d(
                    "Top shard %s is index %s with %s",
                    i,
                    shardTimes.get(i).mIndex,
                    TimeUtil.formatElapsedTime(shardTimes.get(i).mAggTime));
            int give = shardTimes.get(i).mIndex;
            int receive = shardTimes.get(shardTimes.size() - 1 - i).mIndex;
            CLog.d("Giving from shard %s to shard %s", give, receive);
            for (int j = 0; j < (allShards.get(give).size() * (0.2f / (i + 1))); j++) {
                IRemoteTest givetest = allShards.get(give).remove(0);
                allShards.get(receive).add(givetest);
            }
        }
    }

    /** Object holder for shard, their index and their aggregated execution time. */
    private class SortShardObj implements Comparable<SortShardObj> {
        public final int mIndex;
        public final Long mAggTime;

        public SortShardObj(int index, long aggTime) {
            mIndex = index;
            mAggTime = aggTime;
        }

        @Override
        public int compareTo(SortShardObj obj) {
            return obj.mAggTime.compareTo(mAggTime);
        }
    }
}

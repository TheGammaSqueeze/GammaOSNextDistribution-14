/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.invoker;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationGroupMetricKey;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.skipped.SkipReason;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.TimeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A {@link ResultForwarder} that combines the results of a sharded test invocations. It only
 * reports completion of the invocation to the listeners once all sharded invocations are complete.
 *
 * <p>This class is not thread safe. It is expected that clients will lock on this class when
 * sending test results, to prevent invocation callbacks from being called out of order.
 */
public class ShardMainResultForwarder extends ResultForwarder implements ILogSaverListener {

    private final int mInitCount;
    private int mShardsRemaining;
    private long mTotalElapsed = 0L;
    private boolean mStartReported = false;

    private long mFirstShardEndTime = 0L;
    private IInvocationContext mOriginalContext;
    private List<IInvocationContext> mShardContextList;
    private int mShardIndex = 0;

    private Map<String, Long> mInvocationMetrics = new HashMap<>();

    /**
     * Create a {@link ShardMainResultForwarder}.
     *
     * @param listeners the list of {@link ITestInvocationListener} to forward results to when all
     *     shards are completed
     * @param expectedShards the number of shards
     */
    public ShardMainResultForwarder(List<ITestInvocationListener> listeners, int expectedShards) {
        super(listeners);
        mShardsRemaining = expectedShards;
        mInitCount = expectedShards;
        mShardContextList = new ArrayList<>();
    }

    @Override
    public List<ITestInvocationListener> getListeners() {
        return super.getListeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        if (!mStartReported) {
            mOriginalContext = context;
            super.invocationStarted(context);
            mStartReported = true;
        } else {
            Integer shardIndex = null;
            if (context.getConfigurationDescriptor() != null) {
                shardIndex = context.getConfigurationDescriptor().getShardIndex();
            }
            int index = mShardIndex;
            if (shardIndex != null) {
                index = shardIndex;
            }
            // Track serials used in each shard.
            mOriginalContext.addSerialsFromShard(index, context.getSerials());
            mShardContextList.add(context);
            mShardIndex++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        // one of the shards failed. Fail the whole invocation
        // TODO: does any extra logging need to be done ?
        super.invocationFailed(cause);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationFailed(FailureDescription failure) {
        // one of the shards failed. Fail the whole invocation
        super.invocationFailed(failure);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationSkipped(SkipReason reason) {
        // Shouldn't really happen, but if one shard is skipped, report skip.
        super.invocationSkipped(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        invocationEnded(elapsedTime, null);
    }

    /** More detailed callback to differentiate which shard finished. */
    public void invocationEnded(long elapsedTime, IInvocationContext context) {
        mTotalElapsed += elapsedTime;
        if (mInitCount == mShardsRemaining) {
            mFirstShardEndTime = System.currentTimeMillis();
        }
        mShardsRemaining--;
        if (context == null) {
            // Fallback to copy all if we didn't get the right callback.
            copyShardBuildInfoToMain(mOriginalContext, mShardContextList, true);
        } else {
            copyShardBuildInfoToMain(
                    mOriginalContext, Arrays.asList(context), mShardsRemaining <= 0);
        }
        if (mShardsRemaining <= 0) {
            // TODO: consider logging all shard final times.
            CLog.logAndDisplay(
                    LogLevel.INFO,
                    "There was %s between the first and last shard ended.",
                    TimeUtil.formatElapsedTime(System.currentTimeMillis() - mFirstShardEndTime));

            super.invocationEnded(mTotalElapsed);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        for (ITestInvocationListener listener : getListeners()) {
            try {
                // Forward the testLogSaved event to ILogSaverListener
                if (listener instanceof ILogSaverListener) {
                    ((ILogSaverListener) listener)
                            .testLogSaved(dataName, dataType, dataStream, logFile);
                }
            } catch (Exception e) {
                CLog.e("Exception while invoking %s#testLogSaved", listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /** Only forward the testLog instead of saving the log first. */
    public void testLogForward(
            String dataName, LogDataType dataType, InputStreamSource dataStream) {
        for (ITestInvocationListener listener : getListeners()) {
            if (listener instanceof LogSaverResultForwarder) {
                // If the listener is a log saver, we should simply forward the testLog not save
                // again.
                ((LogSaverResultForwarder) listener).testLogForward(dataName, dataType, dataStream);
            } else {
                try {
                    listener.testLog(dataName, dataType, dataStream);
                } catch (RuntimeException e) {
                    CLog.e(
                            "RuntimeException while invoking %s#testLog",
                            listener.getClass().getName());
                    CLog.e(e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        for (ITestInvocationListener listener : getListeners()) {
            try {
                // Forward the logAssociation call
                if (listener instanceof ILogSaverListener) {
                    ((ILogSaverListener) listener).logAssociation(dataName, logFile);
                }
            } catch (RuntimeException e) {
                CLog.e("Failed to provide the log association");
                CLog.e(e);
            }
        }
    }

    /**
     * Copy the build info from the shard builds to the main build in the original invocation
     * context.
     *
     * @param main the original {@link IInvocationContext} from the main invocation.
     * @param shardContexts the list of {@link IInvocationContext}s, one for each shard invocation.
     */
    private void copyShardBuildInfoToMain(
            IInvocationContext main, List<IInvocationContext> shardContexts, boolean lastContext) {
        for (IInvocationContext shard : shardContexts) {
            for (String deviceName : shard.getDeviceConfigNames()) {
                IBuildInfo shardBuild = shard.getBuildInfo(deviceName);
                IBuildInfo mainBuild = main.getBuildInfo(deviceName);
                if (mainBuild != null) {
                    // Copy attributes
                    for (Entry<String, String> entry : shardBuild.getBuildAttributes().entrySet()) {
                        mainBuild.addBuildAttribute(entry.getKey(), entry.getValue());
                    }
                    // Copy file reference
                    for (String vKey : shardBuild.getVersionedFileKeys()) {
                        if (mainBuild.getVersionedFile(vKey) == null) {
                            VersionedFile shardFile = shardBuild.getVersionedFile(vKey);
                            mainBuild.setFile(vKey, shardFile.getFile(), shardFile.getVersion());
                        }
                    }
                } else {
                    // Should not happen
                    CLog.e(
                            "Found a device '%s' in shard configuration but not in parent"
                                    + " configuration.",
                            deviceName);
                }
            }
            // Copy invocation metrics to main
            MultiMap<String, String> attributes = shard.getAttributes();
            for (InvocationGroupMetricKey key : InvocationGroupMetricKey.values()) {
                Set<String> attKeys = new HashSet<>(attributes.keySet());
                for (String attKey : attKeys) {
                    if (attKey.startsWith(key.toString() + ":")) {
                        List<String> values = attributes.get(attKey);
                        if (values == null) {
                            continue;
                        }
                        attributes.remove(attKey);
                        for (String val : values) {
                            if (key.shouldAdd()) {
                                long baseValue = 0L;
                                if (mInvocationMetrics.get(attKey) != null) {
                                    baseValue = mInvocationMetrics.get(attKey);
                                }
                                try {
                                    long newVal = baseValue + Long.parseLong(val);
                                    mInvocationMetrics.put(attKey, newVal);
                                } catch (NumberFormatException e) {
                                    CLog.e(e);
                                }
                            } else {
                                main.addInvocationAttribute(attKey, val);
                            }
                        }
                    }
                }
            }
            for (InvocationMetricKey key : InvocationMetricKey.values()) {
                if (!attributes.containsKey(key.toString())) {
                    continue;
                }
                List<String> values = attributes.get(key.toString());
                if (values == null) {
                    continue;
                }
                attributes.remove(key.toString());
                for (String val : values) {
                    if (key.shouldAdd()) {
                        long baseValue = 0L;
                        if (mInvocationMetrics.get(key.toString()) != null) {
                            baseValue = mInvocationMetrics.get(key.toString());
                        }
                        try {
                            long newVal = baseValue + Long.parseLong(val);
                            mInvocationMetrics.put(key.toString(), newVal);
                        } catch (NumberFormatException e) {
                            // Ignored, it's just not a number
                        }
                    } else {
                        main.addInvocationAttribute(key.toString(), val);
                    }
                }
            }
            if (lastContext) {
                for (Entry<String, Long> entryMetric : mInvocationMetrics.entrySet()) {
                    main.addInvocationAttribute(
                            entryMetric.getKey(), Long.toString(entryMetric.getValue()));
                }
                mInvocationMetrics.clear();
            }
        }
    }
}

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

package com.android.server.connectivity.mdns;

import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * The query scheduler class for calculating next query tasks parameters.
 * <p>
 * The class is not thread-safe and needs to be used on a consistent thread.
 */
public class MdnsQueryScheduler {

    /**
     * The argument for tracking the query tasks status.
     */
    public static class ScheduledQueryTaskArgs {
        public final QueryTaskConfig config;
        public final long timeToRun;
        public final long minTtlExpirationTimeWhenScheduled;
        public final long sessionId;

        ScheduledQueryTaskArgs(@NonNull QueryTaskConfig config, long timeToRun,
                long minTtlExpirationTimeWhenScheduled, long sessionId) {
            this.config = config;
            this.timeToRun = timeToRun;
            this.minTtlExpirationTimeWhenScheduled = minTtlExpirationTimeWhenScheduled;
            this.sessionId = sessionId;
        }
    }

    @Nullable
    private ScheduledQueryTaskArgs mLastScheduledQueryTaskArgs;

    public MdnsQueryScheduler() {
    }

    /**
     * Cancel the scheduled run. The method needed to be called when the scheduled task need to
     * be canceled and rescheduling is not need.
     */
    public void cancelScheduledRun() {
        mLastScheduledQueryTaskArgs = null;
    }

    /**
     * Calculates ScheduledQueryTaskArgs for rescheduling the current task. Returns null if the
     * rescheduling is not necessary.
     */
    @Nullable
    public ScheduledQueryTaskArgs maybeRescheduleCurrentRun(long now,
            long minRemainingTtl, long lastSentTime, long sessionId) {
        if (mLastScheduledQueryTaskArgs == null) {
            return null;
        }
        if (!mLastScheduledQueryTaskArgs.config.shouldUseQueryBackoff()) {
            return null;
        }

        final long timeToRun = calculateTimeToRun(mLastScheduledQueryTaskArgs,
                mLastScheduledQueryTaskArgs.config, now, minRemainingTtl, lastSentTime);

        if (timeToRun <= mLastScheduledQueryTaskArgs.timeToRun) {
            return null;
        }

        mLastScheduledQueryTaskArgs = new ScheduledQueryTaskArgs(mLastScheduledQueryTaskArgs.config,
                timeToRun,
                minRemainingTtl + now,
                sessionId);
        return mLastScheduledQueryTaskArgs;
    }

    /**
     *  Calculates the ScheduledQueryTaskArgs for the next run.
     */
    @NonNull
    public ScheduledQueryTaskArgs scheduleNextRun(
            @NonNull QueryTaskConfig currentConfig,
            long minRemainingTtl,
            long now,
            long lastSentTime,
            long sessionId) {
        final QueryTaskConfig nextRunConfig = currentConfig.getConfigForNextRun();
        final long timeToRun;
        if (mLastScheduledQueryTaskArgs == null) {
            timeToRun = now + nextRunConfig.delayUntilNextTaskWithoutBackoffMs;
        } else {
            timeToRun = calculateTimeToRun(mLastScheduledQueryTaskArgs,
                    nextRunConfig, now, minRemainingTtl, lastSentTime);
        }
        mLastScheduledQueryTaskArgs = new ScheduledQueryTaskArgs(nextRunConfig, timeToRun,
                minRemainingTtl + now,
                sessionId);
        return mLastScheduledQueryTaskArgs;
    }

    /**
     *  Calculates the ScheduledQueryTaskArgs for the initial run.
     */
    public ScheduledQueryTaskArgs scheduleFirstRun(@NonNull QueryTaskConfig taskConfig,
            long now, long minRemainingTtl, long currentSessionId) {
        mLastScheduledQueryTaskArgs = new ScheduledQueryTaskArgs(taskConfig, now /* timeToRun */,
                now + minRemainingTtl/* minTtlExpirationTimeWhenScheduled */,
                currentSessionId);
        return mLastScheduledQueryTaskArgs;
    }

    private static long calculateTimeToRun(@NonNull ScheduledQueryTaskArgs taskArgs,
            QueryTaskConfig queryTaskConfig, long now, long minRemainingTtl, long lastSentTime) {
        final long baseDelayInMs = queryTaskConfig.delayUntilNextTaskWithoutBackoffMs;
        if (!queryTaskConfig.shouldUseQueryBackoff()) {
            return lastSentTime + baseDelayInMs;
        }
        if (minRemainingTtl <= 0) {
            // There's no service, or there is an expired service. In any case, schedule for the
            // minimum time, which is the base delay.
            return lastSentTime + baseDelayInMs;
        }
        // If the next TTL expiration time hasn't changed, then use previous calculated timeToRun.
        if (lastSentTime < now
                && taskArgs.minTtlExpirationTimeWhenScheduled == now + minRemainingTtl) {
            // Use the original scheduling time if the TTL has not changed, to avoid continuously
            // rescheduling to 80% of the remaining TTL as time passes
            return taskArgs.timeToRun;
        }
        return Math.max(now + (long) (0.8 * minRemainingTtl), lastSentTime + baseDelayInMs);
    }
}

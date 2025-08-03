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

package com.android.federatedcompute.services.training.util;

import android.annotation.NonNull;
import android.content.Context;
import android.os.PowerManager;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.BatteryInfo;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.PhFlags;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.internal.annotations.VisibleForTesting;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utilities for checking that the device is currently in an acceptable state for training models.
 */
public class TrainingConditionsChecker {
    private static final String TAG = TrainingConditionsChecker.class.getSimpleName();
    private final BatteryInfo mBatteryInfo;
    private final PowerManager mPowerManager;
    private final Clock mClock;
    private final Flags mFlags;
    private final long mThrottlePeriodMillis;
    private final AtomicLong mLastConditionCheckTimeMillis;

    private static volatile TrainingConditionsChecker sSingletonInstance;

    /**
     * Result of the training condition check. We rely on JobScheduler for unmetered network and
     * device idle check.
     */
    public enum Condition {
        /** Battery is too low to train. */
        BATTERY_NOT_OK,
        /** Device is thermally throttled, and hence not ready to train. */
        THERMALS_NOT_OK,
    }

    @VisibleForTesting
    TrainingConditionsChecker(
            BatteryInfo batteryInfo, PowerManager powerManager, Flags flags, @NonNull Clock clock) {
        this.mBatteryInfo = batteryInfo;
        this.mPowerManager = powerManager;
        this.mFlags = flags;
        this.mThrottlePeriodMillis = mFlags.getTrainingConditionCheckThrottlePeriodMillis();
        this.mClock = clock;
        this.mLastConditionCheckTimeMillis = new AtomicLong(0L);
    }

    /** Gets an instance of {@link TrainingConditionsChecker}. */
    public static TrainingConditionsChecker getInstance(Context context) {
        if (sSingletonInstance == null) {
            synchronized (TrainingConditionsChecker.class) {
                if (sSingletonInstance == null) {
                    Flags flags = PhFlags.getInstance();
                    sSingletonInstance =
                            new TrainingConditionsChecker(
                                    new BatteryInfo(context.getApplicationContext(), flags),
                                    context.getSystemService(PowerManager.class),
                                    flags,
                                    MonotonicClock.getInstance());
                }
            }
        }
        return sSingletonInstance;
    }

    private boolean deviceThermalsOkForTraining() {
        if (mPowerManager == null) {
            // If the device does not expose a PowerManager service, then we can't determine
            // idleness, and then we err on the side of caution and return false.
            LogUtil.w(TAG, "PowerManager is not available when do background training");
            return false;
        }
        return mPowerManager.getCurrentThermalStatus() < mFlags.getThermalStatusToThrottle();
    }

    /**
     * Checks whether conditions allow for federated training. Returns a set of conditions if
     * insufficient, or an empty set if training should be allowed.
     */
    public Set<Condition> checkAllConditionsForFlTraining(TrainingConstraints trainingConstraints) {
        long nowMillis = mClock.currentTimeMillis();

        // Throttling is enabled.
        if (mThrottlePeriodMillis > 0) {
            if (nowMillis - mLastConditionCheckTimeMillis.get() < mThrottlePeriodMillis) {
                LogUtil.i(
                        TAG,
                        "training condition check is throttled %d %d",
                        nowMillis,
                        mLastConditionCheckTimeMillis.get());
                return EnumSet.noneOf(Condition.class);
            } else {
                mLastConditionCheckTimeMillis.set(nowMillis);
            }
        }
        EnumSet<Condition> conditions = EnumSet.noneOf(Condition.class);
        boolean requireBatteryNotLow = trainingConstraints.requiresSchedulerBatteryNotLow();

        if (!mBatteryInfo.batteryOkForTraining(requireBatteryNotLow)) {
            conditions.add(Condition.BATTERY_NOT_OK);
        }

        if (!deviceThermalsOkForTraining()) {
            conditions.add(Condition.THERMALS_NOT_OK);
        }
        return conditions;
    }
}

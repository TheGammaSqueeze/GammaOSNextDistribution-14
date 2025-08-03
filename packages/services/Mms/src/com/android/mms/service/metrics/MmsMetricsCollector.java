/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.mms.service.metrics;

import static com.android.mms.MmsStatsLog.INCOMING_MMS;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS;

import android.app.StatsManager;
import android.content.Context;
import android.util.Log;
import android.util.StatsEvent;

import androidx.annotation.VisibleForTesting;

import com.android.mms.IncomingMms;
import com.android.mms.MmsStatsLog;
import com.android.mms.OutgoingMms;
import com.android.internal.util.ConcurrentUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Implements statsd pullers for Mms.
 *
 * <p>This class registers pullers to statsd, which will be called once a day to obtain mms
 * statistics that cannot be sent to statsd in real time.
 */
public class MmsMetricsCollector implements StatsManager.StatsPullAtomCallback {
    private static final String TAG = MmsMetricsCollector.class.getSimpleName();
    /** Disables various restrictions to ease debugging during development. */
    private static final boolean DBG = false; // STOPSHIP if true
    private static final long MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();
    private static final long MILLIS_PER_SECOND = Duration.ofSeconds(1).toMillis();
    /**
     * Sets atom pull cool down to 23 hours to help enforcing privacy requirement.
     *
     * <p>Applies to certain atoms. The interval of 23 hours leaves some margin for pull operations
     * that occur once a day.
     */
    private static final long MIN_COOLDOWN_MILLIS =
            DBG ? 10L * MILLIS_PER_SECOND : 23L * MILLIS_PER_HOUR;
    private final PersistMmsAtomsStorage mStorage;
    private final StatsManager mStatsManager;


    public MmsMetricsCollector(Context context) {
        this(context, new PersistMmsAtomsStorage(context));
    }

    @VisibleForTesting
    public MmsMetricsCollector(Context context, PersistMmsAtomsStorage storage) {
        mStorage = storage;
        mStatsManager = context.getSystemService(StatsManager.class);
        if (mStatsManager != null) {
            registerAtom(INCOMING_MMS);
            registerAtom(OUTGOING_MMS);
            Log.d(TAG, "[MmsMetricsCollector]: registered atoms");
        } else {
            Log.e(TAG, "[MmsMetricsCollector]: could not get StatsManager, "
                    + "atoms not registered");
        }
    }

    private static StatsEvent buildStatsEvent(IncomingMms mms) {
        return MmsStatsLog.buildStatsEvent(
                INCOMING_MMS,
                mms.getRat(),
                mms.getResult(),
                mms.getRoaming(),
                mms.getSimSlotIndex(),
                mms.getIsMultiSim(),
                mms.getIsEsim(),
                mms.getCarrierId(),
                mms.getAvgIntervalMillis(),
                mms.getMmsCount(),
                mms.getRetryId(),
                mms.getHandledByCarrierApp(),
                mms.getIsManagedProfile());
    }

    private static StatsEvent buildStatsEvent(OutgoingMms mms) {
        return MmsStatsLog.buildStatsEvent(
                OUTGOING_MMS,
                mms.getRat(),
                mms.getResult(),
                mms.getRoaming(),
                mms.getSimSlotIndex(),
                mms.getIsMultiSim(),
                mms.getIsEsim(),
                mms.getCarrierId(),
                mms.getAvgIntervalMillis(),
                mms.getMmsCount(),
                mms.getIsFromDefaultApp(),
                mms.getRetryId(),
                mms.getHandledByCarrierApp(),
                mms.getIsManagedProfile());
    }

    @Override
    public int onPullAtom(int atomTag, List<StatsEvent> data) {
        switch (atomTag) {
            case INCOMING_MMS:
                return pullIncomingMms(data);
            case OUTGOING_MMS:
                return pullOutgoingMms(data);
            default:
                Log.e(TAG, String.format("unexpected atom ID %d", atomTag));
                return StatsManager.PULL_SKIP;
        }
    }

    private int pullIncomingMms(List<StatsEvent> data) {
        List<IncomingMms> incomingMmsList = mStorage.getIncomingMms(MIN_COOLDOWN_MILLIS);
        if (incomingMmsList != null) {
            // MMS List is already shuffled when MMS were inserted.
            incomingMmsList.forEach(mms -> data.add(buildStatsEvent(mms)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Log.w(TAG, "INCOMING_MMS pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullOutgoingMms(List<StatsEvent> data) {
        List<OutgoingMms> outgoingMmsList = mStorage.getOutgoingMms(MIN_COOLDOWN_MILLIS);
        if (outgoingMmsList != null) {
            // MMS List is already shuffled when MMS were inserted.
            outgoingMmsList.forEach(mms -> data.add(buildStatsEvent(mms)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Log.w(TAG, "OUTGOING_MMS pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    /** Registers a pulled atom ID {@code atomId}. */
    private void registerAtom(int atomId) {
        mStatsManager.setPullAtomCallback(atomId, /* metadata= */ null,
                ConcurrentUtils.DIRECT_EXECUTOR, this);
    }

    /** Returns the {@link PersistMmsAtomsStorage} backing the puller. */
    public PersistMmsAtomsStorage getAtomsStorage() {
        return mStorage;
    }
}

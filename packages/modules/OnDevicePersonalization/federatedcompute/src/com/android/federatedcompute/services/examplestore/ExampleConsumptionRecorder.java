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

package com.android.federatedcompute.services.examplestore;

import android.annotation.Nullable;
import android.federatedcompute.common.ExampleConsumption;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Records information regarding example store accesses, including example store collection name,
 * example selection criteria and resumption token.
 */
@ThreadSafe
public class ExampleConsumptionRecorder {
    @GuardedBy("this")
    private final List<SingleQueryRecorder> mSingleQueryRecorders = new ArrayList<>();

    /** Records information for a single query. */
    @ThreadSafe
    public static class SingleQueryRecorder {
        private final String mTaskId;
        private final byte[] mCriteria;

        // The pair of example count and resumption token needs to be updated atomically, therefore
        // the
        // updates are wrapped in synchronized blocks instead of using AtomicReference and
        // AtomicLong.
        @GuardedBy("SingleQueryRecorder.this")
        private int mExampleCount;

        @GuardedBy("SingleQueryRecorder.this")
        private byte[] mResumptionToken;

        private SingleQueryRecorder(String taskId, byte[] criteria) {
            this.mTaskId = taskId;
            this.mCriteria = criteria;
            this.mExampleCount = 0;
            this.mResumptionToken = null;
        }

        /** Increment the number of examples that has been used, and update the resumption token. */
        public synchronized void incrementAndUpdateResumptionToken(
                @Nullable byte[] resumptionToken) {
            mExampleCount++;
            this.mResumptionToken =
                    resumptionToken == null
                            ? null
                            : Arrays.copyOf(resumptionToken, resumptionToken.length);
        }

        /** Returns a single recorded of {@link ExampleConsumption}. */
        public synchronized ExampleConsumption finishRecordingAndGet() {
            return new ExampleConsumption.Builder()
                    .setTaskId(mTaskId)
                    .setSelectionCriteria(mCriteria)
                    .setExampleCount(mExampleCount)
                    .setResumptionToken(mResumptionToken)
                    .build();
        }
    }

    /** Create a {@link SingleQueryRecorder} for the current task. */
    public synchronized SingleQueryRecorder createRecorderForTracking(
            String taskId, byte[] criteria) {
        SingleQueryRecorder recorder = new SingleQueryRecorder(taskId, criteria);
        mSingleQueryRecorders.add(recorder);
        return recorder;
    }

    /** Returns all recorded {@link ExampleConsumption}. */
    public synchronized ArrayList<ExampleConsumption> finishRecordingAndGet() {
        ArrayList<ExampleConsumption> exampleConsumptions = new ArrayList<>();
        for (SingleQueryRecorder recorder : mSingleQueryRecorders) {
            exampleConsumptions.add(recorder.finishRecordingAndGet());
        }
        return exampleConsumptions;
    }
}

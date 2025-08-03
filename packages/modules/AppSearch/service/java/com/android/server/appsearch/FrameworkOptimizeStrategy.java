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
package com.android.server.appsearch;

import android.annotation.NonNull;
import android.util.Log;

import com.android.server.appsearch.external.localstorage.AppSearchImpl;
import com.android.server.appsearch.external.localstorage.OptimizeStrategy;

import com.google.android.icing.proto.GetOptimizeInfoResultProto;

import java.util.Objects;

/**
 * An implementation of {@link OptimizeStrategy} will determine when to trigger {@link
 * AppSearchImpl#optimize()} in Jetpack environment.
 *
 * @hide
 */
public class FrameworkOptimizeStrategy implements OptimizeStrategy {
    private static final String TAG = "AppSearchOptimize";
    private final FrameworkAppSearchConfig mAppSearchConfig;
    FrameworkOptimizeStrategy(@NonNull FrameworkAppSearchConfig config) {
        mAppSearchConfig = Objects.requireNonNull(config);
    }

    @Override
    public boolean shouldOptimize(@NonNull GetOptimizeInfoResultProto optimizeInfo) {
        boolean wantsOptimize =
                optimizeInfo.getOptimizableDocs()
                        >= mAppSearchConfig.getCachedDocCountOptimizeThreshold()
                        || optimizeInfo.getEstimatedOptimizableBytes()
                        >= mAppSearchConfig.getCachedBytesOptimizeThreshold()
                        || optimizeInfo.getTimeSinceLastOptimizeMs()
                        >= mAppSearchConfig.getCachedTimeOptimizeThresholdMs();
        if (wantsOptimize &&
                optimizeInfo.getTimeSinceLastOptimizeMs()
                        < mAppSearchConfig.getCachedMinTimeOptimizeThresholdMs()) {
            // TODO(b/271890504): Produce a log message for statsd when we skip a potential
            //  compaction because the time since the last compaction has not reached
            //  the minimum threshold.
            Log.i(TAG, "Skipping optimization because time since last optimize ["
                    + optimizeInfo.getTimeSinceLastOptimizeMs()
                    + " ms] is lesser than the threshold for minimum time between optimizations ["
                    + mAppSearchConfig.getCachedMinTimeOptimizeThresholdMs() + " ms]");
            return false;
        }
        return wantsOptimize;
    }
}

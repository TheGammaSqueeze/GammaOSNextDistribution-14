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

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.testutil.FakeAppSearchConfig;

import com.android.server.appsearch.icing.proto.GetOptimizeInfoResultProto;
import com.android.server.appsearch.icing.proto.StatusProto;

import org.junit.Test;

// NOTE: The tests in this class are based on the underlying assumption that
// time_optimize_threshold > min_time_optimize_threshold. This ensures that setting
// timeSinceLastOptimize to time_optimize_threshold-1 does not make it lesser than
// min_time_optimize_threshold (otherwise shouldOptimize() would return false for test cases that
// check byteThreshold and docCountThreshold).
public class FrameworkOptimizeStrategyTest {
    FrameworkAppSearchConfig mAppSearchConfig = new FakeAppSearchConfig();
    FrameworkOptimizeStrategy mFrameworkOptimizeStrategy =
            new FrameworkOptimizeStrategy(mAppSearchConfig);

    @Test
    public void testTimeOptimizeThreshold_isGreaterThan_minTimeOptimizeThreshold() {
        assertThat(mAppSearchConfig.getCachedTimeOptimizeThresholdMs())
                .isGreaterThan(mAppSearchConfig.getCachedMinTimeOptimizeThresholdMs());
    }

    @Test
    public void testShouldNotOptimize_underAllThresholds() {
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                mAppSearchConfig.getCachedTimeOptimizeThresholdMs()-1)
                        .setEstimatedOptimizableBytes(
                                mAppSearchConfig.getCachedBytesOptimizeThreshold()-1)
                        .setOptimizableDocs(
                                mAppSearchConfig.getCachedDocCountOptimizeThreshold()-1)
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isFalse();
    }

    @Test
    public void testShouldOptimize_byteThreshold() {
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                mAppSearchConfig.getCachedTimeOptimizeThresholdMs()-1)
                        .setEstimatedOptimizableBytes(
                                mAppSearchConfig.getCachedBytesOptimizeThreshold())
                        .setOptimizableDocs(
                                mAppSearchConfig.getCachedDocCountOptimizeThreshold()-1)
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }

    @Test
    public void testShouldOptimize_timeThreshold() {
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                mAppSearchConfig.getCachedTimeOptimizeThresholdMs())
                        .setEstimatedOptimizableBytes(
                                mAppSearchConfig.getCachedBytesOptimizeThreshold()-1)
                        .setOptimizableDocs(
                                mAppSearchConfig.getCachedDocCountOptimizeThreshold()-1)
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }

    @Test
    public void testShouldOptimize_docCountThreshold() {
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                mAppSearchConfig.getCachedTimeOptimizeThresholdMs()-1)
                        .setEstimatedOptimizableBytes(
                                mAppSearchConfig.getCachedBytesOptimizeThreshold()-1)
                        .setOptimizableDocs(
                                mAppSearchConfig.getCachedDocCountOptimizeThreshold())
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }

    @Test
    public void testShouldNotOptimize_underMinTimeThreshold() {
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                mAppSearchConfig.getCachedMinTimeOptimizeThresholdMs()-1)
                        .setEstimatedOptimizableBytes(
                                mAppSearchConfig.getCachedBytesOptimizeThreshold())
                        .setOptimizableDocs(
                                mAppSearchConfig.getCachedDocCountOptimizeThreshold())
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isFalse();
    }
}

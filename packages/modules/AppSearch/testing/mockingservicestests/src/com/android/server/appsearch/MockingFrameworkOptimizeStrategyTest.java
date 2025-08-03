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
package com.android.server.appsearch;

import static com.android.internal.util.ConcurrentUtils.DIRECT_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.appsearch.icing.proto.GetOptimizeInfoResultProto;
import com.android.server.appsearch.icing.proto.StatusProto;

import org.junit.Rule;
import org.junit.Test;

// This class tests the scenario time_optimize_threshold < min_time_optimize_threshold (which
// shouldn't be the case in an ideal world) as opposed to FrameworkOptimizeStrategyTest which tests
// the scenario time_optimize_threshold > min_time_optimize_threshold.
public class MockingFrameworkOptimizeStrategyTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule
            mDeviceConfigRule = new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testShouldNotOptimize_overOtherThresholds_underMinTimeThreshold() {
        // Create FrameworkAppSearchConfig with min_time_optimize_threshold <
        // time_optimize_threshold
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_BYTES_OPTIMIZE_THRESHOLD,
                Integer.toString(147147),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(900),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_DOC_COUNT_OPTIMIZE_THRESHOLD,
                Integer.toString(369369),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(0),
                false);
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        FrameworkOptimizeStrategy mFrameworkOptimizeStrategy =
                new FrameworkOptimizeStrategy(appSearchConfig);
        // Create optimizeInfo with all values above respective thresholds.
        GetOptimizeInfoResultProto optimizeInfo =
                GetOptimizeInfoResultProto.newBuilder()
                        .setTimeSinceLastOptimizeMs(
                                appSearchConfig.getCachedTimeOptimizeThresholdMs()+1)
                        .setEstimatedOptimizableBytes(
                                appSearchConfig.getCachedBytesOptimizeThreshold()+1)
                        .setOptimizableDocs(
                                appSearchConfig.getCachedDocCountOptimizeThreshold()+1)
                        .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                        .build();

        // Verify shouldOptimize() returns true when
        // min_time_optimize_threshold(0) < time_optimize_threshold(900)
        // < timeSinceLastOptimize(901)
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();

        // Set min_time_optimize_threshold to a value greater than time_optimize_threshold
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                FrameworkAppSearchConfigImpl.KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(1000),
                false);

        // Verify shouldOptimize() returns false when
        // min_time_optimize_threshold(1000) > timeSinceLastOptimize(901)
        // > time_optimize_threshold(900)
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isFalse();
    }
}

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

package com.android.adservices.helpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.helpers.LatencyHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * Android Unit tests for {@link EpochComputationHelper}.
 *
 * <p>To run: atest AdservicesCollectorsHelperTest:com.android.adservices.helpers
 * .EpochComputationHelperTest
 */
public class EpochComputationHelperTest {
    private static final String EPOCH_COMPUTATION_DURATION = "EPOCH_COMPUTATION_DURATION";
    private LatencyHelper mEpochComputationHelper;
    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mEpochComputationHelper = EpochComputationHelper.getCollector(mInputStreamFilter);
        mEpochComputationHelper.startCollecting();
    }

    @Test
    public void testGetMetrics() throws Exception {
        String logcatOutput =
                "06-13 18:09:24.058 20765 20781 I TopicsEpochComputation: "
                        + "(EPOCH_COMPUTATION_DURATION:"
                        + " 14)";
        when(mInputStreamFilter.getStream(any(), any()))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> metrics = mEpochComputationHelper.getMetrics();
        assertThat(metrics.get(EPOCH_COMPUTATION_DURATION)).isEqualTo(14);
    }

    @Test
    public void testGetMetrics_emptyLogcat() throws Exception {
        String logcatOutput = "";
        when(mInputStreamFilter.getStream(any(), any()))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> metrics = mEpochComputationHelper.getMetrics();
        assertThat(metrics.containsKey(EPOCH_COMPUTATION_DURATION)).isFalse();
    }
}

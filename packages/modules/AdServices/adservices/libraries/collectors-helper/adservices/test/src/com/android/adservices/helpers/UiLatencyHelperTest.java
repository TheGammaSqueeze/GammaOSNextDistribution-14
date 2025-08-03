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

package com.android.adservices.helpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.LatencyHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Android Unit tests for {@link UiLatencyHelper}.
 *
 * <p>To run: atest CollectorsHelperAospTest:com.android.helpers.tests.UiLatencyHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class UiLatencyHelperTest {
    private static final String UI_NOTIFICATION_LATENCY_METRIC = "UI_NOTIFICATION_LATENCY_METRIC";
    private static final String UI_SETTINGS_LATENCY_METRIC = "UI_SETTINGS_LATENCY_METRIC";

    private static final String SAMPLE_UI_NOTIFICATION_LATENCY_OUTPUT =
            "06-13 18:09:24.058 20765 20781 D\n"
                    + " UiTestLabel: (UI_NOTIFICATION_LATENCY_METRIC: 14)";
    private static final String SAMPLE_UI_SETTINGS_LATENCY_OUTPUT =
            "06-13 18:09:24.058 20765 20781 D\n"
                    + " UiTestLabel: (UI_SETTINGS_LATENCY_METRIC: 200)";

    private LatencyHelper mUiLatencyHelper;

    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mUiLatencyHelper = UiLatencyHelper.getCollector(mInputStreamFilter);
    }

    /** Test getting metrics for single package. */
    @Test
    public void testGetMetrics() throws Exception {
        String outputString =
                SAMPLE_UI_NOTIFICATION_LATENCY_OUTPUT + "\n" + SAMPLE_UI_SETTINGS_LATENCY_OUTPUT;
        InputStream targetStream = new ByteArrayInputStream(outputString.getBytes());
        doReturn(targetStream).when(mInputStreamFilter).getStream(any(), any());
        Map<String, Long> topicsLatencyMetrics = mUiLatencyHelper.getMetrics();
        assertThat(topicsLatencyMetrics.get(UI_NOTIFICATION_LATENCY_METRIC)).isEqualTo(14);
        assertThat(topicsLatencyMetrics.get(UI_SETTINGS_LATENCY_METRIC)).isEqualTo(200);
    }

    /** Test getting no metrics for single package. */
    @Test
    public void testEmptyLogcat_noMetrics() throws Exception {
        String outputString = "";
        InputStream targetStream = new ByteArrayInputStream(outputString.getBytes());
        doReturn(targetStream).when(mInputStreamFilter).getStream(any(), any());
        Map<String, Long> topicsLatencyMetrics = mUiLatencyHelper.getMetrics();
        assertThat(topicsLatencyMetrics.containsKey(UI_SETTINGS_LATENCY_METRIC)).isFalse();
        assertThat(topicsLatencyMetrics.containsKey(UI_NOTIFICATION_LATENCY_METRIC)).isFalse();
    }
}

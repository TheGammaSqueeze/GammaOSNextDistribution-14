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

package com.android.helpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/**
 * To run, use atest CollectorsHelperAospTest:com.android.helpers.JSScriptEngineLatencyHelperTest
 */
public class JSScriptEngineLatencyHelperTest {
    private static final String TEST_FILTER_LABEL = "JSScriptEngine";
    private static final String SANDBOX_INIT_TIME_AVG = "SANDBOX_INIT_TIME_AVG";
    private static final String ISOLATE_CREATE_TIME_AVG = "ISOLATE_CREATE_TIME_AVG";
    private static final String WEBVIEW_EXECUTION_TIME_AVG = "WEBVIEW_EXECUTION_TIME_AVG";
    private static final String JAVA_EXECUTION_TIME_AVG = "JAVA_EXECUTION_TIME_AVG";
    private static final String NUM_ITERATIONS = "NUM_ITERATIONS";

    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Mock private Clock mClock;

    private LatencyHelper mJSScriptEngineLatencyHelper;
    private Instant mInstant = Clock.systemUTC().instant();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mClock.getZone()).thenReturn(ZoneId.of("UTC")); // Used by DateTimeFormatter.
        when(mClock.instant()).thenReturn(mInstant);
        mJSScriptEngineLatencyHelper =
                JSScriptEngineLatencyHelper.getCollector(mInputStreamFilter, mClock);
        mJSScriptEngineLatencyHelper.startCollecting();
    }

    @Test
    public void testInitTimeInLogcat() throws IOException {
        String logcatOutput =
                "06-13 18:09:24.022 20765 D JSScriptEngine: (SANDBOX_INIT_TIME: 102)\n"
                    + "06-29 02:47:32.030 31075 D JSScriptEngine: (ISOLATE_CREATE_TIME: 1)\n"
                    + "06-13 18:09:24.058 20765 D JSScriptEngine: (JAVA_EXECUTION_TIME: 43)\n"
                    + "06-13 18:09:24.058 31075 D JSScriptEngine: (WEBVIEW_EXECUTION_TIME: 23)\n"
                    + "06-13 18:09:24.129 20765 D JSScriptEngine: (SANDBOX_INIT_TIME: 45)\n"
                    + "06-13 18:09:24.130 31075 D JSScriptEngine: (ISOLATE_CREATE_TIME: 1)\n"
                    + "06-13 18:09:24.152 20765 D JSScriptEngine: (JAVA_EXECUTION_TIME: 19)\n"
                    + "06-29 02:47:31.824 31075 D JSScriptEngine: (WEBVIEW_EXECUTION_TIME: 29)";

        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> actual = mJSScriptEngineLatencyHelper.getMetrics();

        assertThat(actual.get(SANDBOX_INIT_TIME_AVG)).isEqualTo((102 + 45) / 2);
        assertThat(actual.get(ISOLATE_CREATE_TIME_AVG)).isEqualTo((1 + 1) / 2);
        assertThat(actual.get(JAVA_EXECUTION_TIME_AVG)).isEqualTo((43 + 19) / 2);
        assertThat(actual.get(WEBVIEW_EXECUTION_TIME_AVG)).isEqualTo((23 + 29) / 2);
        assertThat(actual.get(NUM_ITERATIONS)).isEqualTo(2);
    }

    @Test
    public void testWithEmptyLogcat() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream("".getBytes()));
        Map<String, Long> actual = mJSScriptEngineLatencyHelper.getMetrics();
        for (Long val : actual.values()) {
            assertThat(val).isEqualTo(0);
        }
    }

    @Test
    public void testInputStreamThrowsException() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenThrow(new IOException());
        Map<String, Long> actual = mJSScriptEngineLatencyHelper.getMetrics();
        for (Long val : actual.values()) {
            assertThat(val).isEqualTo(0);
        }
    }
}

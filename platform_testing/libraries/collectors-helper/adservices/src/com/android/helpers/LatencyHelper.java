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

import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class LatencyHelper implements ICollectorHelper<Long> {
    private static final String TAG = LatencyHelper.class.getSimpleName();
    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final Clock mClock;
    private final ProcessInputForLatencyMetrics mEventStreamProcessor;
    private InputStreamFilter mInputStreamFilter;
    private Instant mInstant;

    public LatencyHelper(
            ProcessInputForLatencyMetrics eventStreamProcessor,
            InputStreamFilter inputStreamFilter) {
        mEventStreamProcessor = eventStreamProcessor;
        mInputStreamFilter = inputStreamFilter;
        mClock = Clock.systemUTC();
    }

    @VisibleForTesting
    public LatencyHelper(
            ProcessInputForLatencyMetrics eventStreamProcessor,
            InputStreamFilter inputStreamFilter,
            Clock clock) {
        mEventStreamProcessor = eventStreamProcessor;
        mInputStreamFilter = inputStreamFilter;
        mClock = clock;
    }

    public static LatencyHelper getLogcatLatencyHelper(
            ProcessInputForLatencyMetrics eventStreamProcessor) {
        return new LatencyHelper(eventStreamProcessor, new LogcatStreamFilter());
    }

    @Override
    public boolean startCollecting() {
        mInstant = mClock.instant();
        return true;
    }

    @Override
    public Map<String, Long> getMetrics() {
        try {
            return mEventStreamProcessor.processInput(
                    mInputStreamFilter.getStream(mEventStreamProcessor.getTestLabel(), mInstant));
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Failed to collect " + mEventStreamProcessor.getTestLabel() + " metrics.",
                    e);
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    public interface ProcessInputForLatencyMetrics {
        String getTestLabel();

        Map<String, Long> processInput(InputStream inputStream) throws IOException;
    }

    public interface InputStreamFilter {
        InputStream getStream(String filterLabel, Instant startTime) throws IOException;
    }

    public static class LogcatStreamFilter implements InputStreamFilter {
        @Override
        public InputStream getStream(String filterLabel, Instant startTime) throws IOException {
            ProcessBuilder pb =
                    new ProcessBuilder(
                            Arrays.asList(
                                    "logcat",
                                    "-s",
                                    filterLabel + ":D",
                                    "-t",
                                    LOG_TIME_FORMATTER.format(startTime)));
            return pb.start().getInputStream();
        }
    }
}

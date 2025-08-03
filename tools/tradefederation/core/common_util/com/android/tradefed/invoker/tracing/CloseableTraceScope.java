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
package com.android.tradefed.invoker.tracing;

import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;

import com.google.common.base.Enums;
import com.google.common.base.Optional;

import perfetto.protos.PerfettoTrace.TrackEvent;

import javax.annotation.Nullable;

/** A scoped class that allows to report tracing section via try-with-resources */
public class CloseableTraceScope implements AutoCloseable {

    private static final String DEFAULT_CATEGORY = "invocation";
    private final String category;
    private final String name;
    private final long startTime;

    @Nullable private final ActiveTrace trace;

    /**
     * Report a scoped trace.
     *
     * @param category The category of the operation
     * @param name The name for reporting the section
     */
    public CloseableTraceScope(String category, String name) {
        this(TracingLogger.getActiveTrace(), category, name);
    }

    /** Constructor. */
    public CloseableTraceScope(String name) {
        this(DEFAULT_CATEGORY, name);
    }

    public CloseableTraceScope(ActiveTrace trace, String name) {
        this(trace, DEFAULT_CATEGORY, name);
    }

    /** Constructor for reporting scope from threads. */
    public CloseableTraceScope() {
        this(DEFAULT_CATEGORY, Thread.currentThread().getName());
    }

    private CloseableTraceScope(ActiveTrace trace, String category, String name) {
        this.category = category;
        this.name = name;
        this.startTime = System.currentTimeMillis();
        this.trace = trace;
        if (this.trace == null) {
            return;
        }
        int threadId = (int) Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        this.trace.reportTraceEvent(
                category, name, threadId, threadName, TrackEvent.Type.TYPE_SLICE_BEGIN);
    }

    @Override
    public void close() {
        if (this.trace == null) {
            return;
        }
        int threadId = (int) Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        this.trace.reportTraceEvent(
                category, name, threadId, threadName, TrackEvent.Type.TYPE_SLICE_END);
        Optional<InvocationMetricKey> optionalKey =
                Enums.getIfPresent(InvocationMetricKey.class, name);
        if (optionalKey.isPresent()
                && Thread.currentThread().getId() == this.trace.reportingThreadId()) {
            InvocationMetricLogger.addInvocationPairMetrics(
                    optionalKey.get(), startTime, System.currentTimeMillis());
        }
    }
}

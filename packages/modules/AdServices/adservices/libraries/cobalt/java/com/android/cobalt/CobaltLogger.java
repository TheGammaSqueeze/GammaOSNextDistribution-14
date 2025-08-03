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

package com.android.cobalt;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/** The API for logging metrics with Cobalt. */
public interface CobaltLogger {
    /**
     * Logs an event for an OCCURRENCE metric.
     *
     * @param metricId registered ID of the OCCURRENCE metric which the event occurred for
     * @param count number of occurrences
     * @param eventVector registered events codes of the event which occurred
     * @return An optional ListenableFuture that is ready when logging completes
     */
    ListenableFuture<Void> logOccurrence(long metricId, long count, List<Integer> eventVector);

    /**
     * Logs an event for a STRING metric.
     *
     * @param metricId registered ID of the STRING metric which the event occurred for
     * @param stringValue the string to log
     * @param eventVector registered events codes of the event which occurred
     * @return An optional ListenableFuture that is ready when logging completes
     */
    ListenableFuture<Void> logString(long metricId, String stringValue, List<Integer> eventVector);
}

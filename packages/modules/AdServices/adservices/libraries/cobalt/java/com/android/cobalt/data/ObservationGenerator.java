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

package com.android.cobalt.data;

import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

/** A generator of observations from the event data in the database. */
public interface ObservationGenerator {
    /**
     * Generate the observations that occurred for a single day and report.
     *
     * @param dayIndex the day index to generate observations for
     * @param eventData the data for events that occurred that are relevant to the day and Report
     * @return the observations to store in the DB for later sending, contained in
     *     UnencryptedObservationBatches with their metadata
     */
    ImmutableList<UnencryptedObservationBatch> generateObservations(
            int dayIndex,
            ImmutableListMultimap<SystemProfile, EventRecordAndSystemProfile> eventData);
}

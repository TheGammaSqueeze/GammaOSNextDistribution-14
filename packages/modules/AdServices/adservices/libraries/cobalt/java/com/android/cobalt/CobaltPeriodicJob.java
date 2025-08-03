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

/**
 * The API for periodic work in Cobalt that can be called from some work scheduling framework.
 *
 * <p>This API should be ideally be called by clients 3-4 times per day and no less than 1 time per
 * day to ensure data is aggregated and sent to Cobalt's backend.
 */
public interface CobaltPeriodicJob {

    /**
     * Generates observations from aggregated data, encrypts them, and send them to Cobalt's
     * backend.
     *
     * @return a ListenableFuture that can be used to determine when observation generation is
     *     complete
     */
    ListenableFuture<Void> generateAggregatedObservations();
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import java.time.Duration;
import java.time.Instant;


/** Interface for interacting with a local or remote {@link Event} store. */
interface EventQuerier<E extends Event> {
    /**
     * Gets the next unseen {@link Event} which wasn't logged before {@code earliestLogTime},
     * or blocks until one is logged.
     *
     * <p>After {@code timeout}, null will be returned.
     */
    E poll(Instant earliestLogTime, Duration timeout);
}

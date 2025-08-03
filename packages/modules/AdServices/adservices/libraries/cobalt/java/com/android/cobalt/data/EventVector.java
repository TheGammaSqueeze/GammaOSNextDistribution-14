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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.List;

/** An ordered list of event codes. */
@AutoValue
public abstract class EventVector {
    /** The list of event codes. */
    public abstract ImmutableList<Integer> eventCodes();

    /**
     * Creates an {@link EventVector}.
     *
     * <p>Used by Room to instantiate objects.
     */
    public static EventVector create(List<Integer> eventCodes) {
        return new AutoValue_EventVector(ImmutableList.copyOf(eventCodes));
    }

    /**
     * Creates an {@link EventVector}.
     *
     * <p>Used by Room to instantiate objects.
     */
    public static EventVector create(Integer... eventCodes) {
        return new AutoValue_EventVector(ImmutableList.copyOf(eventCodes));
    }
}

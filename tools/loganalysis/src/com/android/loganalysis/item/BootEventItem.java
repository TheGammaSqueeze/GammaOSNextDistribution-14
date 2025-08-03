/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.loganalysis.item;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** An {@link IItem} used to store boot event info logged in event log. */
public class BootEventItem extends GenericItem {

    /** Constant for JSON output */
    public static final String EVENT_NAME = "EVENT_NAME";

    public static final String EVENT_DURATION = "DURATION";

    private static final Set<String> ATTRIBUTES =
            new HashSet<String>(Arrays.asList(EVENT_NAME, EVENT_DURATION));

    /** The constructor for {@link BootEventItem}. */
    public BootEventItem() {
        super(ATTRIBUTES);
    }

    public String getEventName() {
        return (String) getAttribute(EVENT_NAME);
    }

    public void setEventName(String eventName) {
        setAttribute(EVENT_NAME, eventName);
    }

    public Double getDuration() {
        return (Double) getAttribute(EVENT_DURATION);
    }

    public void setDuration(Double eventDuration) {
        setAttribute(EVENT_DURATION, eventDuration);
    }
}

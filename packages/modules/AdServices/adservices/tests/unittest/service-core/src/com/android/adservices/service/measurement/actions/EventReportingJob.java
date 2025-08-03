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

package com.android.adservices.service.measurement.actions;

import java.util.Objects;

public final class EventReportingJob implements Action {
    public final long mTimestamp;

    public EventReportingJob(long timestamp) {
        mTimestamp = timestamp;
    }

    @Override
    public long getComparable() {
        return mTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventReportingJob)) return false;
        EventReportingJob that = (EventReportingJob) o;
        return mTimestamp == that.mTimestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTimestamp);
    }
}

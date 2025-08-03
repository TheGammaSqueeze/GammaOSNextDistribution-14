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

package com.android.server.healthconnect.storage.datatypehelpers.aggregation;

import android.annotation.IntDef;

import java.util.List;

/**
 * Class which represents timestamp of the data to aggregate.
 *
 * @hide
 */
public class AggregationTimestamp implements Comparable<AggregationTimestamp> {

    @IntDef({GROUP_BORDER, INTERVAL_START, INTERVAL_END})
    public @interface TimestampType {}

    // Note: values matter for ordering in compareBy
    public static final int GROUP_BORDER = 0;
    public static final int INTERVAL_START = 1;
    public static final int INTERVAL_END = 2;

    private static final List<String> TYPE_PRINT_NAMES = List.of("GROUP_BORDER", "START", "END");

    @TimestampType private final int mType;
    private final long mTime;
    private AggregationRecordData mParentRecord;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public AggregationTimestamp(int type, long time) {
        mTime = time;
        mType = type;
    }

    int getType() {
        return mType;
    }

    long getTime() {
        return mTime;
    }

    AggregationRecordData getParentData() {
        return mParentRecord;
    }

    AggregationTimestamp setParentData(AggregationRecordData parentRecord) {
        mParentRecord = parentRecord;
        return this;
    }

    @Override
    public int compareTo(AggregationTimestamp o) {
        if (this.equals(o)) {
            return 0;
        }

        if (getTime() == o.getTime()) {
            // We sort in the following order:
            // all group borders first as group intervals are inclusive for start,
            // exclusive for end. Then all intervals starts, then all intervals ends.
            // If types are equal, sort by priority, if still equal by last modified time.
            if (getType() != o.getType()) {
                return getType() - o.getType();
            }

            // Equal type and time can happen only if both are either starts or ends of intervals,
            // hence parentData is not null for both. ParentData is null only for group border.
            return getParentData().compareTo(o.getParentData());
        } else if (getTime() < o.getTime()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public String toString() {
        return "Timestamp{type="
                + TYPE_PRINT_NAMES.get(mType)
                + ", time="
                + mTime
                + ", parentData="
                + (mParentRecord == null ? "null" : mParentRecord)
                + "}";
    }
}

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

package com.android.eventlib.events;

import android.content.Context;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.queries.SerializableQuery;
import com.android.queryable.queries.SerializableQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;

import java.io.Serializable;

/**
 * Implementation of {@link Event} which can be used for events not covered by other
 * {@link Event} subclasses.
 *
 * <p>To use, set custom data as {@link #tag()} and {@link #data()}.
 */
public final class CustomEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link CustomEvent} events. */
    public static CustomEventQuery queryPackage(String packageName) {
        return new CustomEventQuery(packageName);
    }

    public static final class CustomEventQuery
            extends EventLogsQuery<CustomEvent, CustomEventQuery> {
        StringQueryHelper<CustomEventQuery> mTag = new StringQueryHelper<>(this);
        SerializableQueryHelper<CustomEventQuery> mData = new SerializableQueryHelper<>(this);

        private CustomEventQuery(String packageName) {
            super(CustomEvent.class, packageName);
        }

        /** Filter for a particular {@link CustomEvent#tag()}. */
        @CheckResult
        public StringQuery<CustomEventQuery> whereTag() {
            return mTag;
        }

        /** Filter for a particular {@link CustomEvent#data()}. */
        @CheckResult
        public SerializableQuery<CustomEventQuery> whereData() {
            return mData;
        }

        @Override
        protected boolean filter(CustomEvent event) {
            if (!mTag.matches(event.mTag)) {
                return false;
            }
            if (!mData.matches(event.mData)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(CustomEvent.class, this)
                    .field("tag", mTag)
                    .field("data", mData)
                    .toString();
        }
    }

    /** Begins logging a {@link CustomEvent}. */
    public static CustomEventLogger logger(Context context) {
        return new CustomEventLogger(context);
    }

    public static final class CustomEventLogger extends EventLogger<CustomEvent> {
        private CustomEventLogger(Context context) {
            super(context, new CustomEvent());
        }

        /** Sets the {@link CustomEvent#tag()}. */
        public CustomEventLogger setTag(String tag) {
            mEvent.mTag = tag;
            return this;
        }

        /** Sets the {@link CustomEvent#data()}. */
        public CustomEventLogger setData(Serializable data) {
            mEvent.mData = data;
            return this;
        }
    }

    protected String mTag;
    protected Serializable mData;

    /** Get the tag set using {@link CustomEventLogger#setTag(String)}. */
    public String tag() {
        return mTag;
    }

    /** Get the tag set using {@link CustomEventLogger#setData(Serializable)}. */
    public Serializable data() {
        return mData;
    }

    @Override
    public String toString() {
        return "CustomEvent{"
                + "tag='" + mTag + "'"
                + ", data=" + mData
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}



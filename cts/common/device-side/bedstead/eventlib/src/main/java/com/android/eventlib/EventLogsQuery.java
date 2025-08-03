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

import android.os.UserHandle;

import com.android.bedstead.nene.users.UserReference;
import com.android.queryable.Queryable;
import com.android.queryable.queries.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Interface to provide additional restrictions on an {@link Event} query.
 */
public abstract class EventLogsQuery<E extends Event, F extends EventLogsQuery>
        extends EventLogs<E> implements Queryable {
    private final Class<E> mEventClass;
    private final String mPackageName;
    private final transient Set<Function<E, Boolean>> mFilters = new HashSet<>();
    private transient UserHandle mUserHandle = null; // null is default, meaning current user

    protected EventLogsQuery(Class<E> eventClass, String packageName) {
        if (eventClass == null || packageName == null) {
            throw new NullPointerException();
        }
        mQuerier = new RemoteEventQuerier<>(packageName, this);
        mEventClass = eventClass;
        mPackageName = packageName;
    }

    /** Get the package name being filtered for. */
    protected String getPackageName() {
        return mPackageName;
    }

    protected Class<E> eventClass() {
        return mEventClass;
    }

    private final transient EventQuerier<E> mQuerier;

    @Override
    protected EventQuerier<E> getQuerier() {
        return mQuerier;
    }

    /** Apply a lambda filter to the results. */
    public F filter(Function<E, Boolean> filter) {
        mFilters.add(filter);
        return (F) this;
    }

    /**
     * Returns true if {@code E} matches custom and default filters for this {@link Event} subclass.
     */
    protected final boolean filterAll(E event) {
        if (mFilters != null) {
            // Filters will be null when called remotely
            for (Function<E, Boolean> filter : mFilters) {
                if (!filter.apply(event)) {
                    return false;
                }
            }
        }
        return filter(event);
    }

    /** Returns true if {@code E} matches the custom filters for this {@link Event} subclass. */
    protected abstract boolean filter(E event);

    /** Query a package running on another user. */
    public F onUser(UserHandle userHandle) {
        if (userHandle == null) {
            throw new NullPointerException();
        }
        mUserHandle = userHandle;
        return (F) this;
    }

    /** Query a package running on another user. */
    public F onUser(UserReference userReference) {
        return onUser(userReference.userHandle());
    }

    UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    public boolean isEmptyQuery() {
        // TODO(276741712): This is not currently used - but should be implemented correctly for
        //  subclasses
        return true;
    }

    public static class ToStringBuilder {

        private final List<String> mFields = new ArrayList<>();
        private final int mNumberOfCustomFilters;

        private ToStringBuilder(String eventType, EventLogsQuery<?, ?> query) {
            mFields.add("type=" + eventType);
            mFields.add("packageName=" + query.mPackageName);
            if (query.mUserHandle != null) {
                mFields.add("user=" + query.mUserHandle);
            }
            mNumberOfCustomFilters = query.mFilters.size();
        }

        public ToStringBuilder field(String fieldName, Query<?> query) {
            mFields.add(query.describeQuery(fieldName));
            return this;
        }

        @Override
        public String toString() {
            if (mNumberOfCustomFilters > 0) {
                mFields.add(mNumberOfCustomFilters + " custom filters");
            }
            return "{" + Queryable.joinQueryStrings(mFields) + "}";
        }
    }

    public <N extends Event> ToStringBuilder toStringBuilder(
            Class<N> eventClass, EventLogsQuery<N, ?> query) {
        return new ToStringBuilder(eventClass.getSimpleName(), query);
    }

    @Override
    public String toString() {
        return describeQuery("Query");
    }
}

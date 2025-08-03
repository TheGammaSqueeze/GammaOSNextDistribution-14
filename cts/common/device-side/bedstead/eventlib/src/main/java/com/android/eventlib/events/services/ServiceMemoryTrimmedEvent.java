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

package com.android.eventlib.events.services;

import android.app.Service;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.ServiceInfo;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.ServiceQueryHelper;

/**
 * Event logged when {@link Service#onTrimMemory(int)}
 */
public class ServiceMemoryTrimmedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceMemoryTrimmedEvent} events. */
    public static ServiceMemoryTrimmedEventQuery queryPackage(String packageName) {
        return new ServiceMemoryTrimmedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceMemoryTrimmedEvent}. */
    public static final class ServiceMemoryTrimmedEventQuery extends
            EventLogsQuery<ServiceMemoryTrimmedEvent,
            ServiceMemoryTrimmedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceMemoryTrimmedEventQuery> mService =
                new ServiceQueryHelper<>(this);
        IntegerQueryHelper<ServiceMemoryTrimmedEventQuery> mLevel = new IntegerQueryHelper<>(this);

        private ServiceMemoryTrimmedEventQuery(String packageName) {
            super(ServiceMemoryTrimmedEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceMemoryTrimmedEventQuery> whereService() {
            return mService;
        }

        /** Query {@link Service}. */
        @CheckResult
        public IntegerQueryHelper<ServiceMemoryTrimmedEventQuery> whereLevel() {
            return mLevel;
        }

        @Override
        protected boolean filter(ServiceMemoryTrimmedEvent event) {
            if (!mLevel.matches(event.mLevel)) {
                return false;
            }
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceMemoryTrimmedEvent.class, this)
                    .field("level", mLevel)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceMemoryTrimmedEvent}. */
    public static ServiceMemoryTrimmedEventLogger logger(Service service,
            String serviceName, int level) {
        return new ServiceMemoryTrimmedEventLogger(service, serviceName, level);
    }

    /** {@link EventLogger} for {@link ServiceMemoryTrimmedEvent}. */
    public static final class ServiceMemoryTrimmedEventLogger extends
            EventLogger<ServiceMemoryTrimmedEvent> {
        private ServiceMemoryTrimmedEventLogger(Service service,
                String serviceName, int level) {
            super(service, new ServiceMemoryTrimmedEvent());
            mEvent.mLevel = level;
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceMemoryTrimmedEventLogger setService(
                String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

        /** Sets the level. */
        public ServiceMemoryTrimmedEventLogger setLevel(int level) {
            mEvent.mLevel = level;
            return this;
        }

    }

    protected ServiceInfo mService;
    protected int mLevel;

    /**
     * The level passed into {@link Service#onTrimMemory(int)}.
     */
    public int level() {
        return mLevel;
    }

    /** Information about the {@link Service} which received the intent. */
    public ServiceInfo service() {
        return mService;
    }

    @Override
    public String toString() {
        return "ServiceMemoryTrimmedEvent{"
                + ", service=" + mService
                + ", level=" + mLevel
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

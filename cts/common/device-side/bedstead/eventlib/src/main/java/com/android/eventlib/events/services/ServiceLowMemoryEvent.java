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
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.ServiceQueryHelper;

/**
 * Event logged when {@link Service#onLowMemory()}
 */
public class ServiceLowMemoryEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceLowMemoryEvent} events. */
    public static ServiceLowMemoryEvent.ServiceLowMemoryEventQuery queryPackage(
            String packageName) {
        return new ServiceLowMemoryEvent.ServiceLowMemoryEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceLowMemoryEvent}. */
    public static final class ServiceLowMemoryEventQuery
            extends EventLogsQuery<ServiceLowMemoryEvent,
            ServiceLowMemoryEvent.ServiceLowMemoryEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceLowMemoryEvent.ServiceLowMemoryEventQuery> mService =
                new ServiceQueryHelper<>(this);

        private ServiceLowMemoryEventQuery(String packageName) {
            super(ServiceLowMemoryEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceLowMemoryEvent.ServiceLowMemoryEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceLowMemoryEvent event) {
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceLowMemoryEvent.class, this)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceLowMemoryEvent}. */
    public static ServiceLowMemoryEvent.ServiceLowMemoryEventLogger logger(Service service,
            String serviceName) {
        return new ServiceLowMemoryEvent.ServiceLowMemoryEventLogger(service, serviceName);
    }

    /** {@link EventLogger} for {@link ServiceLowMemoryEvent}. */
    public static final class ServiceLowMemoryEventLogger extends
            EventLogger<ServiceLowMemoryEvent> {
        private ServiceLowMemoryEventLogger(Service service,
                String serviceName) {
            super(service, new ServiceLowMemoryEvent());
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceLowMemoryEvent.ServiceLowMemoryEventLogger setService(
                String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

    }

    protected ServiceInfo mService;

    /** Information about the {@link Service} which received the intent. */
    public ServiceInfo service() {
        return mService;
    }

    @Override
    public String toString() {
        return "ServiceLowMemoryEvent{"
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

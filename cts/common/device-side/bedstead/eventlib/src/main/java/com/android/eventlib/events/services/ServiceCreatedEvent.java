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
 * Event logged when {@link Service#onCreate()}
 */
public class ServiceCreatedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceCreatedEvent} events. */
    public static ServiceCreatedEvent.ServiceCreatedEventQuery queryPackage(String packageName) {
        return new ServiceCreatedEvent.ServiceCreatedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceCreatedEvent}. */
    public static final class ServiceCreatedEventQuery
            extends EventLogsQuery<ServiceCreatedEvent,
            ServiceCreatedEvent.ServiceCreatedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceCreatedEvent.ServiceCreatedEventQuery> mService =
                new ServiceQueryHelper<>(this);

        private ServiceCreatedEventQuery(String packageName) {
            super(ServiceCreatedEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceCreatedEvent.ServiceCreatedEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceCreatedEvent event) {
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceCreatedEvent.class, this)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceCreatedEvent}. */
    public static ServiceCreatedEvent.ServiceCreatedEventLogger logger(Service service,
            String serviceName) {
        return new ServiceCreatedEvent.ServiceCreatedEventLogger(service, serviceName);
    }

    /** {@link EventLogger} for {@link ServiceCreatedEvent}. */
    public static final class ServiceCreatedEventLogger extends EventLogger<ServiceCreatedEvent> {

        // TODO(b/214187100) Use ServiceInfo here instead of a String to identify the service.
        private ServiceCreatedEventLogger(Service service,
                String serviceName) {
            super(service, new ServiceCreatedEvent());
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceCreatedEvent.ServiceCreatedEventLogger setService(
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
        return "ServiceCreatedEvent{"
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

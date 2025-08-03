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
 * Event logged when {@link Service#onDestroy()}
 */
public class ServiceDestroyedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceDestroyedEvent} events. */
    public static ServiceDestroyedEventQuery queryPackage(String packageName) {
        return new ServiceDestroyedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceDestroyedEvent}. */
    public static final class ServiceDestroyedEventQuery
            extends EventLogsQuery<ServiceDestroyedEvent, ServiceDestroyedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceDestroyedEventQuery> mService = new ServiceQueryHelper<>(this);

        private ServiceDestroyedEventQuery(String packageName) {
            super(ServiceDestroyedEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceDestroyedEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceDestroyedEvent event) {
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceDestroyedEvent.class, this)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceDestroyedEvent}. */
    public static ServiceDestroyedEventLogger logger(Service service,
            String serviceName) {
        return new ServiceDestroyedEventLogger(service, serviceName);
    }

    /** {@link EventLogger} for {@link ServiceDestroyedEvent}. */
    public static final class ServiceDestroyedEventLogger extends
            EventLogger<ServiceDestroyedEvent> {

        // TODO(b/214187100) Use ServiceInfo here instead of a String to identify the service.
        private ServiceDestroyedEventLogger(Service service,
                String serviceName) {
            super(service, new ServiceDestroyedEvent());
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceDestroyedEventLogger setService(String serviceName) {
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
        return "ServiceDestroyedEvent{"
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

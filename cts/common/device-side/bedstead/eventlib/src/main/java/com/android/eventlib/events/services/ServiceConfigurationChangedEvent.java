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
import android.content.res.Configuration;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.ServiceInfo;
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.ServiceQueryHelper;

/**
 * Event logged when {@link Service#onConfigurationChanged(Configuration)}
 */
public class ServiceConfigurationChangedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceConfigurationChangedEvent} events. */
    public static ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery
            queryPackage(String packageName) {
        return new ServiceConfigurationChangedEvent
                .ServiceConfigurationChangedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceConfigurationChangedEvent}. */
    public static final class ServiceConfigurationChangedEventQuery
            extends EventLogsQuery<ServiceConfigurationChangedEvent,
            ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery>
                mService = new ServiceQueryHelper<>(this);

        private ServiceConfigurationChangedEventQuery(String packageName) {
            super(ServiceConfigurationChangedEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceConfigurationChangedEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceConfigurationChangedEvent event) {
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceConfigurationChangedEvent.class, this)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceConfigurationChangedEvent}. */
    public static ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventLogger logger(
            Service service, String serviceName,
            Configuration configuration) {
        return new ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventLogger(
                service, serviceName, configuration);
    }

    /** {@link EventLogger} for {@link ServiceConfigurationChangedEvent}. */
    public static final class ServiceConfigurationChangedEventLogger extends
            EventLogger<ServiceConfigurationChangedEvent> {

        private ServiceConfigurationChangedEventLogger(Service service,
                String serviceName,
                Configuration configuration) {
            super(service, new ServiceConfigurationChangedEvent());
            mEvent.mConfiguration = configuration;
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventLogger setService(
                String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

        /** Sets the {@link Configuration} */
        public ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventLogger
                setConfiguration(Configuration configuration) {
            mEvent.mConfiguration = configuration;
            return this;
        }

    }

    protected ServiceInfo mService;
    protected Configuration mConfiguration;

    /**
     * The {@link Configuration} passed into {@link Service#onConfigurationChanged(Configuration)}.
     */
    public Configuration configuration() {
        return mConfiguration;
    }

    /** Information about the {@link Service} which received the configuration. */
    public ServiceInfo service() {
        return mService;
    }

    @Override
    public String toString() {
        return "ServiceConfigurationChangedEvent{"
                + " configuration=" + configuration()
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

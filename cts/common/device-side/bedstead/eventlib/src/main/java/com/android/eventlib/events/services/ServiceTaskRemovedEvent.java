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
import android.content.Intent;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.ServiceInfo;
import com.android.queryable.queries.IntentQuery;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.ServiceQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link Service#onTaskRemoved(Intent)}
 */
public class ServiceTaskRemovedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceTaskRemovedEvent} events. */
    public static ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery queryPackage(
            String packageName) {
        return new ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceTaskRemovedEvent}. */
    public static final class ServiceTaskRemovedEventQuery
            extends EventLogsQuery<ServiceTaskRemovedEvent,
            ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery> mService =
                new ServiceQueryHelper<>(this);
        IntentQueryHelper<ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery> mIntent =
                new IntentQueryHelper<>(this);

        private ServiceTaskRemovedEventQuery(String packageName) {
            super(ServiceTaskRemovedEvent.class, packageName);
        }

        /**
         * Query {@link Intent} passed into {@link Service#onTaskRemoved(Intent)}.
         */
        @CheckResult
        public IntentQuery<ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceTaskRemovedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mService.matches(event.mService)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ServiceTaskRemovedEvent.class, this)
                    .field("intent", mIntent)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceTaskRemovedEvent}. */
    public static ServiceTaskRemovedEvent.ServiceTaskRemovedEventLogger logger(Service service,
            String serviceName, Intent intent) {
        return new ServiceTaskRemovedEvent.ServiceTaskRemovedEventLogger(service, serviceName,
                intent);
    }

    /** {@link EventLogger} for {@link ServiceTaskRemovedEvent}. */
    public static final class ServiceTaskRemovedEventLogger extends
            EventLogger<ServiceTaskRemovedEvent> {

        private ServiceTaskRemovedEventLogger(Service service,
                String serviceName,
                Intent intent) {
            super(service, new ServiceTaskRemovedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceTaskRemovedEvent.ServiceTaskRemovedEventLogger setService(
                String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

        /** Sets the {@link Intent} that was used to bind to the service. */
        public ServiceTaskRemovedEvent.ServiceTaskRemovedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

    }

    protected ServiceInfo mService;
    protected SerializableParcelWrapper<Intent> mIntent;

    /**
     * The {@link Intent} passed into {@link Service#onTaskRemoved(Intent)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /** Information about the {@link Service} which received the intent. */
    public ServiceInfo service() {
        return mService;
    }

    @Override
    public String toString() {
        return "ServiceTaskRemovedEvent{"
                + " intent=" + intent()
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

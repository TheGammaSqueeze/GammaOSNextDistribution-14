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
 * Event logged when {@link Service#onUnbind(Intent)}
 */
public class ServiceUnboundEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceUnboundEvent} events. */
    public static ServiceUnboundEventQuery queryPackage(String packageName) {
        return new ServiceUnboundEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceUnboundEvent}. */
    public static final class ServiceUnboundEventQuery
            extends EventLogsQuery<ServiceUnboundEvent,
            ServiceUnboundEvent.ServiceUnboundEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceUnboundEvent.ServiceUnboundEventQuery> mService =
                new ServiceQueryHelper<>(this);
        IntentQueryHelper<ServiceUnboundEvent.ServiceUnboundEventQuery> mIntent =
                new IntentQueryHelper<>(this);

        private ServiceUnboundEventQuery(String packageName) {
            super(ServiceUnboundEvent.class, packageName);
        }

        /**
         * Query {@link Intent} passed into {@link Service#onUnbind(Intent)}.
         */
        @CheckResult
        public IntentQuery<ServiceUnboundEventQuery> whereIntent() {
            return mIntent;
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceUnboundEventQuery> whereService() {
            return mService;
        }

        @Override
        protected boolean filter(ServiceUnboundEvent event) {
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
            return toStringBuilder(ServiceUnboundEvent.class, this)
                    .field("intent", mIntent)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceUnboundEvent}. */
    public static ServiceUnboundEventLogger logger(Service service,
            String serviceName, Intent intent) {
        return new ServiceUnboundEventLogger(service, serviceName, intent);
    }

    /** {@link EventLogger} for {@link ServiceUnboundEvent}. */
    public static final class ServiceUnboundEventLogger extends EventLogger<ServiceUnboundEvent> {

        // TODO(b/214187100) Use ServiceInfo here instead of a String to identify the service.
        private ServiceUnboundEventLogger(Service service,
                String serviceName,
                Intent intent) {
            super(service, new ServiceUnboundEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceUnboundEventLogger setService(String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

        /** Sets the {@link Intent} that was used to bind to the service. */
        public ServiceUnboundEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

    }

    protected ServiceInfo mService;
    protected SerializableParcelWrapper<Intent> mIntent;

    /**
     * The {@link Intent} passed into {@link Service#onUnbind(Intent)}.
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
        return "ServiceUnboundEvent{"
                + " intent=" + intent()
                + ", service=" + mService
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQuery;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.ServiceQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link Service#onStartCommand(Intent, int, int)}
 */
public class ServiceStartedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ServiceStartedEvent} events. */
    public static ServiceStartedEventQuery queryPackage(String packageName) {
        return new ServiceStartedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ServiceStartedEvent}. */
    public static final class ServiceStartedEventQuery extends EventLogsQuery<ServiceStartedEvent,
            ServiceStartedEventQuery> {

        private static final long serialVersionUID = 1;

        ServiceQueryHelper<ServiceStartedEventQuery> mService = new ServiceQueryHelper<>(this);
        IntentQueryHelper<ServiceStartedEventQuery> mIntent = new IntentQueryHelper<>(this);
        IntegerQueryHelper<ServiceStartedEventQuery> mFlags = new IntegerQueryHelper<>(this);
        IntegerQueryHelper<ServiceStartedEventQuery> mStartId = new IntegerQueryHelper<>(this);

        private ServiceStartedEventQuery(String packageName) {
            super(ServiceStartedEvent.class, packageName);
        }

        /** Query {@link Service}. */
        @CheckResult
        public ServiceQuery<ServiceStartedEventQuery> whereService() {
            return mService;
        }

        /**
         * Query {@link Intent} passed into {@link Service#onBind(Intent)}.
         */
        @CheckResult
        public IntentQuery<ServiceStartedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Query {@link Service}. */
        @CheckResult
        public IntegerQueryHelper<ServiceStartedEventQuery> whereFlags() {
            return mFlags;
        }

        /** Query {@link Service}. */
        @CheckResult
        public IntegerQueryHelper<ServiceStartedEventQuery> whereStartId() {
            return mStartId;
        }

        @Override
        protected boolean filter(ServiceStartedEvent event) {
            if (!mFlags.matches(event.mFlags)) {
                return false;
            }
            if (!mStartId.matches(event.mStartId)) {
                return false;
            }
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
            return toStringBuilder(ServiceStartedEvent.class, this)
                    .field("flags", mFlags)
                    .field("startId", mStartId)
                    .field("intent", mIntent)
                    .field("service", mService)
                    .toString();
        }
    }


    /** Begins logging a {@link ServiceStartedEvent}. */
    public static ServiceStartedEventLogger logger(Service service,
            String serviceName, Intent intent, int flags, int startId) {
        return new ServiceStartedEventLogger(service, serviceName, intent, flags, startId);
    }

    /** {@link EventLogger} for {@link ServiceStartedEvent}. */
    public static final class ServiceStartedEventLogger extends EventLogger<ServiceStartedEvent> {

        // TODO(b/214187100) Use ServiceInfo here instead of a String to identify the service.
        private ServiceStartedEventLogger(Service service,
                String serviceName, Intent intent, int flags, int startId) {
            super(service, new ServiceStartedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mFlags = flags;
            mEvent.mStartId = startId;
            setService(serviceName);
        }

        /** Sets the {@link Service} which received this event. */
        public ServiceStartedEventLogger setService(String serviceName) {
            mEvent.mService = ServiceInfo.builder()
                    .serviceClass(serviceName)
                    .build();
            return this;
        }

        /** Sets the {@link Intent} supplied to {@link android.content.Context#startService}. */
        public ServiceStartedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the flags used for the start request of this service. */
        public ServiceStartedEventLogger setFlags(int flags) {
            mEvent.mFlags = flags;
            return this;
        }

        /** Sets the startId. */
        public ServiceStartedEventLogger setStartId(int startId) {
            mEvent.mStartId = startId;
            return this;
        }

    }

    protected ServiceInfo mService;
    protected SerializableParcelWrapper<Intent> mIntent;
    protected int mFlags;
    protected int mStartId;

    /**
     * The {@link Intent} passed into {@link Service#onStartCommand(Intent, int, int)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The flags passed into {@link Service#onStartCommand(Intent, int, int)}.
     */
    public int flags() {
        return mFlags;
    }

    /**
     * The startId passed into {@link Service#onStartCommand(Intent, int, int)}.
     */
    public int startId() {
        return mStartId;
    }

    /** Information about the {@link Service} which received the intent. */
    public ServiceInfo service() {
        return mService;
    }

    @Override
    public String toString() {
        return "ServiceStartedEvent{"
                + ", service=" + mService
                + ", flags=" + mFlags
                + ", startId=" + mStartId
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

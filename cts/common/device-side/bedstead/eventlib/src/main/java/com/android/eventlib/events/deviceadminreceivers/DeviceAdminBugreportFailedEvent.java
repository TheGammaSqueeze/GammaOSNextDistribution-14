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

package com.android.eventlib.events.deviceadminreceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DeviceAdminReceiverInfo;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)}
 * is called.
 */
public final class DeviceAdminBugreportFailedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminBugreportFailedEvent} events. */
    public static DeviceAdminBugreportFailedEventQuery queryPackage(String packageName) {
        return new DeviceAdminBugreportFailedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminBugreportFailedEvent}. */
    public static final class DeviceAdminBugreportFailedEventQuery
            extends EventLogsQuery<DeviceAdminBugreportFailedEvent,
            DeviceAdminBugreportFailedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminBugreportFailedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminBugreportFailedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        IntegerQueryHelper<DeviceAdminBugreportFailedEventQuery> mFailureCode =
                new IntegerQueryHelper<>(this);

        private DeviceAdminBugreportFailedEventQuery(String packageName) {
            super(DeviceAdminBugreportFailedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminBugreportFailedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminBugreportFailedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Query {@code failureCode} passed into
         * {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)}.
         */
        @CheckResult
        public IntegerQuery<DeviceAdminBugreportFailedEventQuery> whereFailureCode() {
            return mFailureCode;
        }

        @Override
        protected boolean filter(DeviceAdminBugreportFailedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mFailureCode.matches(event.mFailureCode)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminBugreportFailedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("failureCode", mFailureCode)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminBugreportFailedEvent}. */
    public static DeviceAdminBugreportFailedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context, Intent intent, int failureCode) {
        return new DeviceAdminBugreportFailedEventLogger(
                deviceAdminReceiver, context, intent, failureCode);
    }

    /** {@link EventLogger} for {@link DeviceAdminBugreportFailedEvent}. */
    public static final class DeviceAdminBugreportFailedEventLogger
            extends EventLogger<DeviceAdminBugreportFailedEvent> {
        private DeviceAdminBugreportFailedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, Intent intent, int failureCode) {
            super(context, new DeviceAdminBugreportFailedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mFailureCode = failureCode;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportFailedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportFailedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportFailedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminBugreportFailedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code failureCode} which was received. */
        public DeviceAdminBugreportFailedEventLogger setFailureCode(int failureCode) {
            mEvent.mFailureCode = failureCode;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected int mFailureCode;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    /**
     * The {@code failureCode} passed into
     * {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)}.
     */
    public int failureCode() {
        return mFailureCode;
    }

    @Override
    public String toString() {
        return "DeviceAdminBugreportFailedEvent{"
                + " intent=" + intent()
                + ", failureCode=" + mFailureCode
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

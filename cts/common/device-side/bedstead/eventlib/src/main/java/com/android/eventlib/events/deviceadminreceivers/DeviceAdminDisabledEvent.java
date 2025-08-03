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
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onDisabled(Context, Intent)} is called.
 */
public final class DeviceAdminDisabledEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminDisabledEvent} events. */
    public static DeviceAdminDisabledEventQuery queryPackage(String packageName) {
        return new DeviceAdminDisabledEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminDisabledEvent}. */
    public static final class DeviceAdminDisabledEventQuery
            extends EventLogsQuery<DeviceAdminDisabledEvent, DeviceAdminDisabledEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminDisabledEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminDisabledEventQuery> mIntent = new IntentQueryHelper<>(this);

        private DeviceAdminDisabledEventQuery(String packageName) {
            super(DeviceAdminDisabledEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into {@link DeviceAdminReceiver#onDisabled(Context, Intent)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminDisabledEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminDisabledEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        @Override
        protected boolean filter(DeviceAdminDisabledEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminDisabledEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminDisabledEvent}. */
    public static DeviceAdminDisabledEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
        return new DeviceAdminDisabledEventLogger(deviceAdminReceiver, context, intent);
    }

    /** {@link EventLogger} for {@link DeviceAdminDisabledEvent}. */
    public static final class DeviceAdminDisabledEventLogger
            extends EventLogger<DeviceAdminDisabledEvent> {
        private DeviceAdminDisabledEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
            super(context, new DeviceAdminDisabledEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminDisabledEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminDisabledEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminDisabledEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminDisabledEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into {@link DeviceAdminReceiver#onDisabled(Context, Intent)}.
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

    @Override
    public String toString() {
        return "DeviceAdminDisabledEvent{"
                + " intent=" + intent()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
 * Event logged when {@link DeviceAdminReceiver#onBugreportSharingDeclined(Context, Intent)}
 * is called.
 */
public final class DeviceAdminBugreportSharingDeclinedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminBugreportSharingDeclinedEvent} events. */
    public static DeviceAdminBugreportSharingDeclinedEventQuery queryPackage(String packageName) {
        return new DeviceAdminBugreportSharingDeclinedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminBugreportSharingDeclinedEvent}. */
    public static final class DeviceAdminBugreportSharingDeclinedEventQuery
            extends EventLogsQuery<DeviceAdminBugreportSharingDeclinedEvent,
            DeviceAdminBugreportSharingDeclinedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminBugreportSharingDeclinedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminBugreportSharingDeclinedEventQuery> mIntent =
                new IntentQueryHelper<>(this);

        private DeviceAdminBugreportSharingDeclinedEventQuery(String packageName) {
            super(DeviceAdminBugreportSharingDeclinedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onBugreportSharingDeclined(Context, Intent)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminBugreportSharingDeclinedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminBugreportSharingDeclinedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        @Override
        protected boolean filter(DeviceAdminBugreportSharingDeclinedEvent event) {
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
            return toStringBuilder(DeviceAdminBugreportSharingDeclinedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminBugreportSharingDeclinedEvent}. */
    public static DeviceAdminBugreportSharingDeclinedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
        return new DeviceAdminBugreportSharingDeclinedEventLogger(
                deviceAdminReceiver, context, intent);
    }

    /** {@link EventLogger} for {@link DeviceAdminBugreportSharingDeclinedEvent}. */
    public static final class DeviceAdminBugreportSharingDeclinedEventLogger
            extends EventLogger<DeviceAdminBugreportSharingDeclinedEvent> {
        private DeviceAdminBugreportSharingDeclinedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, Intent intent) {
            super(context, new DeviceAdminBugreportSharingDeclinedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharingDeclinedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharingDeclinedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharingDeclinedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminBugreportSharingDeclinedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onBugreportSharingDeclined(Context, Intent)}.
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
        return "DeviceAdminBugreportSharingDeclinedEvent{"
                + " intent=" + intent()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

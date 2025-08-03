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
import com.android.queryable.queries.IntentQuery;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.LongQuery;
import com.android.queryable.queries.LongQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/** Event logged when {@link DeviceAdminReceiver#onSystemUpdatePending} is called. */
public class DeviceAdminSystemUpdatePendingEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminSystemUpdatePendingEvent} events. */
    public static DeviceAdminSystemUpdatePendingEventQuery queryPackage(String packageName) {
        return new DeviceAdminSystemUpdatePendingEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminSystemUpdatePendingEvent}. */
    public static final class DeviceAdminSystemUpdatePendingEventQuery
            extends EventLogsQuery<DeviceAdminSystemUpdatePendingEvent,
                    DeviceAdminSystemUpdatePendingEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminSystemUpdatePendingEventQuery>
                mDeviceAdminReceiver = new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminSystemUpdatePendingEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        LongQueryHelper<DeviceAdminSystemUpdatePendingEventQuery> mReceivedTime =
                new LongQueryHelper<>(this);

        private DeviceAdminSystemUpdatePendingEventQuery(String packageName) {
            super(DeviceAdminSystemUpdatePendingEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into {@link
         * DeviceAdminReceiver#onPasswordSucceeded(Context, Intent)}.
         */
        @CheckResult
        public IntentQuery<DeviceAdminSystemUpdatePendingEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminSystemUpdatePendingEventQuery>
        whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Query the received time passed into {@link
         * DeviceAdminReceiver#onSystemUpdatePending(Context, Intent, long)}.
         */
        @CheckResult
        public LongQuery<DeviceAdminSystemUpdatePendingEventQuery> whereReceivedTime() {
            return mReceivedTime;
        }

        @Override
        protected boolean filter(DeviceAdminSystemUpdatePendingEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mReceivedTime.matches(event.mReceivedTime)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminSystemUpdatePendingEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("receivedTime", mReceivedTime)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminSystemUpdatePendingEvent}. */
    public static DeviceAdminSystemUpdatePendingEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context,
            Intent intent,
            long receivedTime) {
        return new DeviceAdminSystemUpdatePendingEventLogger(
                deviceAdminReceiver, context, intent, receivedTime);
    }

    /** {@link EventLogger} for {@link DeviceAdminSystemUpdatePendingEvent}. */
    public static final class DeviceAdminSystemUpdatePendingEventLogger
            extends EventLogger<DeviceAdminSystemUpdatePendingEvent> {
        private DeviceAdminSystemUpdatePendingEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context,
                Intent intent,
                long receivedTime) {
            super(context, new DeviceAdminSystemUpdatePendingEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDeviceAdminReceiver(deviceAdminReceiver);
            setReceivedTime(receivedTime);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminSystemUpdatePendingEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminSystemUpdatePendingEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminSystemUpdatePendingEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminSystemUpdatePendingEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the received time. */
        public DeviceAdminSystemUpdatePendingEventLogger setReceivedTime(long receivedTime) {
            mEvent.mReceivedTime = receivedTime;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected long mReceivedTime;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into {@link
     * DeviceAdminReceiver#onPasswordSucceeded(Context, Intent)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The received time passed into {@link
     * DeviceAdminReceiver#onSystemUpdatePending(Context, Intent, long)}.
     */
    public long receivedTime() {
        return mReceivedTime;
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminSystemUpdatePendingEvent{"
                + " intent=" + intent()
                + ", receivedTime=" + receivedTime()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

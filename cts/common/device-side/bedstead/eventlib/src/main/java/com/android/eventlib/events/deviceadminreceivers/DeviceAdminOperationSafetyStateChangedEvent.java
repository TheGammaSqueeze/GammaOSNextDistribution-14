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
import com.android.queryable.queries.BooleanQuery;
import com.android.queryable.queries.BooleanQueryHelper;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, int, boolean)}
 * is called.
 */
public final class DeviceAdminOperationSafetyStateChangedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminOperationSafetyStateChangedEvent} events. */
    public static DeviceAdminOperationSafetyStateChangedEventQuery queryPackage(String packageName) {
        return new DeviceAdminOperationSafetyStateChangedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminOperationSafetyStateChangedEvent}. */
    public static final class DeviceAdminOperationSafetyStateChangedEventQuery
            extends EventLogsQuery<DeviceAdminOperationSafetyStateChangedEvent,
            DeviceAdminOperationSafetyStateChangedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminOperationSafetyStateChangedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntegerQueryHelper<DeviceAdminOperationSafetyStateChangedEventQuery> mReason =
                new IntegerQueryHelper<>(this);
        BooleanQueryHelper<DeviceAdminOperationSafetyStateChangedEventQuery> mIsSafe =
                new BooleanQueryHelper<>(this);

        private DeviceAdminOperationSafetyStateChangedEventQuery(String packageName) {
            super(DeviceAdminOperationSafetyStateChangedEvent.class, packageName);
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminOperationSafetyStateChangedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Query {@code reason} passed into
         * {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, int, boolean)}.
         */
        @CheckResult
        public IntegerQuery<DeviceAdminOperationSafetyStateChangedEventQuery> whereReason() {
            return mReason;
        }

        /**
         * Query {@code isSafe} passed into
         * {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, int, boolean)}.
         */
        @CheckResult
        public BooleanQuery<DeviceAdminOperationSafetyStateChangedEventQuery> whereIsSafe() {
            return mIsSafe;
        }

        @Override
        protected boolean filter(DeviceAdminOperationSafetyStateChangedEvent event) {
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mReason.matches(event.mReason)) {
                return false;
            }
            if (!mIsSafe.matches(event.mIsSafe)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminOperationSafetyStateChangedEvent.class, this)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("reason", mReason)
                    .field("isSafe", mIsSafe)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminOperationSafetyStateChangedEvent}. */
    public static DeviceAdminOperationSafetyStateChangedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context, int reason, boolean isSafe) {
        return new DeviceAdminOperationSafetyStateChangedEventLogger(
                deviceAdminReceiver, context, reason, isSafe);
    }

    /** {@link EventLogger} for {@link DeviceAdminOperationSafetyStateChangedEvent}. */
    public static final class DeviceAdminOperationSafetyStateChangedEventLogger
            extends EventLogger<DeviceAdminOperationSafetyStateChangedEvent> {
        private DeviceAdminOperationSafetyStateChangedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, int reason, boolean isSafe) {
            super(context, new DeviceAdminOperationSafetyStateChangedEvent());
            mEvent.mReason = reason;
            mEvent.mIsSafe = isSafe;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminOperationSafetyStateChangedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminOperationSafetyStateChangedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminOperationSafetyStateChangedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@code reason} which was received. */
        public DeviceAdminOperationSafetyStateChangedEventLogger setReason(int reason) {
            mEvent.mReason = reason;
            return this;
        }

        /** Sets the {@code isSafe} which was received. */
        public DeviceAdminOperationSafetyStateChangedEventLogger setIsSafe(boolean isSafe) {
            mEvent.mIsSafe = isSafe;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected int mReason;
    protected boolean mIsSafe;

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    /**
     * The {@code reason} passed into
     * {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, int, boolean)}.
     */
    public int reason() {
        return mReason;
    }

    /**
     * The {@code isSafe} passed into
     * {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, int, boolean)}.
     */
    public boolean isSafe() {
        return mIsSafe;
    }

    @Override
    public String toString() {
        return "DeviceAdminOperationSafetyStateChangedEvent{"
                + ", reason=" + mReason
                + ", isSafe=" + mIsSafe
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.LongQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}
 * is called.
 */
public final class DeviceAdminNetworkLogsAvailableEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminNetworkLogsAvailableEvent} events. */
    public static DeviceAdminNetworkLogsAvailableEventQuery queryPackage(String packageName) {
        return new DeviceAdminNetworkLogsAvailableEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminNetworkLogsAvailableEvent}. */
    public static final class DeviceAdminNetworkLogsAvailableEventQuery
            extends EventLogsQuery<DeviceAdminNetworkLogsAvailableEvent,
            DeviceAdminNetworkLogsAvailableEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        LongQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> mBatchToken =
                new LongQueryHelper<>(this);
        IntegerQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> mNetworkLogsCount =
                new IntegerQueryHelper<>(this);

        private DeviceAdminNetworkLogsAvailableEventQuery(String packageName) {
            super(DeviceAdminNetworkLogsAvailableEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminNetworkLogsAvailableEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Query {@code batchToken} passed into
         * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public LongQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> whereBatchToken() {
            return mBatchToken;
        }

        /**
         * Query {@code networkLogsCount} passed into
         * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public IntegerQueryHelper<DeviceAdminNetworkLogsAvailableEventQuery> whereNetworkLogsCount() {
            return mNetworkLogsCount;
        }

        @Override
        protected boolean filter(DeviceAdminNetworkLogsAvailableEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mBatchToken.matches(event.mBatchToken)) {
                return false;
            }
            if (!mNetworkLogsCount.matches(event.mNetworkLogsCount)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminNetworkLogsAvailableEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("batchToken", mBatchToken)
                    .field("networkLogsCount", mNetworkLogsCount)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminNetworkLogsAvailableEvent}. */
    public static DeviceAdminNetworkLogsAvailableEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent,
            long batchToken, int networkLogsCount) {
        return new DeviceAdminNetworkLogsAvailableEventLogger(
                deviceAdminReceiver, context, intent, batchToken, networkLogsCount);
    }

    /** {@link EventLogger} for {@link DeviceAdminNetworkLogsAvailableEvent}. */
    public static final class DeviceAdminNetworkLogsAvailableEventLogger
            extends EventLogger<DeviceAdminNetworkLogsAvailableEvent> {
        private DeviceAdminNetworkLogsAvailableEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent,
                long batchToken, int networkLogsCount) {
            super(context, new DeviceAdminNetworkLogsAvailableEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mBatchToken = batchToken;
            mEvent.mNetworkLogsCount = networkLogsCount;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminNetworkLogsAvailableEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminNetworkLogsAvailableEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminNetworkLogsAvailableEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminNetworkLogsAvailableEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code batchToken} which was received. */
        public DeviceAdminNetworkLogsAvailableEventLogger setBatchToken(long batchToken) {
            mEvent.mBatchToken = batchToken;
            return this;
        }

        /** Sets the {@code networkLogsCount} which was received. */
        public DeviceAdminNetworkLogsAvailableEventLogger setNetworkLogsCount(int networkLogsCount) {
            mEvent.mNetworkLogsCount = networkLogsCount;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected long mBatchToken;
    protected int mNetworkLogsCount;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
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
     * The {@code batchToken} passed into
     * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
     */
    public long batchToken() {
        return mBatchToken;
    }

    /**
     * The {@code networkLogsCount} passed into
     * {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
     */
    public int networkLogsCount() {
        return mNetworkLogsCount;
    }

    @Override
    public String toString() {
        return "DeviceAdminNetworkLogsAvailableEvent{"
                + " intent=" + intent()
                + ", batchToken=" + mBatchToken
                + ", networkLogsCount=" + mNetworkLogsCount
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

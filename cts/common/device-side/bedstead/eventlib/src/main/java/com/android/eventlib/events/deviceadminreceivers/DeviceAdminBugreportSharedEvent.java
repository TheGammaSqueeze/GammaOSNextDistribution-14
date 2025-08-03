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
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onBugreportShared(Context, Intent, String)}
 * is called.
 */
public final class DeviceAdminBugreportSharedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminBugreportSharedEvent} events. */
    public static DeviceAdminBugreportSharedEventQuery queryPackage(String packageName) {
        return new DeviceAdminBugreportSharedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminBugreportSharedEvent}. */
    public static final class DeviceAdminBugreportSharedEventQuery
            extends EventLogsQuery<DeviceAdminBugreportSharedEvent,
            DeviceAdminBugreportSharedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminBugreportSharedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminBugreportSharedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        StringQueryHelper<DeviceAdminBugreportSharedEventQuery> mBugreportHash =
                new StringQueryHelper<>(this);

        private DeviceAdminBugreportSharedEventQuery(String packageName) {
            super(DeviceAdminBugreportSharedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onBugreportShared(Context, Intent, String)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminBugreportSharedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminBugreportSharedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@code bugReportHash}. */
        @CheckResult
        public StringQuery<DeviceAdminBugreportSharedEventQuery> whereBugreportHash() {
            return mBugreportHash;
        }

        @Override
        protected boolean filter(DeviceAdminBugreportSharedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mBugreportHash.matches(event.mBugreportHash)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminBugreportSharedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("bugReportHash", mBugreportHash)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminBugreportSharedEvent}. */
    public static DeviceAdminBugreportSharedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context, Intent intent, String bugreportHash) {
        return new DeviceAdminBugreportSharedEventLogger(
                deviceAdminReceiver, context, intent, bugreportHash);
    }

    /** {@link EventLogger} for {@link DeviceAdminBugreportSharedEvent}. */
    public static final class DeviceAdminBugreportSharedEventLogger
            extends EventLogger<DeviceAdminBugreportSharedEvent> {
        private DeviceAdminBugreportSharedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, Intent intent, String bugreportHash) {
            super(context, new DeviceAdminBugreportSharedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mBugreportHash = bugreportHash;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminBugreportSharedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminBugreportSharedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code bugreportHash} which was received. */
        public DeviceAdminBugreportSharedEventLogger setBugreportHash(String bugreportHash) {
            mEvent.mBugreportHash = bugreportHash;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected String mBugreportHash;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onBugreportShared(Context, Intent, String)}.
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
     * The {@code bugreportHash} passed into
     * {@link DeviceAdminReceiver#onBugreportShared(Context, Intent, String)}.
     */
    public String bugreportHash() {
        return mBugreportHash;
    }

    @Override
    public String toString() {
        return "DeviceAdminBugreportSharedEvent{"
                + " intent=" + intent()
                + ", bugreportHash=" + mBugreportHash
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

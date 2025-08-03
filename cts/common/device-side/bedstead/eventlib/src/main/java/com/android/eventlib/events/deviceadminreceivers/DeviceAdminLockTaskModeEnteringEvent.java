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
 * Event logged when {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}
 * is called.
 */
public final class DeviceAdminLockTaskModeEnteringEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminLockTaskModeEnteringEvent} events. */
    public static DeviceAdminLockTaskModeEnteringEventQuery queryPackage(String packageName) {
        return new DeviceAdminLockTaskModeEnteringEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminLockTaskModeEnteringEvent}. */
    public static final class DeviceAdminLockTaskModeEnteringEventQuery
            extends EventLogsQuery<DeviceAdminLockTaskModeEnteringEvent,
            DeviceAdminLockTaskModeEnteringEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminLockTaskModeEnteringEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminLockTaskModeEnteringEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        StringQueryHelper<DeviceAdminLockTaskModeEnteringEventQuery> mPkg =
                new StringQueryHelper<>(this);

        private DeviceAdminLockTaskModeEnteringEventQuery(String packageName) {
            super(DeviceAdminLockTaskModeEnteringEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminLockTaskModeEnteringEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminLockTaskModeEnteringEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Query {@code pkg} passed into
         * {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}.
         */
        @CheckResult
        public StringQuery<DeviceAdminLockTaskModeEnteringEventQuery> wherePkg() {
            return mPkg;
        }

        @Override
        protected boolean filter(DeviceAdminLockTaskModeEnteringEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mPkg.matches(event.pkg())) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminLockTaskModeEnteringEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("pkg", mPkg)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminLockTaskModeEnteringEvent}. */
    public static DeviceAdminLockTaskModeEnteringEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent, String pkg) {
        return new DeviceAdminLockTaskModeEnteringEventLogger(
                deviceAdminReceiver, context, intent, pkg);
    }

    /** {@link EventLogger} for {@link DeviceAdminLockTaskModeEnteringEvent}. */
    public static final class DeviceAdminLockTaskModeEnteringEventLogger
            extends EventLogger<DeviceAdminLockTaskModeEnteringEvent> {
        private DeviceAdminLockTaskModeEnteringEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, Intent intent, String pkg) {
            super(context, new DeviceAdminLockTaskModeEnteringEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mPkg = pkg;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminLockTaskModeEnteringEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminLockTaskModeEnteringEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminLockTaskModeEnteringEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminLockTaskModeEnteringEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code pkg}. */
        public DeviceAdminLockTaskModeEnteringEventLogger setPkg(String pkg) {
            mEvent.mPkg = pkg;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected String mPkg;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@code pkg} passed into
     * {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}.
     */
    public String pkg() {
        return mPkg;
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminLockTaskModeEnteringEvent{"
                + " intent=" + intent()
                + ", pkg=" + mPkg
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

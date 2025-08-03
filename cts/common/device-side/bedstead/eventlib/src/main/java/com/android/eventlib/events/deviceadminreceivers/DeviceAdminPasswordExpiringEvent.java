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
import android.os.UserHandle;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DeviceAdminReceiverInfo;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.UserHandleQuery;
import com.android.queryable.queries.UserHandleQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent)} or
 * {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminPasswordExpiringEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminPasswordExpiringEvent} events. */
    public static DeviceAdminPasswordExpiringEventQuery queryPackage(String packageName) {
        return new DeviceAdminPasswordExpiringEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminPasswordExpiringEvent}. */
    public static final class DeviceAdminPasswordExpiringEventQuery
            extends EventLogsQuery<DeviceAdminPasswordExpiringEvent,
            DeviceAdminPasswordExpiringEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminPasswordExpiringEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminPasswordExpiringEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminPasswordExpiringEventQuery> mUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminPasswordExpiringEventQuery(String packageName) {
            super(DeviceAdminPasswordExpiringEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminPasswordExpiringEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminPasswordExpiringEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminPasswordExpiringEventQuery> whereUser() {
            return mUser;
        }

        @Override
        protected boolean filter(DeviceAdminPasswordExpiringEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mUser.matches(event.mUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminPasswordExpiringEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("user", mUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminPasswordExpiringEvent}. */
    public static DeviceAdminPasswordExpiringEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
        return new DeviceAdminPasswordExpiringEventLogger(deviceAdminReceiver, context, intent);
    }

    /** {@link EventLogger} for {@link DeviceAdminPasswordExpiringEvent}. */
    public static final class DeviceAdminPasswordExpiringEventLogger
            extends EventLogger<DeviceAdminPasswordExpiringEvent> {
        private DeviceAdminPasswordExpiringEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
            super(context, new DeviceAdminPasswordExpiringEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordExpiringEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordExpiringEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordExpiringEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminPasswordExpiringEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminPasswordExpiringEventLogger setUserHandle(UserHandle userHandle) {
            mEvent.mUser = new SerializableParcelWrapper<>(userHandle);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent, UserHandle)}.
     */
    public UserHandle user() {
        if (mUser == null) {
            return null;
        }
        return mUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminPasswordExpiringEvent{"
                + " intent=" + intent()
                + ", user=" + user()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

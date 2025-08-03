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
 * Event logged when {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminUserAddedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminUserAddedEvent} events. */
    public static DeviceAdminUserAddedEventQuery queryPackage(String packageName) {
        return new DeviceAdminUserAddedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminUserAddedEvent}. */
    public static final class DeviceAdminUserAddedEventQuery
            extends EventLogsQuery<DeviceAdminUserAddedEvent,
            DeviceAdminUserAddedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminUserAddedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminUserAddedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminUserAddedEventQuery> mAddedUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminUserAddedEventQuery(String packageName) {
            super(DeviceAdminUserAddedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminUserAddedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminUserAddedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminUserAddedEventQuery> whereAddedUser() {
            return mAddedUser;
        }

        @Override
        protected boolean filter(DeviceAdminUserAddedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mAddedUser.matches(event.mAddedUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminUserAddedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("addedUser", mAddedUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminUserAddedEvent}. */
    public static DeviceAdminUserAddedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context,
            Intent intent, UserHandle addedUser) {
        return new DeviceAdminUserAddedEventLogger(deviceAdminReceiver, context, intent, addedUser);
    }

    /** {@link EventLogger} for {@link DeviceAdminUserAddedEvent}. */
    public static final class DeviceAdminUserAddedEventLogger
            extends EventLogger<DeviceAdminUserAddedEvent> {
        private DeviceAdminUserAddedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context,
                Intent intent, UserHandle addedUser) {
            super(context, new DeviceAdminUserAddedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mAddedUser = new SerializableParcelWrapper<>(addedUser);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserAddedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserAddedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserAddedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminUserAddedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminUserAddedEventLogger setAddedUser(UserHandle addedUser) {
            mEvent.mAddedUser = new SerializableParcelWrapper<>(addedUser);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mAddedUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)}.
     */
    public UserHandle addedUser() {
        if (mAddedUser == null) {
            return null;
        }
        return mAddedUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminUserAddedEvent{"
                + " intent=" + intent()
                + ", addedUser=" + addedUser()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

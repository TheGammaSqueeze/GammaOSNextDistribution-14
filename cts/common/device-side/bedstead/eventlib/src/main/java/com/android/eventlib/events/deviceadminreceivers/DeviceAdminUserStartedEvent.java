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
 * Event logged when {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminUserStartedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminUserStartedEvent} events. */
    public static DeviceAdminUserStartedEventQuery queryPackage(String packageName) {
        return new DeviceAdminUserStartedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminUserStartedEvent}. */
    public static final class DeviceAdminUserStartedEventQuery
            extends EventLogsQuery<DeviceAdminUserStartedEvent,
            DeviceAdminUserStartedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminUserStartedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminUserStartedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminUserStartedEventQuery> mStartedUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminUserStartedEventQuery(String packageName) {
            super(DeviceAdminUserStartedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminUserStartedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminUserStartedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminUserStartedEventQuery> whereStartedUser() {
            return mStartedUser;
        }

        @Override
        protected boolean filter(DeviceAdminUserStartedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mStartedUser.matches(event.mStartedUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminUserStartedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("startedUser", mStartedUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminUserStartedEvent}. */
    public static DeviceAdminUserStartedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent, UserHandle startedUser) {
        return new DeviceAdminUserStartedEventLogger(deviceAdminReceiver, context, intent, startedUser);
    }

    /** {@link EventLogger} for {@link DeviceAdminUserStartedEvent}. */
    public static final class DeviceAdminUserStartedEventLogger
            extends EventLogger<DeviceAdminUserStartedEvent> {
        private DeviceAdminUserStartedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent, UserHandle startedUser) {
            super(context, new DeviceAdminUserStartedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mStartedUser = new SerializableParcelWrapper<>(startedUser);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStartedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStartedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStartedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminUserStartedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminUserStartedEventLogger setStartedUser(UserHandle startedUser) {
            mEvent.mStartedUser = new SerializableParcelWrapper<>(startedUser);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mStartedUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)}.
     */
    public UserHandle startedUser() {
        if (mStartedUser == null) {
            return null;
        }
        return mStartedUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminUserStartedEvent{"
                + " intent=" + intent()
                + ", startedUser=" + startedUser()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
 * Event logged when
 * {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminUserStoppedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminUserStoppedEvent} events. */
    public static DeviceAdminUserStoppedEventQuery queryPackage(String packageName) {
        return new DeviceAdminUserStoppedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminUserStoppedEvent}. */
    public static final class DeviceAdminUserStoppedEventQuery
            extends EventLogsQuery<DeviceAdminUserStoppedEvent,
            DeviceAdminUserStoppedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminUserStoppedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminUserStoppedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminUserStoppedEventQuery> mStoppedUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminUserStoppedEventQuery(String packageName) {
            super(DeviceAdminUserStoppedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminUserStoppedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminUserStoppedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminUserStoppedEventQuery> whereStoppedUser() {
            return mStoppedUser;
        }

        @Override
        protected boolean filter(DeviceAdminUserStoppedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mStoppedUser.matches(event.mStoppedUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminUserStoppedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("stoppedUser", mStoppedUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminUserStoppedEvent}. */
    public static DeviceAdminUserStoppedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context,
            Intent intent, UserHandle stoppedUser) {
        return new DeviceAdminUserStoppedEventLogger(
                deviceAdminReceiver, context, intent, stoppedUser);
    }

    /** {@link EventLogger} for {@link DeviceAdminUserStoppedEvent}. */
    public static final class DeviceAdminUserStoppedEventLogger
            extends EventLogger<DeviceAdminUserStoppedEvent> {
        private DeviceAdminUserStoppedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent,
                UserHandle stoppedUser) {
            super(context, new DeviceAdminUserStoppedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mStoppedUser = new SerializableParcelWrapper<>(stoppedUser);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStoppedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStoppedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserStoppedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminUserStoppedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminUserStoppedEventLogger setStoppedUser(UserHandle stoppedUser) {
            mEvent.mStoppedUser = new SerializableParcelWrapper<>(stoppedUser);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mStoppedUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)}.
     */
    public UserHandle stoppedUser() {
        if (mStoppedUser == null) {
            return null;
        }
        return mStoppedUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminUserStoppedEvent{"
                + " intent=" + intent()
                + ", stoppedUser=" + stoppedUser()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
 * {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminUserSwitchedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminUserSwitchedEvent} events. */
    public static DeviceAdminUserSwitchedEventQuery queryPackage(String packageName) {
        return new DeviceAdminUserSwitchedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminUserSwitchedEvent}. */
    public static final class DeviceAdminUserSwitchedEventQuery
            extends EventLogsQuery<DeviceAdminUserSwitchedEvent,
            DeviceAdminUserSwitchedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminUserSwitchedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminUserSwitchedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminUserSwitchedEventQuery> mSwitchedUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminUserSwitchedEventQuery(String packageName) {
            super(DeviceAdminUserSwitchedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminUserSwitchedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminUserSwitchedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminUserSwitchedEventQuery> whereSwitchedUser() {
            return mSwitchedUser;
        }

        @Override
        protected boolean filter(DeviceAdminUserSwitchedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mSwitchedUser.matches(event.mSwitchedUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminUserSwitchedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("switchedUser", mSwitchedUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminUserSwitchedEvent}. */
    public static DeviceAdminUserSwitchedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context,
            Intent intent, UserHandle switchedUser) {
        return new DeviceAdminUserSwitchedEventLogger(
                deviceAdminReceiver, context, intent, switchedUser);
    }

    /** {@link EventLogger} for {@link DeviceAdminUserSwitchedEvent}. */
    public static final class DeviceAdminUserSwitchedEventLogger
            extends EventLogger<DeviceAdminUserSwitchedEvent> {
        private DeviceAdminUserSwitchedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context,
                Intent intent, UserHandle switchedUser) {
            super(context, new DeviceAdminUserSwitchedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mSwitchedUser = new SerializableParcelWrapper<>(switchedUser);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserSwitchedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserSwitchedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserSwitchedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminUserSwitchedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminUserSwitchedEventLogger setSwitchedUser(UserHandle switchedUser) {
            mEvent.mSwitchedUser = new SerializableParcelWrapper<>(switchedUser);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mSwitchedUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)}.
     */
    public UserHandle switchedUser() {
        if (mSwitchedUser == null) {
            return null;
        }
        return mSwitchedUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminUserSwitchedEvent{"
                + " intent=" + intent()
                + ", switchedUser=" + switchedUser()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

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
 * Event logged when {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminUserRemovedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminUserRemovedEvent} events. */
    public static DeviceAdminUserRemovedEventQuery queryPackage(String packageName) {
        return new DeviceAdminUserRemovedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminUserRemovedEvent}. */
    public static final class DeviceAdminUserRemovedEventQuery
            extends EventLogsQuery<DeviceAdminUserRemovedEvent,
            DeviceAdminUserRemovedEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminUserRemovedEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminUserRemovedEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminUserRemovedEventQuery> mRemovedUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminUserRemovedEventQuery(String packageName) {
            super(DeviceAdminUserRemovedEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminUserRemovedEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminUserRemovedEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminUserRemovedEventQuery> whereRemovedUser() {
            return mRemovedUser;
        }

        @Override
        protected boolean filter(DeviceAdminUserRemovedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mRemovedUser.matches(event.mRemovedUser)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminUserRemovedEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("removedUser", mRemovedUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminUserRemovedEvent}. */
    public static DeviceAdminUserRemovedEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent, UserHandle removedUser) {
        return new DeviceAdminUserRemovedEventLogger(deviceAdminReceiver, context, intent, removedUser);
    }

    /** {@link EventLogger} for {@link DeviceAdminUserRemovedEvent}. */
    public static final class DeviceAdminUserRemovedEventLogger
            extends EventLogger<DeviceAdminUserRemovedEvent> {
        private DeviceAdminUserRemovedEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent, UserHandle removedUser) {
            super(context, new DeviceAdminUserRemovedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mRemovedUser = new SerializableParcelWrapper<>(removedUser);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserRemovedEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserRemovedEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminUserRemovedEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminUserRemovedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminUserRemovedEventLogger setRemovedUser(UserHandle removedUser) {
            mEvent.mRemovedUser = new SerializableParcelWrapper<>(removedUser);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mRemovedUser;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)}.
     */
    public UserHandle removedUser() {
        if (mRemovedUser == null) {
            return null;
        }
        return mRemovedUser.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminUserRemovedEvent{"
                + " intent=" + intent()
                + ", removedUser=" + removedUser()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

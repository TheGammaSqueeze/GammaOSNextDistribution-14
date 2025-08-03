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
 * Event logged when {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent)} or
 * {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent, UserHandle)} is called.
 */
public final class DeviceAdminPasswordSucceededEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminPasswordSucceededEvent} events. */
    public static DeviceAdminPasswordSucceededEventQuery queryPackage(String packageName) {
        return new DeviceAdminPasswordSucceededEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminPasswordSucceededEvent}. */
    public static final class DeviceAdminPasswordSucceededEventQuery
            extends EventLogsQuery<DeviceAdminPasswordSucceededEvent,
            DeviceAdminPasswordSucceededEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminPasswordSucceededEventQuery>
                mDeviceAdminReceiver = new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminPasswordSucceededEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminPasswordSucceededEventQuery> mUser =
                new UserHandleQueryHelper<DeviceAdminPasswordSucceededEventQuery>(this);

        private DeviceAdminPasswordSucceededEventQuery(String packageName) {
            super(DeviceAdminPasswordSucceededEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent)}.
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminPasswordSucceededEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminPasswordSucceededEventQuery>
                whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminPasswordSucceededEventQuery> whereUser() {
            return mUser;
        }

        @Override
        protected boolean filter(DeviceAdminPasswordSucceededEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mUser.matches(event.mUserHandle)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminPasswordSucceededEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("userHandle", mUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminPasswordSucceededEvent}. */
    public static DeviceAdminPasswordSucceededEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
        return new DeviceAdminPasswordSucceededEventLogger(deviceAdminReceiver, context, intent);
    }

    /** {@link EventLogger} for {@link DeviceAdminPasswordSucceededEvent}. */
    public static final class DeviceAdminPasswordSucceededEventLogger
            extends EventLogger<DeviceAdminPasswordSucceededEvent> {
        private DeviceAdminPasswordSucceededEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent) {
            super(context, new DeviceAdminPasswordSucceededEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordSucceededEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordSucceededEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminPasswordSucceededEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminPasswordSucceededEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@link UserHandle}. */
        public DeviceAdminPasswordSucceededEventLogger setUserHandle(UserHandle userHandle) {
            mEvent.mUserHandle = new SerializableParcelWrapper<>(userHandle);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected SerializableParcelWrapper<UserHandle> mUserHandle;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent, UserHandle)}.
     */
    public UserHandle userHandle() {
        if (mUserHandle == null) {
            return null;
        }
        return mUserHandle.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    @Override
    public String toString() {
        return "DeviceAdminPasswordSucceededEvent{"
                + " intent=" + intent()
                + ", userHandle=" + userHandle()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

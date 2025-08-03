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
import android.os.UserHandle;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DeviceAdminReceiverInfo;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.UserHandleQuery;
import com.android.queryable.queries.UserHandleQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when
 * {@link DeviceAdminReceiver#onTransferAffiliatedProfileOwnershipComplete(Context, UserHandle)}
 * is called.
 */
public final class DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent} events. */
    public static DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery queryPackage(
            String packageName) {
        return new DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent}. */
    public static final class DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery
            extends EventLogsQuery<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent,
            DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        UserHandleQueryHelper<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery>
                mUser =
                new UserHandleQueryHelper<>(this);

        private DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery(String packageName) {
            super(DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.class, packageName);
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Queries {@link UserHandle} passed into
         * {@link DeviceAdminReceiver#onTransferAffiliatedProfileOwnershipComplete(Context, UserHandle)}.
         */
        @CheckResult
        public UserHandleQuery<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery> whereUser() {
            return mUser;
        }

        @Override
        protected boolean filter(DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent event) {
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
            return toStringBuilder(
                    DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.class, this)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("user", mUser)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent}. */
    public static DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context, UserHandle user) {
        return new DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger(
                deviceAdminReceiver, context, user);
    }

    /**
     * {@link EventLogger} for
     * {@link DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent}.
     */
    public static final class DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger
            extends EventLogger<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent> {
        private DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, UserHandle user) {
            super(context, new DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent());
            setUser(user);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link UserHandle} which was received. */
        public DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger setUser(
                UserHandle user) {
            mEvent.mUser = new SerializableParcelWrapper<>(user);
            return this;
        }
    }

    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected SerializableParcelWrapper<UserHandle> mUser;

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    /**
     * The {@link UserHandle} passed into
     * {@link DeviceAdminReceiver#onTransferAffiliatedProfileOwnershipComplete(Context, UserHandle)}
     */
    public UserHandle user() {
        if (mUser == null) {
            return null;
        }
        return mUser.get();
    }

    @Override
    public String toString() {
        return "DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent{"
                + ", user=" + user()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

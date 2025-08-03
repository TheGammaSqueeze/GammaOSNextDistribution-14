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
import android.os.PersistableBundle;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DeviceAdminReceiverInfo;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.PersistableBundleQuery;
import com.android.queryable.queries.PersistableBundleQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when
 * {@link DeviceAdminReceiver#onTransferOwnershipComplete(Context, PersistableBundle)} is called.
 */
public final class DeviceAdminTransferOwnershipCompleteEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminTransferOwnershipCompleteEvent} events. */
    public static DeviceAdminTransferOwnershipCompleteEventQuery queryPackage(String packageName) {
        return new DeviceAdminTransferOwnershipCompleteEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminTransferOwnershipCompleteEvent}. */
    public static final class DeviceAdminTransferOwnershipCompleteEventQuery
            extends EventLogsQuery<DeviceAdminTransferOwnershipCompleteEvent,
            DeviceAdminTransferOwnershipCompleteEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminTransferOwnershipCompleteEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        PersistableBundleQueryHelper<DeviceAdminTransferOwnershipCompleteEventQuery> mBundle =
                new PersistableBundleQueryHelper<>(this);

        private DeviceAdminTransferOwnershipCompleteEventQuery(String packageName) {
            super(DeviceAdminTransferOwnershipCompleteEvent.class, packageName);
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminTransferOwnershipCompleteEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /**
         * Queries {@link PersistableBundle} passed into
         * {@link DeviceAdminReceiver#onTransferOwnershipComplete(Context, PersistableBundle)}.
         */
        @CheckResult
        public PersistableBundleQuery<DeviceAdminTransferOwnershipCompleteEventQuery> whereBundle() {
            return mBundle;
        }

        @Override
        protected boolean filter(DeviceAdminTransferOwnershipCompleteEvent event) {
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mBundle.matches(event.mBundle)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminTransferOwnershipCompleteEvent.class, this)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("bundle", mBundle)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminTransferOwnershipCompleteEvent}. */
    public static DeviceAdminTransferOwnershipCompleteEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver,
            Context context, PersistableBundle bundle) {
        return new DeviceAdminTransferOwnershipCompleteEventLogger(
                deviceAdminReceiver, context, bundle);
    }

    /** {@link EventLogger} for {@link DeviceAdminTransferOwnershipCompleteEvent}. */
    public static final class DeviceAdminTransferOwnershipCompleteEventLogger
            extends EventLogger<DeviceAdminTransferOwnershipCompleteEvent> {
        private DeviceAdminTransferOwnershipCompleteEventLogger(
                DeviceAdminReceiver deviceAdminReceiver,
                Context context, PersistableBundle bundle) {
            super(context, new DeviceAdminTransferOwnershipCompleteEvent());
            mEvent.mBundle = new SerializableParcelWrapper<>(bundle);
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferOwnershipCompleteEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferOwnershipCompleteEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminTransferOwnershipCompleteEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link PersistableBundle} which was received. */
        public DeviceAdminTransferOwnershipCompleteEventLogger setBundle(PersistableBundle bundle) {
            mEvent.mBundle = new SerializableParcelWrapper<>(bundle);
            return this;
        }
    }

    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected SerializableParcelWrapper<PersistableBundle> mBundle;

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    /**
     * The {@link PersistableBundle} passed into
     * {@link DeviceAdminReceiver#onTransferOwnershipComplete(Context, PersistableBundle)}.
     */
    public PersistableBundle bundle() {
        if (mBundle == null) {
            return null;
        }
        return mBundle.get();
    }

    @Override
    public String toString() {
        return "DeviceAdminTransferOwnershipCompleteEvent{"
                + ", bundle=" + bundle()
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

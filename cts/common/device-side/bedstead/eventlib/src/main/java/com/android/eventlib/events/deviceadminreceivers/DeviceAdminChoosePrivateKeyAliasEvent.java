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
import android.net.Uri;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DeviceAdminReceiverInfo;
import com.android.queryable.queries.DeviceAdminReceiverQuery;
import com.android.queryable.queries.DeviceAdminReceiverQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;
import com.android.queryable.queries.UriQuery;
import com.android.queryable.queries.UriQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when
 * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)} is called.
 */
public final class DeviceAdminChoosePrivateKeyAliasEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DeviceAdminChoosePrivateKeyAliasEvent} events. */
    public static DeviceAdminChoosePrivateKeyAliasEventQuery queryPackage(String packageName) {
        return new DeviceAdminChoosePrivateKeyAliasEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DeviceAdminChoosePrivateKeyAliasEvent}. */
    public static final class DeviceAdminChoosePrivateKeyAliasEventQuery
            extends EventLogsQuery<DeviceAdminChoosePrivateKeyAliasEvent,
            DeviceAdminChoosePrivateKeyAliasEventQuery> {

        private static final long serialVersionUID = 1;

        DeviceAdminReceiverQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> mDeviceAdminReceiver =
                new DeviceAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        IntegerQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> mUid =
                new IntegerQueryHelper<>(this);
        UriQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> mUri =
                new UriQueryHelper<>(this);
        StringQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> mAlias =
                new StringQueryHelper<>(this);

        private DeviceAdminChoosePrivateKeyAliasEventQuery(String packageName) {
            super(DeviceAdminChoosePrivateKeyAliasEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String).
         */
        @CheckResult
        public IntentQueryHelper<DeviceAdminChoosePrivateKeyAliasEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DeviceAdminReceiver}. */
        @CheckResult
        public DeviceAdminReceiverQuery<DeviceAdminChoosePrivateKeyAliasEventQuery> whereDeviceAdminReceiver() {
            return mDeviceAdminReceiver;
        }

        /** Query {@code uid}. */
        @CheckResult
        public IntegerQuery<DeviceAdminChoosePrivateKeyAliasEventQuery> whereUid() {
            return mUid;
        }

        /** Queries {@link Uri}. */
        @CheckResult
        public UriQuery<DeviceAdminChoosePrivateKeyAliasEventQuery> whereUri() {
            return mUri;
        }

        /** Query {@code alias}. */
        @CheckResult
        public StringQuery<DeviceAdminChoosePrivateKeyAliasEventQuery> whereAlias() {
            return mAlias;
        }

        @Override
        protected boolean filter(DeviceAdminChoosePrivateKeyAliasEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDeviceAdminReceiver.matches(event.mDeviceAdminReceiver)) {
                return false;
            }
            if (!mUid.matches(event.mUid)) {
                return false;
            }
            if (!mUri.matches(event.mUri)) {
                return false;
            }
            if (!mAlias.matches(event.mAlias)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DeviceAdminChoosePrivateKeyAliasEvent.class, this)
                    .field("intent", mIntent)
                    .field("deviceAdminReceiver", mDeviceAdminReceiver)
                    .field("uid", mUid)
                    .field("uri", mUri)
                    .field("alias", mAlias)
                    .toString();
        }
    }

    /** Begins logging a {@link DeviceAdminChoosePrivateKeyAliasEvent}. */
    public static DeviceAdminChoosePrivateKeyAliasEventLogger logger(
            DeviceAdminReceiver deviceAdminReceiver, Context context,
            Intent intent, int uid, Uri uri, String alias) {
        return new DeviceAdminChoosePrivateKeyAliasEventLogger(
                deviceAdminReceiver, context, intent, uid, uri, alias);
    }

    /** {@link EventLogger} for {@link DeviceAdminChoosePrivateKeyAliasEvent}. */
    public static final class DeviceAdminChoosePrivateKeyAliasEventLogger
            extends EventLogger<DeviceAdminChoosePrivateKeyAliasEvent> {
        private DeviceAdminChoosePrivateKeyAliasEventLogger(
                DeviceAdminReceiver deviceAdminReceiver, Context context, Intent intent,
                int uid, Uri uri, String alias) {
            super(context, new DeviceAdminChoosePrivateKeyAliasEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mUid = uid;
            mEvent.mUri = new SerializableParcelWrapper<>(uri);
            mEvent.mAlias = alias;
            setDeviceAdminReceiver(deviceAdminReceiver);
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setDeviceAdminReceiver(
                DeviceAdminReceiver deviceAdminReceiver) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiver);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setDeviceAdminReceiver(
                Class<? extends DeviceAdminReceiver> deviceAdminReceiverClass) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DeviceAdminReceiver} which received this event. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setDeviceAdminReceiver(
                String deviceAdminReceiverClassName) {
            mEvent.mDeviceAdminReceiver = new DeviceAdminReceiverInfo(deviceAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code uid} which was received. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setUid(int uid) {
            mEvent.mUid = uid;
            return this;
        }

        /** Sets the {@link Uri} which was received. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setUri(Uri uri) {
            mEvent.mUri = new SerializableParcelWrapper<>(uri);
            return this;
        }

        /** Sets the {@code alias} which was received. */
        public DeviceAdminChoosePrivateKeyAliasEventLogger setAlias(String alias) {
            mEvent.mAlias = alias;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DeviceAdminReceiverInfo mDeviceAdminReceiver;
    protected int mUid;
    protected SerializableParcelWrapper<Uri> mUri;
    protected String mAlias;

    /**
     * The {@link Intent} passed into
     * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /** Information about the {@link DeviceAdminReceiver} which received the intent. */
    public DeviceAdminReceiverInfo deviceAdminReceiver() {
        return mDeviceAdminReceiver;
    }

    /**
     * The {@code uid} passed into
     * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public int uid() {
        return mUid;
    }

    /**
     * The {@link Uri} passed into
     * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public Uri uri() {
        if (mUri == null) {
            return null;
        }
        return mUri.get();
    }

    /**
     * The {@code alias} passed into
     * {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public String alias() {
        return mAlias;
    }

    @Override
    public String toString() {
        return "DeviceAdminChoosePrivateKeyAliasEvent{"
                + " intent=" + intent()
                + ", uid=" + mUid
                + ", uri=" + uri()
                + ", alias=" + mAlias
                + ", deviceAdminReceiver=" + mDeviceAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

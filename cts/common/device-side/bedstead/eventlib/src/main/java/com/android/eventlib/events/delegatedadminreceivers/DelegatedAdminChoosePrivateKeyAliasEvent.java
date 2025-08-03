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

package com.android.eventlib.events.delegatedadminreceivers;

import android.app.admin.DelegatedAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DelegatedAdminReceiverInfo;
import com.android.queryable.queries.DelegatedAdminReceiverQuery;
import com.android.queryable.queries.DelegatedAdminReceiverQueryHelper;
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
 * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)} is
 * called.
 */
public final class DelegatedAdminChoosePrivateKeyAliasEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link DelegatedAdminChoosePrivateKeyAliasEvent} events. */
    public static DelegatedAdminChoosePrivateKeyAliasEventQuery queryPackage(String packageName) {
        return new DelegatedAdminChoosePrivateKeyAliasEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link DelegatedAdminChoosePrivateKeyAliasEvent}. */
    public static final class DelegatedAdminChoosePrivateKeyAliasEventQuery
            extends EventLogsQuery<DelegatedAdminChoosePrivateKeyAliasEvent,
            DelegatedAdminChoosePrivateKeyAliasEventQuery> {

        private static final long serialVersionUID = 1;

        DelegatedAdminReceiverQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> mDelegatedAdminReceiver =
                new DelegatedAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        IntegerQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> mUid =
                new IntegerQueryHelper<>(this);
        UriQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> mUri =
                new UriQueryHelper<>(this);
        StringQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> mAlias =
                new StringQueryHelper<>(this);

        private DelegatedAdminChoosePrivateKeyAliasEventQuery(String packageName) {
            super(DelegatedAdminChoosePrivateKeyAliasEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String).
         */
        @CheckResult
        public IntentQueryHelper<DelegatedAdminChoosePrivateKeyAliasEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DelegatedAdminReceiver}. */
        @CheckResult
        public DelegatedAdminReceiverQuery<DelegatedAdminChoosePrivateKeyAliasEventQuery> whereDelegatedAdminReceiver() {
            return mDelegatedAdminReceiver;
        }

        /** Query {@code uid}. */
        @CheckResult
        public IntegerQuery<DelegatedAdminChoosePrivateKeyAliasEventQuery> whereUid() {
            return mUid;
        }

        /** Queries {@link Uri}. */
        @CheckResult
        public UriQuery<DelegatedAdminChoosePrivateKeyAliasEventQuery> whereUri() {
            return mUri;
        }

        /** Query {@code alias}. */
        @CheckResult
        public StringQuery<DelegatedAdminChoosePrivateKeyAliasEventQuery> whereAlias() {
            return mAlias;
        }

        @Override
        protected boolean filter(DelegatedAdminChoosePrivateKeyAliasEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDelegatedAdminReceiver.matches(event.mDelegatedAdminReceiver)) {
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
            return toStringBuilder(DelegatedAdminChoosePrivateKeyAliasEvent.class, this)
                    .field("intent", mIntent)
                    .field("delegatedAdminReceiver", mDelegatedAdminReceiver)
                    .field("uid", mUid)
                    .field("uri", mUri)
                    .field("alias", mAlias)
                    .toString();
        }
    }

    /** Begins logging a {@link DelegatedAdminChoosePrivateKeyAliasEvent}. */
    public static DelegatedAdminChoosePrivateKeyAliasEventLogger logger(
            DelegatedAdminReceiver delegatedAdminReceiver, Context context,
            Intent intent, int uid, Uri uri, String alias) {
        return new DelegatedAdminChoosePrivateKeyAliasEventLogger(
                delegatedAdminReceiver, context, intent, uid, uri, alias);
    }

    /** {@link EventLogger} for {@link DelegatedAdminChoosePrivateKeyAliasEvent}. */
    public static final class DelegatedAdminChoosePrivateKeyAliasEventLogger
            extends EventLogger<DelegatedAdminChoosePrivateKeyAliasEvent> {
        private DelegatedAdminChoosePrivateKeyAliasEventLogger(
                DelegatedAdminReceiver delegatedAdminReceiver, Context context, Intent intent,
                int uid, Uri uri, String alias) {
            super(context, new DelegatedAdminChoosePrivateKeyAliasEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mUid = uid;
            mEvent.mUri = new SerializableParcelWrapper<>(uri);
            mEvent.mAlias = alias;
            setDelegatedAdminReceiver(delegatedAdminReceiver);
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setDelegatedAdminReceiver(
                DelegatedAdminReceiver delegatedAdminReceiver) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(delegatedAdminReceiver);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setDelegatedAdminReceiver(
                Class<? extends DelegatedAdminReceiver> delegatedAdminReceiverClass) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(delegatedAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setDelegatedAdminReceiver(
                String delegatedAdminReceiverClassName) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(delegatedAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code uid} which was received. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setUid(int uid) {
            mEvent.mUid = uid;
            return this;
        }

        /** Sets the {@link Uri} which was received. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setUri(Uri uri) {
            mEvent.mUri = new SerializableParcelWrapper<>(uri);
            return this;
        }

        /** Sets the {@code alias} which was received. */
        public DelegatedAdminChoosePrivateKeyAliasEventLogger setAlias(String alias) {
            mEvent.mAlias = alias;
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected DelegatedAdminReceiverInfo mDelegatedAdminReceiver;
    protected int mUid;
    protected SerializableParcelWrapper<Uri> mUri;
    protected String mAlias;

    /**
     * The {@link Intent} passed into
     * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /** Information about the {@link DelegatedAdminReceiver} which received the intent. */
    public DelegatedAdminReceiverInfo delegatedAdminReceiver() {
        return mDelegatedAdminReceiver;
    }

    /**
     * The {@code uid} passed into
     * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public int uid() {
        return mUid;
    }

    /**
     * The {@link Uri} passed into
     * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public Uri uri() {
        if (mUri == null) {
            return null;
        }
        return mUri.get();
    }

    /**
     * The {@code alias} passed into
     * {@link DelegatedAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)
     */
    public String alias() {
        return mAlias;
    }

    @Override
    public String toString() {
        return "DelegatedAdminChoosePrivateKeyAliasEvent{"
                + " intent=" + intent()
                + ", uid=" + mUid
                + ", uri=" + uri()
                + ", alias=" + mAlias
                + ", delegatedAdminReceiver=" + mDelegatedAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

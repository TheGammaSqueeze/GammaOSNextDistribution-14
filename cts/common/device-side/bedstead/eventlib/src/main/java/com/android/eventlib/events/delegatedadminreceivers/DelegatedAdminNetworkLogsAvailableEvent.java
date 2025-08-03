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

import android.app.admin.DelegatedAdminReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DelegatedAdminReceiverInfo;
import com.android.queryable.queries.DelegatedAdminReceiverQuery;
import com.android.queryable.queries.DelegatedAdminReceiverQueryHelper;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.queries.LongQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long,
 * int)}
 * is called.
 */
public final class DelegatedAdminNetworkLogsAvailableEvent extends Event {

    private static final long serialVersionUID = 1;
    protected SerializableParcelWrapper<Intent> mIntent;
    protected DelegatedAdminReceiverInfo mDelegatedAdminReceiver;
    protected long mBatchToken;
    protected int mNetworkLogsCount;

    /** Begins a query for {@link DelegatedAdminNetworkLogsAvailableEvent} events. */
    public static DelegatedAdminNetworkLogsAvailableEventQuery queryPackage(String packageName) {
        return new DelegatedAdminNetworkLogsAvailableEventQuery(packageName);
    }

    /** Begins logging a {@link DelegatedAdminNetworkLogsAvailableEvent}. */
    public static DelegatedAdminNetworkLogsAvailableEventLogger logger(
            DelegatedAdminReceiver delegatedAdminReceiver, Context context, Intent intent,
            long batchToken, int networkLogsCount) {
        return new DelegatedAdminNetworkLogsAvailableEventLogger(
                delegatedAdminReceiver, context, intent, batchToken, networkLogsCount);
    }

    /**
     * The {@link Intent} passed into
     * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
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
     * The {@code batchToken} passed into
     * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
     */
    public long batchToken() {
        return mBatchToken;
    }

    /**
     * The {@code networkLogsCount} passed into
     * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
     */
    public int networkLogsCount() {
        return mNetworkLogsCount;
    }

    @Override
    public String toString() {
        return "DelegatedAdminNetworkLogsAvailableEvent{"
                + " intent=" + intent()
                + ", batchToken=" + mBatchToken
                + ", networkLogsCount=" + mNetworkLogsCount
                + ", delegatedAdminReceiver=" + mDelegatedAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }

    /** {@link EventLogsQuery} for {@link DelegatedAdminNetworkLogsAvailableEvent}. */
    public static final class DelegatedAdminNetworkLogsAvailableEventQuery
            extends EventLogsQuery<DelegatedAdminNetworkLogsAvailableEvent,
            DelegatedAdminNetworkLogsAvailableEventQuery> {

        private static final long serialVersionUID = 1;

        DelegatedAdminReceiverQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery>
                mDelegatedAdminReceiver =
                new DelegatedAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> mIntent =
                new IntentQueryHelper<>(this);
        LongQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> mBatchToken =
                new LongQueryHelper<>(this);
        IntegerQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> mNetworkLogsCount =
                new IntegerQueryHelper<>(this);

        private DelegatedAdminNetworkLogsAvailableEventQuery(String packageName) {
            super(DelegatedAdminNetworkLogsAvailableEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public IntentQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DelegatedAdminReceiver}. */
        @CheckResult
        public DelegatedAdminReceiverQuery<DelegatedAdminNetworkLogsAvailableEventQuery> whereDelegatedAdminReceiver() {
            return mDelegatedAdminReceiver;
        }

        /**
         * Query {@code batchToken} passed into
         * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public LongQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> whereBatchToken() {
            return mBatchToken;
        }

        /**
         * Query {@code networkLogsCount} passed into
         * {@link DelegatedAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}.
         */
        @CheckResult
        public IntegerQueryHelper<DelegatedAdminNetworkLogsAvailableEventQuery> whereNetworkLogsCount() {
            return mNetworkLogsCount;
        }

        @Override
        protected boolean filter(DelegatedAdminNetworkLogsAvailableEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mDelegatedAdminReceiver.matches(event.mDelegatedAdminReceiver)) {
                return false;
            }
            if (!mBatchToken.matches(event.mBatchToken)) {
                return false;
            }
            return mNetworkLogsCount.matches(event.mNetworkLogsCount);
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DelegatedAdminNetworkLogsAvailableEvent.class, this)
                    .field("intent", mIntent)
                    .field("delegatedAdminReceiver", mDelegatedAdminReceiver)
                    .field("batchToken", mBatchToken)
                    .field("networkLogsCount", mNetworkLogsCount)
                    .toString();
        }
    }

    /** {@link EventLogger} for {@link DelegatedAdminNetworkLogsAvailableEvent}. */
    public static final class DelegatedAdminNetworkLogsAvailableEventLogger
            extends EventLogger<DelegatedAdminNetworkLogsAvailableEvent> {
        private DelegatedAdminNetworkLogsAvailableEventLogger(
                DelegatedAdminReceiver delegatedAdminReceiver, Context context, Intent intent,
                long batchToken, int networkLogsCount) {
            super(context, new DelegatedAdminNetworkLogsAvailableEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mBatchToken = batchToken;
            mEvent.mNetworkLogsCount = networkLogsCount;
            setDelegatedAdminReceiver(delegatedAdminReceiver);
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setDelegatedAdminReceiver(
                DelegatedAdminReceiver delegatedAdminReceiver) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(delegatedAdminReceiver);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setDelegatedAdminReceiver(
                Class<? extends DelegatedAdminReceiver> delegatedAdminReceiverClass) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(
                    delegatedAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setDelegatedAdminReceiver(
                String delegatedAdminReceiverClassName) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(
                    delegatedAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }

        /** Sets the {@code batchToken} which was received. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setBatchToken(long batchToken) {
            mEvent.mBatchToken = batchToken;
            return this;
        }

        /** Sets the {@code networkLogsCount} which was received. */
        public DelegatedAdminNetworkLogsAvailableEventLogger setNetworkLogsCount(
                int networkLogsCount) {
            mEvent.mNetworkLogsCount = networkLogsCount;
            return this;
        }
    }
}

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

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.DelegatedAdminReceiverInfo;
import com.android.queryable.queries.DelegatedAdminReceiverQuery;
import com.android.queryable.queries.DelegatedAdminReceiverQueryHelper;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link DelegatedAdminReceiver#onSecurityLogsAvailable(Context, Intent)}
 * is called.
 */
public final class DelegatedAdminSecurityLogsAvailableEvent extends Event {

    private static final long serialVersionUID = 1;
    protected SerializableParcelWrapper<Intent> mIntent;
    protected DelegatedAdminReceiverInfo mDelegatedAdminReceiver;

    /** Begins a query for {@link DelegatedAdminSecurityLogsAvailableEvent} events. */
    public static DelegatedAdminSecurityLogsAvailableEventQuery queryPackage(String packageName) {
        return new DelegatedAdminSecurityLogsAvailableEventQuery(packageName);
    }

    /** Begins logging a {@link DelegatedAdminSecurityLogsAvailableEvent}. */
    public static DelegatedAdminSecurityLogsAvailableEventLogger logger(
            DelegatedAdminReceiver delegatedAdminReceiver, Context context, Intent intent) {
        return new DelegatedAdminSecurityLogsAvailableEventLogger(delegatedAdminReceiver, context,
                intent);
    }

    /**
     * The {@link Intent} passed into
     * {@link DelegatedAdminReceiver#onSecurityLogsAvailable(Context, Intent)}.
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

    @Override
    public String toString() {
        return "DelegatedAdminSecurityLogsAvailableEvent{"
                + " intent=" + intent()
                + ", delegatedAdminReceiver=" + mDelegatedAdminReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }

    /** {@link EventLogsQuery} for {@link DelegatedAdminSecurityLogsAvailableEvent}. */
    public static final class DelegatedAdminSecurityLogsAvailableEventQuery
            extends EventLogsQuery<DelegatedAdminSecurityLogsAvailableEvent,
            DelegatedAdminSecurityLogsAvailableEventQuery> {

        private static final long serialVersionUID = 1;

        DelegatedAdminReceiverQueryHelper<DelegatedAdminSecurityLogsAvailableEventQuery>
                mDelegatedAdminReceiver =
                new DelegatedAdminReceiverQueryHelper<>(this);
        IntentQueryHelper<DelegatedAdminSecurityLogsAvailableEventQuery> mIntent =
                new IntentQueryHelper<>(this);

        private DelegatedAdminSecurityLogsAvailableEventQuery(String packageName) {
            super(DelegatedAdminSecurityLogsAvailableEvent.class, packageName);
        }

        /**
         * Queries {@link Intent} passed into
         * {@link DelegatedAdminReceiver#onSecurityLogsAvailable(Context, Intent)}.
         */
        @CheckResult
        public IntentQueryHelper<DelegatedAdminSecurityLogsAvailableEventQuery> whereIntent() {
            return mIntent;
        }

        /** Queries {@link DelegatedAdminReceiver}. */
        @CheckResult
        public DelegatedAdminReceiverQuery<DelegatedAdminSecurityLogsAvailableEventQuery> whereDelegatedAdminReceiver() {
            return mDelegatedAdminReceiver;
        }

        @Override
        protected boolean filter(DelegatedAdminSecurityLogsAvailableEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            return mDelegatedAdminReceiver.matches(event.mDelegatedAdminReceiver);
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(DelegatedAdminSecurityLogsAvailableEvent.class, this)
                    .field("intent", mIntent)
                    .field("delegatedAdminReceiver", mDelegatedAdminReceiver)
                    .toString();
        }
    }

    /** {@link EventLogger} for {@link DelegatedAdminSecurityLogsAvailableEvent}. */
    public static final class DelegatedAdminSecurityLogsAvailableEventLogger
            extends EventLogger<DelegatedAdminSecurityLogsAvailableEvent> {
        private DelegatedAdminSecurityLogsAvailableEventLogger(
                DelegatedAdminReceiver delegatedAdminReceiver, Context context, Intent intent) {
            super(context, new DelegatedAdminSecurityLogsAvailableEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            setDelegatedAdminReceiver(delegatedAdminReceiver);
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminSecurityLogsAvailableEventLogger setDelegatedAdminReceiver(
                DelegatedAdminReceiver delegatedAdminReceiver) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(delegatedAdminReceiver);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminSecurityLogsAvailableEventLogger setDelegatedAdminReceiver(
                Class<? extends DelegatedAdminReceiver> delegatedAdminReceiverClass) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(
                    delegatedAdminReceiverClass);
            return this;
        }

        /** Sets the {@link DelegatedAdminReceiver} which received this event. */
        public DelegatedAdminSecurityLogsAvailableEventLogger setDelegatedAdminReceiver(
                String delegatedAdminReceiverClassName) {
            mEvent.mDelegatedAdminReceiver = new DelegatedAdminReceiverInfo(
                    delegatedAdminReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public DelegatedAdminSecurityLogsAvailableEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }
    }
}

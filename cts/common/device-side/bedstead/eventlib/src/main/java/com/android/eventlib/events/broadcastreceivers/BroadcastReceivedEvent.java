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

package com.android.eventlib.events.broadcastreceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.BroadcastReceiverInfo;
import com.android.queryable.queries.BroadcastReceiverQuery;
import com.android.queryable.queries.BroadcastReceiverQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.IntentQuery;
import com.android.queryable.queries.IntentQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link BroadcastReceiver#onReceive(Context, Intent)} is called.
 */
public final class BroadcastReceivedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link BroadcastReceivedEvent} events. */
    public static BroadcastReceivedEventQuery queryPackage(String packageName) {
        return new BroadcastReceivedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link BroadcastReceivedEvent}. */
    public static final class BroadcastReceivedEventQuery
            extends EventLogsQuery<BroadcastReceivedEvent, BroadcastReceivedEventQuery> {

        private static final long serialVersionUID = 1;

        BroadcastReceiverQueryHelper<BroadcastReceivedEventQuery> mBroadcastReceiver =
                new BroadcastReceiverQueryHelper<>(this);
        IntentQueryHelper<BroadcastReceivedEventQuery> mIntent = new IntentQueryHelper<>(this);
        IntegerQueryHelper<BroadcastReceivedEventQuery> mResultCode = new IntegerQueryHelper<>(
                this);

        private BroadcastReceivedEventQuery(String packageName) {
            super(BroadcastReceivedEvent.class, packageName);
        }

        /**
         * Query {@link Intent} passed into {@link BroadcastReceiver#onReceive(Context, Intent)}.
         */
        @CheckResult
        public IntentQuery<BroadcastReceivedEventQuery> whereIntent() {
            return mIntent;
        }

        /**
         * Query result code received via {@link BroadcastReceiver#getResultCode()} in {@link
         * BroadcastReceiver#onReceive(Context, Intent)}.
         */
        @CheckResult
        public IntegerQuery<BroadcastReceivedEventQuery> whereResultCode() {
            return mResultCode;
        }

        /** Query {@link BroadcastReceiver}. */
        @CheckResult
        public BroadcastReceiverQuery<BroadcastReceivedEventQuery> whereBroadcastReceiver() {
            return mBroadcastReceiver;
        }

        @Override
        protected boolean filter(BroadcastReceivedEvent event) {
            if (!mIntent.matches(event.mIntent)) {
                return false;
            }
            if (!mBroadcastReceiver.matches(event.mBroadcastReceiver)) {
                return false;
            }
            if (!mResultCode.matches(event.mResultCode)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(BroadcastReceivedEvent.class, this)
                    .field("intent", mIntent)
                    .field("broadcastReceiver", mBroadcastReceiver)
                    .toString();
        }
    }

    /** Begins logging a {@link BroadcastReceivedEvent}. */
    public static BroadcastReceivedEventLogger logger(
            BroadcastReceiver broadcastReceiver, Context context, Intent intent, int resultCode) {
        return new BroadcastReceivedEventLogger(broadcastReceiver, context, intent, resultCode);
    }

    /** {@link EventLogger} for {@link BroadcastReceivedEvent}. */
    public static final class BroadcastReceivedEventLogger
            extends EventLogger<BroadcastReceivedEvent> {
        private BroadcastReceivedEventLogger(
                BroadcastReceiver broadcastReceiver, Context context, Intent intent,
                int resultCode) {
            super(context, new BroadcastReceivedEvent());
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            mEvent.mResultCode = resultCode;
            setBroadcastReceiver(broadcastReceiver);
        }

        /** Sets the {@link BroadcastReceiver} which received this event. */
        public BroadcastReceivedEventLogger setBroadcastReceiver(
                BroadcastReceiver broadcastReceiver) {
            mEvent.mBroadcastReceiver = new BroadcastReceiverInfo(broadcastReceiver);
            return this;
        }

        /** Sets the {@link BroadcastReceiver} which received this event. */
        public BroadcastReceivedEventLogger setBroadcastReceiver(
                Class<? extends BroadcastReceiver> broadcastReceiverClass) {
            mEvent.mBroadcastReceiver = new BroadcastReceiverInfo(broadcastReceiverClass);
            return this;
        }

        /** Sets the {@link BroadcastReceiver} which received this event. */
        public BroadcastReceivedEventLogger setBroadcastReceiver(
                String broadcastReceiverClassName) {
            mEvent.mBroadcastReceiver = new BroadcastReceiverInfo(broadcastReceiverClassName);
            return this;
        }

        /** Sets the {@link Intent} which was received. */
        public BroadcastReceivedEventLogger setIntent(Intent intent) {
            mEvent.mIntent = new SerializableParcelWrapper<>(intent);
            return this;
        }
    }

    protected SerializableParcelWrapper<Intent> mIntent;
    protected BroadcastReceiverInfo mBroadcastReceiver;
    protected int mResultCode;

    /**
     * The {@link Intent} passed into {@link BroadcastReceiver#onReceive(Context, Intent)}.
     */
    public Intent intent() {
        if (mIntent == null) {
            return null;
        }
        return mIntent.get();
    }

    /** Information about the {@link BroadcastReceiver} which received the intent. */
    public BroadcastReceiverInfo broadcastReceiver() {
        return mBroadcastReceiver;
    }

    @Override
    public String toString() {
        return "BroadcastReceivedEvent{"
                + " intent=" + intent()
                + " resultCode=" + mResultCode
                + ", broadcastReceiver=" + mBroadcastReceiver
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}

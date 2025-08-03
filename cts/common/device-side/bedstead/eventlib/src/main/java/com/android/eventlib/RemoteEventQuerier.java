/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Context.BIND_AUTO_CREATE;

import static com.android.eventlib.QueryService.EARLIEST_LOG_TIME_KEY;
import static com.android.eventlib.QueryService.EVENT_KEY;
import static com.android.eventlib.QueryService.QUERIER_KEY;
import static com.android.eventlib.QueryService.TIMEOUT_KEY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link EventQuerier} used to query a single other process.
 */
public class
    RemoteEventQuerier<E extends Event, F extends EventLogsQuery> implements EventQuerier<E> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final String LOG_TAG = "RemoteEventQuerier";
    private static final Context sContext = TestApis.context().instrumentedContext();

    private final String mPackageName;
    private final EventLogsQuery<E, F> mEventLogsQuery;
    private int mPollSkip = 0;

    public RemoteEventQuerier(String packageName, EventLogsQuery<E, F> eventLogsQuery) {
        mPackageName = packageName;
        mEventLogsQuery = eventLogsQuery;
    }

    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onBindingDied(ComponentName name) {
                    mQuery.set(null);
                    Log.e(LOG_TAG, "Binding died for " + name);
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    throw new RuntimeException("onNullBinding for " + name);
                }

                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.i(LOG_TAG, "onServiceConnected for " + className);
                    mQuery.set(IQueryService.Stub.asInterface(service));
                    mConnectionCountdown.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName className) {
                    mQuery.set(null);
                    Log.i(LOG_TAG, "Service disconnected from " + className);
                }
            };

    @Override
    public E poll(Instant earliestLogTime, Duration timeout) {
        try {
            ensureInitialised();
            Instant endTime = Instant.now().plus(timeout);
            Bundle data = createRequestBundle();
            Duration remainingTimeout = Duration.between(Instant.now(), endTime);
            data.putSerializable(TIMEOUT_KEY, remainingTimeout);
            try {
                Bundle resultMessage = mQuery.get().poll(data, mPollSkip++);
                E e = (E) resultMessage.getSerializable(EVENT_KEY);
                while (e != null && !mEventLogsQuery.filterAll(e)) {
                    remainingTimeout = Duration.between(Instant.now(), endTime);
                    data.putSerializable(TIMEOUT_KEY, remainingTimeout);
                    resultMessage = mQuery.get().poll(data, mPollSkip++);
                    e = (E) resultMessage.getSerializable(EVENT_KEY);
                }
                return e;
            } catch (RemoteException e) {
                throw new IllegalStateException("Error making cross-process call", e);
            }
        } finally {
            ensureClosed();
        }
    }

    private Bundle createRequestBundle() {
        Bundle data = new Bundle();
        data.putSerializable(EARLIEST_LOG_TIME_KEY, EventLogs.sEarliestLogTime);
        data.putSerializable(QUERIER_KEY, mEventLogsQuery);
        return data;
    }

    private AtomicReference<IQueryService> mQuery = new AtomicReference<>();
    private CountDownLatch mConnectionCountdown;

    private static final int MAX_INITIALISATION_ATTEMPTS = 20;
    private static final long INITIALISATION_ATTEMPT_DELAY_MS = 50;

    private void ensureClosed() {
        mQuery.set(null);
        sContext.unbindService(connection);
    }

    private void ensureInitialised() {
        // We have retries for binding because there are a number of reasons binding could fail in
        // unpredictable ways
        int attempts = 0;
        while (attempts++ < MAX_INITIALISATION_ATTEMPTS) {
            try {
                ensureInitialisedOrThrow();
                return;
            } catch (Exception | Error e) {
                // Ignore, we will retry
                Log.i(LOG_TAG, "Error connecting", e);
            }
            try {
                Thread.sleep(INITIALISATION_ATTEMPT_DELAY_MS);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while initialising", e);
            }
        }
        ensureInitialisedOrThrow();
    }

    private void ensureInitialisedOrThrow() {
        if (mQuery.get() != null) {
            return;
        }

        blockingConnectOrFail();
    }

    @SuppressWarnings("NewApi") // bindServiceAsUser
    private void blockingConnectOrFail() {
        mConnectionCountdown = new CountDownLatch(1);
        Intent intent = new Intent();
        intent.setPackage(mPackageName);
        intent.setClassName(mPackageName, "com.android.eventlib.QueryService");

        AtomicBoolean didBind = new AtomicBoolean(false);
        if (mEventLogsQuery.getUserHandle() != null
                && mEventLogsQuery.getUserHandle().getIdentifier()
                != TestApis.users().instrumented().id()) {
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                didBind.set(sContext.bindServiceAsUser(
                        intent, connection, /* flags= */ BIND_AUTO_CREATE,
                        mEventLogsQuery.getUserHandle()));
            }
        } else {
            didBind.set(sContext.bindService(intent, connection, /* flags= */ BIND_AUTO_CREATE));
        }

        if (didBind.get()) {
            try {
                mConnectionCountdown.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while binding to service", e);
            }
        } else {
            UserReference user = (mEventLogsQuery.getUserHandle() == null)
                    ? TestApis.users().instrumented()
                    : TestApis.users().find(mEventLogsQuery.getUserHandle());
            if (!user.exists()) {
                throw new AssertionError("Tried to bind to user " + mEventLogsQuery.getUserHandle() + " but does not exist");
            }
            if (!user.isUnlocked()) {
                throw new AssertionError("Tried to bind to user " + user
                        + " but they are not unlocked");
            }
            Package pkg = TestApis.packages().find(mPackageName);
            if (!pkg.installedOnUser(user)) {
                throw new AssertionError("Tried to bind to package " + mPackageName + " but it is not installed on target user " + user);
            }

            throw new IllegalStateException("Tried to bind but call returned false (intent is "
                    + intent + ", user is  " + mEventLogsQuery.getUserHandle() + ")");
        }

        if (mQuery.get() == null) {
            throw new IllegalStateException("Tried to bind but failed. Expected onServiceConnected"
                    + " to have been called but it was not.");
        }
    }
}

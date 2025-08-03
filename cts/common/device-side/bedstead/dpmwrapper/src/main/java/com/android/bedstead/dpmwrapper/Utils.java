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
package com.android.bedstead.dpmwrapper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic helpers.
 */
public final class Utils {

    private static final String TAG = "DpmWrapperUtils";

    static final boolean VERBOSE = false;

    static final int MY_USER_ID = UserHandle.myUserId();

    static final String ACTION_WRAPPED_MANAGER_CALL =
            "com.android.bedstead.dpmwrapper.action.WRAPPED_MANAGER_CALL";
    static final String EXTRA_CLASS = "className";
    static final String EXTRA_METHOD = "methodName";
    static final String EXTRA_NUMBER_ARGS = "number_args";
    static final String EXTRA_ARG_PREFIX = "arg_";

    private static final Object LOCK = new Object();

    @GuardedBy("LOCK")
    private static HandlerThread sHandlerThread;

    @GuardedBy("LOCK")
    private static Handler sHandler;

    static boolean isHeadlessSystemUserMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && UserManager.isHeadlessSystemUserMode();
    }

    static boolean isHeadlessSystemUser() {
        return isHeadlessSystemUserMode() && MY_USER_ID == UserHandle.USER_SYSTEM;
    }

    static boolean isCurrentUserOnHeadlessSystemUser(Context context) {
        return isHeadlessSystemUserMode()
                && context.getSystemService(UserManager.class).isUserForeground();
    }

    static void assertCurrentUserOnHeadlessSystemMode(Context context) {
        if (isCurrentUserOnHeadlessSystemUser(context)) return;

        throw new IllegalStateException("Should only be called by current user ("
                + ActivityManager.getCurrentUser() + ") on headless system user device, but was "
                        + "called by process from user " + MY_USER_ID);
    }

    static String toString(IntentFilter filter) {
        StringBuilder builder = new StringBuilder("[");
        filter.actionsIterator().forEachRemaining((s) -> builder.append(s).append(","));
        builder.deleteCharAt(builder.length() - 1);
        return builder.append(']').toString();
    }

    static Handler getHandler() {
        synchronized (LOCK) {
            if (sHandler == null) {
                sHandlerThread = new HandlerThread("DpmWrapperHandlerThread");
                Log.i(TAG, "Starting handler thread " + sHandlerThread);
                sHandlerThread.start();
                sHandler = new Handler(sHandlerThread.getLooper());
            }
        }
        return sHandler;
    }

    static <T> T callOnHandlerThread(Callable<T> callable) throws Exception {
        if (VERBOSE) Log.v(TAG, "callOnHandlerThread(): called from " + Thread.currentThread());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<T> returnRef = new AtomicReference<>();
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        getHandler().post(() -> {
            Log.d(TAG, "Calling callable on handler thread " + Thread.currentThread());
            try {
                T result = callable.call();
                if (VERBOSE) Log.v(TAG, "Got result: "  + result);
                returnRef.set(result);
            } catch (Exception e) {
                Log.e(TAG, "Got exception: "  + e);
                exceptionRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(50, TimeUnit.SECONDS)) {
            throw new TimeoutException("didn't get result in 50 seconds");
        }

        Exception exception = exceptionRef.get();
        if (exception != null) throw exception;

        return returnRef.get();
    }

    /**
     * Gets a more detailed description of an intent (for example, including extras).
     */
    public static String toString(Intent intent) {
        StringBuilder builder = new StringBuilder("[Intent: action=");
        String action = intent.getAction();
        if (action == null) {
            builder.append("null");
        } else {
            builder.append(action);
        }
        Set<String> categories = intent.getCategories();
        if (categories == null || categories.isEmpty()) {
            builder.append(", no_categories");
        } else {
            builder.append(", ").append(categories.size()).append(" categories: ")
                    .append(categories);
        }
        Bundle extras = intent.getExtras();
        builder.append(", ");
        if (extras == null || extras.isEmpty()) {
            builder.append("no_extras");
        } else {
            appendBundleExtras(builder, extras);
        }
        return builder.append(']').toString();
    }

    public static void appendBundleExtras(StringBuilder builder, Bundle bundle) {
        builder.append(bundle.size()).append(" extras: ");
        bundle.keySet().forEach(
                (key) -> builder.append(key).append('=').append(bundle.get(key)).append(','));
        builder.deleteCharAt(builder.length() - 1);
    }

    private Utils() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}

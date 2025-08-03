/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.shared.common;

import android.content.Context;

import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Entry point to get the application {@link Context} of the app.
 *
 * <p>The goal of this class is to make it easier to get a context in situations like static methods
 * or singletons, although it's not meant as a crutch to keep using them.
 */
@ThreadSafe
public final class ApplicationContextSingleton {
    @VisibleForTesting
    public static final String ERROR_MESSAGE_SET_NOT_CALLED = "set() not called yet";

    private static final AtomicReference<Context> sContext = new AtomicReference<>();

    /**
     * Gets the application context.
     *
     * @throws IllegalStateException if not {@link #set(Context) set} yet.
     */
    public static Context get() {
        Context context = sContext.get();
        Preconditions.checkState(context != null, ERROR_MESSAGE_SET_NOT_CALLED);
        return context;
    }

    /**
     * Sets the application context singleton (as the {@link Context#getApplicationContext()
     * application context} from {@code context}).
     *
     * @throws IllegalStateException if the singleton was already {@link #set(Context) set} and it
     *     is not the same as the {@link Context#getApplicationContext() application context} from
     *     {@code context}).
     */
    public static void set(Context context) {
        Context appContext =
                Objects.requireNonNull(context, "context cannot be null").getApplicationContext();

        Preconditions.checkArgument(
                appContext != null, "Context (%s) does not have an application context", context);

        // Set if it's not set yet
        if (sContext.compareAndSet(null, appContext)) {
            LogUtil.i("Set singleton context as %s", appContext);
            return;
        }

        // Otherwise, check it's the same.
        Context currentAppContext = sContext.get();
        if (currentAppContext != appContext) {
            // TODO(b/309169907): log to CEL
            throw new IllegalStateException(
                    "Trying to set app context as "
                            + appContext
                            + " (from "
                            + context
                            + "), when it was already set as "
                            + currentAppContext);
        }
    }

    // TODO(b/285300419): make it package protected so it's only accessed by rule
    /**
     * Gets the application context, returning {@code null} if it's not set yet.
     *
     * <p>Should only be used on unit tests - production code should call {@link #get()} instead.
     */
    @VisibleForTesting
    public static Context getForTests() {
        Context context = sContext.get();
        LogUtil.i("getForTests(): returning %s", context);
        return context;
    }

    // TODO(b/285300419): make it package protected so it's only accessed by rule
    /**
     * Sets the application context singleton as the given {@code context}, without doing any check.
     *
     * <p>Should only be used on unit tests - production code should call {@link #set(Context)
     * instead.
     */
    @VisibleForTesting
    public static void setForTests(Context context) {
        LogUtil.i("setForTests(): from %s to %s.", sContext.get(), context);
        sContext.set(context);
    }

    private ApplicationContextSingleton() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}

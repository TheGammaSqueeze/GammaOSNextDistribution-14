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

package com.android.activitycontext;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.compatibility.common.util.ShellIdentityUtils.QuadFunction;
import com.android.compatibility.common.util.ShellIdentityUtils.TriFunction;

import org.junit.Assume;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Activity used for tests which need an actual {@link Context}.
 */
public class ActivityContext extends Activity {

    /** Stores the request code, result code, and intent of an activity result. */
    public static final class ActivityResult {
        private final int mRequestCode;
        private final int mResultCode;
        private final Intent mIntent;

        public ActivityResult(int requestCode, int resultCode, Intent intent) {
            mRequestCode = requestCode;
            mResultCode = resultCode;
            mIntent = intent;
        }

        public int getRequestCode() {
            return mRequestCode;
        }

        public int getResultCode() {
            return mResultCode;
        }

        public Intent getIntent() {
            return mIntent;
        }
    }

    private static final class BlockingCallbackImpl extends BlockingCallback<ActivityResult> {
        public void set(ActivityResult result) {
            callbackTriggered(result);
        }
    }

    private static final String LOG_TAG = "ActivityContext";
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private static Function<ActivityContext, ?> sRunnable;
    private static ActivityContext sActivityContext;
    private static @Nullable Object sReturnValue;
    private static @Nullable Object sThrowValue;
    private static CountDownLatch sLatch;

    private static BlockingCallbackImpl sGetActivityCallback = new BlockingCallbackImpl();

    /**
     * Blocks the current thread until a result is set by an activity started by this activity, then
     * returns that result.
     */
    public ActivityResult blockForActivityResult() throws InterruptedException {
        try {
            return sGetActivityCallback.await();
        } finally {
            sGetActivityCallback = new BlockingCallbackImpl();
        }
    }

    /**
     * Run some code using an Activity {@link Context}.
     *
     * <p>This method should only be called from an instrumented app.
     *
     * <p>The {@link Activity} will be valid within the {@code runnable} callback. Passing the
     * {@link Activity} outside of the callback is not recommended because it may become invalid
     * due to lifecycle changes.
     *
     * <p>This method will block until the callback has been executed. It will return the same value
     * as returned by the callback.
     */
    public static <E> E getWithContext(Function<ActivityContext, E> runnable)
            throws InterruptedException {
        return getWithContextInternal(runnable, null);
    }

    /**
     * As {@link #getWithContext(Function)}, and also accepts a callback {@code blocking} will be
     * called before returning, so it can block the thread until some condition is met.
     */
    public static <E> E getWithContext(Function<ActivityContext, E> runnable,
            Consumer<ActivityContext> blocking)
            throws InterruptedException {
        return getWithContextInternal(runnable, blocking);
    }

    /** {@link #getWithContext(Function)} which does not return a value. */
    public static void runWithContext(Consumer<ActivityContext> runnable)
            throws InterruptedException {
        getWithContext((inContext) -> {
            runnable.accept(inContext);
            return null;
        });
    }

    /**
     * {@link #getWithContext(Function, Consumer)} which does not return a value, and also accepts a
     * callback {@code blocking} will be called before returning, so it can block the thread until
     * some condition is met.
     */
    public static void runWithContext(Consumer<ActivityContext> runnable,
            Consumer<ActivityContext> blocking) throws InterruptedException {
        getWithContext((inContext) -> {
            runnable.accept(inContext);
            return null;
        }, blocking);
    }

    /** {@link #getWithContext(Function)} with an additional argument. */
    public static <E, F> F getWithContext(E arg1,
            BiFunction<ActivityContext, E, F> runnable) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1));
    }

    /**
     * {@link #getWithContext(Function, Consumer)} with an additional argument, and also accepts a
     * callback {@code blocking} will be called before returning, so it can block the thread until
     * some condition is met.
     */
    public static <E, F> F getWithContext(E arg1,
            BiFunction<ActivityContext, E, F> runnable,
            Consumer<ActivityContext> blocking) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1), blocking);
    }

    /**
     * {@link #getWithContext(Function)} which takes an additional argument and does not
     * return a value.
     */
    public static <E> void runWithContext(E arg1, BiConsumer<ActivityContext, E> runnable)
            throws InterruptedException {
        getWithContext((inContext) -> {
            runnable.accept(inContext, arg1);
            return null;
        });
    }

    /**
     * {@link #getWithContext(Function, Consumer)} which takes an additional argument and does not
     * return a value, and also accepts a callback {@code blocking} will be called before returning,
     * so it can block the thread until some condition is met.
     */
    public static <E> void runWithContext(E arg1, BiConsumer<ActivityContext, E> runnable,
            Consumer<ActivityContext> blocking)
            throws InterruptedException {
        getWithContext((inContext) -> {
            runnable.accept(inContext, arg1);
            return null;
        }, blocking);
    }

    /** {@link #getWithContext(Function)} with two additional arguments. */
    public static <E, F, G> G getWithContext(E arg1, F arg2,
            TriFunction<ActivityContext, E, F, G> runnable) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1, arg2));
    }

    /**
     * {@link #getWithContext(Function, Consumer)} with two additional arguments, and also accepts a
     * callback {@code blocking} will be called before returning, so it can block the thread until
     * some condition is met.
     */
    public static <E, F, G> G getWithContext(E arg1, F arg2,
            TriFunction<ActivityContext, E, F, G> runnable,
            Consumer<ActivityContext> blocking) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1, arg2), blocking);
    }

    /** {@link #getWithContext(Function)} with three additional arguments. */
    public static <E, F, G, H> H getWithContext(E arg1, F arg2, G arg3,
            QuadFunction<ActivityContext, E, F, G, H> runnable) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1, arg2, arg3));
    }

    /**
     * {@link #getWithContext(Function, Consumer)} with three additional arguments, and also accepts
     * a callback {@code blocking} will be called before returning, so it can block the thread until
     * some condition is met.
     */
    public static <E, F, G, H> H getWithContext(E arg1, F arg2, G arg3,
            QuadFunction<ActivityContext, E, F, G, H> runnable,
            Consumer<ActivityContext> blocking) throws InterruptedException {
        return getWithContext((inContext) -> runnable.apply(inContext, arg1, arg2, arg3), blocking);
    }

    private static <E> E getWithContextInternal(Function<ActivityContext, E> runnable,
            @Nullable Consumer<ActivityContext> blocking)
            throws InterruptedException {
        if (runnable == null) {
            throw new NullPointerException();
        }

        // As we show an Activity we must be in the foreground
        UserReference currentUser = TestApis.users().current();
        try {
            Assume.assumeTrue(
                    "Requires user to be switched to but instrumented user cannot be switched to",
                    TestApis.users().instrumented().canBeSwitchedTo() ||
                            (TestApis.users().instrumented().isProfile()
                                    && TestApis.users().instrumented().parent().canBeSwitchedTo()));
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

            if (!instrumentation.getContext().getPackageName().equals(
                    instrumentation.getTargetContext().getPackageName())) {
                throw new IllegalStateException(
                        "ActivityContext can only be used in test apps which instrument themselves."
                                + " Consider ActivityScenario for this case.");
            }

            synchronized (ActivityContext.class) {
                sRunnable = runnable;

                sLatch = new CountDownLatch(1);
                sReturnValue = null;
                sThrowValue = null;

                Intent intent = new Intent();
                intent.setClass(sContext, ActivityContext.class);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
                sContext.startActivity(intent);
            }

            if (!sLatch.await(5, TimeUnit.MINUTES)) {
                throw new NeneException("Timed out while waiting for lambda with context to"
                        + " complete.");
            }

            if (blocking != null) {
                blocking.accept(sActivityContext);
            }

            synchronized (ActivityContext.class) {
                sRunnable = null;
                sActivityContext = null;

                if (sThrowValue != null) {
                    if (sThrowValue instanceof RuntimeException) {
                        throw (RuntimeException) sThrowValue;
                    }

                    if (sThrowValue instanceof Error) {
                        throw (Error) sThrowValue;
                    }

                    throw new IllegalStateException("Invalid value for sThrowValue");
                }

                return (E) sReturnValue;
            }
        } finally {
            currentUser.switchTo();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (ActivityContext.class) {
            sActivityContext = this;
            if (sRunnable == null) {
                Log.e(LOG_TAG, "Launched ActivityContext without runnable");
            } else {
                try {
                    sReturnValue = sRunnable.apply(this);
                } catch (RuntimeException | Error e) {
                    sThrowValue = e;
                }
                sLatch.countDown();
            }
        }
    }

    @Override
    // TODO(b/198280332): Remove this temporary solution to set return values for methods
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        sGetActivityCallback.set(new ActivityResult(requestCode, resultCode, data));
    }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.compatibility.common.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A receiver that allows caller to wait for the broadcast synchronously. Notice that you should not
 * reuse the instance. Usage is typically like this:
 * <pre>
 *     BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context, "action");
 *     try {
 *         receiver.register();
 *         // Action which triggers intent
 *         Intent intent = receiver.awaitForBroadcast();
 *         // assert the intent
 *     } finally {
 *         receiver.unregisterQuietly();
 *     }
 * </pre>
 *
 * If you do not care what intent is broadcast and just wish to block until a matching intent is
 * received you can use alternative syntax:
 * <pre>
 *     try (BlockingBroadcastReceiver r =
 *              BlockingBroadcastReceiver.create(context, "action").register()) {
 *         // Action which triggers intent
 *     }
 *
 *     // Code which should be executed after broadcast is received
 * </pre>
 *
 * If the broadcast is not received an exception will be thrown. Note that if an exception is thrown
 * within the try block which results in the broadcast not being sent, then that exception will be
 * hidden by the not-received exception.
 */
public class BlockingBroadcastReceiver extends BroadcastReceiver implements AutoCloseable {
    private static final String TAG = "BlockingBroadcast";

    private int mTimeoutSeconds = 240; // 4 minutes by default

    private Intent mReceivedIntent = null;
    private final BlockingQueue<Intent> mBlockingQueue;
    private final Set<IntentFilter> mIntentFilters;
    private final Context mContext;
    @Nullable
    private final Function<Intent, Boolean> mChecker;

    public static BlockingBroadcastReceiver create(Context context, String expectedAction) {
        return create(context, new IntentFilter(expectedAction));
    }

    public static BlockingBroadcastReceiver create(Context context, IntentFilter intentFilter) {
        return create(context, intentFilter, /* checker= */ null);
    }

    public static BlockingBroadcastReceiver create(Context context, String expectedAction,
            Function<Intent, Boolean> checker) {
        return create(context, new IntentFilter(expectedAction), checker);
    }

    public static BlockingBroadcastReceiver create(Context context, IntentFilter intentFilter,
            Function<Intent, Boolean> checker) {
        return create(context, Set.of(intentFilter), checker);
    }

    public static BlockingBroadcastReceiver create(Context context,
            Set<IntentFilter> intentFilters) {
        return create(context, intentFilters, /* checker= */ null);
    }

    public static BlockingBroadcastReceiver create(Context context, Set<IntentFilter> intentFilters,
            Function<Intent, Boolean> checker) {
        BlockingBroadcastReceiver blockingBroadcastReceiver =
                new BlockingBroadcastReceiver(context, intentFilters, checker);

        return blockingBroadcastReceiver;
    }

    public BlockingBroadcastReceiver(Context context) {
        this(context, Set.of());
    }

    public BlockingBroadcastReceiver(Context context, String expectedAction) {
        this(context, new IntentFilter(expectedAction));
    }

    public BlockingBroadcastReceiver(Context context, IntentFilter intentFilter) {
        this(context, intentFilter, /* checker= */ null);
    }

    public BlockingBroadcastReceiver(Context context, IntentFilter intentFilter,
            Function<Intent, Boolean> checker) {
        this(context, Set.of(intentFilter), checker);
    }

    public BlockingBroadcastReceiver(Context context, String expectedAction,
            Function<Intent, Boolean> checker) {
        this(context, new IntentFilter(expectedAction), checker);
    }

    public BlockingBroadcastReceiver(Context context, Set<IntentFilter> intentFilters) {
        this(context, intentFilters, /* checker= */ null);
    }

    public BlockingBroadcastReceiver(
            Context context, Set<IntentFilter> intentFilters, Function<Intent, Boolean> checker) {
        mContext = context;
        mIntentFilters = intentFilters;
        mBlockingQueue = new ArrayBlockingQueue<>(1);
        mChecker = checker;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received intent: " + intent);

        if (mBlockingQueue.remainingCapacity() == 0) {
            Log.i(TAG, "Received intent " + intent + " but queue is full.");
            return;
        }

        if (mChecker == null || mChecker.apply(intent)) {
            mBlockingQueue.add(intent);
        }
    }

    public BlockingBroadcastReceiver register() {
        for (IntentFilter intentFilter : mIntentFilters) {
            mContext.registerReceiver(this, intentFilter,
                    Context.RECEIVER_EXPORTED_UNAUDITED);
        }

        return this;
    }

    public BlockingBroadcastReceiver registerForAllUsers() {
        for (IntentFilter intentFilter : mIntentFilters) {
            mContext.registerReceiverForAllUsers(
                    this, intentFilter, /* broadcastPermission= */ null,
                    /* scheduler= */ null,
                    Context.RECEIVER_EXPORTED_UNAUDITED);
        }

        return this;
    }

    /**
     * Wait until the broadcast.
     *
     * <p>If no matching broadcasts is received within {@link #mTimeoutSeconds} seconds an
     * {@link AssertionError} will be thrown.
     */
    public void awaitForBroadcastOrFail() {
        awaitForBroadcastOrFail(mTimeoutSeconds * 1000);
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within {@link #mTimeoutSeconds} seconds.
     */
    public @Nullable
    Intent awaitForBroadcast() {
        return awaitForBroadcast(mTimeoutSeconds * 1000);
    }

    /**
     * Wait until the broadcast.
     *
     * <p>If no matching broadcasts is received within the given timeout an {@link AssertionError}
     * will be thrown.
     */
    public void awaitForBroadcastOrFail(long timeoutMillis) {
        if (awaitForBroadcast(timeoutMillis) == null) {
            throw new AssertionError("Did not receive matching broadcast");
        }
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within the given timeout.
     */
    public @Nullable
    Intent awaitForBroadcast(long timeoutMillis) {
        if (mReceivedIntent != null) {
            return mReceivedIntent;
        }

        try {
            mReceivedIntent = mBlockingQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForBroadcast get interrupted: ", e);
        }
        return mReceivedIntent;
    }

    public void unregisterQuietly() {
        try {
            mContext.unregisterReceiver(this);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to unregister BlockingBroadcastReceiver: ", ex);
        }
    }

    @Override
    public void close() {
        try {
            awaitForBroadcastOrFail();
        } finally {
            unregisterQuietly();
        }
    }

    /**
     * Change the default timeout to wait for the broadcast. It's 4 minutes by default.
     * @param timeoutSeconds new timeout value, in seconds.
     */
    public void setTimeout(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be a positive number.");
        }
        mTimeoutSeconds = timeoutSeconds;
    }
}
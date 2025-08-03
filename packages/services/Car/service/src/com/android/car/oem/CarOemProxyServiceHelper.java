/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.oem;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Process;
import android.util.ArrayMap;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// TODO(b/239698894):Enhance logging to keep track of timeout calls, and dump last 10 timeout calls.

/**
 * This class does following:
 * <ul>
 *   <li>Handles binder call to OEM Service and exposes multiple APIs to call OEM Service.
 *   <li>Handles OEM service crash.
 *   <li>Tracks circular call for OEM Service.
 * </ul>
 * <p>
 * If there is more than {@link MAX_CIRCULAR_CALLS_PER_CALLER} circular calls per binder or more
 * than {@link MAX_CIRCULAR_CALL_TOTAL} circular calls overall, Car Service and OEM service
 * would be crashed.
 */
public final class CarOemProxyServiceHelper {

    private static final String TAG = CarLog.tagFor(CarOemProxyServiceHelper.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int EXIT_FLAG = 10;
    private static final int MAX_THREAD_POOL_SIZE = 16;
    private static final int MIN_THREAD_POOL_SIZE = 8;

    @VisibleForTesting
    /**
     * Max number of circular calls per caller. A circular is call between CarService and OEM
     * Service where CarService calls OEM service and OEM services calls back to CarService.
     */
    static final int MAX_CIRCULAR_CALLS_PER_CALLER = 5;

    @VisibleForTesting
    /**
     * Max number of total circular calls. A circular is call between CarService and OEM
     * Service where CarService calls OEM service and OEM services calls back to CarService.
     *
     * <p>MAX_CIRCULAR_CALL_TOTAL should be less than MIN_THREAD_POOL_SIZE
     */
    static final int MAX_CIRCULAR_CALL_TOTAL = 6;

    private final Object mLock = new Object();

    private final int mRegularCallTimeoutMs;
    private final int mCrashCallTimeoutMs;
    // Kept for dumping
    private final int mThreadPoolSizeFromRRO;

    // Thread pool size will be read from resource overlay. The default value is 8. The maximum
    // and minimum thread pool size can be MAX_THREAD_POOL_SIZE and MIN_THREAD_POOL_SIZE
    // respectively. If resource overlay value is more than MAX_THREAD_POOL_SIZE, then thread
    // pool size will be MAX_THREAD_POOL_SIZE. If resource overlay value is less than
    // MIN_THREAD_POOL_SIZE, then thread pool size will be MIN_THREAD_POOL_SIZE.
    private final int mBinderDispatchThreadPoolSize;

    /**
     * This map would keep track of possible circular calls
     * <p>
     * Ideally there should not be any call from OEM service to Car Service but it may be required
     * for some reason. In such situation, It is important that it doesn't create circular calls.
     *
     * For example:
     * <ol>
     *   <li>CarService calling OEM Service
     *   <li>OEM Service calling car-lib
     *   <li>Car-lib calling CarService
     *   <li>CarService calling OEM Service again
     * </ol>
     * <p>
     * This may create infinite loop. If something like this is detected, CarService and OEM Service
     * would be crashed, and this map would keep track of such circular calls.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, Integer> mCallerTracker = new ArrayMap<String, Integer>(2);

    private final ExecutorService mThreadPool;
    @GuardedBy("mLock")
    private Callable<String> mOemStackTracer;
    @GuardedBy("mLock")
    private int mTotalCircularCallsInProcess;
    @GuardedBy("mLock")
    private int mOemCarServicePid;

    public CarOemProxyServiceHelper(Context context) {
        Resources res = context.getResources();
        mRegularCallTimeoutMs = res
                .getInteger(R.integer.config_oemCarService_regularCall_timeout_ms);
        mCrashCallTimeoutMs = res
                .getInteger(R.integer.config_oemCarService_crashCall_timeout_ms);
        mThreadPoolSizeFromRRO = res
                .getInteger(R.integer.config_oemCarService_thread_pool_size);
        if (mThreadPoolSizeFromRRO > MAX_THREAD_POOL_SIZE) {
            mBinderDispatchThreadPoolSize = MAX_THREAD_POOL_SIZE;
        } else if (mThreadPoolSizeFromRRO < MIN_THREAD_POOL_SIZE) {
            mBinderDispatchThreadPoolSize = MIN_THREAD_POOL_SIZE;
        } else {
            mBinderDispatchThreadPoolSize = mThreadPoolSizeFromRRO;
        }

        mThreadPool = Executors.newFixedThreadPool(mBinderDispatchThreadPoolSize);

        Slogf.i(TAG, "RegularCallTimeoutMs: %d, CrashCallTimeoutMs: %d, ThreadPoolSizeFromRRO: %d,"
                + " ThreadPoolSize: %d.", mRegularCallTimeoutMs,
                mCrashCallTimeoutMs, mThreadPoolSizeFromRRO, mBinderDispatchThreadPoolSize);
    }

    /**
     * Does timed call to the OEM service and returns default value if OEM service timed out or
     * throws any other Exception.
     *
     * <p>Caller would not know if the call to OEM service timed out or returned a valid value which
     * could be same as defaultValue. It is preferred way to call OEM service if the defaultValue is
     * an acceptable result.
     *
     * @param <T> Type of the result.
     * @param callerTag is tag from the caller. Used for tracking circular calls per binder.
     * @param callable containing binder call.
     * @param defaultValue to be returned if call timeout or any other exception is thrown.
     *
     * @return Result of the binder call. Result can be null.
     */
    @Nullable
    public <T> T doBinderTimedCallWithDefaultValue(String callerTag, Callable<T> callable,
            T defaultValue) {
        if (DBG) {
            Slogf.d(TAG, "Received doBinderTimedCallWithDefaultValue call for caller tag: %s.",
                    callerTag);
        }
        startTracking(callerTag);
        try {
            Future<T> result = mThreadPool.submit(callable);
            try {
                return result.get(mRegularCallTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
                Slogf.w(TAG, "Binder call threw an exception. Return default value %s for caller "
                        + "tag: %s", defaultValue, callerTag);
                return defaultValue;
            }
        } finally {
            stopTracking(callerTag);
        }
    }

    /**
     * Does timed call to the OEM service and throws timeout exception.
     *
     * <p>Throws timeout exception if OEM service times out. If OEM service throw any other
     * exception, it is wrapped in Timeout exception.
     *
     * @param <T> Type of the result.
     * @param callerTag is tag from the caller. Used for tracking circular calls per binder.
     * @param callable containing binder call.
     * @param timeoutMs in milliseconds.
     *
     * @return result of the binder call. Result can be null.
     *
     * @throws TimeoutException if call times out or throws any other exception.
     */
    @Nullable
    public <T> T doBinderTimedCallWithTimeout(String callerTag, Callable<T> callable,
            long timeoutMs) throws TimeoutException {
        if (DBG) {
            Slogf.d(TAG, "Received doBinderTimedCallWithTimeout call for caller tag: %s.",
                    callerTag);
        }
        startTracking(callerTag);
        try {
            Future<T> result = mThreadPool.submit(callable);
            try {
                return result.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                Slogf.w(TAG, "Binder call received Exception", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
                // Throw timeout exception even for other exception as caller knows how to handle
                // timeout exception in this case.
                TimeoutException exception = new TimeoutException();
                exception.initCause(e);
                throw exception;
            }
        } finally {
            stopTracking(callerTag);
        }
    }

    /**
     * Does timed call to OEM service with two different timeouts.
     *
     * <p>If OEM service returns before the {@code defaultTimeoutMs}, it would return OEM response.
     * After {@code defaultTimeoutMs}, call will return {@link Optional#empty()} and queue a tracker
     * for OEM service response asynchronously. Tracker would wait for {@code mCrashCallTimeoutMs}
     * for OEM service to response. If OEM service respond before {code mCrashCallTimeoutMs},
     * callback {@code CallbackForDelayedResult} will be used to post the OEM results on the
     * original caller. If OEM service doesn't respond within {code mCrashCallTimeoutMs}, CarService
     * and OEM service both will be crashed.
     *
     * <p>This call should be used if it is okay to quickly check for results from OEM Service, and
     * it is possible to incorporate results from OEM service if delivered late.
     *
     * <p>If the binder to OEM service throw any exception during short timeout, it would be ignored
     * and {@link Optional#empty()} is returned. If the binder to OEM service throw timeout
     * exception after the longer timeout, it would rash the CarService and OEM Service. If the
     * binder to OEM service throw any other exception during longer timeout, it would be ignored
     * and {@link Optional#empty()} is returned.
     *
     * @param <T> Type of the result.
     * @param callerTag is tag from the caller. Used for tracking circular calls per binder.
     * @param callable containing binder call.
     * @param defaultTimeoutMs in milliseconds.
     * @param callback for the delayed results. Callback waits for {@code mCrashCallTimeoutMs}.
     *
     * @return Optional carrying result of the binder call. Optional can be empty.
     */
    public <T> Optional<T> doBinderCallWithDefaultValueAndDelayedWaitAndCrash(String callerTag,
            Callable<T> callable, long defaultTimeoutMs,
            CallbackForDelayedResult<T> callback) {
        if (DBG) {
            Slogf.d(TAG, "Received doBinderCallWithDefaultValueAndDelayedWaitAndCrash call for"
                    + " caller tag: %s.", callerTag);
        }
        startTracking(callerTag);
        try {
            Future<T> result = mThreadPool.submit(callable);
            try {
                return Optional.ofNullable(result.get(defaultTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
                Slogf.e(TAG, "Binder call received Exception", e);
            }

            // Queue for long wait time check
            mThreadPool.execute(() -> {
                startTracking(callerTag);
                try {
                    callback.onDelayedResults(
                            Optional.ofNullable(
                                    result.get(mCrashCallTimeoutMs, TimeUnit.MILLISECONDS)));
                } catch (TimeoutException e) {
                    Slogf.e(TAG, "Binder call received TimeoutException", e);
                    crashCarService("TimeoutException");
                } catch (InterruptedException | ExecutionException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                    }
                    Slogf.e(TAG, "Binder call received Eexception", e);
                    callback.onDelayedResults(Optional.empty());
                } finally {
                    stopTracking(callerTag);
                }
            });
        } finally {
            stopTracking(callerTag);
        }

        return Optional.empty();
    }

    /**
     * Callback for getting OEM results after default timeout.
     *
     * @param <T> Type of the result.
     */
    public interface CallbackForDelayedResult<T> {
        /**
         * Invoked when OEM results are received after default timeout.
         *
         * @param result received from OEM service
         */
        void onDelayedResults(Optional<T> result);
    }

    private void stopTracking(String callerTag) {
        synchronized (mLock) {
            if (Binder.getCallingPid() != mOemCarServicePid) return;
        }
        // update tracker
        synchronized (mLock) {
            int currentCircularCallForTag = mCallerTracker.getOrDefault(callerTag, 0);
            if (currentCircularCallForTag <= 0 || mTotalCircularCallsInProcess <= 0) {
                Slogf.wtf(TAG, "Current Circular Calls for %s is %d which is unexpected.",
                        callerTag, currentCircularCallForTag);
            }
            mCallerTracker.put(callerTag, currentCircularCallForTag - 1);
            mTotalCircularCallsInProcess = mTotalCircularCallsInProcess - 1;
        }
    }

    private void startTracking(String callerTag) {
        synchronized (mLock) {
            if (Binder.getCallingPid() != mOemCarServicePid) return;
        }

        int currentCircularCallForTag;
        int totalCircularCallsInProcess;

        synchronized (mLock) {
            currentCircularCallForTag = mCallerTracker.getOrDefault(callerTag, 0) + 1;
            mCallerTracker.put(callerTag, currentCircularCallForTag);
            totalCircularCallsInProcess = mTotalCircularCallsInProcess + 1;
            mTotalCircularCallsInProcess = totalCircularCallsInProcess;
        }

        Slogf.w(TAG, "Possible circular call for %s. Current circular calls are %d."
                + " Total circular calls are %d.", callerTag, currentCircularCallForTag,
                totalCircularCallsInProcess);

        if (currentCircularCallForTag > MAX_CIRCULAR_CALLS_PER_CALLER) {
            Slogf.e(TAG, "Current Circular Calls for %s is %d which is more than the limit %d."
                    + " Calling to crash CarService", callerTag, currentCircularCallForTag,
                    MAX_CIRCULAR_CALLS_PER_CALLER);
            crashCarService("Max Circular call for " + callerTag);
        }

        if (totalCircularCallsInProcess > MAX_CIRCULAR_CALL_TOTAL) {
            Slogf.e(TAG, "Total Circular Calls is %d which is more than the limit %d."
                    + " Calling to crash CarService", totalCircularCallsInProcess,
                    MAX_CIRCULAR_CALL_TOTAL);
            crashCarService("Max Circular calls overall");
        }
    }

    /**
     * Does timed call to the OEM service and crashes the OEM and Car Service if call is not served
     * within time.
     *
     * <p>If OEM service throw TimeoutException, it would crash the CarService and OEM service. If
     * OemService throws any other exception, {@link Optional#empty()} is returned.
     *
     * @param <T> Type of the result.
     * @param callerTag is tag from the caller. Used for tracking circular calls per binder.
     * @param callable containing binder call.
     *
     * @return Optional carrying result of the binder call. Optional can be empty.
     */
    public <T> Optional<T> doBinderCallWithTimeoutCrash(String callerTag, Callable<T> callable) {
        if (DBG) {
            Slogf.d(TAG, "Received doBinderCallWithTimeoutCrash call for caller tag: %s.",
                    callerTag);
        }
        startTracking(callerTag);
        try {
            Future<T> result = mThreadPool.submit(callable);
            try {
                return Optional.ofNullable(result.get(mCrashCallTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                Slogf.e(TAG, "Binder call received Exception", e);
                crashCarService("TimeoutException");
            } catch (InterruptedException | ExecutionException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
                Slogf.e(TAG, "Binder call received Exception", e);
                return Optional.empty();
            }
        } finally {
            stopTracking(callerTag);
        }

        throw new AssertionError("Should not return from crashCarService");
    }

    /**
     * Does one way call to OEM Service. Runnable will be queued to threadpool and not waited for
     * completion.
     *
     * <p>It is recommended to use callable with some result if waiting for call to complete is
     * required.
     *
     * @param callerTag is tag from the caller. Used for tracking circular calls per binder.
     * @param runnable containing binder call
     */
    public void doBinderOneWayCall(String callerTag, Runnable runnable) {
        if (DBG) {
            Slogf.d(TAG, "Received doBinderOneWayCall call for caller tag: %s.", callerTag);
        }

        mThreadPool.execute(() -> {
            startTracking(callerTag);
            try {
                Future<?> result = mThreadPool.submit(runnable);
                try {
                    result.get(mRegularCallTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                    }
                    Slogf.e(TAG, "Exception while running a runnable for caller tag: " + callerTag,
                            e);
                }
            } finally {
                stopTracking(callerTag);
            }
        });
    }

    /**
     * Crashes CarService and OEM Service.
     */
    public void crashCarService(String reason) {
        Slogf.e(TAG, "****Crashing CarService and OEM service because %s****", reason);
        Slogf.e(TAG, "Car Service stack-");
        Slogf.e(TAG, Log.getStackTraceString(new Exception()));

        Callable<String> oemStackTracer = null;
        synchronized (mLock) {
            oemStackTracer = mOemStackTracer;
        }

        if (oemStackTracer != null) {
            Slogf.e(TAG, "OEM Service stack-");
            int timeoutMs = 2000;
            String stack = "";
            // Call OEM service directly. Calling any doBinderCallXXX here can lead to infinite loop
            Future<String> result = mThreadPool.submit(oemStackTracer);
            try {
                stack = result.get(timeoutMs, TimeUnit.MILLISECONDS);
                Slogf.e(TAG, stack);
            } catch (Exception e) {
                Slogf.e(TAG, "Didn't received OEM stack within %d milliseconds.\n", timeoutMs);
            }
        }

        int carServicePid = Process.myPid();
        int oemCarServicePid;
        synchronized (mLock) {
            oemCarServicePid = mOemCarServicePid;
        }

        if (oemCarServicePid != 0) {
            Slogf.e(TAG, "Killing OEM service process with PID %d.", oemCarServicePid);
            Process.killProcess(oemCarServicePid);
        }
        Slogf.e(TAG, "Killing Car service process with PID %d.", carServicePid);
        Process.killProcess(carServicePid);
        System.exit(EXIT_FLAG);
    }

    /**
     * Updates PID of the OEM process.
     */
    public void updateOemPid(int pid) {
        synchronized (mLock) {
            mOemCarServicePid = pid;
        }
    }

    /**
     * Dumps
     */
    public void dump(IndentingPrintWriter writer) {
        writer.println("***CarOemProxyServiceHelper dump***");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.printf("mOemCarServicePid: %d\n", mOemCarServicePid);
            writer.printf("mCallerTracker.size: %d\n", mCallerTracker.size());
            if (mCallerTracker.size() > 0) {
                writer.increaseIndent();
                for (int i = 0; i < mCallerTracker.size(); i++) {
                    writer.printf("mCallerTracker entry: %d, CallerTag: %s, CircularCalls: %d\n", i,
                            mCallerTracker.keyAt(i), mCallerTracker.valueAt(i));
                }
                writer.decreaseIndent();
            }
            writer.printf("mRegularCallTimeoutMs: %d\n", mRegularCallTimeoutMs);
            writer.printf("mCrashCallTimeoutMs: %d\n", mCrashCallTimeoutMs);
            writer.printf("mBinderDispatchThreadPoolSize: %d\n", mBinderDispatchThreadPoolSize);
            writer.printf("ThreadPoolSizeFromRRO: %d\n", mThreadPoolSizeFromRRO);
        }
        writer.decreaseIndent();
    }

    /**
     * Updates call for getting OEM stack trace.
     */
    public void updateOemStackCall(Callable<String> oemStackTracer) {
        synchronized (mLock) {
            mOemStackTracer = oemStackTracer;
        }
    }
}

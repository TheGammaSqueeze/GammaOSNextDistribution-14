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

package android.hardware.devicestate.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.server.wm.ActivityManagerTestBase;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

/**
 * Abstract base class for {@link DeviceStateManager} CTS tests.
 */
@RunWith(AndroidJUnit4.class)
public abstract class DeviceStateManagerTestBase extends ActivityManagerTestBase {
    static final int CALLBACK_TIMEOUT_MS = 1000;

    private DeviceStateManager mDeviceStateManager;

    @CallSuper
    @Before
    public void setup() {
        mDeviceStateManager = getInstrumentation().getTargetContext()
                .getSystemService(DeviceStateManager.class);
    }

    /** Returns an instance of {@link DeviceStateManager} for use in tests. */
    @NonNull
    DeviceStateManager getDeviceStateManager() {
        if (mDeviceStateManager == null) {
            // called before setup();
            throw new IllegalStateException();
        }
        return mDeviceStateManager;
    }

    /**
     * Runs the supplied {@code Runnable} ensuring the {@code request} is active during execution.
     * If the request becomes suspended or canceled before or during runnable execution a
     * {@link java.lang.InterruptedException} will be thrown.
     */
    protected final void runWithRequestActive(@NonNull DeviceStateRequest request,
            boolean isBaseStateRequest,
            @NonNull Runnable runnable) throws Throwable {
        final UncaughtExceptionHandler exceptionHandler = new UncaughtExceptionHandler();
        final RequestAwareThread thread = new RequestAwareThread(request, runnable);
        thread.setUncaughtExceptionHandler(exceptionHandler);
        try (DeviceStateRequestSession session =
                     new DeviceStateRequestSession(mDeviceStateManager, request,
                isBaseStateRequest, thread)) {
            // Set the exception handler to get the exception and rethrow.
            thread.start();
            // Wait for the request aware thread to finish executing the runnable. If the request
            // is suspended or canceled this method will throw an InterruptedException.
            thread.join();
        }

        // Rethrow any exceptions from the runnable.
        final Throwable t = exceptionHandler.getThrowable();
        if (t != null) {
            throw t;
        }
    }

    /**
     * An implementation of {@link Thread} that listens to changes in a request state and
     * automatically interrupts if the request is suspended or canceled while the thread
     * is running.
     */
    private static final class RequestAwareThread extends Thread
            implements DeviceStateRequest.Callback {
        private final Object mLock = new Object();

        private final CountDownLatch mActiveLatch = new CountDownLatch(1);

        @NonNull
        private final DeviceStateRequest mRequest;
        @NonNull
        private final Runnable mRunnable;

        @GuardedBy("mLock")
        private boolean mIsRunning;
        @GuardedBy("mLock")
        private boolean mWasSuspendedOrCanceled;

        private RequestAwareThread(@NonNull DeviceStateRequest request,
                @NonNull Runnable runnable) {
            mRequest = request;
            mRunnable = runnable;
        }

        @Override
        public void run() {
            // Wait for the request to be active.
            boolean success;
            try {
                success = mActiveLatch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // This thread was interrupted while waiting for the callback.
                success = false;
            }
            if (!success) {
                throw new RuntimeException("Timed out waiting for " + toString(mRequest)
                        + " to become active.");
            }
            synchronized (mLock) {
                if (mWasSuspendedOrCanceled) {
                    interrupt();
                    return;
                }
                mIsRunning = true;
            }
            try {
                mRunnable.run();
            } finally {
                synchronized (mLock) {
                    mIsRunning = false;
                }
            }
        }

        @Override
        public void onRequestActivated(@NonNull DeviceStateRequest request) {
            if (!request.equals(mRequest)) {
                return;
            }

            mActiveLatch.countDown();
        }

        @Override
        public void onRequestSuspended(@NonNull DeviceStateRequest request) {
            if (!request.equals(mRequest)) {
                return;
            }

            synchronized (mLock) {
                mWasSuspendedOrCanceled = true;
                interruptIfRunningLocked();
            }
        }

        @Override
        public void onRequestCanceled(@NonNull DeviceStateRequest request) {
            if (!request.equals(mRequest)) {
                return;
            }

            synchronized (mLock) {
                mWasSuspendedOrCanceled = true;
                interruptIfRunningLocked();
            }
        }

        private void interruptIfRunningLocked() {
            if (mIsRunning) {
                // Interrupt this thread if the runnable is still running and the request was
                // cancelled or suspended.
                interrupt();
            }
        }

        private static String toString(@NonNull DeviceStateRequest request) {
            return "DeviceStateRequest{state=" + request.getState() + ", flags="
                    + request.getFlags() + "}";
        }
    }

    /**
     * An implementation of {@link Thread.UncaughtExceptionHandler} that simply stores the latest
     * notified uncaught exception.
     */
    private static final class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Nullable
        private Throwable mThrowable;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            mThrowable = e;
        }

        @Nullable
        public Throwable getThrowable() {
            return mThrowable;
        }
    }
}

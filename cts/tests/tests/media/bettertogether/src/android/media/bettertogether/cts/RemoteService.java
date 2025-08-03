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

package android.media.bettertogether.cts;

import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for a service that runs on a remote process. The service that extends this class must
 * be added to AndroidManifest.xml with "android:process" attribute to be run on a separate process.
 */
public abstract class RemoteService extends Service {
    private static final String TAG = "RemoteService";
    public static final long TIMEOUT_MS = 10_000;

    private RemoteServiceStub mBinder;
    private HandlerThread mHandlerThread;
    private volatile Handler mHandler;

    @Override
    public void onCreate() {
        mBinder = new RemoteServiceStub();
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        mHandlerThread.quitSafely();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Called by {@link Invoker#run}. It will be run on a dedicated {@link HandlerThread}.
     *
     * @param testId id of the test case
     * @param step the step of a command to run
     * @param args optional arguments
     * @throws Exception if any
     */
    public abstract void onRun(int testId, int step, @Nullable Bundle args) throws Exception;

    private boolean runOnHandlerSync(TestRunnable runnable) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        mHandler.post(() -> {
            try {
                runnable.run();
            } catch (Throwable th) {
                throwable.set(th);
                Log.e(TAG, "Error while running TestRunnable", th);
            }
            latch.countDown();
        });
        try {
            boolean done = latch.await(TIMEOUT_MS, MILLISECONDS);
            return done && throwable.get() == null;
        } catch (InterruptedException ex) {
            Log.w(TAG, ex);
            return false;
        }
    }

    private interface TestRunnable {
        void run() throws Exception;
    }

    private class RemoteServiceStub extends IRemoteService.Stub {
        @Override
        public boolean run(int testId, int step, Bundle args) throws RemoteException {
            return runOnHandlerSync(() -> onRun(testId, step, args));
        }
    }

    /**
     * A class to run commands on a {@link RemoteService} for a test case.
     */
    public static class Invoker implements Closeable {
        private static final String ASSERTION_MESSAGE =
                "Failed on remote service. See logcat TAG=" + TAG + " for detail.";

        private final Context mContext;
        private final int mTestId;
        private final CountDownLatch mConnectionLatch;
        private final ServiceConnection mServiceConnection;
        private IRemoteService mBinder;

        /**
         * Creates an instance and connects to the remote service.
         *
         * @param context the context
         * @param serviceClass the class of remote service
         * @param testId id of the test case
         * @throws InterruptedException if the thread is interrupted while waiting for connection
         */
        public Invoker(@NonNull Context context,
                @NonNull Class<? extends RemoteService> serviceClass, int testId)
                throws InterruptedException {
            mContext = context;
            mTestId = testId;
            mConnectionLatch = new CountDownLatch(1);
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mBinder = IRemoteService.Stub.asInterface(service);
                    mConnectionLatch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mBinder = null;
                }
            };

            Intent intent = new Intent(mContext, serviceClass);
            mContext.bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
            assertTrue("Failed to bind to service " + serviceClass,
                    mConnectionLatch.await(TIMEOUT_MS, MILLISECONDS));
        }

        /**
         * Disconnects from the remote service.
         */
        @Override
        public void close() {
            mContext.unbindService(mServiceConnection);
        }

        /**
         * Invokes {@link #onRun} on the remote service without optional arguments.
         *
         * @param step the step of a command to run
         * @throws RemoteException if binder throws exception
         */
        public void run(int step) throws RemoteException {
            run(step, null);
        }

        /**
         * Invokes {@link #onRun} on the remote service.
         *
         * @param step the step of a command to run
         * @param args optional arguments
         * @throws RemoteException if binder throws exception
         */
        public void run(int step, @Nullable Bundle args) throws RemoteException {
            assertTrue(ASSERTION_MESSAGE, mBinder.run(mTestId, step, args));
        }
    }
}

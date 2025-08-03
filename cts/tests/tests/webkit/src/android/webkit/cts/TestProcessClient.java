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

package android.webkit.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;

import com.google.common.util.concurrent.SettableFuture;

import junit.framework.Assert;

/**
 * IPC interface to run tests in a freshly spawned service process.
 *
 * CTS test modules usually run all tests in the same process, but some WebView tests
 * need to verify things that only happen once per process. This client interface allows
 * two separate service processes to be created which are guaranteed to be freshly launched
 * and to not have loaded the WebView implementation before the test runs. The caller must
 * close() the client once it's done with it to allow the service process to exit.
 * The two service processes are identical to each other (A and B are arbitrary labels); we
 * have two in case a test needs to run more than one thing at once.
 */
class TestProcessClient extends Assert implements AutoCloseable, ServiceConnection {
    private Context mContext;

    private static final long CONNECT_TIMEOUT_MS = 5000;

    private Object mLock = new Object();

    @GuardedBy("mLock")
    private ITestProcessService mService;

    @GuardedBy("mLock")
    private boolean mIsConnectionClosed = false;

    public static TestProcessClient createProcessA(Context context) throws Throwable {
        return new TestProcessClient(context, TestProcessServiceA.class);
    }

    public static TestProcessClient createProcessB(Context context) throws Throwable {
        return new TestProcessClient(context, TestProcessServiceB.class);
    }

    /** Subclass this to implement test code to run on the service side. */
    abstract static class TestRunnable extends Assert {
        public abstract void run(Context ctx) throws Throwable;
    }

    /** Subclass this to implement test code that runs on the main looper on the service side. */
    abstract static class UiThreadTestRunnable extends TestRunnable {
        // A handler for the main thread.
        private static final Handler sMainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public final void run(Context ctx) throws Throwable {
            final SettableFuture<Void> exceptionPropagatingFuture = SettableFuture.create();
            sMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        runOnUiThread(ctx);
                        exceptionPropagatingFuture.set(null);
                    } catch (Throwable t) {
                        exceptionPropagatingFuture.setException(t);
                    }
                }
            });
            WebkitUtils.waitForFuture(exceptionPropagatingFuture);
        }

        protected abstract void runOnUiThread(Context ctx) throws Throwable;
    }

    static class ProcessFreshChecker extends TestRunnable {
        private static Object sFreshLock = new Object();

        @GuardedBy("sFreshLock")
        private static boolean sFreshProcess = true;

        @Override
        public void run(Context ctx) {
            synchronized (sFreshLock) {
                if (!sFreshProcess) {
                    fail("Service process was unexpectedly reused");
                }
                sFreshProcess = false;
            }
        }
    }

    private TestProcessClient(Context context, Class service) throws Throwable {
        mContext = context;
        Intent i = new Intent(context, service);
        context.bindService(i, this, Context.BIND_AUTO_CREATE);
        synchronized (mLock) {
            if (mService == null) {
                mLock.wait(CONNECT_TIMEOUT_MS);
                if (mService == null) {
                    fail("Timeout waiting for connection");
                }
            }
        }

        // Check that we're using an actual fresh process.
        run(ProcessFreshChecker.class);
    }

    public void run(Class runnableClass) throws Throwable {
        Bundle result;
        synchronized (mLock) {
            result = mService.run(runnableClass.getName());
        }
        Throwable exception =
                (Throwable) result.getSerializable(TestProcessService.REPLY_EXCEPTION_KEY);
        if (exception != null) {
            throw exception;
        }
    }

    public void close() {
        synchronized (mLock) {
            if (mIsConnectionClosed) {
                return;
            }
            mIsConnectionClosed = true;
            try {
                if (mService != null) {
                    mService.exit();
                    fail("This should result in a DeadObjectException");
                }
            } catch (DeadObjectException e) {
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        synchronized (mLock) {
            mService = ITestProcessService.Stub.asInterface(service);
            mLock.notify();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        synchronized (mLock) {
            mService = null;
            mContext.unbindService(this);
            mLock.notify();
            // Service wasn't explicitly disconnected in the close() method.
            if (!mIsConnectionClosed) {
                fail("Service disconnected unexpectedly");
            }
        }
    }
}

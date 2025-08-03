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

package com.android.nn.crashtest.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CrashTestCoordinator {

    private static String TAG = "CrashTestCoordinator";

    private final Context mContext;
    private static final Timer mTestTimeoutTimer = new Timer("TestTimeoutTimer");
    private AtomicBoolean mServiceBound = new AtomicBoolean(false);
    private final AtomicBoolean mAlreadyNotified = new AtomicBoolean(false);
    private String mTestName;

    public interface CrashTestIntentInitializer {
        void addIntentParams(Intent intent);
    }

    public interface CrashTestCompletionListener {
        void testCrashed();

        void testSucceeded();

        void testFailed(String cause);

        void testProgressing(Optional<String> description);
    }

    public CrashTestCoordinator(Context context) {
        mContext = context;
    }

    class KeepAliveServiceConnection implements ServiceConnection {
        private final CrashTestCompletionListener mTestCompletionListener;
        private Messenger mMessenger = null;
        private IBinder mService = null;

        KeepAliveServiceConnection(
                CrashTestCompletionListener testCompletionListener) {
            mTestCompletionListener = testCompletionListener;
        }

        public boolean isServiceAlive() {
            if (mService == null) {
                Log.w(TAG, "Keep alive service connection is not bound.");
                return false;
            }
            return mService.isBinderAlive();
        }

        public void killServiceProcess() throws RemoteException {
            mMessenger.send(Message.obtain(null, CrashTestService.KILL_PROCESS));
        }

        protected void onServiceCrashed() {
            Log.w(TAG, "Test service crashed, unbinding and notifying listener");
            unbindService();
            mTestCompletionListener.testCrashed();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, String.format("Service '%s' connected with binder %s", name, service));

            mService = service;
            mMessenger = new Messenger(service);

            try {
                service.linkToDeath(this::onServiceCrashed, 0);

                final Message setCommChannelMsg = Message.obtain(null,
                        CrashTestService.SET_COMM_CHANNEL);
                setCommChannelMsg.replyTo = new Messenger(new Handler(msgFromTest -> {
                    switch (msgFromTest.what) {
                        case CrashTestService.SUCCESS:
                            if (!mAlreadyNotified.getAndSet(true)) {
                                Log.d(TAG, String.format("Test '%s' succeeded", mTestName));
                                mTestCompletionListener.testSucceeded();
                            }
                            unbindService();
                            break;

                        case CrashTestService.FAILURE:
                            if (!mAlreadyNotified.getAndSet(true)) {
                                String reason = msgFromTest.getData().getString(
                                        CrashTestService.DESCRIPTION);
                                Log.i(TAG,
                                        String.format("Test '%s' failed with reason: %s", mTestName,
                                                reason));
                                mTestCompletionListener.testFailed(reason);
                            }
                            unbindService();
                            break;

                        case CrashTestService.PROGRESS:
                            String description = msgFromTest.getData().getString(
                                    CrashTestService.DESCRIPTION);
                            Log.d(TAG, "Test progress message: " + description);
                            mTestCompletionListener.testProgressing(
                                    Optional.ofNullable(description));
                            break;
                    }
                    return true;
                }));
                mMessenger.send(setCommChannelMsg);
            } catch (RemoteException serviceShutDown) {
                Log.w(TAG, "Unable to talk to service, it might have been shut down",
                        serviceShutDown);
                if (!mAlreadyNotified.getAndSet(true)) {
                    mTestCompletionListener.testCrashed();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            unbindService();
        }
    }

    private final AtomicReference<KeepAliveServiceConnection> mServiceConnection =
            new AtomicReference<>(null);

    /**
     * @throws IllegalStateException if unable to start the service
     */
    public void startTest(Class<? extends CrashTest> crashTestClass,
            CrashTestIntentInitializer intentParamsProvider,
            CrashTestCompletionListener testCompletionListener,
            boolean separateProcess, String testName) {

        final Intent crashTestServiceIntent = new Intent(mContext,
                separateProcess ? OutOfProcessCrashTestService.class
                        : InProcessCrashTestService.class);
        crashTestServiceIntent.putExtra(CrashTestService.EXTRA_KEY_CRASH_TEST_CLASS,
                crashTestClass.getName());
        intentParamsProvider.addIntentParams(crashTestServiceIntent);

        mServiceConnection.set(new KeepAliveServiceConnection(testCompletionListener));

        mServiceBound.set(mContext.bindService(crashTestServiceIntent, mServiceConnection.get(),
                Context.BIND_AUTO_CREATE));

        if (!mServiceBound.get()) {
            Log.e(TAG, String.format("Crash test service failed to start %s for test '%s'.",
                    separateProcess ? " in a separate process"
                            : "in a local process", testName));

            throw new IllegalStateException("Unsable to start service");
        }

        Log.i(TAG, String.format("Crash test service started %s for test '%s'.",
                separateProcess ? " in a separate process"
                        : "in a local process", testName));

        mTestName = testName;
    }

    public void shutdown() {
        unbindService();
    }

    public void killCrashTestService() throws RemoteException, IllegalArgumentException {
        if (!mServiceBound.get()) {
            throw new IllegalArgumentException("No service bound!");
        }
        mServiceConnection.get().killServiceProcess();
    }

    private void unbindService() {
        try {
            KeepAliveServiceConnection sc = mServiceConnection.get();
            if (sc != null) {
                if (mServiceBound.get()) {
                    Log.i(TAG, "Unbinding service");
                    mServiceBound.set(false);
                    mContext.unbindService(sc);
                } else {
                    Log.w(TAG, "Service was not bound!!");
                }
                mServiceConnection.compareAndSet(sc, null);
            }
        } catch (Exception e) {
            Log.w(TAG,
                    "Error trying to unbind service, this might be expected if the service crashed.",
                    e);
        }
    }
}

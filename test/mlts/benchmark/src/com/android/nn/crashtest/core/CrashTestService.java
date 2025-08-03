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

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CrashTestService extends Service {

    public static final String TAG = "CrashTestService";
    public static final String DESCRIPTION = "description";
    public static final String EXTRA_KEY_CRASH_TEST_CLASS = "crash_test_class_name";

    public static final int SUCCESS = 1;
    public static final int FAILURE = 2;
    public static final int PROGRESS = 3;
    public static final int SET_COMM_CHANNEL = 4;
    public static final int KILL_PROCESS = 5;

    // Starting tests only after the crash test coordinator has set the communication
    // channel to me in order to avoid event notifications
    private final CountDownLatch startTest = new CountDownLatch(1);

    Messenger lifecycleListener = null;
    final Messenger mMessenger = new Messenger(new Handler(message -> {
        switch (message.what) {
            case SET_COMM_CHANNEL:
                lifecycleListener = message.replyTo;
                startTest.countDown();
                break;

            case KILL_PROCESS:
                Log.w(TAG, "Shutting down service!");
                System.exit(-1);
        }

        return true;
    }));

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    private void notify(int messageType, String messageBody) {
        if (lifecycleListener == null) {
            Log.e(TAG, "No listener configured, skipping message " + messageType);
            return;
        }
        try {
            final Message message = Message.obtain(null, messageType);
            if (messageBody != null) {
                Bundle data = new Bundle();
                data.putString(DESCRIPTION, messageBody);
                message.setData(data);
            }
            lifecycleListener.send(message);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception sending message", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service is bound");

        try {
            String testClassName = Objects
                    .requireNonNull(intent.getStringExtra(EXTRA_KEY_CRASH_TEST_CLASS));
            Log.v(TAG, "Instantiating test class name '" + testClassName + "'");
            final CrashTest crashTest = (CrashTest) Class.forName(
                    testClassName).newInstance();
            crashTest.init(getApplicationContext(), intent,
                    Optional.of(messageMaybe -> notify(PROGRESS, messageMaybe.orElse(null))));

            Log.i(TAG, "Starting test");

            executor.submit(() -> {
                try {
                    startTest.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    Log.e(TAG, "Interrupted before starting test", e);
                    stopSelf();
                    return;
                }

                try {
                    final Optional<String> testResult = crashTest.call();
                    Log.d(TAG, String.format("Test '%s' completed with result: %s", testClassName,
                            testResult.orElse("success")));
                    notify(testResult.isPresent() ? FAILURE : SUCCESS, testResult.orElse(null));
                } catch (Throwable e) {
                    Log.e(TAG, "Exception in crash test", e);
                    notify(FAILURE, "Exception in crash test: " + e);
                    stopSelf();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception starting test ", e);
            stopSelf();
        } catch (Error error) {
            Log.e(TAG, "Error starting test ", error);
            throw error;
        }

        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Unbinding, shutting down thread pool ");
        executor.shutdown();
        return false;
    }
}

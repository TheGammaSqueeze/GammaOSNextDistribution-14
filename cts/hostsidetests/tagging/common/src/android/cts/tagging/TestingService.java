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

package android.cts.tagging;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public abstract class TestingService extends Service {
    private static String TAG = TestingService.class.getName();

    // Message received from the client.
    public static final int MSG_START_TEST = 1;

    public static final int RESULT_TEST_UNKNOWN = Activity.RESULT_FIRST_USER + 1;
    public static final int RESULT_TEST_SUCCESS = Activity.RESULT_FIRST_USER + 2;
    public static final int RESULT_TEST_IGNORED = Activity.RESULT_FIRST_USER + 3;
    public static final int RESULT_TEST_FAILED = Activity.RESULT_FIRST_USER + 4;
    public static final int RESULT_TEST_CRASHED = Activity.RESULT_FIRST_USER + 5;
    public static final int RESULT_TEST_BIND_FAILED = Activity.RESULT_FIRST_USER + 6;

    // Messages sent to the client.
    public static final int MSG_NOTIFY_TEST_RESULT = 2;

    private Messenger mClient;

    static class IncomingHandler extends Handler {
        private TestingService mService;

        IncomingHandler(TestingService service) {
            mService = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MSG_START_TEST) {
                Log.e(TAG, "TestingService received bad message: " + msg.what);
                super.handleMessage(msg);
                return;
            }
            mService.mClient = msg.replyTo;
            mService.startTests();
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    public TestingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void notifyClientOfResult(int result) {
        try {
            mClient.send(Message.obtain(null, MSG_NOTIFY_TEST_RESULT, result, 0, null));
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send message back to client.");
        }
    }

    private void startTests() {
        int result = runTests();
        notifyClientOfResult(result);
    }

    protected abstract int runTests();
}

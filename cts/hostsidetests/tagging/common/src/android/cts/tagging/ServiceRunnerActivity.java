/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.cts.tagging.TestingService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class ServiceRunnerActivity extends Activity {
  private static String TAG = ServiceRunnerActivity.class.getName();

  private Messenger mService;
  private boolean mIsBound;

  private int mResult;
  private final Object mFinishEvent = new Object();

  public synchronized int getResult() { return mResult; }

  // Handler of incoming messages from service.
  class IncomingHandler extends Handler {
    private ServiceRunnerActivity mActivity;

    IncomingHandler(ServiceRunnerActivity activity) { mActivity = activity; }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case TestingService.MSG_NOTIFY_TEST_RESULT:
          synchronized (mActivity.mFinishEvent) {
            mActivity.mResult = msg.arg1;
            mFinishEvent.notify();
          }
          doUnbindService();
          break;
        default:
          super.handleMessage(msg);
          return;
      }
    }
  }

  // Target we publish for clients to send messages to IncomingHandler.
  final Messenger mMessenger = new Messenger(new IncomingHandler(this));

  private ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      mService = new Messenger(service);

      // Send a message to the service to register.
      try {
        Message msg = Message.obtain(null, TestingService.MSG_START_TEST);
        msg.replyTo = mMessenger;
        mService.send(msg);
      } catch (RemoteException e) {
        // In this case the service has crashed before we could even do anything.
        Log.e(TAG, "Failed to send start message to service.");
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      // This is called when the connection with the service has been unexpectedly
      // disconnected -- that is, its process crashed.
      Log.i(TAG, "Service disconnected.");
      mService = null;
      synchronized (mFinishEvent) {
        mResult = TestingService.RESULT_TEST_CRASHED;
        mFinishEvent.notify();
      }
    }
  };

  void doUnbindService() {
    // Detach our existing connection.
    unbindService(mConnection);
    mIsBound = false;
  }

  public void runExternalService(ComponentName component) throws Exception {
    Intent intent = new Intent();
    intent.setComponent(component);
    runServiceCommon(intent, true);
  }

  public void runService(Class<?> cls) throws Exception {
    Intent intent = new Intent(this, cls);
    runServiceCommon(intent, false);
  }

  void runServiceCommon(Intent intent, boolean external) throws Exception {
    mResult = TestingService.RESULT_TEST_UNKNOWN;
    int flags = Context.BIND_AUTO_CREATE;
    if (external)
      flags |= Context.BIND_EXTERNAL_SERVICE;
    boolean result = bindService(intent, mConnection, flags);
    if (result == false) {
      mResult = TestingService.RESULT_TEST_BIND_FAILED;
      return;
    }

    mIsBound = true;
    Thread thread = new Thread() {
      @Override
      public void run() {
        synchronized (mFinishEvent) {
          while (mResult == TestingService.RESULT_TEST_UNKNOWN) {
            try {
              mFinishEvent.wait();
            } catch (InterruptedException e) {
            }
          }
        }
      }
    };
    thread.start();
    thread.join(50000 /* millis */);
  }
}

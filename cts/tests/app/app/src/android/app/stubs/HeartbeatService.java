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

package android.app.stubs;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

public class HeartbeatService extends Service {

    private static final String TAG = HeartbeatService.class.getSimpleName();

    private static final int CMD_PID = 1;
    private static final String PROCESS_NAME = "process";
    private static final String PROCESS_DEAD = "dead";

    private int mPid;
    private int mUid;
    private String mName;
    private boolean mDead;
    private CountDownLatch mLatch = new CountDownLatch(1);

    private final IHeartbeat.Stub mHeartbeat = new IHeartbeat.Stub() {
        @Override
        public void trigger(int pid, int uid, String name, int countdown, long interval,
                ICallback callback) {
            mPid = pid;
            mUid = uid;
            mName = name;
            new Thread(() -> {
                Log.d(TAG, "Heartbeat thread started");
                for (int i = countdown; i > 0; i--) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                    }
                    try {
                        Log.d(TAG, "Sending heartbeat countdown " + i);
                        callback.onHeartbeat(i);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Client has died");
                        mDead = true;
                        break;
                    }
                }
                // AMS will kill the client if it received callback while being frozen
                // Otherwise, the client will kill itself with SIGKILL
                Intent intent = new Intent();
                intent.setAction(IHeartbeat.HEARTBEAT_DONE);
                intent.setComponent(new ComponentName("android.app.stubs",
                        "android.app.stubs.HeartbeatReceiver"));
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                Log.d(TAG, "Notify the client heartbeat service is done");
                sendBroadcast(intent);
                Log.d(TAG, "Notify the monitor heartbeat service is done");
                mLatch.countDown();
            }).start();
        }

        @Override
        public void monitor(Messenger messenger) {
            new Thread(() -> {
                Log.d(TAG, "Monitor thread started");
                try {
                    mLatch.await();
                    Log.d(TAG, "Detected the end of heartbeat service");
                } catch (InterruptedException e) {
                }
                Message msg = Message.obtain();
                msg.what = CMD_PID;
                msg.arg1 = mPid;
                msg.arg2 = mUid;
                Bundle b = new Bundle();
                b.putString(PROCESS_NAME, mName);
                b.putBoolean(PROCESS_DEAD, mDead);
                msg.obj = b;
                try {
                    Log.d(TAG, "Report monitor result");
                    messenger.send(msg);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to report monitor result");
                }
            }).start();
        }
    };

    /**
     * Return the communication channel to the service.  May return null if
     * clients can not bind to the service.  The returned
     * {@link IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}guide/components/aidl.html">described using
     * aidl</a>.
     *
     * <p><em>Note that unlike other application components, calls on to the
     * IBinder interface returned here may not happen on the main thread
     * of the process</em>.  More information about the main thread can be found in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html">Processes and
     * Threads</a>.</p>
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return an IBinder through which clients can call on to the
     * service.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mHeartbeat;
    }
}

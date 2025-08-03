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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * A simple activity using HeartbeatService to test Messenger and App Freezer
 */
public class HeartbeatActivity extends Activity {
    private static final String TAG = HeartbeatActivity.class.getSimpleName();
    private static final String HEARTBEAT_COUNTDOWN = "countdown";
    private static final String HEARTBEAT_INTERVAL = "interval";

    private IHeartbeat mHeartbeat;
    private ServiceConnection mConnection;
    private final HeartbeatReceiver mReceiver = new HeartbeatReceiver();

    private final ICallback.Stub mCallback = new ICallback.Stub() {
        @Override
        public void onHeartbeat(int countdown) {
            Log.d(TAG, "Received heartbeat countdown " + countdown);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int countdown = getIntent().getIntExtra(HEARTBEAT_COUNTDOWN, IHeartbeat.DEFAULT_COUNTDOWN);
        long interval = getIntent().getLongExtra(HEARTBEAT_INTERVAL, IHeartbeat.DEFAULT_INTERVAL);
        Log.d(TAG, "Heartbeat intent countdown=" + countdown + " interval=" + interval);
        registerReceiver(mReceiver, new IntentFilter(IHeartbeat.HEARTBEAT_DONE),
                Context.RECEIVER_NOT_EXPORTED);
        startHeartbeat(countdown, interval);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
        unregisterReceiver(mReceiver);
    }

    private void startHeartbeat(int countdown, long interval) {
        Intent intent = new Intent(this, HeartbeatService.class);
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mHeartbeat = IHeartbeat.Stub.asInterface(service);
                try {
                    Log.d(TAG, "Trigger heartbeat service");
                    mHeartbeat.trigger(Process.myPid(), Process.myUid(), Process.myProcessName(),
                            countdown, interval, mCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to trigger Heartbeat service");
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mHeartbeat = null;
            }
        };

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
}

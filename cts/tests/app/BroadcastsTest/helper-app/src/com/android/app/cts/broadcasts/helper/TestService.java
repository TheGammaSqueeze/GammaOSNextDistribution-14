/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.app.cts.broadcasts.helper;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.android.app.cts.broadcasts.BroadcastReceipt;
import com.android.app.cts.broadcasts.ICommandReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestService extends Service {
    private static final String TAG = "TestService";

    @GuardedBy("mReceivedBroadcasts")
    private static final ArrayMap<String, ArrayList<BroadcastReceipt>> sReceivedBroadcasts =
            new ArrayMap<>();

    @GuardedBy("mRegisteredReceivers")
    private static final ArraySet<BroadcastReceiver> sRegisteredReceivers = new ArraySet<>();

    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    void registerReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter, int flags) {
        context.registerReceiver(receiver, filter, flags);
        synchronized (sRegisteredReceivers) {
            sRegisteredReceivers.add(receiver);
        }
    }

    void unregisterReceiver(Context context, BroadcastReceiver receiver) {
        context.unregisterReceiver(receiver);
        synchronized (sRegisteredReceivers) {
            sRegisteredReceivers.remove(receiver);
        }
    }

    private class CommandReceiver extends ICommandReceiver.Stub {
        @Override
        public void sendBroadcast(Intent intent, Bundle options) {
            TestService.this.sendBroadcast(intent, null /* receiverPermission */, options);
        }

        @Override
        public void monitorBroadcasts(IntentFilter filter, String cookie) {
            registerReceiver(TestService.this.getApplicationContext(), new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "Received broadcast: " + intent + " for " + cookie);
                    synchronized (sReceivedBroadcasts) {
                        ArrayList<BroadcastReceipt> receivedBroadcasts = sReceivedBroadcasts.get(
                                cookie);
                        if (receivedBroadcasts == null) {
                            receivedBroadcasts = new ArrayList();
                            sReceivedBroadcasts.put(cookie, receivedBroadcasts);
                        }
                        receivedBroadcasts.add(BroadcastReceipt.create(
                                SystemClock.elapsedRealtime(), intent));
                    }
                }
            }, filter, Context.RECEIVER_EXPORTED);
        }

        @Override
        public List<BroadcastReceipt> getReceivedBroadcasts(String cookie) {
            synchronized (sReceivedBroadcasts) {
                final ArrayList<BroadcastReceipt> receivedBroadcasts =
                        sReceivedBroadcasts.get(cookie);
                return receivedBroadcasts == null ? new ArrayList<>() : receivedBroadcasts;
            }
        }

        private <T> String listToString(List<T> list) {
            return list == null ? null : Arrays.toString(list.toArray());
        }

        @Override
        public void clearCookie(String cookie) {
            synchronized (sReceivedBroadcasts) {
                sReceivedBroadcasts.remove(cookie);
            }
        }

        @Override
        public int getPid() {
            return Process.myPid();
        }

        @Override
        public void tearDown() {
            synchronized (sReceivedBroadcasts) {
                sReceivedBroadcasts.clear();
            }
            synchronized (sRegisteredReceivers) {
                for (int i = sRegisteredReceivers.size() - 1; i >= 0; --i) {
                    TestService.this.getApplicationContext().unregisterReceiver(
                            sRegisteredReceivers.valueAt(i));
                    sRegisteredReceivers.removeAt(i);
                }
            }
        }
    }
}


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
package android.app.cts.shortfgstesthelper;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.EXTRA_MESSAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.HELPER_PACKAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.createNotification;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.sContext;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.setMessage;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.HashMap;
import java.util.Objects;

/**
 * This class receives a message from the main test package and run a command.
 */
public class ShortFgsMessageReceiver extends BroadcastReceiver {
    private static final HashMap<ComponentName, ServiceConnection> sServiceConnections =
            new HashMap<>();


    /**
     * Send a ShortFgsMessage to this receiver.
     */
    public static void sendMessage(ShortFgsMessage m) {
        sendMessage(HELPER_PACKAGE, m);
    }

    /**
     * Send a ShortFgsMessage to this receiver in a given package.
     */
    public static void sendMessage(String packageName, ShortFgsMessage m) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(packageName, ShortFgsMessageReceiver.class.getName()));
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(EXTRA_MESSAGE, m);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        sContext.sendBroadcast(i);
        Log.i(TAG, "ShortFgsMessageReceiver.sendMessage: intent=" + i + " message=" + m);
    }

    /**
     * Check the message is sent by the currently running test.
     *
     * We get the "currently running test" information from CallProvider.
     */
    private static boolean isMessageCurrent(ShortFgsMessage m) {
        ShortFgsMessage testInfo = ShortFgsHelper.getCurrentTestInfo();
        if (testInfo.getLastTestEndUptime() > 0) {
            // No test running.
            Log.w(TAG, "ShortFgsMessageReceiver: no test running; dropping it.");
            return false;
        }
        if (m.getTimestamp() < testInfo.getLastTestStartUptime()) {
            // Stale message.
            Log.w(TAG, "ShortFgsMessageReceiver: Stale message; dropping it.");
            return false;
        }
        return true;
    }

    @Override
    public void onReceive(Context context, Intent i) {
        ShortFgsMessage m = Objects.requireNonNull(
                i.getParcelableExtra(EXTRA_MESSAGE, ShortFgsMessage.class));
        Log.i(TAG, "ShortFgsMessageReceiver.onReceive: intent=" + i + " message=" + m);

        // Every time we receive a message, we ask the main test when the last test started / ended,
        // and ignore "stale" messages.
        if (!isMessageCurrent(m)) {
            return;
        }

        Class<?> expectedException = null;
        try {
            // getExpectedExceptionClass() may throw, so we call it inside the try-cacth.
            expectedException = m.getExpectedExceptionClass();

            // Call Context.startForegroundService()?
            if (m.isDoCallStartForegroundService()) {
                // Call startForegroundService for the component, and also pass along
                // the message
                Log.i(TAG, "isDoCallStartForegroundService: " + m);
                sContext.startForegroundService(
                        setMessage(new Intent().setComponent(m.getComponentName()), m));
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Context.startService()?
            if (m.isDoCallStartService()) {
                // Call startService for the component, and also pass along the message
                Log.i(TAG, "isDoCallStartService: " + m);
                sContext.startService(
                        setMessage(new Intent().setComponent(m.getComponentName()), m));
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Service.startForeground()?
            if (m.isDoCallStartForeground()) {
                Log.i(TAG, "isDoCallStartForeground: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .startForeground(m.getNotificationId(),
                                createNotification(), m.getFgsType());
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Service.stopForeground()?
            if (m.isDoCallStopForeground()) {
                Log.i(TAG, "isDoCallStopForeground: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .stopForeground(Service.STOP_FOREGROUND_DETACH);
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Service.stopSelf()?
            if (m.isDoCallStopSelf()) {
                Log.i(TAG, "isDoCallStopSelf: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .stopSelf();
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Kill self, but we can't kill it until the receiver finishes (otherwise
            // ActivityManager would be unhappy, so we use Handler to run it after the receiver
            // finishes.
            if (m.isDoKillProcess()) {
                new Handler(Looper.getMainLooper()).post(ShortFgsMessageReceiver::killSelf);
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Finish the activity?
            if (m.isDoFinishActivity()) {
                MyActivity myActivity = MyActivity.getInstance();
                if (myActivity != null) {
                    myActivity.finish();
                }
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Context.bindService()?
            if (m.isDoCallBindService()) {
                Log.i(TAG, "isDoCallBindService: " + m);
                Intent bindingIntent = new Intent(Intent.ACTION_VIEW)
                        .setComponent(m.getComponentName());
                final boolean success = sContext.bindService(bindingIntent, getServiceConnection(
                        m.getComponentName()), Context.BIND_AUTO_CREATE);
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            // Call Context.unbindService()?
            if (m.isDoCallUnbindService()) {
                Log.i(TAG, "isDoCallUnbindService: " + m);
                sContext.unbindService(getServiceConnection(m.getComponentName()));
                ShortFgsHelper.sendBackAckMessage();
                return;
            }

            throw new RuntimeException("Unknown message " + m + " received");
        } catch (Throwable th) {
            final boolean isExpected = expectedException != null
                    && expectedException.isAssignableFrom(th.getClass());
            if (isExpected) {
                Log.i(TAG, "Expected Exception received: ", th);
            } else {
                Log.w(TAG, "Unexpected exception received: ", th);
            }
            // Send back a failure message...
            ShortFgsMessage reply = new ShortFgsMessage();
            reply.setException(th);

            ShortFgsHelper.sendBackMessage(reply);
        }
    }

    private static void killSelf() {
        Process.killProcess(Process.myPid());
    }

    private ServiceConnection getServiceConnection(ComponentName cn) {
        ServiceConnection ret = sServiceConnections.get(cn);
        if (ret == null) {
            ret = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.i(TAG, "onServiceConnected: " + name);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.i(TAG, "onServiceDisconnected: " + name);
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    Log.i(TAG, "onBindingDied: " + name);
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    Log.i(TAG, "onNullBinding: " + name);
                }
            };
            sServiceConnections.put(cn, ret);
        }
        return ret;
    }
}

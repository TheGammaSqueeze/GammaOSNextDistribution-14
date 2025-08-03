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
package com.google.android.car.userswitchmonitor;

import android.annotation.UserIdInt;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.format.DateFormat;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Service that users {@link CarUserManager.UserLifecycleEvent UserLifecycleEvents} to monitor
 * user switches.
 *
 */
public final class UserSwitchMonitorService extends Service {

    static final String TAG = "UserSwitchMonitor";

    private static final String CMD_CLEAR = "clear";
    private static final String CMD_HELP = "help";
    private static final String CMD_REGISTER = "register";
    private static final String CMD_UNREGISTER = "unregister";

    private final Object mLock = new Object();
    private final int mUserId = android.os.Process.myUserHandle().getIdentifier();
    private final MyListener mListener = new MyListener();

    @GuardedBy("mLock")
    private final List<Event> mEvents = new ArrayList<>();

    @GuardedBy("mLock")
    private final LinkedHashMap<Integer, MyReceiver> mReceivers = new LinkedHashMap<>();

    private Car mCar;
    private CarUserManager mCarUserManager;
    private NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> onCarReady(car, ready));
    }

    private void onCarReady(Car car, boolean ready) {
        Log.d(TAG, "onCarReady(): ready=" + ready);
        if (!ready) {
            Log.w(TAG, "Car not ready yet");
            return;
        }
        mCarUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);
        registerListener();
        mNotificationManager = getSystemService(NotificationManager.class);

        UserManager um = getSystemService(UserManager.class);
        List<UserHandle> users = um.getUserHandles(/* excludeDying= */ false);
        Log.d(TAG, "Users on create: " + users);
        users.forEach((u) -> registerReceiver(u.getIdentifier()));
    }

    private void registerListener() {
        Log.d(TAG, "registerListener(): " + mListener);
        try {
            mCarUserManager.addListener((r)-> r.run(), mListener);
        } catch (Exception e) {
            // Most likely the permission was not granted
            Log.w(TAG, "Could not add listener for user " + getUser() + ": " + e);
        }
    }

    private void registerReceiver(@UserIdInt int userId) {
        Log.d(TAG, "registerReceiver(): userId: " + userId);
        MyReceiver receiver;
        Context context;
        synchronized (mLock) {
            if (mReceivers.containsKey(userId)) {
                Log.d(TAG, "registerReceiver(): already registered for userId: " + userId);
                return;
            }
            context = getContextForUser(userId);
            if (context == null) {
                return;
            }
            receiver = new MyReceiver(userId, context);
            Log.d(TAG, "Saving receiver for user " + userId + ": " + receiver);
            mReceivers.put(userId, receiver);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_PRE_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_USER_FOREGROUND);
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        filter.addAction(Intent.ACTION_USER_INITIALIZE);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_STARTED);
        filter.addAction(Intent.ACTION_USER_STARTING);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);

        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private Context getContextForUser(@UserIdInt int userId) {
        try {
            return createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        } catch (Exception e) {
            Log.w(TAG, "getContextForUser(): could not get context for " + userId
                    + " - did you install the app on it? Exception: " + e);
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand(" + mUserId + "): " + intent);

        String channelId = "4815162342";
        String name = "UserSwitchMonitor";
        NotificationChannel channel = new NotificationChannel(channelId, name,
                NotificationManager.IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel);

        // Cannot use R.drawable because package name is different on app2
        int iconResId = getApplicationInfo().icon;
        startForeground(startId,
                new Notification.Builder(this, channelId)
                        .setContentText(name)
                        .setContentTitle(name)
                        .setSmallIcon(iconResId)
                        .build());

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy(" + mUserId + ")");

        unregisterListener();

        synchronized (mLock) {
            mReceivers.values().forEach(MyReceiver::unregister);
        }

        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
        }
        super.onDestroy();
    }

    private void unregisterListener() {
        Log.d(TAG, "unregisterListener(): " + mListener);
        if (mCarUserManager == null) {
            Log.w(TAG, "Cannot remove listener because manager is null");
            return;
        }
        try {
            mCarUserManager.removeListener(mListener);
        } catch (Exception e) {
            // Most likely the permission was not granted
            Log.w(TAG, "Could not remove listener for user " + getUser() + ": " + e);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (args != null && args.length > 0) {
            executeCommand(pw, args);
            return;
        }
        pw.printf("User id: %d\n", mUserId);
        pw.printf("Listener: %s\n", mListener);

        String indent = "  ";
        synchronized (mLock) {
            pw.printf("Receivers for %d users:\n", mReceivers.size());
            mReceivers.values().forEach((receiver) -> pw.printf("%s%s\n", indent, receiver));

            if (mEvents.isEmpty()) {
                pw.println("Did not receive any event yet");
                return;
            }
            int eventsSize = mEvents.size();
            pw.printf("Received %d events:\n", eventsSize);
            for (int i = 0; i < eventsSize; i++) {
                pw.printf("%s%d: %s\n", indent, (i + 1), mEvents.get(i));
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(): " + intent);
        return null;
    }

    private void executeCommand(PrintWriter pw, String[] args) {
        String cmd = args[0];
        switch (cmd) {
            case CMD_CLEAR:
                cmdClear(pw);
                break;
            case CMD_HELP:
                cmdHelp(pw);
                break;
            case CMD_REGISTER:
                cmdRegister(pw);
                break;
            case CMD_UNREGISTER:
                cmdUnregister(pw);
                break;
            default:
                pw.printf("invalid command: %s\n\n",  cmd);
                cmdHelp(pw);
        }
    }

    private void cmdHelp(PrintWriter pw) {
        pw.printf("Options:\n");
        pw.printf("  help: show this help\n");
        pw.printf("  clear: clear the list of received events\n");
        pw.printf("  register: register the service to receive events\n");
        pw.printf("  unregister: unregister the service from receiving events\n");
    }

    private void cmdRegister(PrintWriter pw) {
        pw.printf("registering listener %s\n", mListener);
        runCmd(pw, () -> registerListener());
    }

    private void cmdUnregister(PrintWriter pw) {
        pw.printf("unregistering listener %s\n", mListener);
        runCmd(pw, () -> unregisterListener());
    }

    private void cmdClear(PrintWriter pw) {
        int size;
        synchronized (mLock) {
            size = mEvents.size();
            mEvents.clear();
        }
        String msg = String.format("Cleared %d events", size);
        Log.i(TAG, msg);
        pw.println(msg);
    }

    private void runCmd(PrintWriter pw, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            Log.e(TAG, "error running command", e);
            pw.printf("failed: %s\n", e);
        }
    }

    private static String toString(@UserIdInt int userId, Intent intent) {
        StringBuilder string = new StringBuilder("Intent[onUser=").append(userId)
                .append(",action=").append(intent.getAction());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            int numberExtras = extras.size();
            string.append(", ").append(numberExtras).append(" extra");
            if (numberExtras > 1) {
                string.append('s');
            }
            string.append(": {");
            int i = 0;
            for (String key : extras.keySet()) {
                @SuppressWarnings("deprecation")
                Object value = extras.get(key);
                string.append(key).append('=').append(value);
                if (++i < numberExtras) {
                    string.append(", ");
                }
            }
            string.append('}');
        }
        return string.append(']').toString();
    }

    private final class MyListener implements CarUserManager.UserLifecycleListener {

        private int mNumberCalls;

        @Override
        public void onEvent(UserLifecycleEvent event) {
            Log.d(TAG, "onEvent(" + mUserId + "): event=" + event + ", numberCalls="
                    + (++mNumberCalls));
            synchronized (mLock) {
                mEvents.add(new Event(event));
            }
            // NOTE: if USER_LIFECYCLE_EVENT_TYPE_CREATED / USER_LIFECYCLE_EVENT_TYPE_REMOVED are
            // sent to apps, we could dynamically register / unregister new receivers here
        }

        @Override
        public String toString() {
            return "MyListener[numberCalls=" + mNumberCalls + "]";
        }
    }

    private final class MyReceiver extends BroadcastReceiver {

        private final @UserIdInt int mUserId;
        private final Context mContext;

        private int mNumberCalls;

        MyReceiver(@UserIdInt int userId, Context context) {
            mUserId = userId;
            mContext = context;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String userFriendlyIntent = UserSwitchMonitorService.toString(mUserId, intent);
            Log.d(TAG, "onReceive(): intent=" + userFriendlyIntent
                    + ",context.userId=" + context.getUserId()
                    + ", numberCalls=" + (++mNumberCalls));
            synchronized (mLock) {
                mEvents.add(new Event(userFriendlyIntent));
            }
        }

        @Override
        public String toString() {
            return "MyReceiver[userId=" + mUserId + ", numberCalls=" + mNumberCalls + "]";
        }

        public void unregister() {
            Log.d(TAG, "Unregistering " + this);
            mContext.unregisterReceiver(this);
        }
    }

    private static final class Event {
        private final long mTimestamp = System.currentTimeMillis();
        private final Object mEvent;

        private Event(Object event) {
            mEvent = event;
        }

        @Override
        public String toString() {
            return "on " + DateFormat.format("MM-dd HH:mm:ss", mTimestamp) + "(" + mTimestamp
                    + "): " + mEvent;
        }
    }
}

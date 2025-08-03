/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.compatibility.common.util.IBinderParcelable;

public class LocalForegroundService extends LocalService {

    private static final String TAG = "LocalForegroundService";
    public static final String EXTRA_COMMAND = "LocalForegroundService.command";
    public static final String EXTRA_FOREGROUND_SERVICE_TYPE = "ForegroundService.type";
    public static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;
    public static String ACTION_START_FGS_RESULT =
            "android.app.stubs.LocalForegroundService.RESULT";

    public static final int COMMAND_START_FOREGROUND = 1;
    public static final int COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION = 2;
    public static final int COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION = 3;
    public static final int COMMAND_STOP_FOREGROUND_DETACH_NOTIFICATION = 4;
    public static final int COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION_USING_FLAGS = 5;
    public static final int COMMAND_START_NO_FOREGROUND = 6;
    public static final int COMMAND_START_FOREGROUND_DEFER_NOTIFICATION = 7;
    public static final int COMMAND_STOP_SELF = 8;

    private final Messenger mMessenger = new Messenger(new IncomingHandler());

    private int mNotificationId = 0;

    private static final int DEFAULT_FGS_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

    protected String getTag() {
        return TAG;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(getTag(), "service created: " + this + " in " + android.os.Process.myPid());
    }

    /** Returns the channel id for this service */
    public static String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String notificationChannelId = getNotificationChannelId();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                notificationChannelId, notificationChannelId,
                NotificationManager.IMPORTANCE_DEFAULT));

        Context context = getApplicationContext();
        final int command = intent.getIntExtra(EXTRA_COMMAND, -1);

        Log.d(getTag(), "service start cmd " + command + ", intent " + intent);

        switch (command) {
            case COMMAND_START_FOREGROUND:
            case COMMAND_START_FOREGROUND_DEFER_NOTIFICATION: {
                handleIncomingMessengerIfNeeded(intent);
                mNotificationId ++;
                final boolean showNow = (command == COMMAND_START_FOREGROUND);
                Log.d(getTag(), "Starting foreground using notification " + mNotificationId);
                Notification.Builder builder =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setContentTitle(getNotificationTitle(mNotificationId))
                                .setSmallIcon(R.drawable.black);
                if (showNow) {
                    builder.setForegroundServiceBehavior(
                            Notification.FOREGROUND_SERVICE_IMMEDIATE);
                }
                final int fgsType = intent.getIntExtra(EXTRA_FOREGROUND_SERVICE_TYPE,
                        DEFAULT_FGS_TYPE);
                try {
                    startForeground(mNotificationId, builder.build(), fgsType);
                } catch (ForegroundServiceStartNotAllowedException e) {
                    Log.d(TAG, "startForeground gets an "
                            + " ForegroundServiceStartNotAllowedException", e);
                }
                break;
            }
            case COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION:
                Log.d(getTag(), "Stopping foreground removing notification");
                stopForeground(true);
                break;
            case COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION:
                Log.d(getTag(), "Stopping foreground without removing notification");
                stopForeground(false);
                break;
            case COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION_USING_FLAGS:
                Log.d(getTag(), "Stopping foreground removing notification using flags");
                stopForeground(Service.STOP_FOREGROUND_REMOVE | Service.STOP_FOREGROUND_DETACH);
                break;
            case COMMAND_STOP_FOREGROUND_DETACH_NOTIFICATION:
                Log.d(getTag(), "Detaching foreground service notification");
                stopForeground(Service.STOP_FOREGROUND_DETACH);
                break;
            case COMMAND_START_NO_FOREGROUND:
                Log.d(getTag(), "Starting without calling startForeground()");
                break;
            default:
                Log.e(getTag(), "Unknown command: " + command);
        }

        sendBroadcast(
                new Intent(ACTION_START_FGS_RESULT).setFlags(Intent.FLAG_RECEIVER_FOREGROUND));

        // Do parent's onStart at the end, so we don't race with the test code waiting for us to
        // execute.
        super.onStart(intent, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(getTag(), "service destroyed: " + this + " in " + android.os.Process.myPid());
        super.onDestroy();
    }

    public static Bundle newCommand(IBinder stateReceiver, int command) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(stateReceiver));
        bundle.putInt(EXTRA_COMMAND, command);
        return bundle;
    }

    public static Bundle newCommand(int command) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(new Binder()));
        bundle.putInt(EXTRA_COMMAND, command);
        return bundle;
    }

    public static String getNotificationTitle(int id) {
        return "I AM FOREGROOT #" + id;
    }

    /**
     * Check if the given {@code intent} has embodied a messenger object which is to receive
     * the messenger interface based controller, if so, send our {@link #mMessenger} to it.
     */
    private void handleIncomingMessengerIfNeeded(final Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            final IBinder binder = extras.getBinder(CommandReceiver.EXTRA_MESSENGER);
            if (binder != null) {
                final Messenger messenger = new Messenger(binder);
                final Bundle reply = new Bundle();
                final Message msg = Message.obtain();
                msg.obj = reply;
                reply.putBinder(CommandReceiver.EXTRA_MESSENGER, mMessenger.getBinder());
                try {
                    messenger.send(msg);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to send back the messenger controller interface");
                }
                msg.recycle();
            }
        }
    }

    private class IncomingHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COMMAND_STOP_SELF:
                    Log.d(TAG, "Stopping self");
                    stopSelf();
                    break;
                default:
                    Log.e(TAG, "Unsupported command via messenger interface: " + msg.what);
                    break;
            }
        }
    }
}

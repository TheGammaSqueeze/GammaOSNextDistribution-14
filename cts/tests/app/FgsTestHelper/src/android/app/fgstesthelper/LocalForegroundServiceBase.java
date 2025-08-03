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

package android.app.fgstesthelper;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.InvalidForegroundServiceTypeException;
import android.app.MissingForegroundServiceTypeException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

/**
 * A foreground service without a specific type.
 */
public abstract class LocalForegroundServiceBase extends Service {
    private static final String TAG = "LocalForegroundServiceBase";
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;

    public static final String EXTRA_COMMAND = "LocalForegroundService.command";
    public static final String EXTRA_FGS_TYPE = "LocalForegroundService.fgs_type";
    public static final String EXTRA_RESULT_CODE = "LocalForegroundService.result_code";
    public static String ACTION_START_FGS_RESULT =
            "android.app.fgs.LocalForegroundService.RESULT";

    public static final int COMMAND_START_FOREGROUND = 1;
    public static final int COMMAND_START_BACKGROUND = 2;
    public static final int COMMAND_SET_FOREGROUND = 3;
    public static final int COMMAND_STOP_SELF = 4;

    public static final int RESULT_OK = 0;
    public static final int RESULT_START_EXCEPTION = 1;
    public static final int RESULT_INVALID_TYPE_EXCEPTION = 2;
    public static final int RESULT_MISSING_TYPE_EXCEPTION = 3;
    public static final int RESULT_SECURITY_EXCEPTION = 4;

    private int mNotificationId = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(getTag(), "service created: " + this + " in " + Process.myPid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    String getTag() {
        return TAG;
    }

    /** Returns the channel id for this service */
    String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    String getNotificationTitle(int id) {
        return "I AM FOREGROOT #" + id;
    }

    abstract int getNotificationIcon();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int command = intent.getIntExtra(EXTRA_COMMAND, -1);

        Log.d(getTag(), "service start cmd " + command + ", intent " + intent);

        int result = RESULT_OK;
        switch (command) {
            case COMMAND_START_FOREGROUND: {
                Log.d(getTag(), "Calling startForeground()");
                result = startForeground(intent.getIntExtra(EXTRA_FGS_TYPE,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST));
                break;
            }
            case COMMAND_START_BACKGROUND:
                Log.d(getTag(), "Starting without calling startForeground()");
                break;
            case COMMAND_SET_FOREGROUND:
                Log.d(getTag(), "Calling startForeground() separately");
                result = startForeground(intent.getIntExtra(EXTRA_FGS_TYPE,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST));
                break;
            case COMMAND_STOP_SELF:
                Log.d(getTag(), "Calling stopSelf()");
                stopSelf(startId);
                return START_NOT_STICKY;
            default:
                Log.e(getTag(), "Unknown command: " + command);
        }

        final Intent reply = new Intent(ACTION_START_FGS_RESULT);
        reply.putExtra(EXTRA_RESULT_CODE, result);
        reply.setFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        sendBroadcast(reply);

        return START_NOT_STICKY;
    }

    private int startForeground(int type) {
        final String notificationChannelId = getNotificationChannelId();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                notificationChannelId, notificationChannelId,
                NotificationManager.IMPORTANCE_DEFAULT));

        mNotificationId++;
        Log.d(getTag(), "Starting foreground using notification " + mNotificationId);
        Notification.Builder builder =
                new Notification.Builder(this, notificationChannelId)
                        .setContentTitle(getNotificationTitle(mNotificationId))
                        .setSmallIcon(getNotificationIcon());
        try {
            startForeground(mNotificationId, builder.build(), type);
        } catch (ForegroundServiceStartNotAllowedException e) {
            // Not expected.
            Log.d(getTag(), "startForeground gets an "
                    + " ForegroundServiceStartNotAllowedException", e);
            return RESULT_START_EXCEPTION;
        } catch (InvalidForegroundServiceTypeException e) {
            Log.d(getTag(), "startForeground gets an "
                    + " InvalidForegroundServiceTypeException " + e);
            return RESULT_INVALID_TYPE_EXCEPTION;
        } catch (MissingForegroundServiceTypeException e) {
            Log.d(getTag(), "startForeground gets an "
                    + " MissingForegroundServiceTypeException " + e);
            return RESULT_MISSING_TYPE_EXCEPTION;
        } catch (SecurityException e) {
            Log.d(getTag(), "startForeground gets an "
                    + " SecurityException " + e);
            return RESULT_SECURITY_EXCEPTION;
        }
        return RESULT_OK;
    }

    @Override
    public void onDestroy() {
        Log.d(getTag(), "service destroyed: " + this + " in " + Process.myPid());
        super.onDestroy();
    }
}

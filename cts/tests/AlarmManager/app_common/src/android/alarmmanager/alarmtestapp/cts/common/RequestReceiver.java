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

package android.alarmmanager.alarmtestapp.cts.common;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This receiver is to be used to communicate with tests in {@link android.alarmmanager.cts}.
 */
public class RequestReceiver extends BroadcastReceiver {
    private static final String TAG = RequestReceiver.class.getSimpleName();
    public static final String PACKAGE_NAME = "android.alarmmanager.alarmtestapp.cts.common";

    public static final String ACTION_GET_CAN_SCHEDULE_EXACT_ALARM =
            PACKAGE_NAME + ".action.GET_CAN_SCHEDULE_EXACT_ALARM";
    public static final String ACTION_SET_EXACT_PI =
            PACKAGE_NAME + ".action.SET_EXACT_PI";
    public static final String ACTION_SET_EXACT_CALLBACK =
            PACKAGE_NAME + ".action.SET_EXACT_CALLBACK";
    public static final String ACTION_SET_EXACT_AND_AWI =
            PACKAGE_NAME + ".action.SET_EXACT_AND_AWI";
    public static final String ACTION_SET_ALARM_CLOCK =
            PACKAGE_NAME + ".action.SET_ALARM_CLOCK";

    public static final int RESULT_SECURITY_EXCEPTION = Activity.RESULT_FIRST_USER + 12;

    private static PendingIntent getAlarmSender(Context context) {
        final Intent alarmAction = new Intent(context, RequestReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(context, 0, alarmAction,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final AlarmManager am = context.getSystemService(AlarmManager.class);
        switch (intent.getAction()) {
            case ACTION_GET_CAN_SCHEDULE_EXACT_ALARM:
                final boolean result = am.canScheduleExactAlarms();
                setResult(Activity.RESULT_OK, String.valueOf(result), null);
                break;
            case ACTION_SET_EXACT_AND_AWI:
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1234,
                            getAlarmSender(context));
                    setResult(Activity.RESULT_OK, null, null);
                } catch (SecurityException se) {
                    setResult(RESULT_SECURITY_EXCEPTION, se.getMessage(), null);
                }
                break;
            case ACTION_SET_ALARM_CLOCK:
                final AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(1234,
                        getAlarmSender(context));
                try {
                    am.setAlarmClock(info, getAlarmSender(context));
                    setResult(Activity.RESULT_OK, null, null);
                } catch (SecurityException se) {
                    setResult(RESULT_SECURITY_EXCEPTION, se.getMessage(), null);
                }
                break;
            case ACTION_SET_EXACT_PI:
                try {
                    am.setExact(AlarmManager.ELAPSED_REALTIME, 1234, getAlarmSender(context));
                    setResult(Activity.RESULT_OK, null, null);
                } catch (SecurityException se) {
                    setResult(RESULT_SECURITY_EXCEPTION, se.getMessage(), null);
                }
                break;
            case ACTION_SET_EXACT_CALLBACK:
                try {
                    am.setExact(AlarmManager.ELAPSED_REALTIME, 1234, TAG,
                            () -> Log.i(TAG, "Listener alarm fired!"), null);
                    setResult(Activity.RESULT_OK, null, null);
                } catch (SecurityException se) {
                    setResult(RESULT_SECURITY_EXCEPTION, se.getMessage(), null);
                }
                break;
            default:
                Log.e(TAG, "Unspecified action " + intent.getAction());
                setResult(Activity.RESULT_CANCELED, null, null);
                break;
        }
    }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.alarmmanager.alarmtestapp.cts;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * This receiver is to be used to schedule alarms as part of tests in
 * {@link android.alarmmanager.cts}
 */
public class TestAlarmScheduler extends BroadcastReceiver {
    private static final String TAG = TestAlarmScheduler.class.getSimpleName();
    private static final String PACKAGE_NAME = "android.alarmmanager.alarmtestapp.cts";

    public static final String ACTION_SET_ALARM = PACKAGE_NAME + ".action.SET_ALARM";
    public static final String EXTRA_TRIGGER_TIME = PACKAGE_NAME + ".extra.TRIGGER_TIME";
    public static final String EXTRA_REPEAT_INTERVAL = PACKAGE_NAME + ".extra.REPEAT_INTERVAL";
    public static final String EXTRA_WINDOW_LENGTH = PACKAGE_NAME + ".extra.WINDOW_LENGTH";
    public static final String EXTRA_TYPE = PACKAGE_NAME + ".extra.TYPE";
    public static final String ACTION_SET_ALARM_CLOCK = PACKAGE_NAME + ".action.SET_ALARM_CLOCK";
    public static final String EXTRA_ALARM_CLOCK_INFO = PACKAGE_NAME + ".extra.ALARM_CLOCK_INFO";
    public static final String EXTRA_TEST_FGS = PACKAGE_NAME + ".extra.TEST_FGS";
    public static final String ACTION_CANCEL_ALL_ALARMS = PACKAGE_NAME + ".action.CANCEL_ALARMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        final AlarmManager am = context.getSystemService(AlarmManager.class);
        final Intent receiverIntent = new Intent(context, TestAlarmReceiver.class);
        receiverIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final long id = SystemClock.elapsedRealtime();
        receiverIntent.putExtra(TestAlarmReceiver.EXTRA_ID, id);
        receiverIntent.putExtra(EXTRA_TEST_FGS, intent.getBooleanExtra(EXTRA_TEST_FGS, false));
        final PendingIntent alarmClockSender = PendingIntent.getBroadcast(context, 0,
                receiverIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent alarmSender = PendingIntent.getBroadcast(context, 1, receiverIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        switch (intent.getAction()) {
            case ACTION_SET_ALARM_CLOCK:
                if (!intent.hasExtra(EXTRA_ALARM_CLOCK_INFO)) {
                    Log.e(TAG, "No alarm clock supplied");
                    break;
                }
                final AlarmManager.AlarmClockInfo alarmClockInfo =
                        intent.getParcelableExtra(EXTRA_ALARM_CLOCK_INFO);
                Log.d(TAG, "Setting alarm clock " + alarmClockInfo + " id: " + id);
                am.setAlarmClock(alarmClockInfo, alarmClockSender);
                setResult(Activity.RESULT_OK, null, null);
                break;
            case ACTION_SET_ALARM:
                if (!intent.hasExtra(EXTRA_TYPE) || !intent.hasExtra(EXTRA_TRIGGER_TIME)) {
                    Log.e(TAG, "Alarm type or trigger time not supplied");
                    break;
                }
                final int type = intent.getIntExtra(EXTRA_TYPE, 0);
                final long triggerTime = intent.getLongExtra(EXTRA_TRIGGER_TIME, 0);
                final long interval = intent.getLongExtra(EXTRA_REPEAT_INTERVAL, 0);
                final long window = intent.getLongExtra(EXTRA_WINDOW_LENGTH, 1);

                Log.d(TAG, "Setting alarm: id=" + id + " type=" + type + ", triggerTime="
                        + triggerTime + ", interval=" + interval + " window=" + window);
                if (interval > 0) {
                    am.setRepeating(type, triggerTime, interval, alarmSender);
                } else {
                    am.setWindow(type, triggerTime, window, alarmSender);
                }
                setResult(Activity.RESULT_OK, null, null);
                break;
            case ACTION_CANCEL_ALL_ALARMS:
                Log.d(TAG, "Cancelling all alarms");
                am.cancel(alarmClockSender);
                am.cancel(alarmSender);
                setResult(Activity.RESULT_OK, null, null);
                break;
            default:
                Log.e(TAG, "Unspecified action " + intent.getAction());
                setResult(Activity.RESULT_CANCELED, null, null);
                break;
        }
    }
}
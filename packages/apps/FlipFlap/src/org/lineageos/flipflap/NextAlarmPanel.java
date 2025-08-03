/*
 * Copyright (c) 2017-2021 The LineageOS Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * Also add information on how to contact you by electronic and paper mail.
 *
 */

package org.lineageos.flipflap;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.Locale;

public class NextAlarmPanel extends LinearLayout {
    private static final String TAG = "ClockPanel";

    private final Context mContext;

    private final AlarmManager mAlarmManager;
    private TextView mAlarmText;

    private boolean mReceiverRegistered;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
                refreshAlarmStatus();
            }
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mReceiverRegistered) {
            IntentFilter filter = new IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
            mContext.registerReceiver(mReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            mReceiverRegistered = true;
            refreshAlarmStatus();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
    }

    public NextAlarmPanel(Context context) {
        this(context, null);
    }

    public NextAlarmPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NextAlarmPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mAlarmText = findViewById(R.id.next_alarm_regular);
    }

    private void refreshAlarmStatus() {
        String nextAlarm = getNextAlarm();
        mAlarmText.setText(nextAlarm);
    }

    public String getNextAlarm() {
        AlarmManager.AlarmClockInfo nextAlarmClock = mAlarmManager.getNextAlarmClock();
        if (nextAlarmClock != null) {
            String skeleton = DateFormat.is24HourFormat(mContext) ? "EHm" : "Ehma";
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
            return (String) DateFormat.format(pattern, nextAlarmClock.getTriggerTime());
        }

        return null;
    }
}

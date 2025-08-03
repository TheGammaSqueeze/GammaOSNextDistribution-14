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

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class RectangularView extends FlipFlapView {
    private static final String TAG = "RectangularView";

    private final ClockPanel mClockPanel;
    private final DatePanel mDatePanel;
    private final NextAlarmPanel mNextAlarmPanel;
    private final AlarmPanel mAlarmPanel;
    private final PhonePanel mPhonePanel;
    private final RelativeLayout mAnswerCallButton;
    private final TextView mEndCallText;
    private final TextView mIncomingCallName;
    private final TextView mIncomingCallNumber;
    private boolean mRinging;
    private boolean mCallActive;
    private boolean mAlarmActive;
    private boolean mNeedsSmallView;

    public RectangularView(Context context) {
        super(context);

        final Resources resources = context.getResources();
        int top = resources.getDimensionPixelSize(R.dimen.rectangular_window_top);
        int height = resources.getDimensionPixelSize(R.dimen.rectangular_window_height);

        inflate(context, R.layout.rectangular_view, this);

        mClockPanel = findViewById(R.id.clock_panel);
        mClockPanel.bringToFront();
        mDatePanel = findViewById(R.id.date_panel);
        mNextAlarmPanel = findViewById(R.id.next_alarm_panel);
        mAlarmPanel = findViewById(R.id.alarm_panel);
        mPhonePanel = findViewById(R.id.phone_panel);
        mIncomingCallName = findViewById(R.id.incoming_call_name);
        mIncomingCallNumber = findViewById(R.id.incoming_call_number);
        mAnswerCallButton = findViewById(R.id.answer_button);
        mAnswerCallButton.setOnClickListener(view -> acceptRingingCall());
        mEndCallText = findViewById(R.id.ignore_text);
        RelativeLayout ignoreCallButton = findViewById(R.id.ignore_button);
        ignoreCallButton.setOnClickListener(view -> endCall());

        ImageButton alarmSnoozeButton = findViewById(R.id.snooze_button);
        alarmSnoozeButton.setOnClickListener(view -> snoozeAlarm());
        ImageButton alarmDismissButton = findViewById(R.id.dismiss_button);
        alarmDismissButton.setOnClickListener(view -> dismissAlarm());

        if ((height - top) < (resources.getSystem().getDisplayMetrics().heightPixels / 3)) {
            mNeedsSmallView = true;
        }
    }

    @Override
    protected boolean supportsAlarmActions() {
        return true;
    }

    @Override
    protected boolean supportsCallActions() {
        return true;
    }

    @Override
    protected void updateAlarmState(boolean active) {
        super.updateAlarmState(active);
        mAlarmActive = active;
        updateViewVisibility();
    }

    @Override
    public void updateCallState(CallState callState) {
        super.updateCallState(callState);
        mRinging = callState.isRinging();
        mCallActive = callState.isActive();
        mIncomingCallName.setText (mCallActive ? callState.getName() : null);
        mIncomingCallNumber.setText(mCallActive ? callState.getNumber() : null);
        updateViewVisibility();
    }

    private void updateViewVisibility() {
        String nextAlarm = mNextAlarmPanel.getNextAlarm();

        if (mRinging || mCallActive) {
            if (mNeedsSmallView) {
                mClockPanel.setVisibility(View.GONE);
                mDatePanel.setVisibility(View.GONE);
            } else {
                mClockPanel.setVisibility(View.VISIBLE);
                mDatePanel.setVisibility(View.VISIBLE);
            }
            mNextAlarmPanel.setVisibility(View.GONE);
            mAlarmPanel.setVisibility(View.GONE);
            mPhonePanel.setVisibility(View.VISIBLE);
            if (mRinging) {
                mAnswerCallButton.setVisibility(View.VISIBLE);
                mEndCallText.setText(getResources().getString(R.string.ignore_call));
            } else {
                mAnswerCallButton.setVisibility(View.GONE);
                mEndCallText.setText(getResources().getString(R.string.end_call));
            }
        } else if (mAlarmActive) {
            if (mNeedsSmallView) {
                mClockPanel.setVisibility(View.GONE);
                mDatePanel.setVisibility(View.GONE);
                mNextAlarmPanel.setVisibility(View.GONE);
            } else {
                mClockPanel.setVisibility(View.VISIBLE);
                mDatePanel.setVisibility(View.VISIBLE);
                mNextAlarmPanel.setVisibility(TextUtils.isEmpty(nextAlarm)
                        ? View.GONE : View.VISIBLE);
            }
            mAlarmPanel.setVisibility(View.VISIBLE);
            mPhonePanel.setVisibility(View.GONE);
        } else {
            mClockPanel.setVisibility(View.VISIBLE);
            mDatePanel.setVisibility(View.VISIBLE);
            mNextAlarmPanel.setVisibility(TextUtils.isEmpty(nextAlarm) ? View.GONE : View.VISIBLE);
            mAlarmPanel.setVisibility(View.GONE);
            mPhonePanel.setVisibility(View.GONE);
        }
    }
}

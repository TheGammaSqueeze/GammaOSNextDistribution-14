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
import android.graphics.Canvas;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class CircleView extends FlipFlapView {
    private static final String TAG = "CircleView";

    private final ClockPanel mClockPanel;
    private final DatePanel mDatePanel;
    private final NextAlarmPanel mNextAlarmPanel;
    private final AlarmPanel mAlarmPanel;
    private final PhonePanel mPhonePanel;
    private final ImageButton mAlarmSnoozeButton;
    private final ImageButton mAlarmDismissButton;
    private final RelativeLayout mAnswerCallButton;
    private final RelativeLayout mIgnoreCallButton;
    private final Resources mResources;
    private final TextView mEndCallText;
    private final TextView mIncomingCallName;
    private final TextView mIncomingCallNumber;
    private boolean mRinging;
    private boolean mCallActive;
    private boolean mAlarmActive;
    private boolean mForceRedraw;
    private float mOriginalRadius;

    public CircleView(Context context) {
        super(context);

        inflate(context, R.layout.circle_view, this);

        mClockPanel = findViewById(R.id.clock_panel);
        mClockPanel.bringToFront();
        mDatePanel = findViewById(R.id.date_panel);
        mNextAlarmPanel = findViewById(R.id.next_alarm_panel);
        mAlarmPanel = findViewById(R.id.alarm_panel);
        mPhonePanel = findViewById(R.id.phone_panel);

        mIncomingCallName = findViewById(R.id.incoming_call_name);
        mIncomingCallNumber = findViewById(R.id.incoming_call_number);
        mAnswerCallButton = findViewById(R.id.answer_button);
        mIgnoreCallButton = findViewById(R.id.ignore_button);
        mEndCallText = findViewById(R.id.ignore_text);
        mAnswerCallButton.setOnClickListener(view -> acceptRingingCall());
        mIgnoreCallButton.setOnClickListener(view -> endCall());

        mAlarmSnoozeButton = findViewById(R.id.snooze_button);
        mAlarmDismissButton = findViewById(R.id.dismiss_button);
        mAlarmSnoozeButton.setOnClickListener(view -> snoozeAlarm());
        mAlarmDismissButton.setOnClickListener(view -> dismissAlarm());

        mResources = context.getResources();
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mOriginalRadius = 4.0F * w / 9.0F;
        mForceRedraw = true; // we need to draw once before we can apply any changes
    }

    private void recalculateLayout() {
        float newRadius = mOriginalRadius + mResources.getDimensionPixelSize(R.dimen.radius_offset);
        float ratio = newRadius / mOriginalRadius;
        float density = mResources.getDisplayMetrics().density;

        // Change size and move Clock Panel down
        TextView theClock = mClockPanel.findViewById(R.id.the_clock);
        changeLayout(mClockPanel, ratio, true, false, false, false, false);
        changeTextSize(theClock, ratio, density);
        // we intentionally skip date and next_alarm - these are already very small and should
        // fit into all relevant circle sizes

        // Change Alarm Panel
        ImageView alertIcon = mAlarmPanel.findViewById(R.id.alarm_alert_icon);
        TextView alertText = mAlarmPanel.findViewById(R.id.alarm_alert_text);
        changeLayout(mAlarmSnoozeButton, ratio, false, true, false, true, true);
        changeLayout(mAlarmDismissButton, ratio, false, false, true, true, true);
        changeLayout(alertIcon, ratio, false, false, false, false, true);
        changeTextSize(alertText, ratio, density);

        // Phone panel
        ImageView phoneIcon = mPhonePanel.findViewById(R.id.incoming_call_alert_icon);
        ImageView answerIcon = mAnswerCallButton.findViewById(R.id.answer_icon);
        TextView answerText = mAnswerCallButton.findViewById(R.id.answer_text);
        ImageView ignoreIcon = mIgnoreCallButton.findViewById(R.id.ignore_icon);
        TextView ignoreText = mIgnoreCallButton.findViewById(R.id.ignore_text);
        changeTextSize(mIncomingCallName, ratio, density);
        changeTextSize(mIncomingCallNumber, ratio, density);
        changeLayout(phoneIcon, ratio, false, false, false, false, true);
        changeLayout(mAnswerCallButton, ratio, false, true, false, false, false);
        changeLayout(answerIcon, ratio, false, false, false, true, true);
        changeTextSize(answerText, ratio, density);
        changeLayout(mIgnoreCallButton, ratio, false, false, true, false, false);
        changeLayout(ignoreIcon, ratio, false, false, false, true, true);
        changeTextSize(ignoreText, ratio, density);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mForceRedraw) {
            mForceRedraw = false;
            recalculateLayout();
        }
    }

    private void changeLayout(View v, float ratio, boolean top, boolean left, boolean right,
                              boolean width, boolean height) {
        RelativeLayout.LayoutParams relParams = (RelativeLayout.LayoutParams) v.getLayoutParams();
        if (top) relParams.topMargin = (int)(relParams.topMargin / ratio + 0.5f);
        if (left) relParams.leftMargin = (int)(relParams.leftMargin / ratio + 0.5f);
        if (right) relParams.rightMargin = (int)(relParams.rightMargin / ratio + 0.5f);
        if (width) relParams.width = (int)(relParams.width * ratio + 0.5f);
        if (height) relParams.height = (int)(relParams.height * ratio + 0.5f);
        v.setLayoutParams(relParams);
    }

    private void changeTextSize(TextView v, float ratio, float density) {
        v.setTextSize(v.getTextSize() / density * ratio);
    }

    private void updateViewVisibility() {
        String nextAlarm = mNextAlarmPanel.getNextAlarm();
        mClockPanel.setVisibility(View.VISIBLE);

        if (mRinging || mCallActive) {
            mAlarmPanel.setVisibility(View.GONE);
            mPhonePanel.setVisibility(View.VISIBLE);
            mDatePanel.setVisibility(View.GONE);
            mNextAlarmPanel.setVisibility(View.GONE);
            if (mRinging) {
                mAnswerCallButton.setVisibility(View.VISIBLE);
                mEndCallText.setText(getResources().getString(R.string.ignore_call));
            } else {
                mAnswerCallButton.setVisibility(View.GONE);
                mEndCallText.setText(getResources().getString(R.string.end_call));
            }
        } else if (mAlarmActive) {
            mDatePanel.setVisibility(View.VISIBLE);
            mNextAlarmPanel.setVisibility(TextUtils.isEmpty(nextAlarm) ? View.GONE : View.VISIBLE);
            mAlarmPanel.setVisibility(View.VISIBLE);
            mPhonePanel.setVisibility(View.GONE);
        } else {
            mDatePanel.setVisibility(View.VISIBLE);
            mNextAlarmPanel.setVisibility(TextUtils.isEmpty(nextAlarm) ? View.GONE : View.VISIBLE);
            mAlarmPanel.setVisibility(View.GONE);
            mPhonePanel.setVisibility(View.GONE);
        }
    }
}

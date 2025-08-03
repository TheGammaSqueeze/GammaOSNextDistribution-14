/*
 * Copyright (c) 2016 The CyanogenMod Project
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

import org.lineageos.flipflap.DotcaseConstants.Notification;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.service.notification.StatusBarNotification;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import androidx.core.content.ContextCompat;

import java.text.Normalizer;

public class DotcaseView extends FlipFlapView {
    private static final String TAG = "DotcaseView";

    private final Context mContext;
    private final Paint mPaint;
    private int mHeartbeat = 0;
    private final List<Notification> mNotifications = new ArrayList<>();
    private boolean mAlarmActive;
    private String mCallerName;
    private String mCallerNumber;
    private int mNameOffset;
    private int mRingCounter;
    private boolean mRinging;
    private boolean mProximityNear;

    private final GestureDetector mDetector;

    private final int mOffsetX;
    private final int mOffsetY;
    private final int mDotRatioX;
    private final int mDotRatioY;

    // 1920x1080 = 48 x 27 dots @ 40 pixels per dot

    private static class timeObject {
        String timeString;
        int hour;
        int min;
        boolean is24Hour;
        boolean am;
    }

    public DotcaseView(Context context) {
        super(context);
        mContext = context;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mDetector = new GestureDetector(mContext, mGestureListener);

        // Get hardware dependent constants from config file
        final Resources res = mContext.getResources();
        mOffsetX = res.getInteger(R.integer.config_OffsetX);
        mOffsetY = res.getInteger(R.integer.config_OffsetY);
        mDotRatioX = res.getInteger(R.integer.config_DotRatioX);
        mDotRatioY = res.getInteger(R.integer.config_DotRatioY);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mAlarmActive) {
            drawAlarm(canvas);
        } else if (mCallerNumber != null) {
            drawName(canvas);
            drawNumber(canvas);
            drawRinger(canvas);
        } else {
            drawTime(canvas);

            if (!mNotifications.isEmpty()) {
                if (mHeartbeat < 3) {
                    drawNotifications(canvas);
                } else {
                    drawBattery(canvas);
                }

                mHeartbeat++;

                if (mHeartbeat > 5) {
                    mHeartbeat = 0;
                }
            } else {
                drawBattery(canvas);
                mHeartbeat = 0;
            }
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!mProximityNear) {
            mDetector.onTouchEvent(event);
            return super.onTouchEvent(event);
        } else {
            // Say that we handled this event so nobody else does
            return true;
        }
    }
    @Override
    protected boolean canUseProximitySensor() {
        return true;
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
    protected boolean supportsNotifications() {
        return true;
    }

    @Override
    protected float getScreenBrightness() {
        return 1.0f;
    }

    @Override
    protected void updateNotifications(List<StatusBarNotification> notifications) {
        mNotifications.clear();
        for (StatusBarNotification sbn : notifications) {
            Notification notification = DotcaseConstants.notificationMap.get(sbn.getPackageName());
            if (notification != null && !mNotifications.contains(notification)) {
                if (sbn.getNotification().priority < android.app.Notification.PRIORITY_DEFAULT) {
                    continue;
                }
                mNotifications.add(notification);
                if (mNotifications.size() == 5) {
                    break;
                }
            }
        }
        postInvalidate();
    }

    @Override
    public void updateProximityState(boolean isNear) {
        super.updateProximityState(isNear);
        mProximityNear = isNear;
    }

    @Override
    public void updateCallState(CallState callState) {
        super.updateCallState(callState);
        mRinging = callState.isRinging();
        // add spaces to make the scroll effect look good
        mCallerName = mRinging ? normalize(callState.getName()) + "  " : null;
        mCallerNumber = mRinging ? callState.getNumber() : null;
        mNameOffset = -6;
        mRingCounter = 0;
        postInvalidate();
    }

    @Override
    protected void updateAlarmState(boolean active) {
        super.updateAlarmState(active);
        mAlarmActive = active;
        mRingCounter = 0;
        postInvalidate();
    }

    private timeObject getTimeObject() {
        timeObject timeObj = new timeObject();
        timeObj.hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        timeObj.min = Calendar.getInstance().get(Calendar.MINUTE);

        if (DateFormat.is24HourFormat(mContext)) {
            timeObj.is24Hour = true;
        } else {
            timeObj.is24Hour = false;
            if (timeObj.hour > 11) {
                if (timeObj.hour > 12) {
                    timeObj.hour = timeObj.hour - 12;
                }
                timeObj.am = false;
            } else {
                if (timeObj.hour == 0) {
                    timeObj.hour = 12;
                }
                timeObj.am = true;
            }
        }

        timeObj.timeString = (timeObj.hour < 10
                ? " " + Integer.toString(timeObj.hour)
                : Integer.toString(timeObj.hour))
                + (timeObj.min < 10
                ? "0" + Integer.toString(timeObj.min)
                : Integer.toString(timeObj.min));
        return timeObj;
    }

    private void drawAlarm(Canvas canvas) {
        int light = 7, dark = 12;
        int clockHeight = DotcaseConstants.clockSprite.length;
        int clockWidth = DotcaseConstants.clockSprite[0].length;
        int ringerHeight = DotcaseConstants.ringerSprite.length;
        int ringerWidth = DotcaseConstants.ringerSprite[0].length;
        timeObject time = getTimeObject();

        int[][] mClockSprite = new int[clockHeight][clockWidth];
        int[][] mRingerSprite = new int[ringerHeight][ringerWidth];

        for (int i = 0; i < ringerHeight; i++) {
            for (int j = 0; j < ringerWidth; j++) {
                if (DotcaseConstants.ringerSprite[i][j] > 0) {
                    mRingerSprite[i][j] =
                            DotcaseConstants.ringerSprite[i][j] == 3 - (mRingCounter % 3)
                                    ? light : dark;
                }
            }
        }

        for (int i = 0; i < clockHeight; i++) {
            for (int j = 0; j < clockWidth; j++) {
                mClockSprite[i][j] = DotcaseConstants.clockSprite[i][j] > 0 ? light : 0;
            }
        }

        dotcaseDrawSprite(DotcaseConstants.getSmallCharSprite(
                time.timeString.charAt(0)), 0, 0, canvas);
        dotcaseDrawSprite(DotcaseConstants.getSmallCharSprite(
                time.timeString.charAt(1)), 4, 0, canvas);
        dotcaseDrawSprite(DotcaseConstants.smallTimeColon, 8, 1, canvas);
        dotcaseDrawSprite(DotcaseConstants.getSmallCharSprite(
                time.timeString.charAt(2)), 11, 0, canvas);
        dotcaseDrawSprite(DotcaseConstants.getSmallCharSprite(
                time.timeString.charAt(3)), 15, 0, canvas);
        dotcaseDrawSprite(mClockSprite, 7, 7, canvas);

        if (!time.is24Hour) {
            if (time.am) {
                dotcaseDrawSprite(DotcaseConstants.amSprite, 18, 0, canvas);
            } else {
                dotcaseDrawSprite(DotcaseConstants.pmSprite, 18, 0, canvas);
            }
        }

        if (mRingCounter / 6 > 0) {
            dotcaseDrawSprite(DotcaseConstants.alarmCancelArray, 2, 21, canvas);
            Collections.reverse(Arrays.asList(mRingerSprite));
        } else {
            dotcaseDrawSprite(DotcaseConstants.snoozeArray, 2, 21, canvas);
        }

        dotcaseDrawSprite(mRingerSprite, 7, 28, canvas);

        mRingCounter = (++mRingCounter % 11);
    }

    private void drawNotifications(Canvas canvas) {
        int count = 0;
        int x = 1;
        int y = 30;

        for (Notification notification : mNotifications) {
            int[][] sprite = DotcaseConstants.getNotificationSprite(notification);
            if (sprite != null) {
                dotcaseDrawSprite(sprite, x + ((count % 3) * 9), y + ((count / 3) * 9), canvas);
                count++;
            }
        }
    }

    private void drawRinger(Canvas canvas) {
        int light, dark;
        int handsetHeight = DotcaseConstants.handsetSprite.length;
        int handsetWidth = DotcaseConstants.handsetSprite[0].length;
        int ringerHeight = DotcaseConstants.ringerSprite.length;
        int ringerWidth = DotcaseConstants.ringerSprite[0].length;

        int[][] mHandsetSprite = new int[handsetHeight][handsetWidth];
        int[][] mRingerSprite = new int[ringerHeight][ringerWidth];

        if (mRingCounter / 3 > 0) {
            light = 2;
            dark = 11;
        } else {
            light = 3;
            dark = 10;
        }

        for (int i = 0; i < ringerHeight; i++) {
            for (int j = 0; j < ringerWidth; j++) {
                if (DotcaseConstants.ringerSprite[i][j] > 0) {
                    mRingerSprite[i][j] =
                            DotcaseConstants.ringerSprite[i][j] == 3 - (mRingCounter % 3)
                                    ? light : dark;
                }
            }
        }

        for (int i = 0; i < handsetHeight; i++) {
            for (int j = 0; j < handsetWidth; j++) {
                mHandsetSprite[i][j] = DotcaseConstants.handsetSprite[i][j] > 0 ? light : 0;
            }
        }

        if (mRingCounter / 3 > 0) {
            Collections.reverse(Arrays.asList(mRingerSprite));
            Collections.reverse(Arrays.asList(mHandsetSprite));
        }

        dotcaseDrawSprite(mHandsetSprite, 6, 21, canvas);
        dotcaseDrawSprite(mRingerSprite, 7, 28, canvas);

        mRingCounter = ++mRingCounter % 5;
    }

    private void drawBattery(Canvas canvas) {
        Intent batteryIntent = mContext.getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED);
        int rawlevel = batteryIntent.getIntExtra("level", -1);
        double scale = batteryIntent.getIntExtra("scale", -1);
        int plugged = batteryIntent.getIntExtra("plugged", -1);
        double level = -1;
        if (rawlevel >= 0 && scale > 0) {
            level = rawlevel / scale;
        }

        dotcaseDrawSprite(DotcaseConstants.batteryOutlineSprite, 1, 35, canvas);

        // 4.34 percents per dot
        int fillDots = (int)Math.round((level * 100) / 4.34);
        int color;

        if (level >= .50) {
            color = Color.GREEN;
        } else if (level >= .25) {
            color = mContext.getResources().getColor(R.color.dotcase_color_orange);
        } else {
            color = Color.RED;
        }

        for (int i = 0; i < fillDots; i++) {
            if (i == 22) {
                dotcaseDrawRect(2 + i, 39, 3 + i, 43, color, canvas);
            } else {
                dotcaseDrawRect(2 + i, 36, 3 + i, 46, color, canvas);
            }
        }

        if (plugged > 0) {
            dotcaseDrawSprite(DotcaseConstants.lightningSprite, 9, 36, canvas);
        }
    }

    private void drawTime(Canvas canvas) {
        timeObject time = getTimeObject();
        int starter;

        if (time.hour < 10) {
            starter = 0;
        } else {
            starter = 3;
        }

        if (!time.is24Hour) {
            if (time.am) {
                dotcaseDrawSprite(DotcaseConstants.amSprite, 3, 18, canvas);
            } else {
                dotcaseDrawSprite(DotcaseConstants.pmSprite, 3, 18, canvas);
            }
        }

        dotcaseDrawSprite(DotcaseConstants.timeColon, starter + 10, 5 + 4, canvas);
        dotcaseDrawSprite(DotcaseConstants.getNumSprite(time.timeString.charAt(0)),
                starter, 5, canvas);
        dotcaseDrawSprite(DotcaseConstants.getNumSprite(time.timeString.charAt(1)),
                starter + 5, 5, canvas);
        dotcaseDrawSprite(DotcaseConstants.getNumSprite(time.timeString.charAt(2)),
                starter + 12, 5, canvas);
        dotcaseDrawSprite(DotcaseConstants.getNumSprite(time.timeString.charAt(3)),
                starter + 17, 5, canvas);
    }

    private void dotcaseDrawPixel(int x, int y, Paint paint, Canvas canvas) {
        canvas.drawRoundRect((x * mDotRatioX + 3 + mOffsetX),
                (y * mDotRatioY + 3 + mOffsetY),
                ((x + 1) * mDotRatioX - 3 + mOffsetX),
                ((y + 1) * mDotRatioY - 3 + mOffsetY),
                mDotRatioX / 2, mDotRatioY / 2, paint);
    }

    private void dotcaseDrawRect(int left, int top, int right,
                                 int bottom, int color, Canvas canvas) {
        for (int x=left; x < right; x++) {
            for (int y=top; y < bottom; y++) {
                mPaint.setColor(color);
                dotcaseDrawPixel(x, y, mPaint, canvas);
            }
        }
    }

    private void dotcaseDrawSprite(int[][] sprite, int x, int y, Canvas canvas) {
        for (int i = 0; i < sprite.length; i++) {
            for (int j = 0; j < sprite[0].length; j++) {
                mPaint.setColor(DotcaseConstants.getColorFromNumber(sprite[i][j]));
                dotcaseDrawPixel(x + j, y + i, mPaint, canvas);
            }
        }
    }

    private void drawName(Canvas canvas) {
        int x = 0, y = 2;
        int nameOffset = Math.max(mNameOffset, 0);
        String correctedName = "";

        // We can fit 7 characters, and the last two are spaces
        if (mCallerName.length() <= 9) {
            // Name is short enough to be drawn completely, cut off spaces at end
            correctedName = mCallerName.substring(0, mCallerName.length() - 2);
        } else if ((nameOffset + 7) > mCallerName.length()) {
            // Looping: end and beginning of the name are visible together
            int overflow = (nameOffset + 7) % mCallerName.length();
            correctedName = mCallerName.substring(nameOffset) + mCallerName.substring(0, overflow);
        } else if ((nameOffset + 7) <= mCallerName.length()) {
            // Draw a consecutive portion of the name
            correctedName = mCallerName.substring(nameOffset, nameOffset + 7);
        }

        for (int i = 0; i < correctedName.length(); i++) {
            int[][] sprite = DotcaseConstants.getSmallCharSprite(correctedName.charAt(i));
            dotcaseDrawSprite(sprite, x + i * 4, y, canvas);
        }

        if (++mNameOffset >= mCallerName.length()) {
            mNameOffset = -3;
        }
    }

    private void drawNumber(Canvas canvas) {
        int x = 0, y = 8;
        for (int i = 3; i < mCallerNumber.length() && i < 10; i++) {
            int[][] sprite = DotcaseConstants.getSmallCharSprite(mCallerNumber.charAt(i));
            dotcaseDrawSprite(sprite, x + (i - 3) * 4, y, canvas);
        }
    }

    private static String normalize(String str) {
        if (str == null) {
            return "";
        }
        return Normalizer.normalize(str.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("æ", "ae")
                .replaceAll("ð", "d")
                .replaceAll("ø", "o")
                .replaceAll("þ", "th")
                .replaceAll("ß", "ss")
                .replaceAll("œ", "oe");
    }

    private final GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (Math.abs(distanceY) < 60) {
                        // Did not meet the threshold for a scroll
                        return true;
                    }

                    if (supportsCallActions() && mRinging) {
                        if (distanceY < 60) {
                            endCall();
                        } else if (distanceY > 60) {
                            acceptRingingCall();
                        }
                    } else if (supportsAlarmActions() && mAlarmActive) {
                        if (distanceY < 60) {
                            dismissAlarm();
                        } else if (distanceY > 60) {
                            snoozeAlarm();
                        }
                    }
                    return true;
                }
            };
}

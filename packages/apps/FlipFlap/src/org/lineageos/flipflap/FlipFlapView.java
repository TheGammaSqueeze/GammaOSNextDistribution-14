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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lineageos.internal.util.LineageLockPatternUtils;
import lineageos.providers.LineageSettings;

public class FlipFlapView extends FrameLayout {
    private static final String TAG = "FlipFlapView";
    private static final String KEY_PASS_TO_SECURITY = "pass_to_security_view";
    private static final String KEY_TOUCH_SENSITIVITY = "use_high_touch_sensitivity";

    private static final int COVER_CLOSED_MSG = 0;
    private static final int RESTORE_SECURITY_VIEW_STATE = 1;

    private final BatteryManager mBatteryManager;
    private final Context mContext;
    private final GestureDetector mDetector;
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;
    private final SensorManager mSensorManager;
    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;
    private CallState mCallState;
    private boolean mAlarmActive;
    private boolean mProximityNear;
    private boolean mNotificationListenerRegistered;
    private boolean mPassToSecurity;

    private int mUserHighTouchState;

    /* Required to only read the setting when it's already restored, else when closing the cover
    within the timeout (1.5s), it would read "true" (because we set it) and always restore that */
    private static boolean mRestoredPassToSecurity = true;

    public FlipFlapView(Context context) {
        super(context);
        mContext = context;
        setBackgroundColor(Color.BLACK);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_FULLSCREEN);

        mDetector = new GestureDetector(context, mGestureListener);
        mBatteryManager = context.getSystemService(BatteryManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
        mSensorManager = context.getSystemService(SensorManager.class);
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);

        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.setReferenceCounted(false);

        changeSecurityViewState();
        checkHighTouchSensitivity();
    }

    protected boolean canUseProximitySensor() {
        return false;
    }

    protected float getScreenBrightness() {
        return 0.5F;
    }

    protected boolean supportsAlarmActions() {
        return false;
    }

    protected boolean supportsCallActions() {
        return false;
    }

    protected boolean supportsNotifications() {
        return false;
    }

    protected void updateNotifications(List<StatusBarNotification> notifications) {
    }

    protected void updateAlarmState(boolean active) {
        mAlarmActive = active;
    }

    protected void dismissAlarm() {
        getContext().sendBroadcast(new Intent(FlipFlapUtils.ACTION_ALARM_DISMISS));
        updateAlarmState(false);
    }

    protected void snoozeAlarm() {
        getContext().sendBroadcast(new Intent(FlipFlapUtils.ACTION_ALARM_SNOOZE));
        updateAlarmState(false);
    }

    protected void updateProximityState(boolean isNear) {
        mProximityNear = isNear;
    }

    protected void updateCallState(CallState callState) {
    }

    protected void acceptRingingCall() {
        mTelecomManager.acceptRingingCall();
        mWakeLock.release();
    }

    protected void endCall() {
        mTelecomManager.endCall();
        mWakeLock.release();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mCallState = new CallState(getContext(), mTelephonyManager.getCallState(), null);
        updateCallState(mCallState);
        if (mCallState.isRinging() && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(FlipFlapUtils.ACTION_ALARM_ALERT);
        filter.addAction(FlipFlapUtils.ACTION_ALARM_DONE);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        getContext().registerReceiver(mReceiver, filter, ContextCompat.RECEIVER_EXPORTED);

        if (supportsNotifications()) {
            try {
                mNotificationListener.registerAsSystemService(getContext(),
                        new ComponentName(getContext(), getClass()), UserHandle.USER_ALL);
                mNotificationListenerRegistered = true;
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register notification listener", e);
            }
        }
        if (canUseProximitySensor()) {
            mSensorManager.registerListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        postScreenOff();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mHandler.removeCallbacksAndMessages(null);
        getContext().unregisterReceiver(mReceiver);
        restoreSecurityViewState();
        restoreHighTouchSensitivity();

        if (supportsNotifications()) {
            try {
                mNotificationListener.unregisterAsSystemService();
                mNotificationListenerRegistered = false;
            } catch (RemoteException e) {
                // Ignore.
            }
        }
        if (canUseProximitySensor()) {
            try {
                mSensorManager.unregisterListener(mSensorEventListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to unregister listener", e);
            }
        }

        mPowerManager.wakeUp(SystemClock.uptimeMillis(), "Cover Opened");
        if(mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        postScreenOff();
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mProximityNear) {
            mDetector.onTouchEvent(event);
            return super.onTouchEvent(event);
        } else {
            // Say that we handled this event so nobody else does
            return true;
        }
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                updateProximityState(event.values[0] < event.sensor.getMaximumRange());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do nothing
        }
    };

    private final GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mPowerManager.isInteractive() && !mAlarmActive) {
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return true;
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action) &&
                    supportsCallActions()) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                mCallState = new CallState(getContext(),state, number);
                updateCallState(mCallState);
                if (mCallState.isRinging() && !mWakeLock.isHeld()) {
                    mWakeLock.acquire();
                } else {
                    if(!mCallState.isRinging() && mWakeLock.isHeld()) {
                        mWakeLock.release();
                    }
                }
            } else if (FlipFlapUtils.ACTION_ALARM_ALERT.equals(action) && supportsAlarmActions()) {
                // add other alarm apps here
                updateAlarmState(true);
                mWakeLock.acquire();
            } else if (FlipFlapUtils.ACTION_ALARM_DONE.equals(action)) {
                updateAlarmState(false);
                mWakeLock.release();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                postScreenOff();
            }
        }
    };

    private final NotificationListenerService mNotificationListener =
            new NotificationListenerService() {
        private RankingMap mRankingMap;
        private final Comparator<StatusBarNotification> mRankingComparator =
                new Comparator<StatusBarNotification>() {

            private final Ranking mLhsRanking = new Ranking();
            private final Ranking mRhsRanking = new Ranking();

            @Override
            public int compare(StatusBarNotification lhs, StatusBarNotification rhs) {
                mRankingMap.getRanking(lhs.getKey(), mLhsRanking);
                mRankingMap.getRanking(rhs.getKey(), mRhsRanking);
                return Integer.compare(mLhsRanking.getRank(), mRhsRanking.getRank());
            }
        };

        @Override
        public void onListenerConnected() {
            handleNotificationUpdate(getCurrentRanking());
        }

        @Override
        public void onNotificationPosted(StatusBarNotification sbn, RankingMap ranking) {
            handleNotificationUpdate(ranking);
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification sbn, RankingMap ranking) {
            handleNotificationUpdate(ranking);
        }

        @Override
        public void onNotificationRankingUpdate(RankingMap ranking) {
            handleNotificationUpdate(ranking);
        }

        private void handleNotificationUpdate(RankingMap ranking) {
            if (!mNotificationListenerRegistered) {
                return;
            }

            mRankingMap = ranking;

            List<StatusBarNotification> notifications = Arrays.asList(getActiveNotifications());
            Collections.sort(notifications, mRankingComparator);
            updateNotifications(notifications);
        }
    };

    private void postScreenOff() {
        mHandler.removeCallbacksAndMessages(null);
        int timeout = FlipFlapUtils.getTimeout(mContext, mBatteryManager.isCharging());
        if (mPowerManager.isInteractive() && timeout != FlipFlapUtils.DELAYED_SCREEN_OFF_NEVER) {
            Message msg = Message.obtain();
            msg.what = COVER_CLOSED_MSG;
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    private void restoreSecurityViewState() {
        Message message = new Message();
        message.what = RESTORE_SECURITY_VIEW_STATE;

        mHandler.sendMessageDelayed(message, 1500);
    }

    private final Handler mHandler = new Handler(true /*async*/) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COVER_CLOSED_MSG:
                    if (!mAlarmActive && !mCallState.isRinging()) {
                        mPowerManager.goToSleep(SystemClock.uptimeMillis());
                    }
                    break;

                case RESTORE_SECURITY_VIEW_STATE:
                    if (shouldChangeSecurityViewState()) {
                        setPassToSecurityView(mPassToSecurity);
                        mPassToSecurity = false;
                        mRestoredPassToSecurity = true;
                    }
                    break;
            }
        }
    };

    private void changeSecurityViewState() {
        if (shouldChangeSecurityViewState() && mRestoredPassToSecurity) {
            mPassToSecurity = shouldPassToSecurityView();
            setPassToSecurityView(true);
            mRestoredPassToSecurity = false;
        }
    }

    private boolean shouldChangeSecurityViewState() {
        return FlipFlapUtils.getPreferences(mContext).getBoolean(KEY_PASS_TO_SECURITY, false);
    }

    private boolean shouldPassToSecurityView() {
        LineageLockPatternUtils llpu = new LineageLockPatternUtils(mContext);
        return llpu.shouldPassToSecurityView(getUserId());
    }

    private void setPassToSecurityView(boolean enabled) {
        LineageLockPatternUtils llpu = new LineageLockPatternUtils(mContext);
        llpu.setPassToSecurityView(enabled, getUserId());
    }

    private void checkHighTouchSensitivity() {
        if (shouldUseHighTouchSensitivity() &&
                FlipFlapUtils.getHighTouchSensitivitySupported(getContext())) {
            mUserHighTouchState = LineageSettings.System.getInt(mContext.getContentResolver(),
                    LineageSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, 0);
            LineageSettings.System.putInt(mContext.getContentResolver(),
                    LineageSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, 1);
        }
    }

    private void restoreHighTouchSensitivity() {
        if (shouldUseHighTouchSensitivity() &&
                FlipFlapUtils.getHighTouchSensitivitySupported(getContext())) {
            LineageSettings.System.putInt(mContext.getContentResolver(),
                    LineageSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, mUserHighTouchState);
        }
    }

    private boolean shouldUseHighTouchSensitivity() {
        return FlipFlapUtils.getPreferences(mContext).getBoolean(KEY_TOUCH_SENSITIVITY, false);
    }

    private int getUserId() {
        return UserHandle.getUserId(Process.myUid());
    }
}

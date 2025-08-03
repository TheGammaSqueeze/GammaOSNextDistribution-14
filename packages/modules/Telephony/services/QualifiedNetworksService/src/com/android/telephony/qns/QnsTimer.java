/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.telephony.qns;

import static com.android.telephony.qns.QnsConstants.CALL_TYPE_IDLE;
import static com.android.telephony.qns.QnsConstants.INVALID_ID;
import static com.android.telephony.qns.QnsUtils.getSystemElapsedRealTime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** This class handles the delayed events triggered in QNS. */
class QnsTimer {

    private static final String TAG = QnsTimer.class.getSimpleName();
    private static final int EVENT_QNS_TIMER_EXPIRED = 1;

    private static final int MIN_ALARM_CALL_ACTIVE_DELAY_MS = 0;
    private static final int MIN_ALARM_SCREEN_OFF_DELAY_MS = 10000;
    private static final int MIN_ALARM_DEVICE_LIGHT_IDLE_DELAY_MS = 30000;
    private static final int MIN_ALARM_DEVICE_IDLE_DELAY_MS = 60000;
    static final String ACTION_ALARM_TIMER_EXPIRED =
            "com.android.telephony.qns.action.ALARM_TIMER_EXPIRED";

    private static final AtomicInteger sTimerId = new AtomicInteger();
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final PowerManager mPowerManager;
    private final HandlerThread mHandlerThread;
    private final BroadcastReceiver mBroadcastReceiver;
    private final PriorityQueue<TimerInfo> mTimerInfos;
    private PendingIntent mPendingIntent;
    private long mMinAlarmTimeMs = MIN_ALARM_SCREEN_OFF_DELAY_MS;
    private int mCurrentAlarmTimerId = INVALID_ID;
    private int mCurrentHandlerTimerId = INVALID_ID;
    private boolean mIsAlarmRequired;
    @VisibleForTesting Handler mHandler;
    private long mLastAlarmTriggerAtMs = Long.MAX_VALUE;
    private int mCallType = CALL_TYPE_IDLE;

    QnsTimer(Context context) {
        mContext = context;
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mBroadcastReceiver = new AlarmReceiver();
        mTimerInfos =
                new PriorityQueue<>(Comparator.comparingLong(TimerInfo::getExpireAtElapsedMillis));
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new QnsTimerHandler();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_ALARM_TIMER_EXPIRED);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED);
        intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    /**
     * This method uses AlarmManager to execute the delayed event passed as param.
     *
     * @param msg message to process.
     * @param delayMs timer value for the delay.
     * @return unique timer id associated with the registered timer.
     */
    int registerTimer(Message msg, long delayMs) {
        int timerId = sTimerId.getAndIncrement();
        TimerInfo timerInfo = new TimerInfo(timerId);
        timerInfo.setMessage(msg);
        timerInfo.setExpireAtElapsedMillis(getSystemElapsedRealTime() + delayMs);
        logd("register timer for timerId=" + timerId + ", with delay=" + delayMs);
        mHandler.post(
                () -> {
                    mTimerInfos.add(timerInfo);
                    updateToShortestDelay(mIsAlarmRequired, false /* forceUpdate */);
                });
        return timerId;
    }

    /**
     * This method unregisters the timer associated to given timerId.
     *
     * @param timerId timer id associated with the running timer.
     */
    void unregisterTimer(int timerId) {
        if (timerId == INVALID_ID) {
            return;
        }
        logd("unregisterTimer for timerId=" + timerId);
        mHandler.post(
                () -> {
                    logd("Cancel timerId=" + timerId);
                    TimerInfo timerInfo = new TimerInfo(timerId);
                    if (mTimerInfos.remove(timerInfo) && timerId == mCurrentAlarmTimerId) {
                        updateToShortestDelay(mIsAlarmRequired, false /* forceUpdate */);
                    }
                });
    }

    /**
     * It updates the call state in QnsTimer. If the call is active the minimum timer value for an
     * alarm is updated to 0ms. Otherwise the value will be based on device state (Idle, Light Idle
     * or Screen off).
     *
     * @param type Call type {@code @QnsConstants.QnsCallType}
     */
    void updateCallState(@QnsConstants.QnsCallType int type) {
        if (mCallType == CALL_TYPE_IDLE && type != CALL_TYPE_IDLE) {
            mHandler.post(
                    () -> {
                        mMinAlarmTimeMs = MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                        if (mIsAlarmRequired) {
                            updateToShortestDelay(true, true /* forceUpdate */);
                        }
                    });
        }
        mCallType = type;
        if (mCallType == CALL_TYPE_IDLE && mIsAlarmRequired) {
            if (mPowerManager.isDeviceIdleMode()) {
                mMinAlarmTimeMs = MIN_ALARM_DEVICE_IDLE_DELAY_MS;
            } else if (mPowerManager.isDeviceLightIdleMode()) {
                mMinAlarmTimeMs = MIN_ALARM_DEVICE_LIGHT_IDLE_DELAY_MS;
            } else {
                mMinAlarmTimeMs = MIN_ALARM_SCREEN_OFF_DELAY_MS; // SCREEN_OFF case
            }
        }
    }

    /**
     * This method performs the following actions: 1. checks if the shortest timer is set for
     * handler or alarm. If not it overrides the earlier set timer with the shortest one. 2. checks
     * for timers in the list those have passed the current elapsed time; and notifies them to
     * respective handlers.
     *
     * @param isAlarmRequired flag indicates if timer is need to setup with Alarm.
     * @param forceUpdate flag indicates to update the delay time for handler and/or alarm
     *     forcefully.
     */
    private void updateToShortestDelay(boolean isAlarmRequired, boolean forceUpdate) {
        TimerInfo timerInfo = mTimerInfos.peek();
        long elapsedTime = getSystemElapsedRealTime();
        while (timerInfo != null && timerInfo.getExpireAtElapsedMillis() <= elapsedTime) {
            logd("Notify timerInfo=" + timerInfo);
            timerInfo.getMessage().sendToTarget();
            mTimerInfos.poll();
            timerInfo = mTimerInfos.peek();
        }
        if (timerInfo == null) {
            logd("No timers are pending to run");
            clearAllTimers();
            return;
        }
        long delay = timerInfo.getExpireAtElapsedMillis() - elapsedTime;
        // Delayed Handler will always set for shortest delay.
        if (timerInfo.getTimerId() != mCurrentHandlerTimerId || forceUpdate) {
            mHandler.removeMessages(EVENT_QNS_TIMER_EXPIRED);
            mHandler.sendEmptyMessageDelayed(EVENT_QNS_TIMER_EXPIRED, delay);
            mCurrentHandlerTimerId = timerInfo.getTimerId();
        }

        // Alarm will always set for shortest from Math.max(delay, mMinAlarmTimeMs)
        if (timerInfo.getTimerId() != mCurrentAlarmTimerId || forceUpdate) {
            if (isAlarmRequired) {
                delay = Math.max(delay, mMinAlarmTimeMs);
                // check if smaller timer alarm is already running for active timer info.
                if (mTimerInfos.contains(new TimerInfo(mCurrentAlarmTimerId))
                        && mLastAlarmTriggerAtMs - elapsedTime < delay
                        && mPendingIntent != null) {
                    logd(
                            "Skip update since minimum Alarm Timer already running for timerId="
                                    + mCurrentAlarmTimerId);
                    return;
                }
                logd("Setup alarm for delay " + delay);
                mLastAlarmTriggerAtMs = elapsedTime + delay;
                setupAlarmFor(mLastAlarmTriggerAtMs);
            } else if (mPendingIntent != null) {
                mAlarmManager.cancel(mPendingIntent);
                mPendingIntent = null;
            }
            mCurrentAlarmTimerId = timerInfo.getTimerId();
            logd("Update timer to timer id=" + mCurrentAlarmTimerId);
        }
    }

    private void clearAllTimers() {
        mHandler.removeMessages(EVENT_QNS_TIMER_EXPIRED);
        if (mPendingIntent != null) {
            logd("Cancel Alarm");
            mAlarmManager.cancel(mPendingIntent);
        }
        mPendingIntent = null;
    }

    private void setupAlarmFor(long triggerAtMillis) {
        mPendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_ALARM_TIMER_EXPIRED),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mAlarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, mPendingIntent);
    }

    private class AlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive action=" + action);
            switch (action) {
                case ACTION_ALARM_TIMER_EXPIRED:
                    mHandler.sendEmptyMessage(EVENT_QNS_TIMER_EXPIRED);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mHandler.post(
                            () -> {
                                mMinAlarmTimeMs =
                                        (mCallType == CALL_TYPE_IDLE)
                                                ? MIN_ALARM_SCREEN_OFF_DELAY_MS
                                                : MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                                if (!mIsAlarmRequired) {
                                    mIsAlarmRequired = true;
                                    updateToShortestDelay(true, true /* forceUpdate */);
                                }
                            });
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mHandler.post(
                            () -> {
                                if (mIsAlarmRequired) {
                                    mIsAlarmRequired = false;
                                    updateToShortestDelay(false, true /* forceUpdate */);
                                }
                            });
                    break;
                case PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED:
                    mHandler.post(
                            () -> {
                                if (mPowerManager.isDeviceLightIdleMode()) {
                                    mMinAlarmTimeMs =
                                            (mCallType == CALL_TYPE_IDLE)
                                                    ? MIN_ALARM_DEVICE_LIGHT_IDLE_DELAY_MS
                                                    : MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                                    if (!mIsAlarmRequired) {
                                        mIsAlarmRequired = true;
                                        updateToShortestDelay(true, true /* forceUpdate */);
                                    }
                                } else {
                                    mMinAlarmTimeMs =
                                            (mCallType == CALL_TYPE_IDLE)
                                                    ? MIN_ALARM_SCREEN_OFF_DELAY_MS
                                                    : MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                                }
                            });
                    break;
                case PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED:
                    mHandler.post(
                            () -> {
                                if (mPowerManager.isDeviceIdleMode()) {
                                    mMinAlarmTimeMs =
                                            (mCallType == CALL_TYPE_IDLE)
                                                    ? MIN_ALARM_DEVICE_IDLE_DELAY_MS
                                                    : MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                                    if (!mIsAlarmRequired) {
                                        mIsAlarmRequired = true;
                                        updateToShortestDelay(true, true /* forceUpdate */);
                                    }
                                } else {
                                    mMinAlarmTimeMs =
                                            (mCallType == CALL_TYPE_IDLE)
                                                    ? MIN_ALARM_SCREEN_OFF_DELAY_MS
                                                    : MIN_ALARM_CALL_ACTIVE_DELAY_MS;
                                }
                            });
                    break;
                default:
                    break;
            }
        }
    }

    private class QnsTimerHandler extends Handler {
        QnsTimerHandler() {
            super(mHandlerThread.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            logd("handleMessage msg.what=" + msg.what);
            switch (msg.what) {
                case EVENT_QNS_TIMER_EXPIRED:
                    logd("Timer expired");
                    updateToShortestDelay(mIsAlarmRequired, false /* forceUpdate */);
                    break;
                default:
                    break;
            }
        }
    }

    static class TimerInfo {
        private final int mTimerId;
        private long mExpireAtElapsedMillis;
        private Message mMsg;

        TimerInfo(int timerId) {
            mTimerId = timerId;
        }

        public int getTimerId() {
            return mTimerId;
        }

        public Message getMessage() {
            return mMsg;
        }

        public void setMessage(Message msg) {
            mMsg = msg;
        }

        public long getExpireAtElapsedMillis() {
            return mExpireAtElapsedMillis;
        }

        public void setExpireAtElapsedMillis(long expireAtElapsedMillis) {
            mExpireAtElapsedMillis = expireAtElapsedMillis;
        }

        /** Timers are equals if they share the same timer id. */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TimerInfo)) return false;
            TimerInfo timerInfo = (TimerInfo) o;
            return mTimerId == timerInfo.mTimerId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTimerId);
        }

        @Override
        public String toString() {
            return "TimerInfo{"
                    + "mTimerId="
                    + mTimerId
                    + ", mExpireAtElapsedMillis="
                    + mExpireAtElapsedMillis
                    + ", mMsg="
                    + mMsg
                    + '}';
        }
    }

    @VisibleForTesting
    PriorityQueue<TimerInfo> getTimersInfo() {
        return mTimerInfos;
    }

    void close() {
        logd("Closing QnsTimer");
        mHandlerThread.quitSafely();
        mContext.unregisterReceiver(mBroadcastReceiver);
        mTimerInfos.clear();
        clearAllTimers();
    }

    private void logd(String s) {
        Log.d(TAG, s);
    }

    /**
     * Dumps the state of {@link QnsTimer}
     *
     * @param pw {@link PrintWriter} to write the state of the object.
     * @param prefix String to append at start of dumped log.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(prefix + "QnsTimer:");
        pw.println(
                prefix
                        + "mIsAlarmRequired="
                        + mIsAlarmRequired
                        + ", mCurrentAlarmTimerId="
                        + mCurrentAlarmTimerId
                        + ", mCurrentHandlerTimerId="
                        + mCurrentHandlerTimerId
                        + ", latest timerId="
                        + sTimerId.get()
                        + ", Current elapsed time="
                        + getSystemElapsedRealTime());
        pw.println(prefix + "mTimerInfos=" + mTimerInfos);
        pw.println(prefix + "mPendingIntent=" + mPendingIntent);
    }
}

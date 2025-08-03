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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import android.os.SystemClock;
import android.os.test.TestLooper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class QnsTimerTest extends QnsTest {

    private static final int EVENT_QNS_TIMER_EXPIRED = 1;
    static final String ACTION_ALARM_TIMER_EXPIRED =
            "com.android.telephony.qns.action.ALARM_TIMER_EXPIRED";
    static final String KEY_TIMER_ID = "key_timer_id";
    @Mock private Context mContext;
    @Mock private AlarmManager mAlarmManager;
    @Mock private PowerManager mPowerManager;
    @Mock Message mMessage;
    private QnsTimer mQnsTimer;
    private BroadcastReceiver mBroadcastReceiver;
    int mTimerId;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(AlarmManager.class)).thenReturn(mAlarmManager);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        mQnsTimer = new QnsTimer(mContext);
        ArgumentCaptor<BroadcastReceiver> args = ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(args.capture(), isA(IntentFilter.class), anyInt());
        mBroadcastReceiver = args.getValue();
    }

    @After
    public void tearDown() {
        mQnsTimer.close();
    }

    @Test
    public void testRegisterTimerForScreenOff() {
        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        mTimerId = mQnsTimer.registerTimer(mMessage, 30000);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        assertTrue(mQnsTimer.getTimersInfo().contains(new QnsTimer.TimerInfo(mTimerId)));
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));
    }

    @Test
    public void testRegisterTimerForScreenOn() {
        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_ON));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        mTimerId = mQnsTimer.registerTimer(mMessage, 80000);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        assertTrue(mQnsTimer.getTimersInfo().contains(new QnsTimer.TimerInfo(mTimerId)));
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager, never())
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));
    }

    @Test
    public void testUnregisterForInvalidId() {
        testRegisterTimerForScreenOn();
        int timerInfoSize = mQnsTimer.getTimersInfo().size();
        mQnsTimer.unregisterTimer(QnsConstants.INVALID_ID);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        assertEquals(timerInfoSize, mQnsTimer.getTimersInfo().size());
    }

    @Test
    public void testUnregisterTimerForScreenOff() {
        testRegisterTimerForScreenOff();
        mQnsTimer.unregisterTimer(mTimerId);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        assertFalse(mQnsTimer.getTimersInfo().contains(new QnsTimer.TimerInfo(mTimerId)));
        assertFalse(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager).cancel(isA(PendingIntent.class));
    }

    @Test
    public void testUnregisterTimerForScreenOn() {
        testRegisterTimerForScreenOn();
        mQnsTimer.unregisterTimer(mTimerId);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        assertFalse(mQnsTimer.getTimersInfo().contains(new QnsTimer.TimerInfo(mTimerId)));
        assertFalse(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager, never()).cancel(isA(PendingIntent.class));
    }

    @Test
    public void testUpdateTimerTypeToAlarm() {
        testRegisterTimerForScreenOn();

        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));
    }

    @Test
    public void testTimerExpired() {
        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        mTimerId = mQnsTimer.registerTimer(mMessage, 50);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        assertTrue(mQnsTimer.getTimersInfo().contains(new QnsTimer.TimerInfo(mTimerId)));
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));

        waitForDelayedHandlerAction(mQnsTimer.mHandler, 40, 200);
        mBroadcastReceiver.onReceive(mContext, new Intent(ACTION_ALARM_TIMER_EXPIRED));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        verify(mMessage).sendToTarget();
        assertFalse(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager).cancel(isA(PendingIntent.class));
    }

    @Test
    public void testMultipleTimerRegistered() {
        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        TestLooper testLooper = new TestLooper();
        Handler h = new Handler(testLooper.getLooper());

        mQnsTimer.registerTimer(Message.obtain(h, 4), 300);
        mQnsTimer.registerTimer(Message.obtain(h, 3), 200);
        mQnsTimer.registerTimer(Message.obtain(h, 1), 50);
        mQnsTimer.registerTimer(Message.obtain(h, 1), 50);
        mQnsTimer.registerTimer(Message.obtain(h, 2), 100);
        mQnsTimer.registerTimer(Message.obtain(h, 2), 100);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 320, 100);

        // alarm timer should update for shortest delay and since the minimum timer value is 10
        // secs for screen off condition, the alarm timer will not replace be replaced until new
        // timer requested for less than 10 secs.
        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));

        // verify order of message received:
        Message msg = testLooper.nextMessage();
        assertEquals(1, msg.what);
        msg = testLooper.nextMessage();
        assertEquals(1, msg.what);
        msg = testLooper.nextMessage();
        assertEquals(2, msg.what);
        msg = testLooper.nextMessage();
        assertEquals(2, msg.what);
        msg = testLooper.nextMessage();
        assertEquals(3, msg.what);
        msg = testLooper.nextMessage();
        assertEquals(4, msg.what);
    }

    @Test
    public void testCancelOngoingAlarm() {
        mBroadcastReceiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        TestLooper testLooper = new TestLooper();
        Handler h = new Handler(testLooper.getLooper());

        int timerId1 = mQnsTimer.registerTimer(Message.obtain(h, 1), 61 * 1000);
        int timerId2 = mQnsTimer.registerTimer(Message.obtain(h, 2), 1000);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);

        assertEquals(2, mQnsTimer.getTimersInfo().size());
        assertEquals(timerId2, mQnsTimer.getTimersInfo().peek().getTimerId());
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        verify(mAlarmManager, times(2))
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));

        mQnsTimer.unregisterTimer(timerId2);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        assertEquals(1, mQnsTimer.getTimersInfo().size());
        assertEquals(timerId1, mQnsTimer.getTimersInfo().peek().getTimerId());
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));

        verify(mAlarmManager, times(3))
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));
    }

    @Test
    public void testAlarmOnLiteIdleModeMinDelay() {
        int setDelay = 20000;
        when(mPowerManager.isDeviceLightIdleMode()).thenReturn(true);
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        long delay = setupAlarmForDelay(setDelay);

        // assume 100ms as max delay in execution
        assertTrue(delay < 30000 && delay > 30000 - 100);
    }

    @Test
    public void testAlarmOnLiteIdleMode() {
        int setDelay = 40000;
        when(mPowerManager.isDeviceLightIdleMode()).thenReturn(true);
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        long delay = setupAlarmForDelay(setDelay);

        // assume 100ms as max delay in execution
        assertTrue(delay < setDelay && delay > setDelay - 100);
    }

    @Test
    public void testAlarmOnIdleModeMinDelay() {
        int setDelay = 50000;
        when(mPowerManager.isDeviceIdleMode()).thenReturn(true);
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
        long delay = setupAlarmForDelay(setDelay);

        // assume 100ms as max delay in execution
        assertTrue(delay < 60000 && delay > 60000 - 100);
    }

    @Test
    public void testAlarmOnIdleMode() {
        int setDelay = 70000;
        when(mPowerManager.isDeviceIdleMode()).thenReturn(true);
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
        long delay = setupAlarmForDelay(setDelay);

        // assume 100ms as max delay in execution
        assertTrue(delay < setDelay && delay > setDelay - 100);
    }

    private long setupAlarmForDelay(int setDelay) {
        mQnsTimer.registerTimer(mMessage, setDelay);

        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        ArgumentCaptor<Long> capture = ArgumentCaptor.forClass(Long.class);
        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), capture.capture(), isA(PendingIntent.class));
        return capture.getValue() - SystemClock.elapsedRealtime();
    }

    @Test
    public void testAlarmInCallActiveState() {
        mQnsTimer.updateCallState(QnsConstants.CALL_TYPE_VOICE);
        int setDelay = 4000;
        when(mPowerManager.isDeviceIdleMode()).thenReturn(true);
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
        long delay = setupAlarmForDelay(setDelay);

        // assume 100ms as max delay in execution
        assertTrue(delay < setDelay && delay > setDelay - 100);
    }

    @Test
    public void testDeviceMovesToActiveState() {
        int setDelay = 30000;
        CountDownLatch latch = new CountDownLatch(2);
        HandlerThread ht = new HandlerThread("");
        ht.start();
        Handler tempHandler = spy(new Handler(ht.getLooper()));
        when(mPowerManager.isDeviceLightIdleMode()).thenReturn(true, false);
        mBroadcastReceiver.onReceive(sMockContext, new Intent(Intent.ACTION_SCREEN_OFF));
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        mQnsTimer.registerTimer(mMessage, setDelay);
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        verify(mAlarmManager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), isA(PendingIntent.class));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 5000);

        mQnsTimer.mHandler = tempHandler;
        mBroadcastReceiver.onReceive(
                sMockContext, new Intent(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        mBroadcastReceiver.onReceive(sMockContext, new Intent(Intent.ACTION_SCREEN_ON));
        waitForDelayedHandlerAction(mQnsTimer.mHandler, 10, 200);
        verify(mAlarmManager).cancel(isA(PendingIntent.class));

        // Handler should reset for the updated delay
        verify(tempHandler).removeMessages(EVENT_QNS_TIMER_EXPIRED);
        verify(tempHandler).sendEmptyMessageDelayed(anyInt(), anyLong());
        assertTrue(mQnsTimer.mHandler.hasMessages(EVENT_QNS_TIMER_EXPIRED));
        ht.quit();
    }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.utils;

import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.Dependencies;
import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;
import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmWithListener;
import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmWithPendingIntent;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Message;
import android.os.Process;

import org.junit.Before;
import org.junit.Test;

public class IkeAlarmTest {
    private static final String ALARM_TAG = "IkeAlarmTest.TEST_ALARM";
    private static final long ALARM_DELAY_MS = 1000;

    private Context mMockContext;
    private AlarmManager mMockAlarmMgr;
    private Message mMockMessage;
    private PendingIntent mMockPendingIntent;
    private Dependencies mMockDeps;

    private IkeAlarmConfig mAlarmConfig;

    @Before
    public void setUp() {
        mMockContext = mock(Context.class);
        mMockAlarmMgr = mock(AlarmManager.class);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmMgr);

        mMockMessage = mock(Message.class);
        mMockPendingIntent = mock(PendingIntent.class);
        mMockDeps = mock(Dependencies.class);

        mAlarmConfig =
                new IkeAlarmConfig(
                        mMockContext, ALARM_TAG, ALARM_DELAY_MS, mMockPendingIntent, mMockMessage);
    }

    @Test
    public void testNewExactAlarm() throws Exception {
        IkeAlarm alarm = IkeAlarm.newExactAlarm(mAlarmConfig, mMockDeps);
        assertTrue(alarm instanceof IkeAlarmWithListener);
    }

    @Test
    public void testNewExactAndAllowWhileIdleAlarmWithSystemUid() throws Exception {
        when(mMockDeps.getMyUid()).thenReturn(Process.SYSTEM_UID);

        IkeAlarm alarm = IkeAlarm.newExactAndAllowWhileIdleAlarm(mAlarmConfig, mMockDeps);
        assertTrue(alarm instanceof IkeAlarmWithListener);
    }

    @Test
    public void testNewExactAndAllowWhileIdleAlarmWithNonSystemUid() throws Exception {
        when(mMockDeps.getMyUid()).thenReturn(Process.SYSTEM_UID + 1);

        IkeAlarm alarm = IkeAlarm.newExactAndAllowWhileIdleAlarm(mAlarmConfig, mMockDeps);
        assertTrue(alarm instanceof IkeAlarmWithPendingIntent);
    }
}

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

package com.android.car.docklib.events;

import static com.android.car.dockutil.events.DockEventSenderHelper.EXTRA_COMPONENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.docklib.DockInterface;
import com.android.car.dockutil.events.DockEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DockEventsReceiverTest {
    @Mock
    public Context mContext;
    @Mock
    public Intent mIntent;
    @Mock
    public DockInterface mDockInterface;

    private DockEventsReceiver mDockEventsReceiver;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDockEventsReceiver = new DockEventsReceiver(mDockInterface);
    }

    @Test
    public void onReceive_intentWithNoAction_noOp() {
        when(mIntent.getAction()).thenReturn(null);

        mDockEventsReceiver.onReceive(mContext, mIntent);

        verifyZeroInteractions(mDockInterface);
    }

    @Test
    public void onReceive_intentWithActionNotConvertibleToDockEvent_noOp() {
        String action = "action";
        assertThat(DockEvent.toDockEvent(action)).isNull();
        when(mIntent.getAction()).thenReturn(action);

        mDockEventsReceiver.onReceive(mContext, mIntent);

        verifyZeroInteractions(mDockInterface);
    }

    @Test
    public void onReceive_intentWithNoData_noOp() {
        when(mIntent.getAction()).thenReturn(DockEvent.LAUNCH.toString());
        when(mIntent.getParcelableExtra(eq(EXTRA_COMPONENT), eq(ComponentName.class)))
                .thenReturn(null);

        mDockEventsReceiver.onReceive(mContext, mIntent);

        verifyZeroInteractions(mDockInterface);
    }

    @Test
    public void onReceive_intentWithDockEventAndData_callController() {
        when(mIntent.getAction()).thenReturn(DockEvent.LAUNCH.toString());
        ComponentName component = new ComponentName("testPackage", "testClass");
        when(mIntent.getParcelableExtra(eq(EXTRA_COMPONENT), eq(ComponentName.class)))
                .thenReturn(component);

        mDockEventsReceiver.onReceive(mContext, mIntent);

        verify(mDockInterface).appLaunched(component);
    }
}

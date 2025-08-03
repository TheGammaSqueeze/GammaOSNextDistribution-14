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

package com.android.car.dockutil.events;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.car.dockutil.events.DockEventSenderHelper.EXTRA_COMPONENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.dockutil.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DockEventSenderHelperTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock
    public ActivityManager.RunningTaskInfo mRunningTaskInfo;
    @Mock
    public Context mContext;
    @Mock
    public Resources mResources;
    @Mock
    public Intent mIntent;
    @Mock
    public ComponentName mAppComponent;
    @Captor
    public ArgumentCaptor<Intent> mIntentCaptor;
    private DockEventSenderHelper mDockEventSenderHelper;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources()).thenReturn(mResources);
        mSetFlagsRule.enableFlags(Flags.FLAG_DOCK_FEATURE);
        mDockEventSenderHelper = new DockEventSenderHelper(mContext);
    }

    @Test
    public void sendEventBroadcast_nonDefaultDisplay_broadcastNotSent() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY + 1);

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.LAUNCH, mRunningTaskInfo);

        verify(mContext, never()).sendBroadcast(any(Intent.class), anyString());
    }

    @Test
    public void sendEventBroadcast_noBastActivity_noBaseIntentComponent_broadcastNotSent() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        mRunningTaskInfo.baseActivity = null;
        when(mIntent.getComponent()).thenReturn(null);
        mRunningTaskInfo.baseIntent = mIntent;

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.LAUNCH, mRunningTaskInfo);

        verify(mContext, never()).sendBroadcast(any(Intent.class), anyString());
    }

    @Test
    public void sendEventBroadcast_broadcastSent_receiverPermissionSet() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        mRunningTaskInfo.baseActivity = mAppComponent;

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.LAUNCH, mRunningTaskInfo);

        verify(mContext).sendBroadcast(any(Intent.class),
                eq(DockPermission.DOCK_RECEIVER_PERMISSION.toString()));
    }


    @Test
    public void sendEventBroadcast_launchEvent_broadcastSent() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        mRunningTaskInfo.baseActivity = mAppComponent;

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.LAUNCH, mRunningTaskInfo);

        verify(mContext).sendBroadcast(mIntentCaptor.capture(), anyString());
        Intent intentSent = mIntentCaptor.getValue();
        assertThat(intentSent.getAction()).isEqualTo(DockEvent.LAUNCH.toString());
        assertThat(intentSent.getExtras()).isNotNull();
        assertThat(intentSent.getExtras().getParcelable(EXTRA_COMPONENT, ComponentName.class))
                .isEqualTo(mAppComponent);
    }

    @Test
    public void sendEventBroadcast_pinEvent_broadcastSent() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        mRunningTaskInfo.baseActivity = mAppComponent;

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.PIN, mRunningTaskInfo);

        verify(mContext).sendBroadcast(mIntentCaptor.capture(), anyString());
        Intent intentSent = mIntentCaptor.getValue();
        assertThat(intentSent.getAction()).isEqualTo(DockEvent.PIN.toString());
        assertThat(intentSent.getExtras()).isNotNull();
        assertThat(intentSent.getExtras().getParcelable(EXTRA_COMPONENT, ComponentName.class))
                .isEqualTo(mAppComponent);
    }


    @Test
    public void sendEventBroadcast_unpinEvent_broadcastSent() {
        when(mRunningTaskInfo.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        mRunningTaskInfo.baseActivity = mAppComponent;

        mDockEventSenderHelper.sendEventBroadcast(DockEvent.UNPIN, mRunningTaskInfo);

        verify(mContext).sendBroadcast(mIntentCaptor.capture(), anyString());
        Intent intentSent = mIntentCaptor.getValue();
        assertThat(intentSent.getAction()).isEqualTo(DockEvent.UNPIN.toString());
        assertThat(intentSent.getExtras()).isNotNull();
        assertThat(intentSent.getExtras().getParcelable(EXTRA_COMPONENT, ComponentName.class))
                .isEqualTo(mAppComponent);
    }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiContext;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.SoftApNotifier}.
 */
@SmallTest
public class SoftApNotifierTest extends WifiBaseTest {
    private static final String TEST_SSID = "Test SSID";

    @Mock
    WifiContext mContext;
    @Mock Resources mResources;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    SoftApNotifier mSoftApNotifier;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        mSoftApNotifier = new SoftApNotifier(mContext, mFrameworkFacade, mWifiNotificationManager);
    }

    /**
     * Verify that a shutdown timeout expired notification will be generated/pushed when calling.
     *
     * @throws Exception
     */
    @Test
    public void showSoftApShutdownTimeoutExpiredNotification() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_STATUS))).thenReturn(mNotificationBuilder);
        mSoftApNotifier.showSoftApShutdownTimeoutExpiredNotification();
        verify(mWifiNotificationManager).notify(
                eq(mSoftApNotifier.NOTIFICATION_ID_SOFTAP_AUTO_DISABLED), any());
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFrameworkFacade).getActivity(
                any(Context.class), anyInt(), intent.capture(), anyInt());
        assertEquals(mSoftApNotifier.ACTION_HOTSPOT_PREFERENCES, intent.getValue().getAction());
    }

    /**
     * Verify that we don't attempt to dismiss the wrong password notification when starting a new
     * connection attempt with the previous connection not resulting in a wrong password error.
     *
     * @throws Exception
     */
    @Test
    public void dismissSoftApShutdownTimeoutExpiredNotification() throws Exception {
        mSoftApNotifier.dismissSoftApShutdownTimeoutExpiredNotification();
        verify(mWifiNotificationManager).cancel(
                eq(mSoftApNotifier.NOTIFICATION_ID_SOFTAP_AUTO_DISABLED));
    }
}

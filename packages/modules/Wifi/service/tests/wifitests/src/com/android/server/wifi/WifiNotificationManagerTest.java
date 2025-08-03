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

package com.android.server.wifi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import androidx.test.filters.SmallTest;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WifiNotificationManager}.
 */
@SmallTest
public class WifiNotificationManagerTest extends WifiBaseTest {
    private WifiNotificationManager mWifiNotificationManager;

    private static final String NOTIFICATION_TAG = "com.android.wifi";
    private static final int TEST_MESSAGE_ID = 10;

    @Mock private Context mContext;
    @Mock private NotificationManager mNotificationManager;
    @Mock private Context mContextForOwner;
    @Mock private Context mContextForAnotherUser;
    @Mock private NotificationManager mNotificationManagerForAnotherUser;
    @Mock private Notification mNotification;
    @Mock private Resources mResources;
    @Mock private StatusBarNotification mStatusBarNotification;
    @Mock private UserHandle mUser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageName()).thenReturn("WifiAPK");
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle.class)))
                .thenReturn(mContext);
        when(mContext.getSystemService(NotificationManager.class))
                .thenReturn(mNotificationManager);
        when(mContextForAnotherUser.getSystemService(NotificationManager.class))
                .thenReturn(mNotificationManagerForAnotherUser);
        mWifiNotificationManager = new WifiNotificationManager(mContext);
    }

    @Test
    public void testNotify() {
        mWifiNotificationManager.notify(TEST_MESSAGE_ID, mNotification);
        verify(mNotificationManager, never()).notify(anyString(), anyInt(), any());

        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).createNotificationChannels(any());
        mWifiNotificationManager.notify(TEST_MESSAGE_ID, mNotification);
        verify(mNotificationManager).notify(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID), any());
    }

    @Test
    public void testCancel() {
        mWifiNotificationManager.cancel(TEST_MESSAGE_ID);
        verify(mNotificationManager, never()).cancel(anyString(), anyInt());

        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).createNotificationChannels(any());
        mWifiNotificationManager.cancel(TEST_MESSAGE_ID);
        verify(mNotificationManager).cancel(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID));
    }

    @Test
    public void testUserSwitchNotificationSendCorrect() throws Exception {
        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).createNotificationChannels(any());
        mWifiNotificationManager.notify(TEST_MESSAGE_ID, mNotification);
        verify(mNotificationManager).notify(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID), any());
        verify(mNotificationManagerForAnotherUser, never()).notify(anyString(), anyInt(), any());
        clearInvocations(mNotificationManager);
        when(mNotificationManager.getActiveNotifications())
                .thenReturn(new StatusBarNotification[]{mStatusBarNotification});
        when(mStatusBarNotification.getTag()).thenReturn(NOTIFICATION_TAG);
        when(mStatusBarNotification.getId()).thenReturn(TEST_MESSAGE_ID);
        // Test user switch
        when(mContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(mContextForAnotherUser);
        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).cancel(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID));
        verify(mNotificationManagerForAnotherUser).createNotificationChannels(any());
        mWifiNotificationManager.notify(TEST_MESSAGE_ID, mNotification);
        verify(mNotificationManagerForAnotherUser).notify(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID),
                any());
        verify(mNotificationManager, never()).notify(anyString(), anyInt(), any());
    }

    @Test
    public void testApmNotificationNotCancelled() {
        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).createNotificationChannels(any());
        mWifiNotificationManager.notify(TEST_MESSAGE_ID, mNotification);
        mWifiNotificationManager.notify(SystemMessage.NOTE_WIFI_APM_NOTIFICATION, mNotification);
        verify(mNotificationManager).notify(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID), any());
        verify(mNotificationManager).notify(eq(NOTIFICATION_TAG),
                eq(SystemMessage.NOTE_WIFI_APM_NOTIFICATION), any());
        // clearing existing notifications doesn't cancel the APM notification
        StatusBarNotification testNotification = new StatusBarNotification("pkg", "opPkg",
                TEST_MESSAGE_ID, NOTIFICATION_TAG, 0, 0, 0, mNotification, mUser, 0);
        StatusBarNotification apmNotification = new StatusBarNotification("pkg", "opPkg",
                SystemMessage.NOTE_WIFI_APM_NOTIFICATION, NOTIFICATION_TAG, 0, 0, 0, mNotification,
                mUser, 0);
        when(mNotificationManager.getActiveNotifications()).thenReturn(
                new StatusBarNotification[]{testNotification, apmNotification});
        mWifiNotificationManager.createNotificationChannels();
        verify(mNotificationManager).cancel(eq(NOTIFICATION_TAG), eq(TEST_MESSAGE_ID));
        verify(mNotificationManager, never()).cancel(eq(NOTIFICATION_TAG),
                eq(SystemMessage.NOTE_WIFI_APM_NOTIFICATION));
    }
}

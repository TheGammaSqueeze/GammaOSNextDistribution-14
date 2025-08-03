/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.bluetooth;

import static com.android.internal.messages.nano.SystemMessageProto.SystemMessage.NOTE_BT_APM_NOTIFICATION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.service.notification.StatusBarNotification;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothNotificationManagerTest {

    @Mock
    NotificationManager mNotificationManager;

    Context mContext;
    BluetoothNotificationManager mBluetoothNotificationManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(InstrumentationRegistry.getTargetContext()));
        doReturn(mNotificationManager).when(mContext).getSystemService(NotificationManager.class);
        doReturn(mContext).when(mContext).createPackageContextAsUser(anyString(), anyInt(), any());

        mBluetoothNotificationManager = new BluetoothNotificationManager(mContext);
    }

    @Test
    public void createNotificationChannels_callsNotificationManagerCreateNotificationChannels() {
        mBluetoothNotificationManager.createNotificationChannels();

        verify(mNotificationManager).createNotificationChannels(any());
    }

    @Test
    public void notify_callsNotificationManagerNotify() {
        int id = 1234;
        Notification notification = mock(Notification.class);

        mBluetoothNotificationManager.notify(id, notification);

        verify(mNotificationManager).notify(anyString(), eq(id), eq(notification));
    }

    @Test
    public void sendApmNotification_callsNotificationManagerNotify_withApmNotificationId() {
        mBluetoothNotificationManager.sendApmNotification("test_title", "test_message");

        verify(mNotificationManager).notify(anyString(), eq(NOTE_BT_APM_NOTIFICATION), any());
    }

    @Test
    public void getActiveNotifications() {
        StatusBarNotification[] notifications = new StatusBarNotification[0];
        when(mNotificationManager.getActiveNotifications()).thenReturn(notifications);

        assertThat(mBluetoothNotificationManager.getActiveNotifications())
                .isEqualTo(notifications);
    }
}

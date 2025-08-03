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
package com.android.car.admin;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

import static com.android.car.admin.NotificationHelper.CHANNEL_ID_DEFAULT;
import static com.android.car.admin.NotificationHelper.CHANNEL_ID_HIGH;
import static com.android.car.admin.NotificationHelper.newNotificationBuilder;
import static com.android.internal.R.string.android_system_label;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;


@RunWith(Parameterized.class)
public final class NotificationHelperValidImportanceTest {

    private static final String SYSTEM_LABEL = "System, I am your Label!";
    private static final String IMPORTANCE_NAME = "The name is Bond, Importance Bond!";

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private final Context mRealContext = InstrumentationRegistry.getInstrumentation().getContext();

    private Context mSpiedContext;

    @Mock
    private NotificationManager mNotificationMgr;

    private final int mImportance;
    private final String mChannelId;
    private final int mResId;

    public NotificationHelperValidImportanceTest(int importance, String channelId, int resId) {
        mImportance = importance;
        mChannelId = channelId;
        mResId = resId;
    }

    @Before
    public void setContext() {
        mSpiedContext = spy(mRealContext);
        when(mSpiedContext.getSystemService(NotificationManager.class))
                .thenReturn(mNotificationMgr);
        when(mSpiedContext.getString(android_system_label)).thenReturn(SYSTEM_LABEL);
        when(mSpiedContext.getString(mResId)).thenReturn(IMPORTANCE_NAME);
    }

    @Test
    public void testNewNotificationBuilder() {
        Notification.Builder builder = newNotificationBuilder(mSpiedContext, mImportance);
        assertWithMessage("builder").that(builder).isNotNull();

        // Check custom label
        Bundle extras = builder.getExtras();
        assertWithMessage("extras").that(extras).isNotNull();
        String label = extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME);
        assertWithMessage("label (extra %s)", Notification.EXTRA_SUBSTITUTE_APP_NAME).that(label)
                .isEqualTo(SYSTEM_LABEL);

        // Check notification arguments
        ArgumentCaptor<NotificationChannel> captor = ArgumentCaptor
                .forClass(NotificationChannel.class);
        verify(mNotificationMgr).createNotificationChannel(captor.capture());
        NotificationChannel channel = captor.getValue();
        assertWithMessage("channel id").that(channel.getId()).isEqualTo(mChannelId);
        assertWithMessage("importance").that(channel.getImportance()).isEqualTo(mImportance);
        assertWithMessage("name").that(channel.getName()).isEqualTo(IMPORTANCE_NAME);
    }

    @Parameterized.Parameters
    public static List<Object[]> provideParams() {
        return Arrays.asList(new Object[][] {
            { IMPORTANCE_DEFAULT, CHANNEL_ID_DEFAULT, R.string.importance_default },
            { IMPORTANCE_HIGH, CHANNEL_ID_HIGH, R.string.importance_high }
        });
    }
}

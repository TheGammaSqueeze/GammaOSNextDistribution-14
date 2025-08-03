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

package com.android.car.notification.template;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Calendar;

@RunWith(AndroidJUnit4.class)
public class CarNotificationBodyViewTest {
    private static final String TEST_TITLE = "TEST_TITLE";
    private static final String TEST_BODY = "TEST BODY";
    private static final String TEST_COUNT = "TEST BODY";
    private static final long TEST_WHEN = Calendar.getInstance().getTime().getTime();
    private static final String EXPECTED_WHEN = "now";
    private static final Drawable TEST_DRAWABLE = new ShapeDrawable();

    private CarNotificationBodyView mCarNotificationBodyView;
    private Context mContext;
    @Mock
    private StatusBarNotification mMockStatusBarNotification;
    @Mock
    private Notification mMockNotification;
    @Mock
    private Context mMockContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mCarNotificationBodyView = new CarNotificationBodyView(mContext, /* attrs= */ null);
        mCarNotificationBodyView.onFinishInflate();
        when(mMockStatusBarNotification.getNotification()).thenReturn(mMockNotification);
        when(mMockContext.getPackageManager()).thenReturn(mock(PackageManager.class));
        when(mMockStatusBarNotification.getPackageContext(any())).thenReturn(mMockContext);
    }

    @Test
    public void onBind_launcherIconUsed_titleTextSet() {
        mCarNotificationBodyView.bind(TEST_TITLE, TEST_BODY, mMockStatusBarNotification,
                /* largeIcon= */ null, /* titleIcon= */ null,
                TEST_COUNT, TEST_WHEN);

        assertThat(mCarNotificationBodyView.getTitleView().getText()).isEqualTo(TEST_TITLE);
    }

    @Test
    public void onBind_launcherIconUsed_contentTextSet() {
        mCarNotificationBodyView.bind(TEST_TITLE, TEST_BODY, mMockStatusBarNotification,
                /* largeIcon= */ null, /* titleIcon= */ null,
                TEST_COUNT, TEST_WHEN);

        assertThat(mCarNotificationBodyView.getContentView().getText()).isEqualTo(TEST_BODY);
    }

    @Test
    public void onBind_launcherIconUsed_countTextSet() {
        mCarNotificationBodyView.bind(TEST_TITLE, TEST_BODY, mMockStatusBarNotification,
                /* largeIcon= */ null, /* titleIcon= */ null,
                TEST_COUNT, TEST_WHEN);

        assertThat(mCarNotificationBodyView.getCountView().getText()).isEqualTo(TEST_COUNT);
    }

    @Test
    public void onBind_launcherIconUsed_timeSet() {
        mCarNotificationBodyView.bind(TEST_TITLE, TEST_BODY, mMockStatusBarNotification,
                /* largeIcon= */ null, /* titleIcon= */ null,
                TEST_COUNT, TEST_WHEN);

        assertThat(mCarNotificationBodyView.getTimeView().getText()).isEqualTo(EXPECTED_WHEN);
    }
}

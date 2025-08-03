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

package com.android.car.notification.headsup;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.notification.CarNotificationTypeItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CarHeadsUpNotificationContainerTest {
    private static final String TAG1 = "TAG1";
    private static final String TAG2 = "TAG2";
    private static final String TAG3 = "TAG2";
    private static final String TAG4 = "TAG2";
    private static final String TAG5 = "TAG2";
    private static final String TAG6 = "TAG2";
    @Mock
    WindowManager mWindowManager;
    private CarHeadsUpNotificationContainer mCarHeadsUpNotificationContainer;
    private View mNotificationView1;
    private View mNotificationView2;
    private View mNotificationView3;
    private View mNotificationView4;
    private View mNotificationView5;
    private View mNotificationView6;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = ApplicationProvider.getApplicationContext();
        mCarHeadsUpNotificationContainer = new CarHeadsUpNotificationContainer(
                context, mWindowManager) {
            @Override
            protected WindowManager.LayoutParams getWindowManagerLayoutParams() {
                return null;
            }
        };

        verify(mWindowManager).addView(
                mCarHeadsUpNotificationContainer.getHunWindow(), /* params= */ null);

        mCarHeadsUpNotificationContainer.getHunWindow().setVisibility(View.INVISIBLE);

        mNotificationView1 = new View(context);
        mNotificationView1.setTag(TAG1);
        mNotificationView2 = new View(context);
        mNotificationView2.setTag(TAG2);
        mNotificationView3 = new View(context);
        mNotificationView3.setTag(TAG3);
        mNotificationView4 = new View(context);
        mNotificationView4.setTag(TAG4);
        mNotificationView5 = new View(context);
        mNotificationView5.setTag(TAG5);
        mNotificationView6 = new View(context);
        mNotificationView6.setTag(TAG6);
    }

    @Test
    public void displayNotification_emptyContent_notificationOnlyChildInContentView() {
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView1,
                CarNotificationTypeItem.INBOX);

        assertThat(mCarHeadsUpNotificationContainer.getHunContent().getChildCount()).isEqualTo(1);
        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(0).getTag()).isEqualTo(
                TAG1);
    }

    @Test
    public void displayNotification_lowerImportanceContent_higherImportanceOnTop() {
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView1,
                CarNotificationTypeItem.INBOX);

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView2,
                CarNotificationTypeItem.CALL);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(1).getTag()).isEqualTo(
                TAG2);
    }

    @Test
    public void displayNotification_higherImportanceContent_lowerImportanceBehind() {
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView1,
                CarNotificationTypeItem.CALL);

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView2,
                CarNotificationTypeItem.INBOX);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(0).getTag()).isEqualTo(
                TAG2);
    }

    @Test
    public void displayNotification_allImportancePresent_newOtherImportance_newIndex1() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView6,
                CarNotificationTypeItem.MESSAGE);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(1).getTag()).isEqualTo(
                TAG6);
    }

    @Test
    public void displayNotification_allImportancePresent_newCallImportance_newIndex2() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView6,
                CarNotificationTypeItem.CALL);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(2).getTag()).isEqualTo(
                TAG6);
    }

    @Test
    public void displayNotification_allImportancePresent_newNavigationImportance_newIndex3() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView6,
                CarNotificationTypeItem.NAVIGATION);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(3).getTag()).isEqualTo(
                TAG6);
    }

    @Test
    public void displayNotification_allImportancePresent_newWarningImportance_newIndex4() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView6,
                CarNotificationTypeItem.WARNING);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(4).getTag()).isEqualTo(
                TAG6);
    }

    @Test
    public void displayNotification_allImportancePresent_newEmergencyImportance_newIndex5() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView6,
                CarNotificationTypeItem.EMERGENCY);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(5).getTag()).isEqualTo(
                TAG6);
    }

    @Test
    public void removeNotification_oneViewInContent_contentEmpty() {
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView1,
                CarNotificationTypeItem.INBOX);

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView1);

        assertThat(mCarHeadsUpNotificationContainer.getHunContent().getChildCount()).isEqualTo(0);
    }

    @Test
    public void removeNotification_allImportancePresent_otherRemoved_callIndex0() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView1);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(0).getTag()).isEqualTo(
                TAG2);
    }

    @Test
    public void removeNotification_allImportancePresent_callRemoved_navigationIndex1() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView2);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(1).getTag()).isEqualTo(
                TAG3);
    }

    @Test
    public void removeNotification_allImportancePresent_navigationRemoved_warningIndex2() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView3);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(2).getTag()).isEqualTo(
                TAG4);
    }

    @Test
    public void removeNotification_allImportancePresent_warningRemoved_emergencyIndex3() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView4);

        assertThat(
                mCarHeadsUpNotificationContainer.getHunContent().getChildAt(3).getTag()).isEqualTo(
                TAG5);
    }

    @Test
    public void removeNotification_allImportancePresent_emergencyRemoved_childAtIndex4ReturnsNull() {
        displayOneNotificationOfEveryImportance();

        mCarHeadsUpNotificationContainer.removeNotification(mNotificationView5);

        assertThat(mCarHeadsUpNotificationContainer.getHunContent().getChildAt(4)).isEqualTo(null);
    }

    private void displayOneNotificationOfEveryImportance() {
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView1,
                CarNotificationTypeItem.INBOX);
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView2,
                CarNotificationTypeItem.CALL);
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView3,
                CarNotificationTypeItem.NAVIGATION);
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView4,
                CarNotificationTypeItem.WARNING);
        mCarHeadsUpNotificationContainer.displayNotification(mNotificationView5,
                CarNotificationTypeItem.EMERGENCY);
    }
}

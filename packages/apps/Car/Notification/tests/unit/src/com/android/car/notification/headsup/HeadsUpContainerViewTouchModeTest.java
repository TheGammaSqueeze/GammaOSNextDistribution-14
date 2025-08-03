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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.car.ui.utils.RotaryConstants.ROTARY_FOCUS_DELEGATING_CONTAINER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.notification.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class HeadsUpContainerViewTouchModeTest {

    private Context mContext;
    private HeadsUpContainerView mHeadsUpContainerView;

    @Rule
    public ActivityTestRule<HeadsUpContainerViewTestActivity> mActivityRule =
            new ActivityTestRule<>(HeadsUpContainerViewTestActivity.class,
                    /* initialTouchMode= */ true);

    @Mock
    private Handler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
        launchTestActivity();
        mHeadsUpContainerView = mActivityRule.getActivity().getHeadsUpContainerView();
        mHeadsUpContainerView.setHandler(mHandler);
    }

    private void launchTestActivity() {
        Intent intent = new Intent()
                .setClassName(mContext.getPackageName(),
                        HeadsUpContainerViewTestActivity.class.getName())
                .setPackage(mContext.getPackageName());
        mActivityRule.launchActivity(intent);
    }

    @Test
    public void init_hasContentDescription() {
        assertThat(mHeadsUpContainerView.getContentDescription()).isEqualTo(
                ROTARY_FOCUS_DELEGATING_CONTAINER);
    }

    @Test
    public void init_isNotClickable() {
        assertThat(mHeadsUpContainerView.isClickable()).isFalse();
    }

    @Test
    public void addView_inTouchMode_noPrevFocus_addNewChild_nothingScheduled() {
        getInstrumentation().runOnMainSync(() -> {
            View notif1 = getTestNotificationView();
            mHeadsUpContainerView.addView(notif1);
        });

        verify(mHandler, never()).postDelayed(any(Runnable.class), anyLong());
    }

    @Test
    public void addView_inTouchMode_hasPrevFocus_addNewChild_nothingScheduled() {
        // Setup.
        getInstrumentation().runOnMainSync(() -> {
            View notif1 = getTestNotificationView();
            mHeadsUpContainerView.addView(notif1);
        });

        // Focus the current topmost notification. This needs to be done in a separate lambda so
        // that the view can be properly laid out.
        getInstrumentation().runOnMainSync(() -> mHeadsUpContainerView.requestFocus());

        getInstrumentation().runOnMainSync(() -> {
            View notif2 = getTestNotificationView();
            mHeadsUpContainerView.addView(notif2);
        });

        verify(mHandler, never()).postDelayed(any(Runnable.class), anyLong());
    }

    private View getTestNotificationView() {
        return LayoutInflater.from(mContext).inflate(R.layout.test_notification,
                /* root= */ null);
    }
}

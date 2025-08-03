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

package com.android.systemui.car.systembar;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.AlphaOptimizedImageView;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CarSystemBarButtonTest extends SysuiTestCase {

    private static final String DIALER_BUTTON_ACTIVITY_NAME =
            "com.android.car.dialer/.ui.TelecomActivity";
    private static final String BROADCAST_ACTION_NAME =
            "android.car.intent.action.TOGGLE_HVAC_CONTROLS";

    private ActivityManager mActivityManager;
    // LinearLayout with CarSystemBarButtons with different configurations.
    private LinearLayout mTestView;
    // Does not have any selection state which is the default configuration.
    private CarSystemBarButton mDefaultButton;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(mContext);
        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        mActivityManager = spy(am);
        when(mContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        mTestView = (LinearLayout) LayoutInflater.from(mContext).inflate(
                R.layout.car_system_bar_button_test, /* root= */ null);
        mDefaultButton = mTestView.findViewById(R.id.default_no_selection_state);
    }

    @Test
    public void onCreate_iconIsVisible() {
        AlphaOptimizedImageView icon = mDefaultButton.findViewById(
                R.id.car_nav_button_icon_image);

        assertThat(icon.getDrawable()).isNotNull();
    }

    @Test
    public void onSelected_selectedIconDefined_togglesIcon() {
        mDefaultButton.setSelected(true);
        waitForIdleSync();
        Drawable selectedIconDrawable = ((AlphaOptimizedImageView) mDefaultButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();

        mDefaultButton.setSelected(false);
        waitForIdleSync();
        Drawable unselectedIconDrawable = ((AlphaOptimizedImageView) mDefaultButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();

        assertThat(selectedIconDrawable).isNotEqualTo(unselectedIconDrawable);
    }

    @Test
    public void onSelected_selectedIconUndefined_displaysSameIcon() {
        CarSystemBarButton selectedIconUndefinedButton = mTestView.findViewById(
                R.id.selected_icon_undefined);

        selectedIconUndefinedButton.setSelected(true);
        waitForIdleSync();
        Drawable selectedIconDrawable = ((AlphaOptimizedImageView) mDefaultButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();


        selectedIconUndefinedButton.setSelected(false);
        waitForIdleSync();
        Drawable unselectedIconDrawable = ((AlphaOptimizedImageView) mDefaultButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();

        assertThat(selectedIconDrawable).isEqualTo(unselectedIconDrawable);
    }

    @Test
    public void onUnselected_doesNotHighlightWhenSelected_applySelectedAlpha() {
        mDefaultButton.setSelected(false);
        waitForIdleSync();

        assertThat(mDefaultButton.getAlpha()).isEqualTo(mDefaultButton.getSelectedAlpha());
    }

    @Test
    public void onSelected_doesNotHighlightWhenSelected_applyDefaultUnselectedAlpha() {
        mDefaultButton.setSelected(true);
        waitForIdleSync();

        assertThat(mDefaultButton.getIconAlpha()).isEqualTo(mDefaultButton.getUnselectedAlpha());
    }

    @Test
    public void onUnselected_highlightWhenSelected_applyDefaultUnselectedAlpha() {
        CarSystemBarButton highlightWhenSelectedButton = mTestView.findViewById(
                R.id.highlightable_no_more_button);
        highlightWhenSelectedButton.setSelected(false);
        waitForIdleSync();

        assertThat(highlightWhenSelectedButton.getIconAlpha()).isEqualTo(
                highlightWhenSelectedButton.getUnselectedAlpha());
    }

    @Test
    public void onSelected_highlightWhenSelected_applyDefaultSelectedAlpha() {
        CarSystemBarButton highlightWhenSelectedButton = mTestView.findViewById(
                R.id.highlightable_no_more_button);
        highlightWhenSelectedButton.setSelected(true);
        waitForIdleSync();

        assertThat(highlightWhenSelectedButton.getIconAlpha()).isEqualTo(
                highlightWhenSelectedButton.getSelectedAlpha());
    }

    @Test
    public void onSelected_doesNotShowMoreWhenSelected_doesNotShowMoreIcon() {
        mDefaultButton.setSelected(true);
        AlphaOptimizedImageView moreIcon = mDefaultButton.findViewById(
                R.id.car_nav_button_more_icon);
        waitForIdleSync();

        assertThat(moreIcon.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onSelected_showMoreWhenSelected_showsMoreIcon() {
        CarSystemBarButton showMoreWhenSelected = mTestView.findViewById(
                R.id.not_highlightable_more_button);
        showMoreWhenSelected.setSelected(true);
        AlphaOptimizedImageView moreIcon = showMoreWhenSelected.findViewById(
                R.id.car_nav_button_more_icon);
        waitForIdleSync();

        assertThat(moreIcon.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onUnselected_showMoreWhenSelected_doesNotShowMoreIcon() {
        CarSystemBarButton showMoreWhenSelected = mTestView.findViewById(
                R.id.highlightable_no_more_button);
        showMoreWhenSelected.setSelected(true);
        showMoreWhenSelected.setSelected(false);
        AlphaOptimizedImageView moreIcon = showMoreWhenSelected.findViewById(
                R.id.car_nav_button_more_icon);
        waitForIdleSync();

        assertThat(moreIcon.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onUnselected_withAppIcon_showsAppIcon() {
        CarSystemBarButton roleBasedButton = mTestView.findViewById(R.id.role_based_button);
        Drawable appIcon = getContext().getDrawable(R.drawable.ic_android);

        roleBasedButton.setAppIcon(appIcon);
        roleBasedButton.setSelected(false);
        waitForIdleSync();

        Drawable currentDrawable = ((AlphaOptimizedImageView) roleBasedButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();

        assertThat(currentDrawable).isEqualTo(appIcon);
    }

    @Test
    public void onUnselected_withAppIcon_applyUnselectedAlpha() {
        CarSystemBarButton roleBasedButton = mTestView.findViewById(R.id.role_based_button);

        roleBasedButton.setAppIcon(getContext().getDrawable(R.drawable.ic_android));
        roleBasedButton.setSelected(false);
        waitForIdleSync();

        assertThat(roleBasedButton.getIconAlpha()).isEqualTo(roleBasedButton.getUnselectedAlpha());
    }

    @Test
    public void onSelected_withAppIcon_showsAppIcon() {
        CarSystemBarButton roleBasedButton = mTestView.findViewById(R.id.role_based_button);
        Drawable appIcon = getContext().getDrawable(R.drawable.ic_android);

        roleBasedButton.setSelected(true);
        roleBasedButton.setAppIcon(appIcon);
        waitForIdleSync();

        Drawable currentDrawable = ((AlphaOptimizedImageView) roleBasedButton.findViewById(
                R.id.car_nav_button_icon_image)).getDrawable();

        assertThat(currentDrawable).isEqualTo(appIcon);
    }

    @Test
    public void onSelected_withAppIcon_applySelectedAlpha() {
        CarSystemBarButton roleBasedButton = mTestView.findViewById(R.id.role_based_button);

        roleBasedButton.setSelected(true);
        roleBasedButton.setAppIcon(getContext().getDrawable(R.drawable.ic_android));
        waitForIdleSync();

        assertThat(roleBasedButton.getIconAlpha()).isEqualTo(roleBasedButton.getSelectedAlpha());
    }

    @Test
    public void onClick_launchesIntentActivity() {
        mDefaultButton.performClick();

        CarSystemBarButton dialerButton = mTestView.findViewById(R.id.dialer_activity);
        dialerButton.performClick();
        waitForIdleSync();

        assertThat(getCurrentActivityName()).isEqualTo(DIALER_BUTTON_ACTIVITY_NAME);
    }

    @Test
    public void onLongClick_longIntentDefined_launchesLongIntentActivity() {
        mDefaultButton.performClick();
        waitForIdleSync();

        CarSystemBarButton dialerButton = mTestView.findViewById(
                R.id.long_click_dialer_activity);
        dialerButton.performLongClick();

        assertThat(getCurrentActivityName()).isEqualTo(DIALER_BUTTON_ACTIVITY_NAME);
    }

    @Test
    public void onClick_useBroadcast_broadcastsIntent() {
        CarSystemBarButton button = mTestView.findViewById(R.id.broadcast);
        reset(mContext);

        button.performClick();
        waitForIdleSync();

        verify(mContext).sendBroadcastAsUser(argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent argument) {
                return argument.getAction().equals(BROADCAST_ACTION_NAME);
            }
        }), any());
    }

    @Test
    public void onClick_requestBackstackClear_clearBackStack() {
        CarSystemBarButton dialerButton =
                mTestView.findViewById(R.id.dialer_activity_clear_backstack);

        dialerButton.performClick();

        verify(mActivityManager).moveTaskToFront(anyInt(), anyInt());
    }

    @Test
    public void onClick_useBroadcast_requestBackstackClear_doesNotClearingBackstack() {
        CarSystemBarButton button =
                mTestView.findViewById(R.id.broadcast_try_clear_backstack);

        button.performClick();

        verify(mActivityManager, never()).moveTaskToFront(anyInt(), anyInt());
    }

    @Test
    public void onSetUnseen_hasUnseen_showsUnseenIndicator() {
        mDefaultButton.setUnseen(true);
        ImageView hasUnseenIndicator = mDefaultButton.findViewById(R.id.car_nav_button_unseen_icon);
        waitForIdleSync();

        assertThat(hasUnseenIndicator.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onSetUnseen_doesNotHaveUnseen_hidesUnseenIndicator() {
        mDefaultButton.setUnseen(false);
        ImageView hasUnseenIndicator = mDefaultButton.findViewById(R.id.car_nav_button_unseen_icon);
        waitForIdleSync();

        assertThat(hasUnseenIndicator.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onSetDisabled_enabled_refreshIconAlpha() {
        mDefaultButton.setDisabled(false, /* runnable= */ null);

        assertThat(mDefaultButton.getAlpha()).isEqualTo(mDefaultButton.getSelectedAlpha());
    }

    @Test
    public void onSetDisabled_disdabledAlpha() {
        mDefaultButton.setDisabled(true, /* runnable= */ null);

        assertThat(mDefaultButton.getIconAlpha()).isEqualTo(mDefaultButton.getDisabledAlpha());
    }

    @Test
    public void onSetDisabled_nullRunnable_doesNotSendBroadcast() {
        mDefaultButton.setDisabled(true, /* runnable= */ null);

        mDefaultButton.performClick();

        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), any());
    }

    @Test
    public void onSetDisabled_runnable() {
        Runnable mockRunnable = mock(Runnable.class);
        mDefaultButton.setDisabled(true, /* runnable= */ mockRunnable);

        mDefaultButton.performClick();

        verify(mockRunnable).run();
    }

    /**
     * Returns the name of the first activity running in fullscreen mode (i.e. excludes things
     * like TaskViews).
     */
    private String getCurrentActivityName() {
        try {
            ActivityTaskManager.RootTaskInfo rootTaskInfo =
                    ActivityTaskManager.getService().getRootTaskInfoOnDisplay(
                            WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_UNDEFINED,
                            mContext.getDisplayId());
            if (rootTaskInfo == null || rootTaskInfo.topActivity == null) {
                return null;
            }
            return rootTaskInfo.topActivity.flattenToShortString();
        } catch (RemoteException e) {
            return null;
        }
    }
}

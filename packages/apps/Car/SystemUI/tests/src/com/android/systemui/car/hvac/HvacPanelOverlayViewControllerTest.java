/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.hvac;

import static com.android.systemui.car.window.OverlayPanelViewController.OVERLAY_FROM_BOTTOM_BAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.InOrderImpl;

import java.util.Collections;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class HvacPanelOverlayViewControllerTest extends SysuiTestCase {
    HvacPanelOverlayViewController mHvacPanelOverlayViewController;

    @Mock
    HvacController mHvacController;
    @Mock
    OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private FlingAnimationUtils.Builder mFlingAnimationUtilsBuilder;
    @Mock
    private FlingAnimationUtils mFlingAnimationUtils;
    @Mock
    CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    ConfigurationController mConfigurationController;
    @Mock
    UiModeManager mUiModeManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mFlingAnimationUtilsBuilder.setMaxLengthSeconds(anyFloat())).thenReturn(
                mFlingAnimationUtilsBuilder);
        when(mFlingAnimationUtilsBuilder.setSpeedUpFactor(anyFloat())).thenReturn(
                mFlingAnimationUtilsBuilder);
        when(mFlingAnimationUtilsBuilder.build()).thenReturn(mFlingAnimationUtils);

        mHvacPanelOverlayViewController = new HvacPanelOverlayViewController(
                mContext, getContext().getOrCreateTestableResources().getResources(),
                mHvacController, mOverlayViewGlobalStateController, mFlingAnimationUtilsBuilder,
                mCarDeviceProvisionedController, mConfigurationController, mUiModeManager);
    }

    @Test
    public void onScroll_updateDim() {
        int height = 100;
        View mockLayout = mock(View.class);
        when(mockLayout.getHeight()).thenReturn(height);
        mHvacPanelOverlayViewController.setLayout(mockLayout);
        mHvacPanelOverlayViewController.setOverlayDirection(OVERLAY_FROM_BOTTOM_BAR);

        mHvacPanelOverlayViewController.onScroll(50);

        verify(mOverlayViewGlobalStateController).updateWindowDimBehind(
                eq(mHvacPanelOverlayViewController), anyFloat());
    }

    @Test
    public void onConfigChanged_oldHVACViewRemoved_newHVACViewAdded() {
        Configuration config = new Configuration();
        config.uiMode = Configuration.UI_MODE_NIGHT_YES;
        int mockIndex = 3;
        View mockLayout = mock(View.class);
        HvacPanelView mockHvacPanelView = mock(HvacPanelView.class);
        ViewGroup mockHvacPanelParentView = mock(ViewGroup.class);
        when(mockHvacPanelParentView.indexOfChild(mockHvacPanelView)).thenReturn(mockIndex);
        when(mockHvacPanelParentView.generateLayoutParams(any())).thenReturn(
                mock(ViewGroup.LayoutParams.class));
        when(mockHvacPanelView.getParent()).thenReturn(mockHvacPanelParentView);
        when(mockHvacPanelView.getLayoutParams()).thenReturn(mock(ViewGroup.LayoutParams.class));
        when(mockHvacPanelView.findViewById(R.id.hvac_temperature_text)).thenReturn(
                mock(TextView.class));
        when(mockLayout.findViewById(R.id.hvac_panel)).thenReturn(mockHvacPanelView);
        mHvacPanelOverlayViewController.setLayout(mockLayout);

        mHvacPanelOverlayViewController.onConfigChanged(config);

        InOrder inOrder = new InOrderImpl(Collections.singletonList(mockHvacPanelParentView));
        inOrder.verify(mockHvacPanelParentView).removeView(mockHvacPanelView);
        inOrder.verify(mockHvacPanelParentView).addView(
                argThat(view -> view.hashCode() != mockHvacPanelView.hashCode()),
                eq(mockIndex));
    }
}

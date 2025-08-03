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

package com.android.systemui.car.statusicon.ui;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.lifecycle.InstantTaskExecutorRule;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class QuickControlsEntryPointsControllerTest extends SysuiTestCase {
    private QuickControlsEntryPointsController mQuickControlsEntryPointsController;

    @Mock
    private Map mIconControllerCreators;
    @Mock
    private Provider<StatusIconPanelViewController.Builder> mPanelControllerBuilderProvider;
    @Mock
    private StatusIconPanelViewController.Builder mPanelControllerBuilder;
    @Mock
    private StatusIconPanelViewController mPanelController;
    @Mock
    Provider<StatusIconController> mProvider;
    @Mock
    private StatusIconController mStatusIconController;
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mPanelControllerBuilderProvider.get()).thenReturn(mPanelControllerBuilder);
        setupBuilderMocks();
        mQuickControlsEntryPointsController = new QuickControlsEntryPointsController(
                mContext,
                mContext.getOrCreateTestableResources().getResources(),
                mIconControllerCreators,
                mPanelControllerBuilderProvider);
    }

    @Test
    public void getClassNameOfSelectedView_getsSelectedView() {
        String selectedClassName = "selectedClassName";
        View selectedView = mock(View.class);
        when(selectedView.isSelected()).thenReturn(true);
        Map<String, View> statusIconViewClassMap = Map.of("className", mock(View.class),
                selectedClassName, selectedView);
        mQuickControlsEntryPointsController.setStatusIconViewClassMap(statusIconViewClassMap);

        String resultClassName = mQuickControlsEntryPointsController.getClassNameOfSelectedView();

        assertThat(resultClassName).isEqualTo(selectedClassName);
    }

    @Test
    public void getStatusIconControllersStringArray_getArray() {
        int resultValue = mQuickControlsEntryPointsController.getStatusIconControllersStringArray();

        assertThat(resultValue).isEqualTo(R.array.config_quickControlsEntryPointIconControllers);
    }

    @Test
    public void getButtonViewLayout_getButton() {
        int resultValue = mQuickControlsEntryPointsController.getButtonViewLayout(false);

        assertThat(resultValue).isEqualTo(R.layout.car_qc_entry_points_button_horizontal);
    }

    @Test
    public void addIconViews_qcEntryPointContainerAdded_addViewAfterGettingShowAsDropDown() {
        doReturn(mStatusIconController).when(mProvider).get();
        doReturn(mProvider).when(mIconControllerCreators).get(any());
        ViewGroup baseLayout = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.car_bottom_system_bar_test, /* root= */ null);
        ViewGroup qcEntryPointContainer = baseLayout.findViewById(R.id.qc_entry_points_container);
        spyOn(qcEntryPointContainer);

        mQuickControlsEntryPointsController.addIconViews(qcEntryPointContainer,
                /* shouldAttachPanel= */ true);

        verify((QuickControlsEntryPointContainer) qcEntryPointContainer, times(3)).showAsDropDown();
        verify(qcEntryPointContainer, times(3)).addView(any());
    }

    private void setupBuilderMocks() {
        when(mPanelControllerBuilder.setXOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setYOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setGravity(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setDisabledWhileDriving(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.setShowAsDropDown(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.build(any(), anyInt(), anyInt())).thenReturn(mPanelController);
    }
}

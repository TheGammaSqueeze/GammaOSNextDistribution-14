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

package com.android.systemui.car.systembar;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CarSystemBarViewFactoryTest extends SysuiTestCase {
    private CarSystemBarViewFactory mCarSystemBarViewFactory;

    @Mock
    QuickControlsEntryPointsController mQuickControlsEntryPointsController;
    @Mock
    View mStatusIconView;
    @Mock
    private CarSystemBarElementInitializer mCarSystemBarElementInitializer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mQuickControlsEntryPointsController.getViewFromClassName("testClsName"))
                .thenReturn(mStatusIconView);
        mCarSystemBarViewFactory = new CarSystemBarViewFactory(mContext,
                mock(FeatureFlags.class), mQuickControlsEntryPointsController,
                mock(UserTracker.class), mCarSystemBarElementInitializer);
    }

    @Test
    public void callQuickControlsOnClickFromClass_callOnClick() {
        mCarSystemBarViewFactory.callQuickControlsOnClickFromClassName("testClsName");

        verify(mStatusIconView, times(1)).callOnClick();
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class QuickControlsEntryPointContainerTest extends SysuiTestCase {
    private QuickControlsEntryPointContainer mQuickControlsEntryPointContainer;

    private ViewGroup mLayout;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.car_bottom_system_bar_test, /* root= */ null, false);

        mQuickControlsEntryPointContainer = mLayout.findViewById(R.id.qc_entry_points_container);
    }

    @Test
    public void showAsDropDown_returnFalse() {
        boolean returnValue = mQuickControlsEntryPointContainer.showAsDropDown();

        assertThat(returnValue).isFalse();
    }

    @Test
    public void getPanelGravity_returnValueIsNotZero() {
        int returnValue = mQuickControlsEntryPointContainer.getPanelGravity();

        assertThat(returnValue).isNotEqualTo(0);
    }
}

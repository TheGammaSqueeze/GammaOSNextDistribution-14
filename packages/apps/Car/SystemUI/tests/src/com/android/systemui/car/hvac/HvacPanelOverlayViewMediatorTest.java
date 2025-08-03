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

package com.android.systemui.car.hvac;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class HvacPanelOverlayViewMediatorTest extends SysuiTestCase {

    private HvacPanelOverlayViewMediator mHvacPanelOverlayViewMediator;

    @Mock
    private Context mContext;
    @Mock
    private CarSystemBarController mCarSystemBarController;
    @Mock
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private UserTracker mUserTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHvacPanelOverlayViewMediator = new HvacPanelOverlayViewMediator(mContext,
                mCarSystemBarController, mHvacPanelOverlayViewController, mBroadcastDispatcher,
                mUserTracker);
    }

    @Test
    public void closeSystemDialogsIntent_hvacPanelIsExpanded_togglesHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(true);
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController).toggle();
    }

    @Test
    public void closeSystemDialogsIntent_hvacPanelIsNotExpanded_doesNotToggleHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(false);
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController, never()).toggle();
    }

    @Test
    public void notCloseSystemDialogsIntent_hvacPanelIsExpanded_doesNotToggleHvacPanel() {
        when(mHvacPanelOverlayViewController.isPanelExpanded()).thenReturn(true);
        Intent intent = new Intent();

        mHvacPanelOverlayViewMediator.mBroadcastReceiver.onReceive(getContext(), intent);

        verify(mHvacPanelOverlayViewController, never()).toggle();
    }
}

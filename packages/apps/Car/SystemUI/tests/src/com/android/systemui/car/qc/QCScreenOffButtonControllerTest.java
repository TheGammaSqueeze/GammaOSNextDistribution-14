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

package com.android.systemui.car.qc;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class QCScreenOffButtonControllerTest extends SysuiTestCase {
    private QCFooterView mView;
    private QCScreenOffButtonController mController;

    @Mock
    private Car mCar;
    @Mock
    private CarPowerManager mCarPowerManager;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(mContext);
        mView = spy(new QCFooterView(mContext));
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        mController = new QCScreenOffButtonController(mView, mDisableController,
                mStateController, mContext, mUserTracker, mCarServiceProvider);
        mController.init();

        when(mCar.getCarManager(CarPowerManager.class)).thenReturn(mCarPowerManager);
        attachCarPowerManager();
    }

    @Test
    public void onPowerButtonClicked_setDisplayPowerState() {
        mView.callOnClick();

        verify(mCarPowerManager).setDisplayPowerState(anyInt(), eq(false));
    }

    private void attachCarPowerManager() {
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> captor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        mController.onViewAttached();
        verify(mCarServiceProvider).addListener(captor.capture());
        captor.getValue().onConnected(mCar);
    }
}

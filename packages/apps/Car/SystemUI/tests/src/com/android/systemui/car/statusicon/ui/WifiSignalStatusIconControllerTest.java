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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.WifiIndicators;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class WifiSignalStatusIconControllerTest extends SysuiTestCase {
    @Mock
    Resources mResources;
    @Mock
    NetworkController mNetworkController;
    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;

    private StatusIconView mView;
    private WifiSignalStatusIconController mWifiSignalStatusIconController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mView = new StatusIconView(mContext);
        mWifiSignalStatusIconController = new WifiSignalStatusIconController(mView,
                mDisableController, mStateController, mResources, mContext, mNetworkController);
    }

    @Test
    public void onAttached_registersNetworkCallback() {
        mWifiSignalStatusIconController.onViewAttached();
        verify(mNetworkController).addCallback(any());
    }

    @Test
    public void onDetached_unregistersNetworkCallback() {
        mWifiSignalStatusIconController.onViewAttached();

        mWifiSignalStatusIconController.onViewDetached();

        verify(mNetworkController).removeCallback(any());
    }

    @Test
    public void onUpdateStatus_updatesWifiIcon() {
        mWifiSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ true));

        assertThat(mWifiSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mWifiSignalStatusIconController.getWifiSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_showsWifiIndicatorIcon() {
        mWifiSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ true));

        verify(mResources).getDrawable(eq(R.drawable.icon), any());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_showsDisabledWifiIcon() {
        mWifiSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ false));

        verify(mResources).getDrawable(eq(R.drawable.ic_status_wifi_disabled), any());
    }

    private WifiIndicators getWifiIndicator(boolean enabled) {
        IconState iconState = new IconState(/* visible= */ true, R.drawable.icon,
                /* contentDescription= */ "");
        return new WifiIndicators(enabled, iconState, /* qsIcon= */ null, /* activityIn= */ false,
                /* activityOut= */ false, /* qsDescription=" */ "", /* isTransient= */ false,
                /* statusLabel= */ "");
    }
}

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

package com.android.systemui.car.statusicon.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.HotspotController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class SignalStatusIconControllerTest extends SysuiTestCase {

    @Mock
    Resources mResources;
    @Mock
    NetworkController mNetworkController;
    @Mock
    HotspotController mHotspotController;
    private SignalStatusIconController mSignalStatusIconController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSignalStatusIconController = new SignalStatusIconController(mContext, mResources,
                mNetworkController, mHotspotController);
    }

    @Test
    public void onInit_registersNetworkCallbacks() {
        verify(mNetworkController).addCallback(any());
        verify(mHotspotController).addCallback(any());
    }

    @Test
    public void onDestroy_unregistersNetworkCallbacks() {
        mSignalStatusIconController.onDestroy();
        verify(mNetworkController).removeCallback(any());
        verify(mHotspotController).removeCallback(any());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotDisabled_showsMobileDataIcon() {
        mSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ false));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getMobileSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotDisabled_showsWifiIcon() {
        mSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getWifiSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotEnabled_showsHotspotIcon() {
        mSignalStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ false));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotEnabled_showsHotspotIcon() {
        mSignalStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */  false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotEnabled_ethernetEnabled_showsHotspotIcon() {
        mSignalStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */ true));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotDisabled_ethernetEnabled_showsEthernetIcon() {
        mSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.setEthernetIndicators(
                getEthernetIndicator(/* enabled= */ true));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getEthernetIconDrawable());
    }

    private WifiIndicators getWifiIndicator(boolean enabled) {
        IconState iconState = new IconState(true, R.drawable.icon, "");
        return new WifiIndicators(enabled, iconState, null, false, false, "", false, "");
    }

    private IconState getEthernetIndicator(boolean enabled) {
        return new IconState(enabled, R.drawable.stat_sys_ethernet_fully, "");
    }
}

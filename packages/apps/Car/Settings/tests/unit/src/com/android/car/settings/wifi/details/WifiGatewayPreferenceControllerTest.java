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

package com.android.car.settings.wifi.details;

import static org.mockito.Mockito.when;

import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.RouteInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.net.InetAddress;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class WifiGatewayPreferenceControllerTest
        extends WifiDetailsBasePreferenceControllerTestCase {
    private static final String GATE_WAY = "gateway";

    private WifiGatewayPreferenceController mPreferenceController;

    @Mock
    private LinkProperties mMockLinkProperties;
    @Mock
    private RouteInfo mMockRouteInfo;
    @Mock
    private InetAddress mMockInetAddress;
    @Mock
    private IpPrefix mMockIpPrefix;

    @Override
    protected void initTest() {
        when(mMockWifiInfoProvider.getLinkProperties()).thenReturn(mMockLinkProperties);
    }

    @Override
    protected WifiDetailsBasePreferenceController<WifiDetailsPreference> getController() {
        if (mPreferenceController == null) {
            mPreferenceController = new WifiGatewayPreferenceController(mContext,
                    /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        }
        return mPreferenceController;
    }

    @Override
    protected void setUpConnectedState() {
        super.setUpConnectedState();
        when(mMockLinkProperties.getRoutes()).thenReturn(Arrays.asList(mMockRouteInfo));
        when(mMockRouteInfo.isDefaultRoute()).thenReturn(true);
        try {
            when(mMockRouteInfo.getDestination()).thenReturn(mMockIpPrefix);
            when(mMockIpPrefix.getAddress()).thenReturn(
                    InetAddress.getByAddress(new byte[]{4, 3, 2, 1}));
        } catch (Exception e) { }
        when(mMockRouteInfo.hasGateway()).thenReturn(true);
        when(mMockRouteInfo.getGateway()).thenReturn(mMockInetAddress);
        when(mMockInetAddress.getHostAddress()).thenReturn(GATE_WAY);
    }

    @Override
    protected String getDesiredDetailText() {
        return GATE_WAY;
    }
}

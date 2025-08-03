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

import android.net.LinkAddress;
import android.net.LinkProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class WifiSubnetPreferenceControllerTest
        extends WifiDetailsBasePreferenceControllerTestCase{

    private WifiSubnetPreferenceController mPreferenceController;

    @Mock
    private LinkProperties mMockLinkProperties;
    @Mock
    private LinkAddress mMockLinkAddress;

    @Override
    protected void initTest() {
        when(mMockWifiInfoProvider.getLinkProperties()).thenReturn(mMockLinkProperties);
        when(mMockLinkProperties.getLinkAddresses()).thenReturn(Collections.emptyList());
    }

    @Override
    protected WifiDetailsBasePreferenceController<WifiDetailsPreference> getController() {
        if (mPreferenceController == null) {
            mPreferenceController = new WifiSubnetPreferenceController(mContext,
                    /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        }
        return mPreferenceController;
    }

    @Override
    protected void setUpConnectedState() {
        super.setUpConnectedState();
        when(mMockLinkProperties.getLinkAddresses()).thenReturn(
                Collections.singletonList(mMockLinkAddress));
        InetAddress address = Inet4Address.LOOPBACK;
        when(mMockLinkAddress.getAddress()).thenReturn(address);
    }

    @Override
    protected String getDesiredDetailText() {
        return "0.0.0.0";
    }
}

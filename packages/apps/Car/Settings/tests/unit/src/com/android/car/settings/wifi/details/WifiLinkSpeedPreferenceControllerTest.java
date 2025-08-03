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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.net.wifi.WifiInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class WifiLinkSpeedPreferenceControllerTest
        extends WifiDetailsBasePreferenceControllerTestCase {
    private static final int VALID_LINK_SPEED = 100;

    private WifiLinkSpeedPreferenceController mPreferenceController;

    @Mock
    private WifiInfo mMockWifiInfo;

    @Override
    protected void initTest() {
        when(mMockWifiInfoProvider.getWifiInfo()).thenReturn(mMockWifiInfo);
    }

    @Override
    protected WifiDetailsBasePreferenceController<WifiDetailsPreference> getController() {
        if (mPreferenceController == null) {
            mPreferenceController = new WifiLinkSpeedPreferenceController(mContext,
                    /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        }
        return mPreferenceController;
    }

    @Override
    protected void setUpConnectedState() {
        super.setUpConnectedState();
        when(mMockWifiInfo.getLinkSpeed()).thenReturn(VALID_LINK_SPEED);
    }

    @Override
    protected String getDesiredDetailText() {
        return mContext.getString(R.string.link_speed, VALID_LINK_SPEED);
    }

    @Test
    public void onCreate_negativeLinkSpeed_preferenceNotVisible() {
        super.setUpConnectedState();
        int linkSpeed = -1;
        when(mMockWifiInfo.getLinkSpeed()).thenReturn(linkSpeed);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isVisible()).isFalse();
    }
}

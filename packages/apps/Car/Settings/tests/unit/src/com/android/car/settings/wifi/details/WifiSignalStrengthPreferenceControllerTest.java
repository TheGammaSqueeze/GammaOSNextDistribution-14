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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WifiSignalStrengthPreferenceControllerTest
        extends WifiDetailsBasePreferenceControllerTestCase {
    private static final int LEVEL = 1;

    private WifiSignalStrengthPreferenceController mPreferenceController;

    @Override
    protected WifiDetailsBasePreferenceController<WifiDetailsPreference> getController() {
        if (mPreferenceController == null) {
            mPreferenceController = new WifiSignalStrengthPreferenceController(mContext,
                    /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        }
        return mPreferenceController;
    }

    @Override
    protected void setUpConnectedState() {
        super.setUpConnectedState();
        when(mMockWifiEntry.getLevel()).thenReturn(LEVEL);
    }

    @Override
    protected String getDesiredDetailText() {
        return mContext.getResources().getStringArray(R.array.wifi_signals)[LEVEL];
    }
}

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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WifiSecurityPreferenceControllerTest
        extends WifiDetailsBasePreferenceControllerTestCase {
    private static final String TEST_SECURITY_STRING = "test_security";

    private WifiSecurityPreferenceController mPreferenceController;

    @Override
    protected WifiDetailsBasePreferenceController<WifiDetailsPreference> getController() {
        if (mPreferenceController == null) {
            mPreferenceController = new WifiSecurityPreferenceController(mContext,
                    /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        }
        return mPreferenceController;
    }

    @Override
    protected void setUpConnectedState() {
        super.setUpConnectedState();
        when(mMockWifiEntry.getSecurityString(anyBoolean())).thenReturn(TEST_SECURITY_STRING);
    }

    @Override
    protected String getDesiredDetailText() {
        return TEST_SECURITY_STRING;
    }
}

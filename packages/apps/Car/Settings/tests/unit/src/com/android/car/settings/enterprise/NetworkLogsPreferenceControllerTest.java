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
package com.android.car.settings.enterprise;

import android.text.format.DateUtils;

import androidx.preference.Preference;

import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class NetworkLogsPreferenceControllerTest
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private NetworkLogsPreferenceController mController;

    @Mock
    private Preference mPreference;

    @Before
    public void setUp() throws Exception {
        mController = new NetworkLogsPreferenceController(mSpiedContext, mPreferenceKey,
                mFragmentController, mUxRestrictions);
    }

    @Test
    public void testUpdateState_noLogs() {
        mockGetLastNetworkLogRetrievalTime(-1);

        mController.updateState(mPreference);

        verifyPreferenceTitleNeverSet(mPreference);
        verifyPreferenceSummarySet(mPreference, R.string.enterprise_privacy_none);
        verifyPreferenceIconNeverSet(mPreference);
    }

    @Test
    public void testUpdateState_withLogs() {
        long now = System.currentTimeMillis();
        mockGetLastNetworkLogRetrievalTime(now);

        mController.updateState(mPreference);

        verifyPreferenceTitleNeverSet(mPreference);
        String formattedDate = DateUtils.formatDateTime(mRealContext, now,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE);
        verifyPreferenceSummarySet(mPreference, formattedDate);
        verifyPreferenceIconNeverSet(mPreference);
    }
}

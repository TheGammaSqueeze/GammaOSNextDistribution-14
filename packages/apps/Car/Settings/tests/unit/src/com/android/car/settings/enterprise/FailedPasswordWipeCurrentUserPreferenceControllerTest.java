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

import static org.mockito.Mockito.when;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class FailedPasswordWipeCurrentUserPreferenceControllerTest
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private FailedPasswordWipeCurrentUserPreferenceController mController;

    @Mock
    private Preference mPreference;

    @Before
    public void setUp() throws Exception {
        mController = new FailedPasswordWipeCurrentUserPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions,
                mEnterprisePrivacyFeatureProvider);
    }

    @Test
    public void testGetAvailabilityStatus_maxNotSet() throws Exception {
        mockGetMaximumFailedPasswordsForWipe(0);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxNotSet_zoneWrite() throws Exception {
        mController.setAvailabilityStatusForZone("write");
        mockGetMaximumFailedPasswordsForWipe(0);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxNotSet_zoneRead() throws Exception {
        mController.setAvailabilityStatusForZone("read");
        mockGetMaximumFailedPasswordsForWipe(0);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxNotSet_zoneHidden() throws Exception {
        mController.setAvailabilityStatusForZone("hidden");
        mockGetMaximumFailedPasswordsForWipe(0);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxSet() throws Exception {
        mockGetMaximumFailedPasswordsForWipe(42);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxSet_zoneWrite() throws Exception {
        mController.setAvailabilityStatusForZone("write");
        mockGetMaximumFailedPasswordsForWipe(42);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_maxSet_zoneRead() throws Exception {
        mController.setAvailabilityStatusForZone("read");
        mockGetMaximumFailedPasswordsForWipe(42);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_maxSet_zoneHidden() throws Exception {
        mController.setAvailabilityStatusForZone("hidden");
        mockGetMaximumFailedPasswordsForWipe(42);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState_set() {
        mockGetMaximumFailedPasswordsForWipe(42);

        mController.updateState(mPreference);

        String expectedSummary = mRealContext.getResources().getQuantityString(
                R.plurals.enterprise_privacy_number_failed_password_wipe, 42, 42);
        verifyPreferenceTitleNeverSet(mPreference);
        verifyPreferenceSummarySet(mPreference, expectedSummary);
        verifyPreferenceIconNeverSet(mPreference);
    }

    private void mockGetMaximumFailedPasswordsForWipe(int max) {
        when(mEnterprisePrivacyFeatureProvider.getMaximumFailedPasswordsBeforeWipeInCurrentUser())
                .thenReturn(max);
    }
}

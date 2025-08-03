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

package com.android.car.settings.language;

import static android.os.UserManager.DISALLOW_CONFIG_LOCALE;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_HIDDEN;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_READ;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_WRITE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.internal.app.LocaleHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class LanguageSettingsEntryPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private LanguageSettingsEntryPreferenceController mController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mController = new LanguageSettingsEntryPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @Test
    public void onCreate_setsSummary() {
        mController.onCreate(mLifecycleOwner);

        Locale locale = mController.getConfiguredLocale();
        String summary = LocaleHelper.getDisplayName(locale, locale, /* sentenceCase= */ true);
        assertThat(mPreference.getSummary()).isEqualTo(summary);
    }

    @Test
    public void getAvailabilityStatus_noRestriction_zoneWrite_available() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, false);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_WRITE);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_zoneWrite_disabledForProfile() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, true);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_WRITE);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getDefaultAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_noRestriction_zoneRead_availableForViewing() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, false);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_READ);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_zoneRead_disabledForProfile() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, true);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_READ);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_noRestriction_zoneHidden_conditionallyUnavailable() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, false);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_HIDDEN);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_zoneHidden_disabledForProfile() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, DISALLOW_CONFIG_LOCALE, true);
        mController.setAvailabilityStatusForZone(PREF_AVAILABILITY_STATUS_HIDDEN);

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }
}

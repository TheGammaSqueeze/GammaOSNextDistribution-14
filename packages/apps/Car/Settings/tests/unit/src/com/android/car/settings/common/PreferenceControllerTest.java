/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.car.ui.preference.UxRestrictablePreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PreferenceControllerTest {

    private static final CarUxRestrictions LIMIT_STRINGS_UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH, /* timestamp= */
                    0).build();
    private static final CarUxRestrictions NO_SETUP_UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, /* timestamp= */ 0).build();

    private static final CarUxRestrictions BASELINE_UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
    private static final String PREFERENCE_KEY = "key";

    private LifecycleOwner mLifecycleOwner;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private FakePreferenceController mPreferenceController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CarUiPreference mPreference;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();

        MockitoAnnotations.initMocks(this);

        mPreferenceController = new FakePreferenceController(mContext, PREFERENCE_KEY,
                mFragmentController, BASELINE_UX_RESTRICTIONS);
    }

    @Test
    public void onUxRestrictionsChanged_restricted_preferenceRestricted() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        Mockito.reset(mPreference);
        mPreferenceController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        verify((UxRestrictablePreference) mPreference).setUxRestricted(true);
    }

    @Test
    public void onUxRestrictionsChanged_restricted_viewOnly_preferenceUnrestricted() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatus(AVAILABLE_FOR_VIEWING);
        mPreferenceController.onCreate(mLifecycleOwner);

        Mockito.reset(mPreference);
        mPreferenceController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        verify((UxRestrictablePreference) mPreference).setUxRestricted(false);
    }

    @Test
    @UiThreadTest
    public void onUxRestrictionsChanged_restricted_preferenceGroup_preferencesRestricted() {
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen preferenceScreen = preferenceManager.createPreferenceScreen(mContext);
        CarUiPreference preference1 = mock(CarUiPreference.class);
        CarUiPreference preference2 = mock(CarUiPreference.class);
        preferenceScreen.addPreference(preference1);
        preferenceScreen.addPreference(preference2);

        mPreferenceController.setPreference(preferenceScreen);
        mPreferenceController.onCreate(mLifecycleOwner);

        Mockito.reset(preference1);
        Mockito.reset(preference2);
        mPreferenceController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        verify((UxRestrictablePreference) preference1).setUxRestricted(true);
        verify((UxRestrictablePreference) preference2).setUxRestricted(true);
    }

    @Test
    public void shouldApplyUxRestrictions_baseline() {
        boolean result = mPreferenceController.shouldApplyUxRestrictions(BASELINE_UX_RESTRICTIONS);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldApplyUxRestrictions_isNoSetup() {
        mPreferenceController.setUxRestrictionsIgnoredConfig(false, new HashSet<String>());
        boolean result = mPreferenceController.shouldApplyUxRestrictions(NO_SETUP_UX_RESTRICTIONS);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldApplyUxRestrictions_containsKey() {
        Set prefsThatIgnore = new HashSet<String>();
        prefsThatIgnore.add(PREFERENCE_KEY);

        mPreferenceController.setUxRestrictionsIgnoredConfig(false, prefsThatIgnore);
        boolean result = mPreferenceController.shouldApplyUxRestrictions(NO_SETUP_UX_RESTRICTIONS);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldApplyUxRestrictions_not_containsKey() {
        Set prefsThatIgnore = new HashSet<String>();
        prefsThatIgnore.add("unknown key");

        mPreferenceController.setUxRestrictionsIgnoredConfig(false, prefsThatIgnore);
        boolean result = mPreferenceController.shouldApplyUxRestrictions(NO_SETUP_UX_RESTRICTIONS);
        assertThat(result).isTrue();
    }

    @Test
    public void shouldApplyUxRestrictions_allIgnore() {
        Set prefsThatIgnore = new HashSet<String>();
        prefsThatIgnore.add("unknown key");

        mPreferenceController.setUxRestrictionsIgnoredConfig(true, prefsThatIgnore);
        boolean result = mPreferenceController.shouldApplyUxRestrictions(NO_SETUP_UX_RESTRICTIONS);
        assertThat(result).isFalse();
    }

    @Test
    public void onCreate_unrestricted_disabled_preferenceUnrestricted() {
        mPreference.setEnabled(false);
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        verify((UxRestrictablePreference) mPreference).setUxRestricted(false);
    }

    @Test
    public void refreshUi_notCreated_doesNothing() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.refreshUi();

        verify(mPreference, never()).setVisible(anyBoolean());
        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_created_available_preferenceShownAndEnabled() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        Mockito.reset(mPreference);

        mPreferenceController.refreshUi();

        verify(mPreference).setVisible(true);
        verify(mPreference).setEnabled(true);
    }

    @Test
    public void refreshUi_created_availableForViewing_preferenceShownAndDisabled() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatus(AVAILABLE_FOR_VIEWING);
        mPreferenceController.onCreate(mLifecycleOwner);
        Mockito.reset(mPreference);

        mPreferenceController.refreshUi();

        verify(mPreference).setVisible(true);
        verify(mPreference).setEnabled(false);
    }

    @Test
    public void refreshUi_created_notAvailable_preferenceHidden() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatus(CONDITIONALLY_UNAVAILABLE);
        mPreferenceController.onCreate(mLifecycleOwner);
        Mockito.reset(mPreference);

        mPreferenceController.refreshUi();

        verify(mPreference).setVisible(false);
    }

    @Test
    public void refreshUi_created_available_updatesState() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.refreshUi();

        // onCreate, refreshUi.
        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(2);
        assertThat(mPreferenceController.getUpdateStateArg()).isEqualTo(mPreference);
    }

    @Test
    public void refreshUi_created_notAvailable_doesNotUpdateState() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatus(CONDITIONALLY_UNAVAILABLE);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.refreshUi();

        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(0);
    }


    @Test
    public void onUxRestrictionsChanged_created_available_updatesState() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.onUxRestrictionsChanged(LIMIT_STRINGS_UX_RESTRICTIONS);

        // onCreate, onUxRestrictionsChanged.
        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(2);
    }

    @Test
    public void onUxRestrictionsChanged_notCreated_available_doesNotUpdateState() {
        mPreferenceController.onUxRestrictionsChanged(LIMIT_STRINGS_UX_RESTRICTIONS);

        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(0);
    }

    @Test
    public void onCreate_available_updatesState() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(1);
    }

    @Test
    public void onStart_available_updatesState() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        // onCreate, onStart
        assertThat(mPreferenceController.getUpdateStateCallCount()).isEqualTo(2);
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneWrite() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        verify(mPreference).setEnabled(true);
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneRead() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        verify(mPreference).setEnabled(false);
    }
    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneHidden() {
        mPreferenceController.setPreference(mPreference);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    private static class FakePreferenceController extends
            PreferenceController<Preference> {

        private int mAvailabilityStatus;
        private int mUpdateStateCallCount;
        private Preference mUpdateStateArg;
        private boolean mAllIgnoresUxRestrictions = false;
        private Set<String> mPreferencesIgnoringUxRestrictions = new HashSet<>();

        FakePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
            mAvailabilityStatus = AVAILABLE;
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }

        @Override
        protected int getDefaultAvailabilityStatus() {
            return mAvailabilityStatus;
        }

        @Override
        protected void updateState(Preference preference) {
            mUpdateStateArg = preference;
            mUpdateStateCallCount++;
        }

        public void setAvailabilityStatus(int availabilityStatus) {
            mAvailabilityStatus = availabilityStatus;
        }

        public Preference getUpdateStateArg() {
            return mUpdateStateArg;
        }

        public int getUpdateStateCallCount() {
            return mUpdateStateCallCount;
        }

        public void setUxRestrictionsIgnoredConfig(boolean allIgnore, Set preferencesThatIgnore) {
            mAllIgnoresUxRestrictions = allIgnore;
            mPreferencesIgnoringUxRestrictions = preferencesThatIgnore;
        }

        @Override
        protected boolean isUxRestrictionsIgnored(boolean allIgnores, Set prefsThatIgnore) {
            return super.isUxRestrictionsIgnored(mAllIgnoresUxRestrictions,
                    mPreferencesIgnoringUxRestrictions);
        }
    }
}

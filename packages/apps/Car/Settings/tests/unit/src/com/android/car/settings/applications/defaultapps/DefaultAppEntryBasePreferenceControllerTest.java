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

package com.android.car.settings.applications.defaultapps;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.applications.DefaultAppInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DefaultAppEntryBasePreferenceControllerTest {
    private static final CharSequence TEST_LABEL = "Test Label";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private TestDefaultAppEntryBasePreferenceController mController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new CarUiPreference(mContext);
        mController = new TestDefaultAppEntryBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @Test
    public void refreshUi_hasDefaultAppWithLabel_summaryAndIconAreSet() {
        DefaultAppInfo defaultAppInfo = mock(DefaultAppInfo.class);
        when(defaultAppInfo.loadLabel()).thenReturn(TEST_LABEL);
        when(defaultAppInfo.loadIcon()).thenReturn(mContext.getDrawable(R.drawable.test_icon));
        mController.setCurrentDefaultAppInfo(defaultAppInfo);
        mController.onCreate(mLifecycleOwner);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(TEST_LABEL);
        assertThat(mPreference.getIcon()).isNotNull();
    }

    @Test
    public void refreshUi_hasDefaultAppWithoutLabel_summaryAndIconAreNotSet() {
        DefaultAppInfo defaultAppInfo = mock(DefaultAppInfo.class);
        when(defaultAppInfo.loadLabel()).thenReturn(null);
        when(defaultAppInfo.loadIcon()).thenReturn(null);
        mController.setCurrentDefaultAppInfo(defaultAppInfo);
        mController.onCreate(mLifecycleOwner);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.app_list_preference_none));
        assertThat(mPreference.getIcon()).isNull();
    }

    @Test
    public void refreshUi_hasNoDefaultApp_summaryAndIconAreNotSet() {
        mController.setCurrentDefaultAppInfo(null);
        mController.onCreate(mLifecycleOwner);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.app_list_preference_none));
        assertThat(mPreference.getIcon()).isNull();
    }

    private static class TestDefaultAppEntryBasePreferenceController extends
            DefaultAppEntryBasePreferenceController<Preference> {

        private DefaultAppInfo mDefaultAppInfo;

        TestDefaultAppEntryBasePreferenceController(Context context,
                String preferenceKey, FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }

        @Nullable
        @Override
        protected DefaultAppInfo getCurrentDefaultAppInfo() {
            return mDefaultAppInfo;
        }

        protected void setCurrentDefaultAppInfo(DefaultAppInfo defaultAppInfo) {
            mDefaultAppInfo = defaultAppInfo;
        }
    }
}

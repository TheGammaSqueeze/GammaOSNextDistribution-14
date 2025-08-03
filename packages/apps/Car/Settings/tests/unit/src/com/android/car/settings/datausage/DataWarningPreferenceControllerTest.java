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

package com.android.car.settings.datausage;

import static android.net.NetworkPolicy.WARNING_DISABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.NetworkTemplate;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DataWarningPreferenceControllerTest {

    private static final long BYTES_IN_GIGABYTE = 1024 * 1024 * 1024;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private TwoStatePreference mEnablePreference;
    private PreferenceGroup mPreferenceGroup;
    private Preference mWarningPreference;
    private DataWarningPreferenceController mPreferenceController;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private NetworkPolicyEditor mPolicyEditor;
    @Mock
    private NetworkTemplate mNetworkTemplate;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataWarningPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        mEnablePreference = new SwitchPreference(mContext);
        mEnablePreference.setKey(mContext.getString(R.string.pk_data_set_warning));
        mPreferenceGroup.addPreference(mEnablePreference);
        mWarningPreference = new Preference(mContext);
        mWarningPreference.setKey(mContext.getString(R.string.pk_data_warning));
        mPreferenceGroup.addPreference(mWarningPreference);

        mPreferenceController.setNetworkPolicyEditor(mPolicyEditor);
        mPreferenceController.setNetworkTemplate(mNetworkTemplate);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_warningDisabled_summaryEmpty() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mWarningPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_warningDisabled_preferenceDisabled() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mWarningPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_warningDisabled_switchUnchecked() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_warningEnabled_summaryPopulated() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mPreferenceController.refreshUi();

        assertThat(mWarningPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_warningEnabled_preferenceEnabled() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mPreferenceController.refreshUi();

        assertThat(mWarningPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_warningEnabled_switchChecked() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mPreferenceController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isTrue();
    }

    @Test
    public void onPreferenceChanged_toggleFalse_warningBytesDisabled() {
        mEnablePreference.callChangeListener(false);
        verify(mPolicyEditor).setPolicyWarningBytes(mNetworkTemplate, WARNING_DISABLED);
    }

    @Test
    public void onPreferenceChanged_toggleTrue_warningBytesNotDisabled() {
        mEnablePreference.callChangeListener(true);

        ArgumentCaptor<Long> setWarning = ArgumentCaptor.forClass(Long.class);
        verify(mPolicyEditor).setPolicyWarningBytes(eq(mNetworkTemplate), setWarning.capture());
        assertThat(setWarning.getValue()).isNotEqualTo(WARNING_DISABLED);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClicked_launchesFragment() {
        mWarningPreference.performClick();

        verify(mMockFragmentController).launchFragment(any(DataWarningSetThresholdFragment.class));
    }
}

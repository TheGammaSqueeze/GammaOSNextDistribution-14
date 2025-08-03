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

import static android.net.NetworkPolicy.LIMIT_DISABLED;

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
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DataLimitPreferenceControllerTest {

    private static final long GIB_IN_BYTES = 1024 * 1024 * 1024;
    private static final long EPSILON = 100;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private DataLimitPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreferenceGroup;
    private Preference mLimitPreference;
    private TwoStatePreference mEnablePreference;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private NetworkPolicyEditor mMockPolicyEditor;
    @Mock
    private NetworkTemplate mMockNetworkTemplate;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataLimitPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mEnablePreference = new SwitchPreference(mContext);
        mEnablePreference.setKey(mContext.getString(R.string.pk_data_set_limit));
        mPreferenceGroup.addPreference(mEnablePreference);
        mLimitPreference = new CarUiPreference(mContext);
        mLimitPreference.setKey(mContext.getString(R.string.pk_data_limit));
        mPreferenceGroup.addPreference(mLimitPreference);

        mPreferenceController.setNetworkPolicyEditor(mMockPolicyEditor);
        mPreferenceController.setNetworkTemplate(mMockNetworkTemplate);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_limitDisabled_summaryEmpty() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(LIMIT_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mLimitPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_limitDisabled_preferenceDisabled() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(LIMIT_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mLimitPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_limitDisabled_switchUnchecked() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(LIMIT_DISABLED);
        mPreferenceController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_limitEnabled_summaryPopulated() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(5 * GIB_IN_BYTES);
        mPreferenceController.refreshUi();

        assertThat(mLimitPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_limitEnabled_preferenceEnabled() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(5 * GIB_IN_BYTES);
        mPreferenceController.refreshUi();

        assertThat(mLimitPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_limitEnabled_switchChecked() {
        when(mMockPolicyEditor.getPolicyLimitBytes(mMockNetworkTemplate))
                .thenReturn(5 * GIB_IN_BYTES);
        mPreferenceController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isTrue();
    }

    @Test
    public void onPreferenceChanged_toggleFalse_limitBytesDisabled() {
        mEnablePreference.callChangeListener(false);
        verify(mMockPolicyEditor).setPolicyLimitBytes(mMockNetworkTemplate, LIMIT_DISABLED);
    }

    @Test
    public void onPreferenceChanged_toggleTrue_showsDialog() {
        mEnablePreference.callChangeListener(true);

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void onDialogConfirm_noWarningThreshold_setsLimitTo5GB() {
        mPreferenceController.onConfirm(null);

        verify(mMockPolicyEditor).setPolicyLimitBytes(mMockNetworkTemplate, 5 * GIB_IN_BYTES);
    }

    @Test
    public void onDialogConfirm_hasWarningThreshold_setsLimitToWithMultiplier() {
        when(mMockPolicyEditor.getPolicyWarningBytes(mMockNetworkTemplate))
                .thenReturn(5 * GIB_IN_BYTES);
        mPreferenceController.onConfirm(null);

        ArgumentCaptor<Long> setLimit = ArgumentCaptor.forClass(Long.class);
        verify(mMockPolicyEditor).setPolicyLimitBytes(eq(mMockNetworkTemplate), setLimit.capture());

        long setValue = setLimit.getValue();
        // Due to precision errors, add and subtract a small epsilon.
        assertThat(setValue).isGreaterThan(
                (long) (5 * GIB_IN_BYTES * DataLimitPreferenceController.LIMIT_BYTES_MULTIPLIER)
                        - EPSILON);
        assertThat(setValue).isLessThan(
                (long) (5 * GIB_IN_BYTES * DataLimitPreferenceController.LIMIT_BYTES_MULTIPLIER)
                        + EPSILON);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClicked_launchesFragment() {
        mLimitPreference.performClick();

        verify(mMockFragmentController).launchFragment(any(DataLimitSetThresholdFragment.class));
    }
}

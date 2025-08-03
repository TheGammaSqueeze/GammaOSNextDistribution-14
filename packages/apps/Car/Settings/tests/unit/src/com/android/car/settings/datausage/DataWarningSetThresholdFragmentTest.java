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

import static com.android.car.settings.datausage.DataWarningSetThresholdFragment.MIB_IN_BYTES;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.NetworkTemplate;

import androidx.fragment.app.FragmentManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DataWarningSetThresholdFragmentTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private DataWarningSetThresholdFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;

    @Mock
    private NetworkPolicyEditor mMockNetworkPolicyEditor;
    @Mock
    private NetworkTemplate mMockNetworkTemplate;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    @UiThreadTest
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
    }

    @Test
    public void onActivityCreated_titleIsSet() {
        ToolbarController toolbar = mActivity.getToolbar();
        assertThat(toolbar.getTitle().toString()).isEqualTo(
                mContext.getString(R.string.data_usage_warning_editor_title));
    }

    @Test
    public void onActivityCreated_saveButtonClicked_savesThreshold() {
        mFragment.onSave(MIB_IN_BYTES);

        verify(mMockNetworkPolicyEditor).setPolicyWarningBytes(mMockNetworkTemplate, MIB_IN_BYTES);
    }

    private void setUpFragment() throws Throwable {
        String dataUsageSetThresholdFragmentTag = "data_usage_set_threshold_fragment";
        DataWarningSetThresholdFragment fragment = DataWarningSetThresholdFragment.newInstance(
                mMockNetworkTemplate);
        fragment.mPolicyEditor = mMockNetworkPolicyEditor;
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment, dataUsageSetThresholdFragmentTag)
                    .commitNow();
        });
        mFragment = spy((DataWarningSetThresholdFragment) mFragmentManager
                .findFragmentByTag(dataUsageSetThresholdFragmentTag));
    }
}

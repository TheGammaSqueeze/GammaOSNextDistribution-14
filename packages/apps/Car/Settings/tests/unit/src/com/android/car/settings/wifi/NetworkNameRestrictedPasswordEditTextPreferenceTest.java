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

package com.android.car.settings.wifi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.testutils.SinglePaneTestActivity;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class NetworkNameRestrictedPasswordEditTextPreferenceTest {
    private static final String KEY = "test_key";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private FragmentManager mFragmentManager;
    private NetworkNameRestrictedPasswordEditTextPreference mPreference;
    private MockitoSession mSession;

    // Using single pane to inherit initial fragment.
    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Mock
    private Toast mMockToast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(Toast.class, withSettings().lenient())
                .startMocking();
        when(Toast.makeText(any(), anyInt(), anyInt())).thenReturn(mMockToast);

        SettingsFragment fragment = (SettingsFragment) mFragmentManager.findFragmentById(
                R.id.fragment_container);

        mPreference = new NetworkNameRestrictedPasswordEditTextPreference(mContext);
        mPreference.setKey(KEY);
        fragment.getPreferenceScreen().addPreference(mPreference);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void performClick_noName_toastShown() {
        mPreference.performClick();

        ExtendedMockito.verify(
                () -> Toast.makeText(any(),
                        eq(R.string.wifi_no_network_name),
                        anyInt()));
        verify(mMockToast).show();
    }

    @Test
    public void performClick_hasName_showsDialog() {
        ExtendedMockito.spyOn(mPreference);
        PreferenceManager preferenceManager = mock(PreferenceManager.class);
        when(mPreference.getPreferenceManager()).thenReturn(preferenceManager);
        mPreference.setNetworkName("test_name");
        mPreference.performClick();

        verify(preferenceManager).showDialog(mPreference);
    }
}

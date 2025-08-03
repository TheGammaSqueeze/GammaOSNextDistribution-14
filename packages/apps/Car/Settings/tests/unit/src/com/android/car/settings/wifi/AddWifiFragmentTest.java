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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AddWifiFragmentTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private AddWifiFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MockitoSession mSession;

    @Mock
    private LocalBroadcastManager mMockLocalBroadcastManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBroadcastManager.class, withSettings().lenient()).startMocking();
        ExtendedMockito.when(LocalBroadcastManager.getInstance(any())).thenReturn(
                mMockLocalBroadcastManager);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    @UiThreadTest
    public void onCreate_registersReceivers() {
        mFragment.onCreate(null);
        ArgumentCaptor<IntentFilter> argumentCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);
        verify(mMockLocalBroadcastManager, times(2))
                .registerReceiver(any(), argumentCaptor.capture());

        List<IntentFilter> capturedIntentFilters = argumentCaptor.getAllValues();
        assertThat(capturedIntentFilters.get(0).hasAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isTrue();
        assertThat(capturedIntentFilters.get(1).hasAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isTrue();
    }

    @Test
    @UiThreadTest
    public void onDestroy_unregistersReceivers() {
        mFragment.onCreate(null);
        Mockito.reset(mMockLocalBroadcastManager);
        mFragment.onDestroy();

        verify(mMockLocalBroadcastManager, times(2))
                .unregisterReceiver(any());
    }

    @Test
    @UiThreadTest
    public void initialState_buttonDisabled() {
        mFragment.onCreate(null);
        mFragment.onStart();
        assertThat(mFragment.getToolbarMenuItems().get(0).isEnabled()).isFalse();
    }

    @Test
    @UiThreadTest
    public void receiveNameChangeIntent_emptyName_buttonDisabled() {
        mFragment.onCreate(null);
        mFragment.onStart();

        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "");
        mFragment.mNameChangeReceiver.onReceive(mContext, intent);

        assertThat(mFragment.getToolbarMenuItems().get(0).isEnabled()).isFalse();
    }

    @Test
    @UiThreadTest
    public void receiveNameChangeIntent_name_buttonEnabled() {
        mFragment.onCreate(null);
        mFragment.onStart();

        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "test_network_name");
        mFragment.mNameChangeReceiver.onReceive(mContext, intent);

        assertThat(mFragment.getToolbarMenuItems().get(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void receiveSecurityChangeIntent_nameSet_buttonDisabled() {
        mFragment.onCreate(null);
        mFragment.onStart();
        Intent nameIntent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        nameIntent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "test_network_name");
        mFragment.mNameChangeReceiver.onReceive(mContext, nameIntent);

        Intent securityIntent = new Intent(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        securityIntent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiEntry.SECURITY_PSK);
        mFragment.mSecurityChangeReceiver.onReceive(mContext, securityIntent);

        assertThat(mFragment.getToolbarMenuItems().get(0).isEnabled()).isFalse();
    }

    private void setUpFragment() throws Throwable {
        String addWifiFragmentTag = "add_wifi_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new AddWifiFragment(), addWifiFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (AddWifiFragment) mFragmentManager.findFragmentByTag(addWifiFragmentTag);
    }
}

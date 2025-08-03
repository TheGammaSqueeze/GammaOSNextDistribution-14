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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NetworkSecurityPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private ListPreference mListPreference;
    private NetworkSecurityPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private LocalBroadcastManager mMockLocalBroadcastManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mListPreference = new ListPreference(mContext);
        mPreferenceController = new NetworkSecurityPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mListPreference);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBroadcastManager.class, withSettings().lenient()).startMocking();
        when(LocalBroadcastManager.getInstance(any())).thenReturn(
                mMockLocalBroadcastManager);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void handlePreferenceChanged_unsecureNetwork_summaryUpdated() {
        String value = Integer.toString(WifiEntry.SECURITY_NONE);
        mListPreference.callChangeListener(value);

        assertThat(mListPreference.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.wifi_security_none));
    }

    @Test
    public void handlePreferenceChanged_pskNetwork_summaryUpdated() {
        String value = Integer.toString(WifiEntry.SECURITY_PSK);
        mListPreference.callChangeListener(value);

        assertThat(mListPreference.getSummary().toString()).isEqualTo(
                mContext.getString(R.string.wifi_security_psk_generic));
    }

    @Test
    public void handlePreferenceChanged_broadcastIsSent() {
        String value = Integer.toString(WifiEntry.SECURITY_PSK);
        mListPreference.callChangeListener(value);

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(
                Intent.class);
        verify(mMockLocalBroadcastManager).sendBroadcastSync(argumentCaptor.capture());
        List<Intent> intents = argumentCaptor.getAllValues();
        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).getAction()).isEqualTo(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        assertThat(intents.get(0).getIntExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiEntry.SECURITY_NONE)).isEqualTo(Integer.parseInt(value));
    }
}

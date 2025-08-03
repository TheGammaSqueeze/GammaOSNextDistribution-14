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

package com.android.car.settings.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import com.google.android.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MobileNetworkListPreferenceControllerTest {

    private static final int SUB_ID = 1;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private MobileNetworkListPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private SubscriptionManager mSubscriptionManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mPreferenceController = new MobileNetworkListPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
    }

    @Test
    public void onCreate_containsElements() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, /* cardString= */"", "mncString");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    @UiThreadTest
    public void onPreferenceClicked_launchesFragment() {
        SubscriptionInfo info = createSubscriptionInfo(SUB_ID, /* simSlotIndex= */ 1,
                /* cardString= */"", "mncString");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);
        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<MobileNetworkFragment> captor = ArgumentCaptor.forClass(
                MobileNetworkFragment.class);
        verify(mFragmentController).launchFragment(captor.capture());

        assertThat(captor.getValue().getArguments().getInt(MobileNetworkFragment.ARG_NETWORK_SUB_ID,
                -1)).isEqualTo(SUB_ID);
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, int simSlotIndex,
            String cardString, String mncString) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                simSlotIndex, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", mncString,
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, cardString);
        return subInfo;
    }
}

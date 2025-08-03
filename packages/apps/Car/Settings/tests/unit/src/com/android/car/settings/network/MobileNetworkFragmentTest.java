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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.ToolbarController;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class MobileNetworkFragmentTest {
    private static final int SUB_ID = 1;
    private static final String TEST_NAME = "Test Name";
    private static SubscriptionManager sMockSubscriptionManager = mock(SubscriptionManager.class);

    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;

    @Mock
    private static MobileNetworkUpdateManager.PhoneChangeReceiver sMockPhoneChangeReceiver;
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        when(sMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(
                Arrays.asList(
                        createSubscriptionInfo(SUB_ID + 1, TEST_NAME + "_1"),
                        createSubscriptionInfo(SUB_ID + 2, TEST_NAME + "_2")));
    }

    @After
    public void tearDown() {
        Mockito.reset(sMockSubscriptionManager);
    }

    @Test
    public void onMobileNetworkUpdated_startWithArgument_updateTitle() throws Throwable {
        setUpFragmentWithSubId(SUB_ID, TEST_NAME);

        ToolbarController toolbar = mActivity.getToolbar();
        assertThat(toolbar.getTitle()).isEqualTo(TEST_NAME);
    }

    @Test
    public void onMobileNetworkUpdated_noArgumentProvided_updateTitle() throws Throwable {
        setUpFragment();
        when(sMockSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(
                Collections.singletonList(createSubscriptionInfo(SUB_ID, TEST_NAME)));

        ToolbarController toolbar = mActivity.getToolbar();
        assertThat(toolbar.getTitle()).isEqualTo(TEST_NAME + "_1");
    }

    private void setUpFragment() throws Throwable {
        String mobileNetworkFragmentTag = "mobile_network_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new TestMobileNetworkFragment(),
                            mobileNetworkFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void setUpFragmentWithSubId(int subId, String name) throws Throwable {
        SubscriptionInfo info = createSubscriptionInfo(subId, name);
        when(sMockSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(
                Collections.singletonList(info));

        String mobileNetworkFragmentTag = "mobile_network_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TestMobileNetworkFragment.newInstance(subId),
                            mobileNetworkFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, String name) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ name, /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }

    public static class TestMobileNetworkFragment extends MobileNetworkFragment {
        public static MobileNetworkFragment newInstance(int subId) {
            MobileNetworkFragment fragment = new TestMobileNetworkFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_NETWORK_SUB_ID, subId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        SubscriptionManager getSubscriptionManager(Context context) {
            return sMockSubscriptionManager;
        }

        @Override
        MobileNetworkUpdateManager getMobileNetworkUpdateManager(Context context, int subId) {
            return new MobileNetworkUpdateManager(subId, sMockSubscriptionManager,
                    sMockPhoneChangeReceiver);
        }
    }
}

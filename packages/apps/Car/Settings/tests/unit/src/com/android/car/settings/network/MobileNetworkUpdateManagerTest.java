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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.testutils.BaseCarSettingsTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MobileNetworkUpdateManagerTest {

    private static final int SUB_ID = 1;

    private BaseCarSettingsTestActivity mActivity;
    private MobileNetworkUpdateManager mMobileNetworkUpdateManager;

    @Mock
    private MobileNetworkUpdateManager.MobileNetworkUpdateListener mMockMobileNetworkUpdateListener;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private MobileNetworkUpdateManager.PhoneChangeReceiver mMockPhoneChangeReceiver;
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
    }

    @Test
    @UiThreadTest
    public void onStart_receiverRegistered() {
        setupMobileNetworkUpdateManager(SUB_ID);

        verify(mMockPhoneChangeReceiver).register();
    }

    @Test
    @UiThreadTest
    public void onStop_receiverUnregistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        mMobileNetworkUpdateManager.onStop(mActivity);

        verify(mMockPhoneChangeReceiver).unregister();
    }

    @Test
    @UiThreadTest
    public void onStart_subscriptionListenerRegistered() {
        setupMobileNetworkUpdateManager(SUB_ID);

        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(any());
    }

    @Test
    @UiThreadTest
    public void onStop_subscriptionListenerUnregistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        mMobileNetworkUpdateManager.onStop(mActivity);

        verify(mMockSubscriptionManager).removeOnSubscriptionsChangedListener(any());
    }

    @Test
    @UiThreadTest
    public void onMobileNetworkUpdated_withInitialSubId_pickSubId() {
        setupMobileNetworkUpdateManager(SUB_ID);
        List<SubscriptionInfo> newSubscriptionInfoList = List.of(createSubscriptionInfo(SUB_ID + 1),
                createSubscriptionInfo(SUB_ID + 2));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList(true)).thenReturn(
                newSubscriptionInfoList);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(
                newSubscriptionInfoList);
        mMobileNetworkUpdateManager.mOnSubscriptionsChangeListener.onSubscriptionsChanged();

        // Ensure SubId doesn't change. There are three statements because using any() causes a
        // runtime exception.
        verify(mMockMobileNetworkUpdateListener, never()).onMobileNetworkUpdated(SUB_ID);
        verify(mMockMobileNetworkUpdateListener, never()).onMobileNetworkUpdated(SUB_ID + 1);
        verify(mMockMobileNetworkUpdateListener, never()).onMobileNetworkUpdated(SUB_ID + 2);
    }

    @Test
    @UiThreadTest
    public void onMobileNetworkUpdated_withoutInitialSubId_pickDefaultSubId() {
        setupMobileNetworkUpdateManager(MobileNetworkUpdateManager.SUB_ID_NULL);
        List<SubscriptionInfo> newSubscriptionInfoList = List.of(createSubscriptionInfo(SUB_ID + 1),
                createSubscriptionInfo(SUB_ID + 2));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList(true)).thenReturn(
                newSubscriptionInfoList);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(
                newSubscriptionInfoList);
        mMobileNetworkUpdateManager.mOnSubscriptionsChangeListener.onSubscriptionsChanged();

        verify(mMockMobileNetworkUpdateListener).onMobileNetworkUpdated(SUB_ID + 1);
    }

    private void setupMobileNetworkUpdateManager(int subId) {
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Collections.singletonList(createSubscriptionInfo(subId)));
        when(mMockSubscriptionManager.getSelectableSubscriptionInfoList())
                .thenReturn(Collections.singletonList(createSubscriptionInfo(subId)));
        mMobileNetworkUpdateManager = new MobileNetworkUpdateManager(subId,
                mMockSubscriptionManager, mMockPhoneChangeReceiver);
        mActivity.getLifecycle().addObserver(mMobileNetworkUpdateManager);
        mMobileNetworkUpdateManager.registerListener(mMockMobileNetworkUpdateListener);
    }

    private SubscriptionInfo createSubscriptionInfo(int subId) {
        SubscriptionInfo subInfo = new SubscriptionInfo(/* id= */ subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }
}

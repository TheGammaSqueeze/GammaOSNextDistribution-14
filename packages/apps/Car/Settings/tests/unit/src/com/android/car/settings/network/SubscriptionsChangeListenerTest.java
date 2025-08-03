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

import static com.android.car.settings.network.SubscriptionsChangeListener.RADIO_TECH_CHANGED_FILTER;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class SubscriptionsChangeListenerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SubscriptionsChangeListener mSubscriptionsChangeListener;

    @Mock
    private SubscriptionsChangeListener.SubscriptionsChangeAction mSubscriptionsChangeAction;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        mSubscriptionsChangeListener = new SubscriptionsChangeListener(mContext,
                mSubscriptionsChangeAction);
    }

    @Test
    public void start_registersListener() {
        verify(mMockSubscriptionManager, never()).addOnSubscriptionsChangedListener(
                mSubscriptionsChangeListener.mSubscriptionsChangedListener);
        mSubscriptionsChangeListener.start();

        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                mSubscriptionsChangeListener.mSubscriptionsChangedListener);
    }

    @Test
    public void onSubscriptionChange_triggersAction() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.mSubscriptionsChangedListener.onSubscriptionsChanged();

        verify(mSubscriptionsChangeAction).onSubscriptionsChanged();
    }

    @Test
    public void stop_unregistersListener() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.stop();

        verify(mMockSubscriptionManager).removeOnSubscriptionsChangedListener(
                mSubscriptionsChangeListener.mSubscriptionsChangedListener);
    }

    @Test
    public void start_registersReceiver() {
        mSubscriptionsChangeListener.start();

        verify(mContext).registerReceiver(any(), eq(RADIO_TECH_CHANGED_FILTER));
    }

    @Test
    public void onReceive_triggersAction() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.mRadioTechChangeReceiver.onReceive(mContext, new Intent());

        verify(mSubscriptionsChangeAction).onSubscriptionsChanged();
    }

    @Test
    public void stop_unregistersReceiver() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.stop();

        verify(mContext).unregisterReceiver(any());
    }
}

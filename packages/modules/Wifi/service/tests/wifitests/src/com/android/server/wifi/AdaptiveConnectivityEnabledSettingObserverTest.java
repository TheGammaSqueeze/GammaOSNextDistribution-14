/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.AdaptiveConnectivityEnabledSettingObserver.SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.proto.nano.WifiMetricsProto;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@SmallTest
public class AdaptiveConnectivityEnabledSettingObserverTest extends WifiBaseTest {

    private AdaptiveConnectivityEnabledSettingObserver mAdaptiveConnectivityEnabledSettingObserver;

    private TestLooper mLooper;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private Context mContext;

    @Captor private ArgumentCaptor<ContentObserver> mContentObserverCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();
        Handler handler = new Handler(mLooper.getLooper());

        mAdaptiveConnectivityEnabledSettingObserver =
                new AdaptiveConnectivityEnabledSettingObserver(
                        handler, mWifiMetrics, mFrameworkFacade, mContext);

        // True pre-initialization
        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isTrue();
    }

    @Test
    public void initiallyTrue_adaptiveConnectivityToggled_getCorrectValue() throws Exception {
        // initialize to true
        when(mFrameworkFacade.getSecureIntegerSetting(
                eq(mContext), eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(1);

        mAdaptiveConnectivityEnabledSettingObserver.initialize();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isTrue();
        verify(mFrameworkFacade).registerContentObserver(
                eq(mContext), any(), anyBoolean(), mContentObserverCaptor.capture());
        verify(mWifiMetrics).setAdaptiveConnectivityState(true);

        // set to false
        when(mFrameworkFacade.getSecureIntegerSetting(eq(mContext),
                eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(0);

        mContentObserverCaptor.getValue().onChange(false);
        mLooper.dispatchAll();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isFalse();
        verify(mWifiMetrics).setAdaptiveConnectivityState(false);
        verify(mWifiMetrics).logUserActionEvent(
                WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_OFF);

        // set to true again
        when(mFrameworkFacade.getSecureIntegerSetting(eq(mContext),
                eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(1);

        mContentObserverCaptor.getValue().onChange(false);
        mLooper.dispatchAll();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isTrue();
        verify(mWifiMetrics, times(2)).setAdaptiveConnectivityState(true);
        verify(mWifiMetrics).logUserActionEvent(
                WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_ON);
    }

    @Test
    public void initiallyFalse_adaptiveConnectivityToggled_getCorrectValue() throws Exception {
        // initialize to false
        when(mFrameworkFacade.getSecureIntegerSetting(eq(mContext),
                eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(0);

        mAdaptiveConnectivityEnabledSettingObserver.initialize();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isFalse();
        verify(mFrameworkFacade).registerContentObserver(
                eq(mContext), any(), anyBoolean(), mContentObserverCaptor.capture());
        verify(mWifiMetrics).setAdaptiveConnectivityState(false);

        // set to true
        when(mFrameworkFacade.getSecureIntegerSetting(eq(mContext),
                eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(1);

        mContentObserverCaptor.getValue().onChange(false);
        mLooper.dispatchAll();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isTrue();
        verify(mWifiMetrics).setAdaptiveConnectivityState(true);
        verify(mWifiMetrics).logUserActionEvent(
                WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_ON);

        // set to false
        when(mFrameworkFacade.getSecureIntegerSetting(eq(mContext),
                eq(SETTINGS_SECURE_ADAPTIVE_CONNECTIVITY_ENABLED), anyInt()))
                .thenReturn(0);

        mContentObserverCaptor.getValue().onChange(false);
        mLooper.dispatchAll();

        assertThat(mAdaptiveConnectivityEnabledSettingObserver.get()).isFalse();
        verify(mWifiMetrics, times(2)).setAdaptiveConnectivityState(false);
        verify(mWifiMetrics).logUserActionEvent(
                WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_OFF);
    }
}

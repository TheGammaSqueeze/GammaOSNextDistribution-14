/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.server.wifi.WifiCarrierInfoManager.ANONYMOUS_IDENTITY;
import static com.android.server.wifi.WifiPseudonymManager.RETRY_INTERVALS_FOR_CONNECTION_ERROR;
import static com.android.server.wifi.WifiPseudonymManager.RETRY_INTERVALS_FOR_SERVER_ERROR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Looper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.entitlement.CarrierSpecificServiceEntitlement;
import com.android.server.wifi.entitlement.PseudonymInfo;
import com.android.server.wifi.hotspot2.PasspointManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Unit tests for {@link WifiPseudonymManager}.
 */
@SmallTest
public class WifiPseudonymManagerTest extends WifiBaseTest {
    private static final int CARRIER_ID = 1;
    private static final int WRONG_CARRIER_ID = 99;
    private static final String PSEUDONYM = "pseudonym";
    private static final int SUB_ID = 2;
    private static final String IMSI = "imsi";
    private static final String DECORATED_PSEUDONYM = PSEUDONYM + "@";
    private static final String MCCMNC = "mccmnc";

    // Same as PseudonymInfo.DEFAULT_PSEUDONYM_TTL_IN_MILLIS
    private static final long DEFAULT_PSEUDONYM_TTL_IN_MILLIS = Duration.ofDays(2).toMillis();

    @Mock
    private WifiContext mWifiContext;
    @Mock
    private WifiInjector mWifiInjector;
    @Mock
    private WifiConfiguration mWifiConfiguration;
    @Mock
    private WifiEnterpriseConfig mEnterpriseConfig;
    @Mock
    private WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock
    private WifiConfigManager mWifiConfigManager;
    @Mock
    private NetworkUpdateResult mNetworkUpdateResult;
    @Mock
    private PasspointManager mPasspointManager;
    @Mock
    private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock
    Clock mClock;
    @Mock AlarmManager mAlarmManager;
    final ArgumentCaptor<WifiPseudonymManager.RetrieveListener> mRetrieveListenerArgumentCaptor =
            ArgumentCaptor.forClass(WifiPseudonymManager.RetrieveListener.class);
    final ArgumentCaptor<Integer> mAlarmTypeCaptor = ArgumentCaptor.forClass(Integer.class);
    final ArgumentCaptor<Long> mWindowStartCaptor = ArgumentCaptor.forClass(Long.class);
    final ArgumentCaptor<Long> mWindowLengthCaptor = ArgumentCaptor.forClass(Long.class);

    private WifiPseudonymManager mWifiPseudonymManager;
    @Mock private Looper mLooper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mClock.getWallClockMillis()).thenReturn(Instant.now().toEpochMilli());
        when(mWifiInjector.getWifiCarrierInfoManager()).thenReturn(mWifiCarrierInfoManager);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getPasspointManager()).thenReturn(mPasspointManager);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiCarrierInfoManager.getSimInfo(anyInt())).thenReturn(
                new WifiCarrierInfoManager.SimInfo(IMSI, MCCMNC, CARRIER_ID, CARRIER_ID));
        mWifiPseudonymManager =
                new WifiPseudonymManager(
                        mWifiContext, mWifiInjector, mClock, mAlarmManager, mLooper);
    }

    @Test
    public void getValidPseudonymInfo_byDefault_empty() {
        assertTrue(mWifiPseudonymManager.getValidPseudonymInfo(CARRIER_ID).isEmpty());
    }

    @Test
    public void getValidPseudonymInfo_expiredPseudonym_empty() {
        setAnExpiredPseudonym(mWifiPseudonymManager);
        assertTrue(mWifiPseudonymManager.getValidPseudonymInfo(WRONG_CARRIER_ID).isEmpty());
    }

    @Test
    public void getValidPseudonymInfo_wrongCarrierId_empty() {
        setAFreshPseudonym(mWifiPseudonymManager);
        assertTrue(mWifiPseudonymManager.getValidPseudonymInfo(WRONG_CARRIER_ID).isEmpty());
    }

    @Test
    public void getValidPseudonymInfo_freshPseudonym_present() {
        setAFreshPseudonym(mWifiPseudonymManager);
        assertTrue(mWifiPseudonymManager.getValidPseudonymInfo(CARRIER_ID).isPresent());
    }

    @Test
    public void retrievePseudonymOnFailureTimeout_expiredPseudonym_scheduleRefresh() {
        long nowTime = Instant.now().toEpochMilli();
        when(mClock.getWallClockMillis()).thenReturn(nowTime);
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        mWifiPseudonymManager.mLastFailureTimestampArray.put(CARRIER_ID,
                nowTime - Duration.ofDays(7).toMillis());

        mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(CARRIER_ID);

        verify(mAlarmManager, times(1))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        assertEquals(AlarmManager.RTC_WAKEUP, mAlarmTypeCaptor.getValue().intValue());
        long maxStartTime =
                nowTime
                        + WifiPseudonymManager.TEN_SECONDS_IN_MILLIS
                        + Duration.ofSeconds(1).toMillis();
        assertTrue(mWindowStartCaptor.getValue().longValue() <= maxStartTime);
        assertEquals(
                WifiPseudonymManager.TEN_MINUTES_IN_MILLIS,
                mWindowLengthCaptor.getValue().longValue());
        assertEquals(CARRIER_ID, mRetrieveListenerArgumentCaptor.getValue().mCarrierId);
    }

    @Test
    public void retrievePseudonymOnFailureTimeout_noPseudonym_notScheduleRefresh() {
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        mWifiPseudonymManager.retrievePseudonymOnFailureTimeoutExpired(CARRIER_ID);
        verifyNoMoreInteractions(mAlarmManager);
    }

    @Test
    public void setInBandPseudonymInfo_noPseudonym_empty() {
        mWifiPseudonymManager.setInBandPseudonym(CARRIER_ID, PSEUDONYM);
        Optional<PseudonymInfo> pseudonymInfoOptional =
                mWifiPseudonymManager.getValidPseudonymInfo(CARRIER_ID);
        assertTrue(pseudonymInfoOptional.isEmpty());
    }

    @Test
    public void setInBandPseudonymInfo_expiredPseudonym_present() {
        setAnExpiredPseudonym(mWifiPseudonymManager);

        mWifiPseudonymManager.setInBandPseudonym(CARRIER_ID, PSEUDONYM);
        Optional<PseudonymInfo> pseudonymInfoOptional =
                mWifiPseudonymManager.getValidPseudonymInfo(CARRIER_ID);
        assertTrue(pseudonymInfoOptional.isPresent());
        assertEquals(PSEUDONYM, pseudonymInfoOptional.get().getPseudonym());
    }

    @Test
    public void updateWifiConfiguration_expiredPseudonym_noUpdate() {
        mWifiConfiguration.enterpriseConfig = mEnterpriseConfig;
        mWifiConfiguration.carrierId = CARRIER_ID;
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        when(mEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        setAnExpiredPseudonym(mWifiPseudonymManager);

        mWifiPseudonymManager.updateWifiConfiguration(mWifiConfiguration);

        verify(mWifiCarrierInfoManager, never()).decoratePseudonymWith3GppRealm(any(), anyString());
    }

    @Test
    public void updateWifiConfiguration_freshPseudonymAndPasspoint_update() {
        mWifiConfiguration.enterpriseConfig = mEnterpriseConfig;
        mWifiConfiguration.carrierId = CARRIER_ID;
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        when(mEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        when(mWifiConfiguration.isPasspoint()).thenReturn(true);
        // Make sure that there is one PseudonymInfo in WifiPseudonymManager
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID,
                new PseudonymInfo(PSEUDONYM, IMSI));
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(mWifiConfiguration, PSEUDONYM))
                .thenReturn(DECORATED_PSEUDONYM);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(mNetworkUpdateResult);
        when(mEnterpriseConfig.getAnonymousIdentity()).thenReturn(ANONYMOUS_IDENTITY + "@");

        mWifiPseudonymManager.updateWifiConfiguration(
                mWifiConfiguration);

        verify(mEnterpriseConfig).setAnonymousIdentity(DECORATED_PSEUDONYM);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
        verify(mPasspointManager).setAnonymousIdentity(any());
        verify(mWifiNetworkSuggestionsManager, never()).setAnonymousIdentity(any());
    }

    @Test
    public void updateWifiConfiguration_freshPseudonymAndNonPasspointNetworkSuggestion_update() {
        mWifiConfiguration.enterpriseConfig = mEnterpriseConfig;
        mWifiConfiguration.carrierId = CARRIER_ID;
        mWifiConfiguration.fromWifiNetworkSuggestion = true;
        when(mWifiConfiguration.isPasspoint()).thenReturn(false);
        // Make sure that there is one PseudonymInfo in WifiPseudonymManager
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID,
                new PseudonymInfo(PSEUDONYM, IMSI));
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        when(mEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(mWifiConfiguration, PSEUDONYM))
                .thenReturn(DECORATED_PSEUDONYM);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(mNetworkUpdateResult);
        when(mEnterpriseConfig.getAnonymousIdentity()).thenReturn(ANONYMOUS_IDENTITY + "@");
        mWifiPseudonymManager.updateWifiConfiguration(
                mWifiConfiguration);

        verify(mEnterpriseConfig).setAnonymousIdentity(DECORATED_PSEUDONYM);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
        verify(mPasspointManager, never()).setAnonymousIdentity(any());
        verify(mWifiNetworkSuggestionsManager).setAnonymousIdentity(any());
    }

    @Test
    public void updateWifiConfiguration_freshPseudonym_update() {
        mWifiConfiguration.enterpriseConfig = mEnterpriseConfig;
        mWifiConfiguration.carrierId = CARRIER_ID;
        mWifiConfiguration.fromWifiNetworkSuggestion = false;
        when(mWifiConfiguration.isPasspoint()).thenReturn(false);

        // Make sure that there is one PseudonymInfo in WifiPseudonymManager
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID,
                new PseudonymInfo(PSEUDONYM, IMSI));
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        when(mEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(mWifiConfiguration, PSEUDONYM))
                .thenReturn(DECORATED_PSEUDONYM);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(mNetworkUpdateResult);
        when(mEnterpriseConfig.getAnonymousIdentity()).thenReturn(ANONYMOUS_IDENTITY + "@");

        mWifiPseudonymManager.updateWifiConfiguration(
                mWifiConfiguration);

        verify(mEnterpriseConfig).setAnonymousIdentity(DECORATED_PSEUDONYM);
        verify(mWifiConfigManager).addOrUpdateNetwork(any(), anyInt());
        verify(mPasspointManager, never()).setAnonymousIdentity(any());
        verify(mWifiNetworkSuggestionsManager, never()).setAnonymousIdentity(any());
    }

    @Test
    public void updateWifiConfiguration_freshPseudonymWithDecoratedIdentity_noUpdate() {
        mWifiConfiguration.enterpriseConfig = mEnterpriseConfig;
        mWifiConfiguration.carrierId = CARRIER_ID;

        // Make sure that there is one PseudonymInfo in WifiPseudonymManager
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID,
                new PseudonymInfo(PSEUDONYM, IMSI));
        when(mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(CARRIER_ID)).thenReturn(true);
        when(mEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        when(mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(mWifiConfiguration, PSEUDONYM))
                .thenReturn(DECORATED_PSEUDONYM);
        when(mWifiConfigManager.addOrUpdateNetwork(any(), anyInt()))
                .thenReturn(mNetworkUpdateResult);
        when(mEnterpriseConfig.getAnonymousIdentity()).thenReturn(DECORATED_PSEUDONYM);

        mWifiPseudonymManager.updateWifiConfiguration(
                mWifiConfiguration);

        verify(mEnterpriseConfig, never()).setAnonymousIdentity(any());
    }

    @Test
    public void setPseudonymAndScheduleRefresh_freshPseudonym_schedule() {
        PseudonymInfo pseudonymInfo = new PseudonymInfo(PSEUDONYM, IMSI);
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID, pseudonymInfo);
        verify(mAlarmManager, times(1))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        assertEquals(AlarmManager.RTC_WAKEUP, mAlarmTypeCaptor.getValue().intValue());
        long maxStartTime = Instant.now().toEpochMilli() + pseudonymInfo.getLttrInMillis();
        assertTrue(mWindowStartCaptor.getValue().longValue() <= maxStartTime);
        assertEquals(CARRIER_ID, mRetrieveListenerArgumentCaptor.getValue().mCarrierId);
        assertTrue(mWifiPseudonymManager.getValidPseudonymInfo(CARRIER_ID).isPresent());
    }

    @Test
    public void retrieveOobPseudonymIfNeeded_noPseudonym_scheduleRefresh() {
        mWifiPseudonymManager.retrieveOobPseudonymIfNeeded(CARRIER_ID);

        verify(mAlarmManager, times(1))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        assertEquals(AlarmManager.RTC_WAKEUP, mAlarmTypeCaptor.getValue().intValue());
        long maxStartTime = Instant.now().toEpochMilli();
        assertTrue("The difference is:" + (mWindowStartCaptor.getValue() - maxStartTime),
                mWindowStartCaptor.getValue() <= maxStartTime);
        assertEquals(CARRIER_ID, mRetrieveListenerArgumentCaptor.getValue().mCarrierId);
    }

    @Test
    public void retrieveOobPseudonymIfNeeded_freshPseudonym_scheduleRefresh() {
        setAFreshPseudonym(mWifiPseudonymManager);
        mWifiPseudonymManager.retrieveOobPseudonymIfNeeded(CARRIER_ID);

        verify(mAlarmManager, times(2))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        assertEquals(AlarmManager.RTC_WAKEUP, mAlarmTypeCaptor.getValue().intValue());
        long maxStartTime =
                Instant.now().toEpochMilli()
                        + mWifiPseudonymManager
                                .getValidPseudonymInfo(CARRIER_ID)
                                .get()
                                .getLttrInMillis();
        assertTrue("The difference is:" + (mWindowStartCaptor.getValue() - maxStartTime),
                mWindowStartCaptor.getValue() <= maxStartTime + 2);
        assertEquals(CARRIER_ID, mRetrieveListenerArgumentCaptor.getValue().mCarrierId);
    }

    @Test
    public void retrieveOobPseudonymIfNeeded_expiredPseudonym_scheduleRefresh() {
        when(mWifiCarrierInfoManager.getMatchingSubId(CARRIER_ID)).thenReturn(SUB_ID);
        setAnExpiredPseudonym(mWifiPseudonymManager);
        when(mWifiCarrierInfoManager.getMatchingSubId(CARRIER_ID)).thenReturn(SUB_ID);
        mWifiPseudonymManager.retrieveOobPseudonymIfNeeded(CARRIER_ID);

        verify(mAlarmManager, times(2))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        assertEquals(AlarmManager.RTC_WAKEUP, mAlarmTypeCaptor.getValue().intValue());
        long maxStartTime = Instant.now().toEpochMilli();
        assertTrue("The difference is:" + (mWindowStartCaptor.getValue() - maxStartTime),
                mWindowStartCaptor.getValue() <= maxStartTime);
        assertEquals(CARRIER_ID, mRetrieveListenerArgumentCaptor.getValue().mCarrierId);
    }

    @Test
    public void retrieveOobPseudonymWithRateLimit_expiredPseudonym_scheduleRefresh() {
        setAnExpiredPseudonym(mWifiPseudonymManager);
        mWifiPseudonymManager.retrieveOobPseudonymWithRateLimit(CARRIER_ID);
        verify(mAlarmManager, times(2))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
    }

    @Test
    public void retrieveOobPseudonymWithRateLimit_emptyPseudonym_noScheduleRefresh() {
        mWifiPseudonymManager.retrieveOobPseudonymWithRateLimit(CARRIER_ID);
        verify(mAlarmManager, never())
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
    }

    @Test
    public void retrieveOobPseudonymWithRateLimit_freshPseudonym_noScheduleRefresh() {
        mWifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID,
                new PseudonymInfo(PSEUDONYM, IMSI));
        verify(mAlarmManager, times(1))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        mWifiPseudonymManager.retrieveOobPseudonymWithRateLimit(CARRIER_ID);
        verifyNoMoreInteractions(mAlarmManager);
    }

    @Test
    public void mRetrieveCallback_transientFailureConnectionError_scheduleRetry() {
        // Return failure more than the MAX_RETRY_TIMES(RETRY_INTERVALS_FOR_CONNECTION_ERROR.length)
        for (int retryCount = 0;
                retryCount < (RETRY_INTERVALS_FOR_CONNECTION_ERROR.length * 2);
                retryCount++) {
            mWifiPseudonymManager.mRetrieveCallback.onFailure(
                    CARRIER_ID,
                    CarrierSpecificServiceEntitlement.REASON_TRANSIENT_FAILURE,
                    "Server error");
        }

        /*
         * Verify that the device only retries
         * MAX_RETRY_TIMES(RETRY_INTERVALS_FOR_CONNECTION_ERROR.length)
         */
        verify(mAlarmManager, times(RETRY_INTERVALS_FOR_CONNECTION_ERROR.length))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        long maxStartTime =
                Instant.now().toEpochMilli()
                        + RETRY_INTERVALS_FOR_SERVER_ERROR[
                                RETRY_INTERVALS_FOR_CONNECTION_ERROR.length - 1];
        assertTrue(mWindowStartCaptor.getValue().longValue() <= maxStartTime);
    }

    @Test
    public void mRetrieveCallback_transientFailureServerError_scheduleRetry() {
        // Return failure more than the MAX_RETRY_TIMES(RETRY_INTERVALS_FOR_SERVER_ERROR.length).
        for (int retryCount = 0;
                retryCount < (RETRY_INTERVALS_FOR_SERVER_ERROR.length * 2);
                retryCount++) {
            mWifiPseudonymManager.mRetrieveCallback.onFailure(
                    CARRIER_ID,
                    CarrierSpecificServiceEntitlement.REASON_TRANSIENT_FAILURE,
                    "Server error");
        }

        /*
         * Verify that the device only retries
         * MAX_RETRY_TIMES(RETRY_INTERVALS_FOR_SERVER_ERROR.length)
         */
        verify(mAlarmManager, times(RETRY_INTERVALS_FOR_SERVER_ERROR.length))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        long maxStartTime =
                Instant.now().toEpochMilli()
                        + RETRY_INTERVALS_FOR_SERVER_ERROR[
                                RETRY_INTERVALS_FOR_SERVER_ERROR.length - 1];
        assertTrue(mWindowStartCaptor.getValue().longValue() <= maxStartTime);
    }

    @Test
    public void mRetrieveCallback_nonTransientFailure_noScheduleRetry() {
        mWifiPseudonymManager.mRetrieveCallback.onFailure(
                CARRIER_ID,
                CarrierSpecificServiceEntitlement.REASON_NON_TRANSIENT_FAILURE,
                "Authentication failed");
        verify(mAlarmManager, never())
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
    }

    @Test
    public void mRetrieveCallback_success_scheduleRefresh() {
        PseudonymInfo pseudonymInfo = new PseudonymInfo(PSEUDONYM, IMSI,
                Duration.ofDays(2).toMillis(), Instant.now().toEpochMilli());
        mWifiPseudonymManager.mRetrieveCallback.onSuccess(CARRIER_ID, pseudonymInfo);

        verify(mAlarmManager, times(1))
                .setWindow(
                        mAlarmTypeCaptor.capture(),
                        mWindowStartCaptor.capture(),
                        mWindowLengthCaptor.capture(),
                        any(),
                        mRetrieveListenerArgumentCaptor.capture(),
                        any());
        long maxStartTime = Instant.now().toEpochMilli() + pseudonymInfo.getLttrInMillis();
        assertTrue(mWindowStartCaptor.getValue().longValue() <= maxStartTime);
    }
    private void setAnExpiredPseudonym(WifiPseudonymManager wifiPseudonymManager) {
        long ttl = Duration.ofDays(2).toMillis();
        PseudonymInfo pseudonymInfo = new PseudonymInfo(PSEUDONYM, IMSI, ttl,
                Instant.now().toEpochMilli() - ttl);
        wifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID, pseudonymInfo);
    }

    private void setAFreshPseudonym(WifiPseudonymManager wifiPseudonymManager) {
        PseudonymInfo pseudonymInfo =
                new PseudonymInfo(
                        PSEUDONYM,
                        IMSI,
                        DEFAULT_PSEUDONYM_TTL_IN_MILLIS,
                        Instant.now().toEpochMilli());
        wifiPseudonymManager.setPseudonymAndScheduleRefresh(CARRIER_ID, pseudonymInfo);
    }
}

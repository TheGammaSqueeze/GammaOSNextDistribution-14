/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.stats;

import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplTest.sCallerMetadata;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertTrue;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class AdsRelevanceExecutionLoggerFactoryTest {
    private MockitoSession mStaticMockSession = null;

    private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mAdServicesLoggerMock = Mockito.spy(AdServicesLoggerImpl.getInstance());
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testPersistAdSelectionResultAdsRelevanceExecutionLogger_telemetryEnabled() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsEnabled(),
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerImpl);
    }

    @Test
    public void testPersistAdSelectionResultAdsRelevanceExecutionLogger_telemetryDisabled() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsDisabled(),
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerNoLoggingImpl);
    }

    @Test
    public void testGetAdSelectionDataAdsRelevanceExecutionLogger_telemetryEnabled() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsEnabled(),
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerImpl);
    }

    @Test
    public void testGetAdSelectionDataAdsRelevanceExecutionLogger_telemetryDisabled() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsDisabled(),
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerNoLoggingImpl);
    }

    @Test
    public void testUpdateSignalsAdsRelevanceExecutionLogger() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        FlagsFactory.getFlagsForTest(),
                        AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerImpl);
    }

    @Test
    public void testUnknownApiAdsRelevanceExecutionLogger() {
        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        FlagsFactory.getFlagsForTest(),
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
        assertTrue(adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger()
                instanceof AdsRelevanceExecutionLoggerNoLoggingImpl);
    }

    private static class FlagsWithGetFledgeAuctionServerApiUsageMetricsEnabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return true;
        }
    }

    private static class FlagsWithGetFledgeAuctionServerApiUsageMetricsDisabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return false;
        }
    }
}

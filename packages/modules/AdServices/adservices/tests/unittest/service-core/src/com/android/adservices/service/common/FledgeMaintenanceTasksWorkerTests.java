/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.common;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.CommonFixture;
import android.adservices.common.KeyedFrequencyCap;
import android.content.pm.PackageManager;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.InteractionReportingTableClearedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Instant;

public class FledgeMaintenanceTasksWorkerTests {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionContextDao mEncryptionContextDaoMock;
    @Mock private PackageManager mPackageManagerMock;
    private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorker;
    private MockitoSession mMockitoSession;

    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private KAnonMessageDao mKAnonMessageDaoMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mMockitoSession = ExtendedMockito.mockitoSession().initMocks(this).startMocking();

        mFledgeMaintenanceTasksWorker =
                new FledgeMaintenanceTasksWorker(
                        TEST_FLAGS,
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);
    }

    @After
    public void teardown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testClearExpiredAdSelectionData_removesExpiredData() throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);

        mFledgeMaintenanceTasksWorker.clearExpiredAdSelectionData();

        // Verifies InteractionReportingTableClearedStats get the correct value.
        verify(mAdServicesLoggerMock, times(1))
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        InteractionReportingTableClearedStats stats = argumentCaptor.getValue();
        assertThat(stats.getNumUrisCleared()).isEqualTo(0);
        assertThat(stats.getNumUnreportedUris()).isEqualTo(-1);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        TEST_FLAGS.getAdSelectionExpirationWindowS());

        verify(mAdSelectionEntryDaoMock).removeExpiredAdSelection(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock).removeExpiredBuyerDecisionLogic();
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractions();
        verify(mAdSelectionEntryDaoMock)
                .removeExpiredAdSelectionInitializations(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock, never())
                .removeExpiredRegisteredAdInteractionsFromUnifiedTable();
        verify(mEncryptionContextDaoMock)
                .removeExpiredEncryptionContext(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock, times(2)).getTotalNumRegisteredAdInteractions();
        verifyNoMoreInteractions(mAdSelectionEntryDaoMock);
        verify(mAdSelectionDebugReportDaoMock).deleteDebugReportsBeforeTime(expectedExpirationTime);
        verifyNoMoreInteractions(mAdSelectionDebugReportDaoMock);
    }

    @Test
    public void
            testClearExpiredAdSelectionData_serverAuctionDisabled_doesntClearDataFromUnifiedFlow() {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);

        Flags flagsWithAuctionServerDisabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeEventLevelDebugReportingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeBeaconReportingMetricsEnabled() {
                        return true;
                    }
                };
        FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorkerWithAuctionDisabled =
                new FledgeMaintenanceTasksWorker(
                        flagsWithAuctionServerDisabled,
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        mFledgeMaintenanceTasksWorkerWithAuctionDisabled.clearExpiredAdSelectionData();

        // Verifies InteractionReportingTableClearedStats get the correct value.
        verify(mAdServicesLoggerMock, times(1))
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        InteractionReportingTableClearedStats stats = argumentCaptor.getValue();
        assertThat(stats.getNumUrisCleared()).isEqualTo(0);
        assertThat(stats.getNumUnreportedUris()).isEqualTo(-1);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        flagsWithAuctionServerDisabled.getAdSelectionExpirationWindowS());
        verify(mAdSelectionEntryDaoMock).removeExpiredAdSelection(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock).removeExpiredBuyerDecisionLogic();
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractions();
        verify(mAdSelectionEntryDaoMock, never())
                .removeExpiredAdSelectionInitializations(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock, never())
                .removeExpiredRegisteredAdInteractionsFromUnifiedTable();
        verify(mEncryptionContextDaoMock, never())
                .removeExpiredEncryptionContext(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock, times(2)).getTotalNumRegisteredAdInteractions();
        verifyNoMoreInteractions(mAdSelectionEntryDaoMock);
        verify(mAdSelectionDebugReportDaoMock).deleteDebugReportsBeforeTime(expectedExpirationTime);
        verifyNoMoreInteractions(mAdSelectionDebugReportDaoMock);
    }

    @Test
    public void
            testClearExpiredAdSelectionData_serverAuctionDisabled_unifiedTablesEnabled_ClearsUnifiedTables() {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);

        Flags flagsWithAuctionServerDisabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeEventLevelDebugReportingEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeOnDeviceAuctionShouldUseUnifiedTables() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeBeaconReportingMetricsEnabled() {
                        return true;
                    }
                };
        FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorkerWithAuctionDisabled =
                new FledgeMaintenanceTasksWorker(
                        flagsWithAuctionServerDisabled,
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        mFledgeMaintenanceTasksWorkerWithAuctionDisabled.clearExpiredAdSelectionData();

        // Verifies InteractionReportingTableClearedStats get the correct value.
        verify(mAdServicesLoggerMock, times(1))
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        InteractionReportingTableClearedStats stats = argumentCaptor.getValue();
        assertThat(stats.getNumUrisCleared()).isEqualTo(0);
        assertThat(stats.getNumUnreportedUris()).isEqualTo(-1);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        flagsWithAuctionServerDisabled.getAdSelectionExpirationWindowS());
        verify(mAdSelectionEntryDaoMock).removeExpiredAdSelection(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock).removeExpiredBuyerDecisionLogic();
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractions();
        verify(mAdSelectionEntryDaoMock)
                .removeExpiredAdSelectionInitializations(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock).removeExpiredRegisteredAdInteractionsFromUnifiedTable();
        verify(mEncryptionContextDaoMock, never())
                .removeExpiredEncryptionContext(eq(expectedExpirationTime));
        verify(mAdSelectionEntryDaoMock, times(2)).getTotalNumRegisteredAdInteractions();
        verifyNoMoreInteractions(mAdSelectionEntryDaoMock);
        verify(mAdSelectionDebugReportDaoMock, never())
                .deleteDebugReportsBeforeTime(expectedExpirationTime);
        verifyNoMoreInteractions(mAdSelectionDebugReportDaoMock);
    }

    @Test
    public void testClearExpiredFrequencyCapHistogramData_adFilteringEnabled_doesMaintenance() {
        final class FlagsWithAdFilteringFeatureEnabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return false;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        KeyedFrequencyCap.MAX_INTERVAL.getSeconds());

        verify(mFrequencyCapDaoMock).deleteAllExpiredHistogramData(eq(expectedExpirationTime));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedBuyerHistogramData(any(EnrollmentDao.class));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedSourceAppHistogramData(
                        any(PackageManager.class), any(Flags.class));
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void
            testClearExpiredFrequencyCapHistogramData_enrollmentDisabled_skipsBuyerMaintenance() {
        final class FlagsWithAdFilteringFeatureEnabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        KeyedFrequencyCap.MAX_INTERVAL.getSeconds());

        verify(mFrequencyCapDaoMock).deleteAllExpiredHistogramData(eq(expectedExpirationTime));
        verify(mFrequencyCapDaoMock)
                .deleteAllDisallowedSourceAppHistogramData(
                        any(PackageManager.class), any(Flags.class));
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testClearExpiredFrequencyCapHistogramData_adFilteringDisabled_skipsMaintenance() {
        final class FlagsWithAdFilteringFeatureDisabled implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return false;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return false;
            }
        }

        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagsWithAdFilteringFeatureDisabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        worker.clearInvalidFrequencyCapHistogramData(mPackageManagerMock);

        verifyNoMoreInteractions(mFrequencyCapDaoMock, mPackageManagerMock);
    }

    @Test
    public void testClearExpiredAdSelectionDataDebugReportingDisabledDoesNotClearDebugReportData() {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);

        Flags flagsWithAuctionServerDisabled =
                new Flags() {
                    @Override
                    public boolean getFledgeEventLevelDebugReportingEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeBeaconReportingMetricsEnabled() {
                        return true;
                    }
                };
        FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorkerWithAuctionDisabled =
                new FledgeMaintenanceTasksWorker(
                        flagsWithAuctionServerDisabled,
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        mFledgeMaintenanceTasksWorkerWithAuctionDisabled.clearExpiredAdSelectionData();

        // Verifies InteractionReportingTableClearedStats get the correct value.
        verify(mAdServicesLoggerMock, times(1))
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        InteractionReportingTableClearedStats stats = argumentCaptor.getValue();
        assertThat(stats.getNumUrisCleared()).isEqualTo(0);
        assertThat(stats.getNumUnreportedUris()).isEqualTo(-1);

        Instant expectedExpirationTime =
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                        flagsWithAuctionServerDisabled.getAdSelectionExpirationWindowS());
        verify(mAdSelectionDebugReportDaoMock, never())
                .deleteDebugReportsBeforeTime(expectedExpirationTime);
        verifyNoMoreInteractions(mAdSelectionDebugReportDaoMock);
    }

    @Test
    public void
            testRemovedExpiredKAnonEntites_withKAnonFeatureFlagEnabled_removesExpiredEntities() {
        final class FlagWithKAnonEnabled implements Flags {
            @Override
            public boolean getFledgeKAnonSignJoinFeatureEnabled() {
                return true;
            }
        }
        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagWithKAnonEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        worker.clearExpiredKAnonMessageEntities();

        verify(mKAnonMessageDaoMock, times(1))
                .removeExpiredEntities(CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI.instant());
    }

    @Test
    public void testRemovedExpiredKAnonEntites_withKAnonFeatureFlagDisabled_doesNothing() {
        final class FlagWithKAnonEnabled implements Flags {
            @Override
            public boolean getFledgeKAnonSignJoinFeatureEnabled() {
                return false;
            }
        }
        FledgeMaintenanceTasksWorker worker =
                new FledgeMaintenanceTasksWorker(
                        new FlagWithKAnonEnabled(),
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        mEnrollmentDaoMock,
                        mEncryptionContextDaoMock,
                        mAdSelectionDebugReportDaoMock,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        mKAnonMessageDaoMock);

        worker.clearExpiredKAnonMessageEntities();

        verifyZeroInteractions(mKAnonMessageDaoMock);
    }
}

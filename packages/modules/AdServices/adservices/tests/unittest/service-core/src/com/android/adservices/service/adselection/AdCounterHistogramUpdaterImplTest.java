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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionFixture;
import com.android.adservices.data.adselection.DBAdSelectionHistogramInfo;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.common.FledgeRoomConverters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashSet;

public class AdCounterHistogramUpdaterImplTest {
    private static final long AD_SELECTION_ID = 10;
    private static final int ABSOLUTE_MAX_TOTAL_EVENT_COUNT = 20;
    private static final int LOWER_MAX_TOTAL_EVENT_COUNT = 15;
    private static final int ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT = 10;
    private static final int LOWER_MAX_PER_BUYER_EVENT_COUNT = 5;
    private static final String SERIALIZED_AD_COUNTER_KEYS =
            FledgeRoomConverters.serializeIntegerSet(AdDataFixture.getAdCounterKeys());

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;

    private AdCounterHistogramUpdater mAdCounterHistogramUpdater;
    private boolean mAuctionServerEnabledForUpdateHistogram;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAuctionServerEnabledForUpdateHistogram = false;
        mAdCounterHistogramUpdater =
                new AdCounterHistogramUpdaterImpl(
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                        LOWER_MAX_TOTAL_EVENT_COUNT,
                        ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                        LOWER_MAX_PER_BUYER_EVENT_COUNT,
                        mAuctionServerEnabledForUpdateHistogram,
                        false);
    }

    @Test
    public void testNewUpdater_nullAdSelectionDaoThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                null,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_nullFrequencyCapDaoThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                null,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidAbsoluteMaxTotalEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                0,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                -1,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidLowerMaxTotalEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                0,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                -1,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidAbsoluteMaxPerBuyerEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                0,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                -1,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidLowerMaxPerBuyerEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                0,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                -1,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidAbsoluteAndLowerMaxTotalEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testNewUpdater_invalidAbsoluteAndLowerMaxPerBuyerEventCountThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDaoMock,
                                mFrequencyCapDaoMock,
                                ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_TOTAL_EVENT_COUNT,
                                LOWER_MAX_PER_BUYER_EVENT_COUNT,
                                ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                                mAuctionServerEnabledForUpdateHistogram,
                                false));
    }

    @Test
    public void testUpdateWinHistogram_nullDbAdSelectionDataThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterHistogramUpdater.updateWinHistogram(/* dbAdSelection= */ null));
    }

    @Test
    public void testUpdateWinHistogram_emptyCustomAudienceSignalsDoesNothing() {
        mAdCounterHistogramUpdater.updateWinHistogram(
                DBAdSelectionFixture.getValidDbAdSelectionBuilder()
                        .setCustomAudienceSignals(null)
                        .build());

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testUpdateWinHistogram_nullAdCounterKeysDoesNothing() {
        mAdCounterHistogramUpdater.updateWinHistogram(
                DBAdSelectionFixture.getValidDbAdSelectionBuilder()
                        .setAdCounterIntKeys(null)
                        .build());

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testUpdateWinHistogram_emptyAdCounterKeysDoesNothing() {
        mAdCounterHistogramUpdater.updateWinHistogram(
                DBAdSelectionFixture.getValidDbAdSelectionBuilder()
                        .setAdCounterIntKeys(new HashSet<>())
                        .build());

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testUpdateWinHistogram_withAdCounterKeysPersists() {
        final DBAdSelection validDbAdSelection =
                DBAdSelectionFixture.getValidDbAdSelectionBuilder().build();

        mAdCounterHistogramUpdater.updateWinHistogram(validDbAdSelection);

        HistogramEvent.Builder expectedEventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setBuyer(validDbAdSelection.getCustomAudienceSignals().getBuyer())
                        .setCustomAudienceOwner(
                                validDbAdSelection.getCustomAudienceSignals().getOwner())
                        .setCustomAudienceName(
                                validDbAdSelection.getCustomAudienceSignals().getName())
                        .setTimestamp(validDbAdSelection.getCreationTimestamp())
                        .setSourceApp(validDbAdSelection.getCallerPackageName());

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            verify(mFrequencyCapDaoMock)
                    .insertHistogramEvent(
                            eq(expectedEventBuilder.setAdCounterKey(key).build()),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
    }

    @Test
    public void testUpdateNonWinHistogram_nullCallerPackageNameThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                null,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                CommonFixture.FIXED_NOW));
    }

    @Test
    public void testUpdateNonWinHistogram_invalidAdEventTypeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                CommonFixture.TEST_PACKAGE_NAME,
                                FrequencyCapFilters.AD_EVENT_TYPE_INVALID,
                                CommonFixture.FIXED_NOW));
    }

    @Test
    public void testUpdateNonWinHistogram_winAdEventTypeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                CommonFixture.TEST_PACKAGE_NAME,
                                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                                CommonFixture.FIXED_NOW));
    }

    @Test
    public void testUpdateNonWinHistogram_nullTimestampThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                CommonFixture.TEST_PACKAGE_NAME,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                null));
    }

    @Test
    public void testUpdateNonWinHistogram_missingAdSelectionStops() {
        doReturn(null)
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());

        mAdCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                CommonFixture.FIXED_NOW);

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testUpdateNonWinHistogram_nullAdCounterKeysStops() {
        doReturn(DBAdSelectionHistogramInfo.create(CommonFixture.VALID_BUYER_1, null))
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());

        mAdCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                CommonFixture.FIXED_NOW);

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testUpdateNonWinHistogram_withAdCounterKeysPersists() {
        doReturn(
                        DBAdSelectionHistogramInfo.create(
                                CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS))
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());

        mAdCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                CommonFixture.FIXED_NOW);

        HistogramEvent.Builder expectedEventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setTimestamp(CommonFixture.FIXED_NOW)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME);

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            verify(mFrequencyCapDaoMock)
                    .insertHistogramEvent(
                            eq(expectedEventBuilder.setAdCounterKey(key).build()),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
    }

    @Test
    public void testUpdateNonWinHistogram_withAdCounterKeysPersists_auctionServerOff() {
        boolean auctionServerEnabledForUpdateHistogram = false;
        AdCounterHistogramUpdater adCounterHistogramUpdater =
                new AdCounterHistogramUpdaterImpl(
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                        LOWER_MAX_TOTAL_EVENT_COUNT,
                        ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                        LOWER_MAX_PER_BUYER_EVENT_COUNT,
                        auctionServerEnabledForUpdateHistogram,
                        false);
        doReturn(
                        DBAdSelectionHistogramInfo.create(
                                CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS))
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());

        adCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                CommonFixture.FIXED_NOW);

        HistogramEvent.Builder expectedEventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setTimestamp(CommonFixture.FIXED_NOW)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME);

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            verify(mFrequencyCapDaoMock)
                    .insertHistogramEvent(
                            eq(expectedEventBuilder.setAdCounterKey(key).build()),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
        verify(mAdSelectionEntryDaoMock, times(0)).getAdSelectionHistogramInfo(anyLong(), any());
        verify(mAdSelectionEntryDaoMock, times(0))
                .getAdSelectionHistogramInfoFromUnifiedTable(anyLong(), any());
    }

    @Test
    public void testUpdateNonWinHistogram_withAdCounterKeysPersists_auctionServerOn() {
        boolean auctionServerEnabledForUpdateHistogram = true;
        AdCounterHistogramUpdater adCounterHistogramUpdater =
                new AdCounterHistogramUpdaterImpl(
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                        LOWER_MAX_TOTAL_EVENT_COUNT,
                        ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                        LOWER_MAX_PER_BUYER_EVENT_COUNT,
                        auctionServerEnabledForUpdateHistogram,
                        false);

        doReturn(
                        DBAdSelectionHistogramInfo.create(
                                CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS))
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfo(anyLong(), any());

        adCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                CommonFixture.FIXED_NOW);

        HistogramEvent.Builder expectedEventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setTimestamp(CommonFixture.FIXED_NOW)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME);

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            verify(mFrequencyCapDaoMock)
                    .insertHistogramEvent(
                            eq(expectedEventBuilder.setAdCounterKey(key).build()),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
        verify(mAdSelectionEntryDaoMock, times(0))
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());
        verify(mAdSelectionEntryDaoMock, times(0))
                .getAdSelectionHistogramInfoFromUnifiedTable(anyLong(), any());
    }

    @Test
    public void testUpdateNonWinHistogram_withAdCounterKeysPersists_unifiedFlagOn() {
        boolean unifiedTablesEnabled = true;
        AdCounterHistogramUpdater adCounterHistogramUpdater =
                new AdCounterHistogramUpdaterImpl(
                        mAdSelectionEntryDaoMock,
                        mFrequencyCapDaoMock,
                        ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                        LOWER_MAX_TOTAL_EVENT_COUNT,
                        ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                        LOWER_MAX_PER_BUYER_EVENT_COUNT,
                        false,
                        unifiedTablesEnabled);

        doReturn(
                        DBAdSelectionHistogramInfo.create(
                                CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS))
                .when(mAdSelectionEntryDaoMock)
                .getAdSelectionHistogramInfoFromUnifiedTable(anyLong(), any());

        adCounterHistogramUpdater.updateNonWinHistogram(
                AD_SELECTION_ID,
                CommonFixture.TEST_PACKAGE_NAME,
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                CommonFixture.FIXED_NOW);

        HistogramEvent.Builder expectedEventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setTimestamp(CommonFixture.FIXED_NOW)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME);

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            verify(mFrequencyCapDaoMock)
                    .insertHistogramEvent(
                            eq(expectedEventBuilder.setAdCounterKey(key).build()),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
        verify(mAdSelectionEntryDaoMock, times(0))
                .getAdSelectionHistogramInfoInOnDeviceTable(anyLong(), any());
        verify(mAdSelectionEntryDaoMock, times(0)).getAdSelectionHistogramInfo(anyLong(), any());
    }
}

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

package com.android.adservices.data.adselection;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoSession;

import java.util.Set;

public class AuctionServerAdSelectionDaoTest {
    private AuctionServerAdSelectionDao mServerAdSelectionDao;
    private MockitoSession mStaticMockSession = null;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
        mServerAdSelectionDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .auctionServerAdSelectionDao();
    }

    @After
    public void cleanup() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testInsertAndRead_validInput_success() {
        long adSelectionId = 12345678L;
        AdTechIdentifier seller = AdSelectionConfigFixture.SELLER_1;
        AdTechIdentifier winnerBuyer = AdSelectionConfigFixture.BUYER_1;
        Uri winnerAdRenderUri = Uri.parse("test.com/ad_render");
        Set<Integer> adCounterKeys = AdDataFixture.getAdCounterKeys();
        Uri sellerReportingUri = Uri.parse("seller.reporting.uri");
        Uri buyerReportingUri = Uri.parse("buyer.reporting.uri");
        DBAuctionServerAdSelection serverAdSelection =
                DBAuctionServerAdSelection.create(
                        adSelectionId,
                        seller,
                        winnerBuyer,
                        winnerAdRenderUri,
                        adCounterKeys,
                        sellerReportingUri,
                        buyerReportingUri);

        mServerAdSelectionDao.insertAuctionServerAdSelection(serverAdSelection);
        Assert.assertEquals(
                serverAdSelection,
                mServerAdSelectionDao.getAuctionServerAdSelection(adSelectionId));
    }

    @Test
    public void testUpdate_validInput_success() {
        long adSelectionId = 12345678L;
        AdTechIdentifier seller = AdSelectionConfigFixture.SELLER_1;

        DBAuctionServerAdSelection serverAdSelectionEmpty =
                DBAuctionServerAdSelection.create(
                        adSelectionId, seller, null, null, null, null, null);
        mServerAdSelectionDao.insertAuctionServerAdSelection(serverAdSelectionEmpty);
        Assert.assertEquals(
                serverAdSelectionEmpty,
                mServerAdSelectionDao.getAuctionServerAdSelection(adSelectionId));

        AdTechIdentifier winnerBuyer = AdSelectionConfigFixture.BUYER_1;
        Uri winnerAdRenderUri = Uri.parse("test.com/ad_render");
        Set<Integer> adCounterKeys = AdDataFixture.getAdCounterKeys();
        Uri sellerReportingUri = Uri.parse("seller.reporting.uri");
        Uri buyerReportingUri = Uri.parse("buyer.reporting.uri");
        DBAuctionServerAdSelection serverAdSelectionFull =
                DBAuctionServerAdSelection.create(
                        adSelectionId,
                        seller,
                        winnerBuyer,
                        winnerAdRenderUri,
                        adCounterKeys,
                        sellerReportingUri,
                        buyerReportingUri);

        int updatedRows =
                mServerAdSelectionDao.updateAuctionServerAdSelection(serverAdSelectionFull);
        Assert.assertEquals(1, updatedRows);
        Assert.assertEquals(
                serverAdSelectionFull,
                mServerAdSelectionDao.getAuctionServerAdSelection(adSelectionId));
    }

    @Test
    public void testInsert_duplicateAdSelectionId_aborts() {
        long adSelectionId = 12345678L;
        AdTechIdentifier seller = AdSelectionConfigFixture.SELLER_1;
        AdTechIdentifier winnerBuyer = AdSelectionConfigFixture.BUYER_1;
        Uri winnerAdRenderUri = Uri.parse("test.com/ad_render");
        Set<Integer> adCounterKeys = AdDataFixture.getAdCounterKeys();
        Uri sellerReportingUri = Uri.parse("seller.reporting.uri");
        Uri buyerReportingUri = Uri.parse("buyer.reporting.uri");
        DBAuctionServerAdSelection serverAdSelection =
                DBAuctionServerAdSelection.create(
                        adSelectionId,
                        seller,
                        winnerBuyer,
                        winnerAdRenderUri,
                        adCounterKeys,
                        sellerReportingUri,
                        buyerReportingUri);

        mServerAdSelectionDao.insertAuctionServerAdSelection(serverAdSelection);

        Assert.assertThrows(
                SQLiteConstraintException.class,
                () -> mServerAdSelectionDao.insertAuctionServerAdSelection(serverAdSelection));
    }

    @Test
    public void testDelete_retrievingDeletedId_fails() {
        long adSelectionId = 12345678L;
        AdTechIdentifier seller = AdSelectionConfigFixture.SELLER_1;
        AdTechIdentifier winnerBuyer = AdSelectionConfigFixture.BUYER_1;
        Uri winnerAdRenderUri = Uri.parse("test.com/ad_render");
        Set<Integer> adCounterKeys = AdDataFixture.getAdCounterKeys();
        Uri sellerReportingUri = Uri.parse("seller.reporting.uri");
        Uri buyerReportingUri = Uri.parse("buyer.reporting.uri");
        DBAuctionServerAdSelection serverAdSelection =
                DBAuctionServerAdSelection.create(
                        adSelectionId,
                        seller,
                        winnerBuyer,
                        winnerAdRenderUri,
                        adCounterKeys,
                        sellerReportingUri,
                        buyerReportingUri);

        mServerAdSelectionDao.insertAuctionServerAdSelection(serverAdSelection);
        Assert.assertEquals(
                serverAdSelection,
                mServerAdSelectionDao.getAuctionServerAdSelection(adSelectionId));

        mServerAdSelectionDao.removeAdSelectionById(adSelectionId);
        Assert.assertNull(mServerAdSelectionDao.getAuctionServerAdSelection(adSelectionId));
    }
}

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

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;

class ReportingComputationHelperUnifiedTablesDisabled implements ReportingComputationHelper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final AdSelectionEntryDao mAdSelectionEntryDao;

    ReportingComputationHelperUnifiedTablesDisabled(AdSelectionEntryDao adSelectionEntryDao) {
        mAdSelectionEntryDao = adSelectionEntryDao;
    }

    @Override
    public boolean doesAdSelectionIdExist(long adSelectionId) {
        sLogger.v("Checking old table for ad selection ID");
        return mAdSelectionEntryDao.doesAdSelectionIdExist(adSelectionId);
    }

    @Override
    public boolean doesAdSelectionMatchingCallerPackageNameExist(
            long adSelectionId, String callerPackageName) {
        return mAdSelectionEntryDao.doesAdSelectionMatchingCallerPackageNameExistInOnDeviceTable(
                adSelectionId, callerPackageName);
    }

    @Override
    public ReportingComputationData getReportingComputation(long adSelectionId) {
        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(adSelectionId);
        return ReportingComputationData.builder()
                .setBuyerDecisionLogicJs(adSelectionEntry.getBuyerDecisionLogicJs())
                .setBuyerDecisionLogicUri(adSelectionEntry.getBiddingLogicUri())
                .setSellerContextualSignals(
                        parseAdSelectionSignalsOrEmpty(
                                adSelectionEntry.getSellerContextualSignals()))
                .setBuyerContextualSignals(
                        parseAdSelectionSignalsOrEmpty(
                                adSelectionEntry.getBuyerContextualSignals()))
                .setWinningCustomAudienceSignals(adSelectionEntry.getCustomAudienceSignals())
                .setWinningRenderUri(adSelectionEntry.getWinningAdRenderUri())
                .setWinningBid(adSelectionEntry.getWinningAdBid())
                .build();
    }
}

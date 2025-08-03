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
import com.android.adservices.data.adselection.DBReportingComputationInfo;
import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;

class ReportingComputationHelperUnifiedTablesEnabled implements ReportingComputationHelper {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final AdSelectionEntryDao mAdSelectionEntryDao;

    ReportingComputationHelperUnifiedTablesEnabled(AdSelectionEntryDao adSelectionEntryDao) {
        mAdSelectionEntryDao = adSelectionEntryDao;
    }

    @Override
    public boolean doesAdSelectionIdExist(long adSelectionId) {
        sLogger.v("Checking new table for ad selection ID");
        return mAdSelectionEntryDao.doesReportingComputationInfoExist(adSelectionId);
    }

    @Override
    public boolean doesAdSelectionMatchingCallerPackageNameExist(
            long adSelectionId, String callerPackageName) {
        return mAdSelectionEntryDao
                .doesAdSelectionMatchingCallerPackageNameExistInServerAuctionTable(
                        adSelectionId, callerPackageName);
    }

    @Override
    public ReportingComputationData getReportingComputation(long adSelectionId) {
        DBReportingComputationInfo reportingComputationInfoById =
                mAdSelectionEntryDao.getReportingComputationInfoById(adSelectionId);
        return ReportingComputationData.builder()
                .setBuyerDecisionLogicJs(reportingComputationInfoById.getBuyerDecisionLogicJs())
                .setBuyerDecisionLogicUri(reportingComputationInfoById.getBiddingLogicUri())
                .setSellerContextualSignals(
                        parseAdSelectionSignalsOrEmpty(
                                reportingComputationInfoById.getSellerContextualSignals()))
                .setBuyerContextualSignals(
                        parseAdSelectionSignalsOrEmpty(
                                reportingComputationInfoById.getBuyerContextualSignals()))
                .setWinningCustomAudienceSignals(
                        reportingComputationInfoById.getCustomAudienceSignals())
                .setWinningRenderUri(reportingComputationInfoById.getWinningAdRenderUri())
                .setWinningBid(reportingComputationInfoById.getWinningAdBid())
                .build();
    }
}

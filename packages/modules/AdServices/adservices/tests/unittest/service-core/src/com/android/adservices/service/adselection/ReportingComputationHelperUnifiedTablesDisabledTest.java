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

import static android.adservices.adselection.DataHandlersFixture.DB_AD_SELECTION_ENTRY;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionEntryDao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReportingComputationHelperUnifiedTablesDisabledTest {
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    private ReportingComputationHelperUnifiedTablesDisabled
            mReportingComputationHelperUnifiedTablesDisabled;

    private static final int AD_SELECTION_ID = 1;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mReportingComputationHelperUnifiedTablesDisabled =
                new ReportingComputationHelperUnifiedTablesDisabled(mAdSelectionEntryDaoMock);
    }

    @Test
    public void testDoesAdSelectionIdExist() {
        mReportingComputationHelperUnifiedTablesDisabled.doesAdSelectionIdExist(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoMock).doesAdSelectionIdExist(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoMock, never()).doesReportingComputationInfoExist(anyLong());
    }

    @Test
    public void testDoesAdSelectionMatchingCallerPackageNameExist() {
        mReportingComputationHelperUnifiedTablesDisabled
                .doesAdSelectionMatchingCallerPackageNameExist(AD_SELECTION_ID, TEST_PACKAGE_NAME);
        verify(mAdSelectionEntryDaoMock)
                .doesAdSelectionMatchingCallerPackageNameExistInOnDeviceTable(
                        AD_SELECTION_ID, TEST_PACKAGE_NAME);
        verify(mAdSelectionEntryDaoMock, never())
                .doesAdSelectionMatchingCallerPackageNameExistInServerAuctionTable(
                        anyLong(), any());
    }

    @Test
    public void testGetReportingComputation() {
        when(mAdSelectionEntryDaoMock.getAdSelectionEntityById(AD_SELECTION_ID))
                .thenReturn(DB_AD_SELECTION_ENTRY);
        mReportingComputationHelperUnifiedTablesDisabled.getReportingComputation(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoMock).getAdSelectionEntityById(AD_SELECTION_ID);
        verify(mAdSelectionEntryDaoMock, never()).getReportingComputationInfoById(anyLong());
    }
}

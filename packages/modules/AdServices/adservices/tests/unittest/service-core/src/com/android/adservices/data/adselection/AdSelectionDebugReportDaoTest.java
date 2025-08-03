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

import static android.adservices.common.CommonFixture.FIXED_EARLIER_ONE_DAY;
import static android.adservices.common.CommonFixture.FIXED_NEXT_ONE_DAY;
import static android.adservices.common.CommonFixture.FIXED_NOW;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AdSelectionDebugReportDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri DEBUG_URI = Uri.parse("https://example.com/debug_report");
    private static final DBAdSelectionDebugReport DB_AD_SELECTION_DEBUG_REPORT =
            DBAdSelectionDebugReport.create(null, DEBUG_URI, false, FIXED_NOW.toEpochMilli());
    private static final List<DBAdSelectionDebugReport> AD_SELECTION_DEBUG_REPORT_LIST =
            Collections.singletonList(DB_AD_SELECTION_DEBUG_REPORT);
    private AdSelectionDebugReportDao mAdSelectionDebugReportDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAdSelectionDebugReportDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDebugReportingDatabase.class)
                        .build()
                        .getAdSelectionDebugReportDao();
    }

    @Test
    public void testPersistAdSelectionDebugReporting() {
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(AD_SELECTION_DEBUG_REPORT_LIST);
    }

    @Test
    public void testPersistAdSelectionDebugReportingHandlesDuplicateEntries() {
        DBAdSelectionDebugReport dbAdSelectionDebugReport =
                DBAdSelectionDebugReport.create(1234L, DEBUG_URI, true, FIXED_NOW.toEpochMilli());
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(
                Collections.singletonList(dbAdSelectionDebugReport));
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(
                Collections.singletonList(dbAdSelectionDebugReport));
        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NOW, 1);

        Assert.assertNotNull(debugReports);
        Assert.assertFalse(debugReports.isEmpty());
        Assert.assertEquals(1, debugReports.size());
    }

    @Test
    public void testGetDebugReportsBeforeTime() {
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(AD_SELECTION_DEBUG_REPORT_LIST);

        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NOW, 1);

        Assert.assertNotNull(debugReports);
        Assert.assertFalse(debugReports.isEmpty());
        Assert.assertEquals(1, debugReports.size());
        DBAdSelectionDebugReport actualDebugReport = debugReports.get(0);
        Assert.assertEquals(
                DB_AD_SELECTION_DEBUG_REPORT.getDebugReportUri(),
                actualDebugReport.getDebugReportUri());
        Assert.assertEquals(
                DB_AD_SELECTION_DEBUG_REPORT.getDevOptionsEnabled(),
                actualDebugReport.getDevOptionsEnabled());
        Assert.assertEquals(
                DB_AD_SELECTION_DEBUG_REPORT.getCreationTimestamp(),
                actualDebugReport.getCreationTimestamp());
    }

    @Test
    public void testDeleteDebugReportsBeforeTime() {

        DBAdSelectionDebugReport debugReportBeforeCurrentTime =
                DBAdSelectionDebugReport.create(
                        null, DEBUG_URI, true, FIXED_EARLIER_ONE_DAY.toEpochMilli());
        DBAdSelectionDebugReport debugReportAfterCurrentTime =
                DBAdSelectionDebugReport.create(
                        null, DEBUG_URI, true, FIXED_NEXT_ONE_DAY.toEpochMilli());
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(
                List.of(debugReportBeforeCurrentTime, debugReportAfterCurrentTime));

        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NEXT_ONE_DAY, 1000);
        mAdSelectionDebugReportDao.deleteDebugReportsBeforeTime(FIXED_NOW);
        List<DBAdSelectionDebugReport> debugReportsAfterDelete =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NEXT_ONE_DAY, 1000);

        Assert.assertNotNull(debugReports);
        Assert.assertEquals(2, debugReports.size());
        Assert.assertNotNull(debugReportsAfterDelete);
        Assert.assertEquals(1, debugReportsAfterDelete.size());
    }

    @Test
    public void testGetDebugReportsBeforeTimeHonorsLimit() {
        int debugReportsSize = 10;
        List<DBAdSelectionDebugReport> debugReports = new ArrayList<>(debugReportsSize);
        for (int i = 0; i < debugReportsSize; i++) {
            debugReports.add(DB_AD_SELECTION_DEBUG_REPORT);
        }
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(debugReports);
        int largeLimit = 100;
        int lowLimit = 5;
        int zeroLimit = 0;

        List<DBAdSelectionDebugReport> debugReportsWithLargeLimits =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NOW, largeLimit);
        List<DBAdSelectionDebugReport> debugReportsWithLowLimits =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NOW, lowLimit);
        List<DBAdSelectionDebugReport> debugReportsWithZeroLimit =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(FIXED_NOW, zeroLimit);

        Assert.assertNotNull(debugReportsWithLargeLimits);
        Assert.assertFalse(debugReportsWithLargeLimits.isEmpty());
        Assert.assertEquals(debugReportsSize, debugReportsWithLargeLimits.size());
        Assert.assertNotNull(debugReportsWithLowLimits);
        Assert.assertFalse(debugReportsWithLowLimits.isEmpty());
        Assert.assertEquals(lowLimit, debugReportsWithLowLimits.size());
        Assert.assertNotNull(debugReportsWithZeroLimit);
        Assert.assertTrue(debugReportsWithZeroLimit.isEmpty());
    }
}

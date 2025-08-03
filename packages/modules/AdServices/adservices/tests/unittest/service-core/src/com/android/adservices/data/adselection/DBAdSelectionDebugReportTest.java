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

import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

public class DBAdSelectionDebugReportTest {

    private static final long CREATION_TIMESTAMP = Instant.now().toEpochMilli();
    private static final Uri DEBUG_URI = Uri.parse("https://example.com/debug_report");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAdSelectionDebugReport_create_success() {
        Long adSelectionDebugReportId = 123L;
        DBAdSelectionDebugReport dbAdSelectionDebugReport =
                DBAdSelectionDebugReport.create(
                        adSelectionDebugReportId, DEBUG_URI, false, CREATION_TIMESTAMP);
        DBAdSelectionDebugReport expected =
                DBAdSelectionDebugReport.builder()
                        .setAdSelectionDebugReportId(adSelectionDebugReportId)
                        .setDebugReportUri(DEBUG_URI)
                        .setDevOptionsEnabled(false)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .build();
        Assert.assertEquals(expected, dbAdSelectionDebugReport);
    }

    @Test
    public void testAdSelectionDebugReport_create_adSelectionDebugReportIdNull() {
        DBAdSelectionDebugReport dbAdSelectionDebugReport =
                DBAdSelectionDebugReport.create(null, DEBUG_URI, false, CREATION_TIMESTAMP);
        DBAdSelectionDebugReport expected =
                DBAdSelectionDebugReport.builder()
                        .setAdSelectionDebugReportId(null)
                        .setDebugReportUri(DEBUG_URI)
                        .setDevOptionsEnabled(false)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .build();
        Assert.assertEquals(expected, dbAdSelectionDebugReport);
    }

    @Test
    public void testAdSelectionDebugReport_create_DebugReportUriEmpty() {
        DBAdSelectionDebugReport dbAdSelectionDebugReport =
                DBAdSelectionDebugReport.create(null, Uri.EMPTY, true, CREATION_TIMESTAMP);
        DBAdSelectionDebugReport expected =
                DBAdSelectionDebugReport.builder()
                        .setAdSelectionDebugReportId(null)
                        .setDebugReportUri(Uri.EMPTY)
                        .setDevOptionsEnabled(true)
                        .setCreationTimestamp(CREATION_TIMESTAMP)
                        .build();
        Assert.assertEquals(expected, dbAdSelectionDebugReport);
    }

    @Test
    public void testAdSelectionDebugReport_create_DebugReportUriNull_throwsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> DBAdSelectionDebugReport.create(null, null, false, CREATION_TIMESTAMP));
    }
}

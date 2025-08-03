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
package android.device.collectors;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import java.io.File;

/** Unit tests for {@link OatDumpCollector}. */
@RunWith(AndroidJUnit4.class)
public final class OatDumpCollectorTest {

    @After
    public void tearDown() {
        // We don't expect state held between tests, so just clear the cache directory.
        File[] cacheFiles =
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getExternalCacheDir()
                        .listFiles();
        for (File file : cacheFiles) {
            file.delete();
        }
    }

    /** Test that an oat dump is collected and reported at run start and end. */
    @Test
    public void testCollectOatDump_onRunStartAndRunEnd() throws Exception {
        Bundle bundle = new Bundle();
        // This test relies on actual output from com.android.systemui, which we expect is stable.
        bundle.putString(OatDumpCollector.OAT_DUMP_PROCESS, "com.android.systemui");
        File outputDir =
                InstrumentationRegistry.getInstrumentation().getContext().getExternalCacheDir();
        bundle.putString(OatDumpCollector.ADDITIONAL_TEST_OUTPUT_DIR, outputDir.getAbsolutePath());

        OatDumpCollector collector = new OatDumpCollector(bundle);
        collector.setInstrumentation(InstrumentationRegistry.getInstrumentation());
        collector.setUp();

        DataRecord record = new DataRecord();
        collector.onTestRunStart(
                record, Description.createTestDescription("fakeClass", "fakeTest"));

        String filenameStartKey =
                collector.getOatDumpFileKey(OatDumpCollector.TEST_RUN_START_SUFFIX);
        String filenameStartValue =
                collector.getOatDumpFilePath(OatDumpCollector.TEST_RUN_START_SUFFIX);
        String odexFileSizeStartKey =
                collector.getOdexFileSizeKey(OatDumpCollector.TEST_RUN_START_SUFFIX);
        String reportedSizeStartKey =
                collector.getReportedSizeKey(OatDumpCollector.TEST_RUN_START_SUFFIX);

        // Check that the file actually exists first.
        assertTrue(new File(outputDir, filenameStartValue).exists());

        Bundle startMetrics = record.createBundleFromMetrics();
        assertThat(startMetrics.getString(filenameStartKey)).contains(filenameStartValue);
        assertThat(startMetrics.getString(odexFileSizeStartKey)).isNotNull();
        assertThat(startMetrics.getString(reportedSizeStartKey)).isNotNull();

        collector.onTestRunEnd(record, new Result());

        String filenameEndKey = collector.getOatDumpFileKey(OatDumpCollector.TEST_RUN_END_SUFFIX);
        String filenameEndValue =
                collector.getOatDumpFilePath(OatDumpCollector.TEST_RUN_END_SUFFIX);
        String odexFileSizeEndKey =
                collector.getOdexFileSizeKey(OatDumpCollector.TEST_RUN_END_SUFFIX);
        String reportedSizeEndKey =
                collector.getReportedSizeKey(OatDumpCollector.TEST_RUN_END_SUFFIX);

        // Check that the file actually exists first.
        assertTrue(new File(outputDir, filenameEndValue).exists());

        Bundle endMetrics = record.createBundleFromMetrics();
        assertThat(endMetrics.getString(filenameEndKey)).contains(filenameEndValue);
        assertThat(endMetrics.getString(odexFileSizeEndKey)).isNotNull();
        assertThat(endMetrics.getString(reportedSizeEndKey)).isNotNull();
    }

    /** Test that the collector will throw early if a process to track isn't specified. */
    @Test
    public void testFailsIfProcessNotSpecified() {
        Bundle bundle = new Bundle();
        bundle.putString(
                OatDumpCollector.ADDITIONAL_TEST_OUTPUT_DIR,
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getExternalCacheDir()
                        .getAbsolutePath());
        OatDumpCollector collector = new OatDumpCollector(bundle);
        assertThrows(AssertionError.class, () -> collector.setUp());
    }

    /** Test that the collector will throw early if an output directory isn't specified. */
    @Test
    public void testFailsIfOutputDirNotSpecified() {
        Bundle bundle = new Bundle();
        bundle.putString(OatDumpCollector.OAT_DUMP_PROCESS, "com.android.systemui");
        OatDumpCollector collector = new OatDumpCollector(bundle);
        assertThrows(AssertionError.class, () -> collector.setUp());
    }
}

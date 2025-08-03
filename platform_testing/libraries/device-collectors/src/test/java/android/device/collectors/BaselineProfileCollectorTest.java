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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import static android.device.collectors.BaselineProfileCollector.ADDITIONAL_TEST_OUTPUT_DIR;
import static android.device.collectors.BaselineProfileCollector.BASELINE_PROFILE_SUFFIX;

import android.os.Bundle;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

/**
 * Android Unit tests for {@link BaselineProfileCollector}.
 *
 * <p>To run: atest CollectorDeviceLibTest:android.device.collectors.BaselineProfileCollectorTest
 *
 * <p>Note: The design of BaseMetricListener and DataRecords make it hard to test the metrics
 * reported. Directly invoking BaseMetricListener#onTestRunEnd instead of
 * BaseMetricListener#testRunFinished is not desirable; however it's much simpler.
 */
@RunWith(AndroidJUnit4.class)
public class BaselineProfileCollectorTest {
    // A {@code Description} to pass when faking a test run start call.
    private static final Description FAKE_DESCRIPTION = Description.createSuiteDescription("run");

    @After
    public void clearOutputDirectory() {
        // Assume all external files are from this test.
        for (File file : getOutputDir().listFiles()) {
            file.delete();
        }
    }

    private BaselineProfileCollector getCollector() {
        Bundle args = new Bundle();
        args.putString(ADDITIONAL_TEST_OUTPUT_DIR, getOutputDir().getAbsolutePath());
        return new BaselineProfileCollector(args);
    }

    private void createFileInOutputDir(String filename) throws IOException {
        new File(getOutputDir().toPath().resolve(filename).toString()).createNewFile();
    }

    private File getOutputDir() {
        return InstrumentationRegistry.getInstrumentation().getContext().getExternalCacheDir();
    }

    private DataRecord getTransparentDataRecord() {
        return spy(new DataRecord());
    }

    /** Test that a baseline profile is reported if one exists in the provided directory. */
    @Test
    public void testBaselineProfile_isReportedIfOneExists() throws Exception {
        BaselineProfileCollector collector = getCollector();
        String profileFilename = String.format("test-%s", BASELINE_PROFILE_SUFFIX);
        createFileInOutputDir(profileFilename);

        collector.testRunStarted(FAKE_DESCRIPTION);
        DataRecord transparentDataRecord = getTransparentDataRecord();
        collector.onTestRunEnd(transparentDataRecord, new Result());

        verify(transparentDataRecord)
                .addStringMetric(
                        "baseline-profile",
                        getOutputDir().toPath().resolve(profileFilename).toString());
    }

    /** Test that all baseline profiles are reported if many exist in the provided directory. */
    @Test
    public void testBaselineProfile_areAllReportedIfManyExists() throws Exception {
        BaselineProfileCollector collector = getCollector();
        String profile1Filename = String.format("test1-%s", BASELINE_PROFILE_SUFFIX);
        String profile2Filename = String.format("test2-%s", BASELINE_PROFILE_SUFFIX);
        createFileInOutputDir(profile1Filename);
        createFileInOutputDir(profile2Filename);

        collector.testRunStarted(FAKE_DESCRIPTION);
        DataRecord transparentDataRecord = getTransparentDataRecord();
        collector.onTestRunEnd(transparentDataRecord, new Result());

        verify(transparentDataRecord)
                .addStringMetric(
                        "baseline-profile1",
                        getOutputDir().toPath().resolve(profile1Filename).toString());
        verify(transparentDataRecord)
                .addStringMetric(
                        "baseline-profile2",
                        getOutputDir().toPath().resolve(profile2Filename).toString());
    }

    /** Test that no baseline profiles are reported if none exist in the provided directory. */
    @Test
    public void testBaselineProfile_isNotReportedIfNoneExist() throws Exception {
        BaselineProfileCollector collector = getCollector();

        collector.testRunStarted(FAKE_DESCRIPTION);
        DataRecord transparentDataRecord = getTransparentDataRecord();
        collector.onTestRunEnd(transparentDataRecord, new Result());

        verify(transparentDataRecord, never()).addStringMetric(anyString(), anyString());
    }

    /** Test that the collector will throw if the required output dir option isn't provided. */
    @Test
    public void testThrows_ifAdditionalTestOutputDir_notSpecified() throws Exception {
        BaselineProfileCollector collector = new BaselineProfileCollector(new Bundle());
        try {
            collector.testRunStarted(Description.createSuiteDescription("run"));
            throw new RuntimeException("Expected collector to throw because of missing option.");
        } catch (Throwable e) {
        }
    }
}

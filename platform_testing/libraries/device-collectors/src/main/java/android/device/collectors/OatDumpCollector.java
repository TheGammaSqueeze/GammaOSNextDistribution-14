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

import static junit.framework.Assert.assertTrue;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.uiautomator.UiDevice;

import org.junit.runner.Description;
import org.junit.runner.Result;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects an oat dump to help debugging performance issues with ART profiles. */
@OptionClass(alias = "oat-dump-collector")
public class OatDumpCollector extends BaseMetricListener {
    private static final String LOG_TAG = OatDumpCollector.class.getSimpleName();

    private static final String OAT_DUMP_FMT_COMMAND = "oatdump --oat-file=%s";
    private static final String DUMPSYS_COMMAND = "dumpsys package %s";

    @VisibleForTesting static final String OAT_DUMP_PROCESS = "oat-dump-process";
    @VisibleForTesting static final String ADDITIONAL_TEST_OUTPUT_DIR = "additionalTestOutputDir";

    @VisibleForTesting static final String TEST_RUN_START_SUFFIX = "run-start";
    @VisibleForTesting static final String TEST_RUN_END_SUFFIX = "run-end";

    private String mAdditionalTestOutputDir;
    private String mOatDumpProcess;
    private UiDevice mDevice;

    public OatDumpCollector() {
        super();
    }

    @VisibleForTesting
    public OatDumpCollector(Bundle args) {
        super(args);
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        collectOatDumpAndMetrics(runData, TEST_RUN_START_SUFFIX);
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        collectOatDumpAndMetrics(runData, TEST_RUN_END_SUFFIX);
    }

    /**
     * Performs an oat dump by finding the location of the .odex file and then calling the oatdump
     * command against it. The contents are piped into a file that can be searched for optimized or
     * unoptimized code.
     */
    private void collectOatDumpAndMetrics(DataRecord record, String phaseSuffix) {
        // Get file name from dumpsys
        String dumpsysCommand = String.format(DUMPSYS_COMMAND, mOatDumpProcess);
        String dumpsysOutput = null;
        try {
            dumpsysOutput = getDevice().executeShellCommand(dumpsysCommand);
        } catch (IOException e) {
            throw new RuntimeException("Failed to communicate with device.");
        }

        Pattern odexFilePattern = Pattern.compile("\\[location is (.+dex)\\]");
        Matcher odexFileMatch = odexFilePattern.matcher(dumpsysOutput);
        if (!odexFileMatch.find()) {
            throw new RuntimeException("Failed to find expected oatdump file location.");
        }

        // The oatdump is very large, so it needs to be written to a file with buffering.
        File odexFile = new File(odexFileMatch.group(1));
        String oatDumpCommand = String.format(OAT_DUMP_FMT_COMMAND, odexFile);
        Log.i(LOG_TAG, String.format("Running oatdump with \"%s\"", oatDumpCommand));
        File oatDumpFile = new File(mAdditionalTestOutputDir, getOatDumpFilePath(phaseSuffix));
        Log.i(
                LOG_TAG,
                String.format("Writing oatdump output to %s", oatDumpFile.getAbsolutePath()));
        try (FileOutputStream outputStream = new FileOutputStream(oatDumpFile);
                ParcelFileDescriptor pfd =
                        getInstrumentation().getUiAutomation().executeShellCommand(oatDumpCommand);
                FileInputStream oatDumpInputStream = new FileInputStream(pfd.getFileDescriptor())) {

            byte[] buffer = new byte[1024];
            int length;

            while ((length = oatDumpInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            String fileKey = getOatDumpFileKey(phaseSuffix);
            record.addStringMetric(fileKey, oatDumpFile.getAbsolutePath());

            String odexFileSizeKey = getOdexFileSizeKey(phaseSuffix);
            record.addStringMetric(odexFileSizeKey, String.valueOf(odexFile.length()));

            String reportedSizeKey = getReportedSizeKey(phaseSuffix);
            record.addStringMetric(reportedSizeKey, readSizeMetricFromOatDump(oatDumpFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write the oatdump to the output file.", e);
        }
    }

    private String readSizeMetricFromOatDump(File oatDumpFile) {
        try (FileInputStream oatDumpInputStream = new FileInputStream(oatDumpFile);
                InputStreamReader oatDumpStreamReader = new InputStreamReader(oatDumpInputStream);
                BufferedReader oatDumpBufferedReader = new BufferedReader(oatDumpStreamReader)) {
            String line;

            while ((line = oatDumpBufferedReader.readLine()) != null) {
                if (line.startsWith("SIZE:")) {
                    return oatDumpBufferedReader.readLine();
                }
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Failed to read oatdump to find reported size.", e);
        }
        throw new RuntimeException("Failed to find oatdump's reported size.");
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();

        assertTrue(
                "Specify the oat-dump-process option to use this. See comments for more details.",
                args.containsKey(OAT_DUMP_PROCESS));
        mOatDumpProcess = args.getString(OAT_DUMP_PROCESS);

        assertTrue(
                "Specify the additionalTestOutputDir option to use this. See comments for more"
                        + " details.",
                args.containsKey(ADDITIONAL_TEST_OUTPUT_DIR));
        mAdditionalTestOutputDir = args.getString(ADDITIONAL_TEST_OUTPUT_DIR);
    }

    @VisibleForTesting
    String getOatDumpFilePath(String suffix) {
        return String.format("oat-dump-file-%s.txt", suffix);
    }

    @VisibleForTesting
    String getOatDumpFileKey(String suffix) {
        return String.format("oat-dump-file-%s", suffix);
    }

    @VisibleForTesting
    String getOdexFileSizeKey(String suffix) {
        return String.format("oat-dump-odex-file-size-%s", suffix);
    }

    @VisibleForTesting
    String getReportedSizeKey(String suffix) {
        return String.format("oat-dump-reported-size-%s", suffix);
    }

    /** Returns the currently active {@link UiDevice}. */
    @VisibleForTesting
    public UiDevice getDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(getInstrumentation());
        }
        return mDevice;
    }
}

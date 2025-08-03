/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.device.collectors.annotations.OptionClass;
import android.util.Log;

import org.junit.runner.Description;
import org.junit.runner.Result;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.text.SimpleDateFormat;

/**
 * A {@link BaseMetricListener} that captures mobility changes to stationary state(3) in logcat.
 *
 * <p>This class needs external storage permission. See {@link BaseMetricListener} how to grant
 * external storage permission, especially at install time.
 */
@OptionClass(alias = "mobility-change-collector")
public class MobilityChangeCollector extends BaseMetricListener {
    static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    public static final String DEFAULT_DIR = "run_listeners/mobility_changes";

    private File mDestDir;
    private String mStartTime = null;

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        mDestDir = createAndEmptyDirectory(DEFAULT_DIR);
        mStartTime = DATE_FORMATTER.format(System.currentTimeMillis());
    }

    /** On test run end, check for mobility state changes to 3 */
    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        try {
            final String fileName = "mobility-changes.txt";
            File mobilityLog = new File(mDestDir, fileName);
            getMobilityChangesSince(mStartTime, mobilityLog);
            runData.addFileMetric(
                    String.format("%s_%s", getTag(), mobilityLog.getName()), mobilityLog);
            String mobilityChanges =
                    new String(
                            executeCommandBlocking(
                                    String.format("cat %s | wc -l", mobilityLog.getAbsolutePath())),
                            StandardCharsets.UTF_8);
            runData.addStringMetric("mobility-changes", mobilityChanges);
        } catch (IOException | InterruptedException e) {
            Log.e(getTag(), "Error trying to retrieve mobility logcat.", e);
        }
    }

    /** @hide */
    protected void getMobilityChangesSince(String startTime, File saveTo)
            throws IOException, InterruptedException {
        // ProcessBuilder is used here in favor of UiAutomation.executeShellCommand() because the
        // logcat command requires the timestamp to be quoted which in Java requires
        // Runtime.exec(String[]) or ProcessBuilder to work properly, and UiAutomation does not
        // support this for now.
        ProcessBuilder pb =
                new ProcessBuilder(
                        Arrays.asList(
                                "logcat", "-t", startTime, "-d", "updateSconeInfo mobility:3"));
        pb.redirectOutput(saveTo);
        Process proc = pb.start();
        // Make the process blocking to ensure consistent behavior.
        proc.waitFor();
    }
}

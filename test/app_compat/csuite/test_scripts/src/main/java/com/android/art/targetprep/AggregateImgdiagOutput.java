/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.art.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.ITargetPreparer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collect all imgdiag dirty objects into one file. */
public class AggregateImgdiagOutput implements ITestLoggerReceiver, ITargetPreparer {
    @Option(
            name = "imgdiag-out-path",
            description = "Path to directory containing imgdiag output files.")
    private String mImgdiagOutPath;

    private ITestLogger mTestLogger;

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    @Override
    public void setUp(TestInformation testInformation) {}

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        Assert.assertTrue(testInformation.getDevice().doesFileExist(mImgdiagOutPath));

        Pattern imgdiagOutRegex = Pattern.compile("imgdiag_(\\S+_\\d+)\\.txt");
        String dirtyObjPrefix = "dirty_obj:";

        JSONObject combinedData = new JSONObject();
        IFileEntry deviceImgdiagOutDir = testInformation.getDevice().getFileEntry(mImgdiagOutPath);
        for (IFileEntry child : deviceImgdiagOutDir.getChildren(false)) {
            Matcher m = imgdiagOutRegex.matcher(child.getName());
            if (!m.matches()) {
                continue;
            }

            String key = m.group(1);

            String fileContents = testInformation.getDevice().pullFileContents(child.getFullPath());
            Collection<String> dirty_objects =
                    fileContents
                            .lines()
                            .filter(line -> line.startsWith(dirtyObjPrefix))
                            .map(line -> line.substring(dirtyObjPrefix.length()).strip())
                            .toList();

            try {
                combinedData.put(key, new JSONArray(dirty_objects));
            } catch (JSONException exception) {
                Assert.fail(exception.toString());
            }
        }

        mTestLogger.testLog(
                "combined_imgdiag_data",
                LogDataType.JSON,
                new ByteArrayInputStreamSource(combinedData.toString().getBytes()));
    }
}

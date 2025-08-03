/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Random;

/** Functional tests for the {@link ITestDevice} user management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestDeviceShellFuncTest implements IDeviceTest {
    private TestDevice mTestDevice;
    private String mInternalStorage;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();
        mInternalStorage = "/data/local/tmp";
    }

    /** Runs a couple basic commands like `ls` and `cat` on a file we push. */
    @Test
    public void testExecuteShellCommand_basic() throws Exception {
        final String testTag = "testExecuteShellCommand_basic";
        String devicePath = null;

        try {
            // Construct device path
            devicePath = String.format("%s/%s", mInternalStorage, testTag);

            // Push random string to path
            String contents = generateRandomString(64);
            mTestDevice.pushString(contents, devicePath);

            // If there isn't reasonable confidence that the file
            // was pushed successfully then this is an invalid test
            assumeTrue(mTestDevice.doesFileExist(devicePath));

            // Execute command to list the path
            String output = mTestDevice.executeShellCommand(String.format("ls %s", devicePath));

            // Verify `ls` outputs the file we anticipate
            assertEquals(devicePath.trim(), output.trim());

            // Execute `cat ${devicePath}`
            String contentsOutput =
                    mTestDevice.executeShellCommand(String.format("cat %s", devicePath));

            // Verify that `cat` output the string we expected.
            assertEquals(contents.trim(), contentsOutput.trim());
        } finally {
            if (devicePath != null) {
                mTestDevice.deleteFile(devicePath);
            }
        }
    }

    /**
     * Runs a couple basic commands like `ls` and `cat` on a file that we push
     *
     * <p>Uses the executeShellV2Command API instead of the original
     */
    @Test
    public void testExecuteShellCommandV2_basic() throws Exception {
        final String testTag = "testExecuteShellCommandV2_basic";
        String devicePath = null;

        try {
            // Construct device path
            devicePath = String.format("%s/%s", mInternalStorage, testTag);

            // Push random string to path
            String contents = generateRandomString(64);
            mTestDevice.pushString(contents, devicePath);

            // If there isn't reasonable confidence that the file
            // was pushed successfully then this is an invalid test
            assumeTrue(mTestDevice.doesFileExist(devicePath));

            // Execute command to list the path
            CommandResult outputResult =
                    mTestDevice.executeShellV2Command(String.format("ls %s", devicePath));

            // Check that the command succeeded (it should have)
            assertEquals(CommandStatus.SUCCESS, outputResult.getStatus());

            // Verify `ls` outputs the file we anticipate
            assertEquals(devicePath.trim(), outputResult.getStdout().trim());

            // Execute `cat ${devicePath}`
            CommandResult contentsOutputResult =
                    mTestDevice.executeShellV2Command(String.format("cat %s", devicePath));

            // Check the command succeeded (usually should)
            assumeTrue(CommandStatus.SUCCESS == contentsOutputResult.getStatus());

            // Verify that `cat` output the string we expected.
            assertEquals(contents.trim(), contentsOutputResult.getStdout().trim());
        } finally {
            if (devicePath != null) {
                mTestDevice.deleteFile(devicePath);
            }
        }
    }

    /** Tests one of the variants of executeShellV2Command */
    @Test
    public void testExecuteShellCommand_pipeStdin() throws Exception {
        final String testTag = "testExecuteShellCommand_pipeStdin";
        final String contents = generateRandomString(64);
        File input = FileUtil.createTempFile(testTag, "");
        FileUtil.writeToFile(contents, input);

        try {
            CommandResult result = mTestDevice.executeShellV2Command("cat", input);

            assumeTrue(CommandStatus.SUCCESS == result.getStatus());
            assertEquals(contents, result.getStdout());
        } finally {
            FileUtil.deleteFile(input);
        }
    }

    private static String generateRandomString(int length) {
        int unicodeLower = 32;
        int unicodeUpper = 126;
        Random gen = new Random();

        return gen.ints(unicodeLower, unicodeUpper + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

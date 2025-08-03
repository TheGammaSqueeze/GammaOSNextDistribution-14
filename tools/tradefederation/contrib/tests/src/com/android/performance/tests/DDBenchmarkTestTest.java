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
package com.android.performance.tests;

import com.android.performance.DDBenchmarkTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DDBenchmarkTestTest {
    public static final double DELTA = 0;

    public static void checkDDCommandOptions(String ddCommand, String[] expectedOptions) {
        for (String option : expectedOptions) Assert.assertTrue(ddCommand.contains(option));
    }

    private static void testBandwidthInMiB(
            String bandwidthS, String unit, double expectedBandwidth) {
        double bandwidth = DDBenchmarkTest.bandwidthInMiB(bandwidthS, unit);
        Assert.assertEquals(expectedBandwidth, bandwidth, DELTA);
    }

    @Test
    public void testBandwidthInMiBInputKiB() {
        testBandwidthInMiB("1024", "k/s", 1.f);
    }

    @Test
    public void testBandwidthInMiBInputMiB() {
        testBandwidthInMiB("1", "M/s", 1.f);
    }

    @Test
    public void testBandwidthInMiBInputGiB() {
        testBandwidthInMiB("1", "G/s", 1024.f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBandwidthInMiBInputUnknown() {
        String bandwidthS = "1";
        String unit = "UnknownUnit/s";
        DDBenchmarkTest.bandwidthInMiB(bandwidthS, unit);
    }

    @Test
    public void testBuildDDCommandEmpty() {
        String ddBinary = "dd";
        String inputFile = null;
        String outputFile = null;
        String inputBlockSize = null;
        String outputBlockSize = null;
        String ddBlockSize = null;
        String count = null;
        String inputFlags = null;
        String outputFlags = null;
        String conv = null;
        String ddCommand =
                DDBenchmarkTest.buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);
        String[] expectedOptions = {"dd"};
        checkDDCommandOptions(ddCommand, expectedOptions);
    }

    @Test
    public void testBuildDDCommandInputOutput() {
        String ddBinary = "dd";
        String inputFile = "/dev/zero";
        String outputFile = "/dev/null";
        String inputBlockSize = null;
        String outputBlockSize = null;
        String ddBlockSize = null;
        String count = null;
        String inputFlags = null;
        String outputFlags = null;
        String conv = null;
        String ddCommand =
                DDBenchmarkTest.buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);
        String[] expectedOptions = {"dd", " if=/dev/zero", " of=/dev/null"};
        checkDDCommandOptions(ddCommand, expectedOptions);
    }

    @Test
    public void testBuildDDCommandBlockSize() {
        String ddBinary = "dd";
        String inputFile = null;
        String outputFile = null;
        String inputBlockSize = "4k";
        String outputBlockSize = "8k";
        String ddBlockSize = "1M";
        String count = null;
        String inputFlags = null;
        String outputFlags = null;
        String conv = null;
        String ddCommand =
                DDBenchmarkTest.buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);
        String[] expectedOptions = {"dd", " ibs=4k", " obs=8k", " bs=1M"};
        checkDDCommandOptions(ddCommand, expectedOptions);
    }

    @Test
    public void testBuildDDCommandFlags() {
        String ddBinary = "dd";
        String inputFile = null;
        String outputFile = null;
        String inputBlockSize = null;
        String outputBlockSize = null;
        String ddBlockSize = null;
        String count = null;
        String inputFlags = "input_flag_test,flag2";
        String outputFlags = "output_flag_test,flag3";
        String conv = null;
        String ddCommand =
                DDBenchmarkTest.buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);
        String[] expectedOptions = {
            "dd", " iflag=input_flag_test,flag2", " oflag=output_flag_test,flag3"
        };
        checkDDCommandOptions(ddCommand, expectedOptions);
    }

    @Test
    public void testBuildDDCommandConv() {
        String ddBinary = "dd";
        String inputFile = null;
        String outputFile = null;
        String inputBlockSize = null;
        String outputBlockSize = null;
        String ddBlockSize = null;
        String count = null;
        String inputFlags = null;
        String outputFlags = null;
        String conv = "fsync";
        String ddCommand =
                DDBenchmarkTest.buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);
        String[] expectedOptions = {"dd", " conv=fsync"};
        checkDDCommandOptions(ddCommand, expectedOptions);
    }
}

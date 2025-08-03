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

import com.android.performance.InodeopBenchmarkTest;
import com.android.performance.InodeopBenchmarkTest.InodeopOutputV0;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class InodeopBenchmarkTestTest {
    private static final String MOCK_INODEOP_OUTPUT_V0 = "0;test_workload;15.2;ms";
    private static final String[] MOCK_INODEOP_OUTPUT_FIELDS_V0 = MOCK_INODEOP_OUTPUT_V0.split(";");
    private static final Map<InodeopOutputV0, String> expectedFields = Map.of(
                InodeopOutputV0.VERSION, "0",
                InodeopOutputV0.WORKLOAD, "test_workload",
                InodeopOutputV0.EXEC_TIME, "15.2",
                InodeopOutputV0.TIME_UNIT, "ms");

    private static void checkInodeopCommandOptions(
            String inodeopCommand, String[] expectedOptions) {
        for (String option : expectedOptions) Assert.assertTrue(inodeopCommand.contains(option));
    }

    @Test
    public void testGetInodeOutputFieldVersion() {
        String version =
                InodeopBenchmarkTest.getInodeopOutputField(
                        MOCK_INODEOP_OUTPUT_FIELDS_V0, InodeopOutputV0.VERSION);
        Assert.assertEquals(expectedFields.get(InodeopOutputV0.VERSION), version);
    }

    @Test
    public void testGetInodeOutputFieldWorkload() {
        String workload =
                InodeopBenchmarkTest.getInodeopOutputField(
                        MOCK_INODEOP_OUTPUT_FIELDS_V0, InodeopOutputV0.WORKLOAD);
        Assert.assertEquals(expectedFields.get(InodeopOutputV0.WORKLOAD), workload);
    }

    @Test
    public void testGetInodeOutputFieldExecTime() {
        String execTime =
                InodeopBenchmarkTest.getInodeopOutputField(
                        MOCK_INODEOP_OUTPUT_FIELDS_V0, InodeopOutputV0.EXEC_TIME);
        Assert.assertEquals(expectedFields.get(InodeopOutputV0.EXEC_TIME), execTime);
    }

    @Test
    public void testGetInodeOutputFieldTimeUnit() {
        String timeUnit =
                InodeopBenchmarkTest.getInodeopOutputField(
                        MOCK_INODEOP_OUTPUT_FIELDS_V0, InodeopOutputV0.TIME_UNIT);
        Assert.assertEquals(expectedFields.get(InodeopOutputV0.TIME_UNIT), timeUnit);
    }

    @Test
    public void testBuildInodeopCommandEmpty() {
        String inodeopBinary = "inodeop";
        String directory = null;
        String fromDirectory = null;
        String toDirectory = null;
        int nFiles = 0;
        boolean maintainState = false;
        String workload = null;
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 0"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }

    @Test
    public void testBuildInodeopCommandWorkload() {
        String inodeopBinary = "inodeop";
        String directory = null;
        String fromDirectory = null;
        String toDirectory = null;
        int nFiles = 0;
        boolean maintainState = false;
        String workload = "create";
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 0", "-w create"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }

    @Test
    public void testBuildInodeopCommandDirectory() {
        String inodeopBinary = "inodeop";
        String directory = "/test_dir";
        String fromDirectory = null;
        String toDirectory = null;
        int nFiles = 0;
        boolean maintainState = false;
        String workload = null;
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 0", "-d /test_dir"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }

    @Test
    public void testBuildInodeopCommandFromDirectory() {
        String inodeopBinary = "inodeop";
        String directory = null;
        String fromDirectory = "/test_from_dir";
        String toDirectory = null;
        int nFiles = 0;
        boolean maintainState = false;
        String workload = null;
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 0", "-f /test_from_dir"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }

    @Test
    public void testBuildInodeopCommandToDirectory() {
        String inodeopBinary = "inodeop";
        String directory = null;
        String fromDirectory = null;
        String toDirectory = "test_to_dir";
        int nFiles = 0;
        boolean maintainState = false;
        String workload = null;
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 0", "-t test_to_dir"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }

    @Test
    public void testBuildInodeopCommandMaintainState() {
        String inodeopBinary = "inodeop";
        String directory = null;
        String fromDirectory = null;
        String toDirectory = null;
        int nFiles = 5;
        boolean maintainState = true;
        String workload = null;
        String inodeopCommand =
                InodeopBenchmarkTest.buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);
        String[] expectedOptions = {"inodeop", "-n 5", "-s"};
        checkInodeopCommandOptions(inodeopCommand, expectedOptions);
    }
}

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

package com.android.tradefed.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import com.google.common.base.Throwables;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class PythonVirtualenvHelperTest {

    private File mVenvDir;

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mVenvDir);
    }

    @Test
    public void testActivate_shouldThrowNPE_whenVirtualenvPathIsNull() throws Exception {
        String nullVirtualenvPath = null;
        IRunUtil runUtil = mock(RunUtil.class);

        try {
            PythonVirtualenvHelper.activate(runUtil, nullVirtualenvPath);
            fail("Should have thrown an exception");
        } catch (NullPointerException e) {
            assertThat(
                    String.format(
                            "An unexpected exception was thrown, full stack trace: %s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    containsString("Path to the Python virtual environment should not be null"));
        }
    }

    @Test
    public void testActivate_whenVirtualenvPathIsInvalid() throws Exception {
        mVenvDir = FileUtil.createTempDir("venv");
        mVenvDir.delete();
        IRunUtil runUtil = mock(RunUtil.class);

        try {
            PythonVirtualenvHelper.activate(runUtil, mVenvDir.getAbsolutePath());
            fail("Should have thrown an exception");
        } catch (RuntimeException e) {
            assertThat(
                    String.format(
                            "An unexpected exception was thrown, full stack trace: %s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    containsString("Invalid python virtualenv path"));
        }
    }

    @Test
    public void testActivate_whenPythonBinNotFound() throws Exception {
        mVenvDir = FileUtil.createTempDir("venv");
        IRunUtil runUtil = mock(RunUtil.class);

        try {
            PythonVirtualenvHelper.activate(runUtil, mVenvDir.getAbsolutePath());
            fail("Should have thrown an exception");
        } catch (RuntimeException e) {
            assertThat(
                    String.format(
                            "An unexpected exception was thrown, full stack trace: %s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    containsString("Invalid python virtualenv path"));
        }
    }

    @Test
    public void testActivate_success() throws Exception {
        mVenvDir = FileUtil.createTempDir("venv");
        File pythonBin = new File(mVenvDir, "bin");
        pythonBin.mkdir();
        IRunUtil runUtil = mock(RunUtil.class);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(
                "Name: pip\nLocation: "
                        + Paths.get(mVenvDir.getAbsolutePath(), "lib/python3.8/site-packages"));
        when(runUtil.runTimedCmd(anyLong(), anyString(), eq("show"), eq("pip"))).thenReturn(result);

        PythonVirtualenvHelper.activate(runUtil, mVenvDir.getAbsolutePath());

        verify(runUtil)
                .setEnvVariable("PATH", pythonBin.getAbsolutePath() + ":" + System.getenv("PATH"));
        verify(runUtil).setEnvVariable("VIRTUAL_ENV", mVenvDir.getAbsolutePath());
        verify(runUtil)
                .setEnvVariable(
                        "PYTHONPATH",
                        new File(mVenvDir, "lib/python3.8/site-packages").getAbsolutePath()
                                + ":"
                                + System.getenv("PYTHONPATH"));
        verify(runUtil).unsetEnvVariable("PYTHONHOME");
    }

    @Test
    public void testActivate_pipShowFails() throws Exception {
        mVenvDir = FileUtil.createTempDir("venv");
        File pythonBin = new File(mVenvDir, "bin");
        pythonBin.mkdir();
        IRunUtil runUtil = mock(RunUtil.class);
        when(runUtil.runTimedCmd(anyLong(), anyString(), eq("show"), eq("pip")))
                .thenReturn(new CommandResult());

        try {
            PythonVirtualenvHelper.activate(runUtil, mVenvDir.getAbsolutePath());
            fail("Should have thrown an exception");
        } catch (RuntimeException e) {
            assertThat(
                    String.format(
                            "An unexpected exception was thrown, full stack trace: %s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    containsString("pip3 show pip"));
        }
    }
}

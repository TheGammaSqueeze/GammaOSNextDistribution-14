/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.base.Throwables;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

@RunWith(JUnit4.class)
public class PythonVirtualenvPreparerTest {
    private PythonVirtualenvPreparer mPreparer;
    @Mock IRunUtil mMockRunUtil;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        mPreparer = new PythonVirtualenvPreparer();
        mPreparer.mRunUtil = mMockRunUtil;
    }

    @Test
    public void testInstallDeps_reqFile_success() throws Exception {
        mPreparer.setRequirementsFile(new File("foobar"));
        when(mMockRunUtil.runTimedCmd(
                        anyLong(), (String) any(), (String) any(), (String) any(), (String) any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        IBuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo, mMockDevice);
        assertTrue(buildInfo.getFile("PYTHONPATH") != null);
    }

    @Test
    public void testInstallDeps_depModule_success() throws Exception {
        mPreparer.addDepModule("blahblah");
        when(mMockRunUtil.runTimedCmd(anyLong(), (String) any(), (String) any(), (String) any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        IBuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo, mMockDevice);
        assertTrue(buildInfo.getFile("PYTHONPATH") != null);
    }

    @Test
    public void testInstallDeps_reqFile_failure() throws Exception {
        mPreparer.setRequirementsFile(new File("foobar"));
        when(mMockRunUtil.runTimedCmd(
                        anyLong(), (String) any(), (String) any(), (String) any(), (String) any()))
                .thenReturn(new CommandResult(CommandStatus.TIMED_OUT));

        IBuildInfo buildInfo = new BuildInfo();
        try {
            mPreparer.installDeps(buildInfo, mMockDevice);
            fail("installDeps succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertTrue(buildInfo.getFile("PYTHONPATH") == null);
        }
    }

    @Test
    public void testInstallDeps_depModule_failure() throws Exception {
        mPreparer.addDepModule("blahblah");
        when(mMockRunUtil.runTimedCmd(anyLong(), (String) any(), (String) any(), (String) any()))
                .thenReturn(new CommandResult(CommandStatus.TIMED_OUT));

        IBuildInfo buildInfo = new BuildInfo();
        try {
            mPreparer.installDeps(buildInfo, mMockDevice);
            fail("installDeps succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertTrue(buildInfo.getFile("PYTHONPATH") == null);
        }
    }

    @Test
    public void testInstallDeps_noDeps() throws Exception {
        BuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo, mMockDevice);
        assertTrue(buildInfo.getFile("PYTHONPATH") == null);
    }

    @Test
    public void testStartVirtualenv_throwTSE_whenVirtualenvNotFound() throws Exception {
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("bash: virtualenv: command not found");
        when(mMockRunUtil.runTimedCmd(anyLong(), Mockito.eq("virtualenv"), Mockito.eq("--version")))
                .thenReturn(result);

        try {
            mPreparer.startVirtualenv(new BuildInfo(), mMockDevice);
            fail("startVirtualenv succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertThat(
                    String.format(
                            "An unexpected exception was thrown:\n%s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    containsString("virtualenv is not installed."));
        }
    }

    @Test
    public void testStartVirtualenv_throwTSE_whenVirtualenvIsTooOld() throws Exception {
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("virtualenv 16.7.10 from /path/to/site-packages/virtualenv/__init__.py");
        when(mMockRunUtil.runTimedCmd(anyLong(), Mockito.eq("virtualenv"), Mockito.eq("--version")))
                .thenReturn(result);

        try {
            mPreparer.startVirtualenv(new BuildInfo(), mMockDevice);
            fail("startVirtualenv succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertEquals(
                    String.format(
                            "An unexpected exception was thrown:\n%s",
                            Throwables.getStackTraceAsString(e)),
                    e.getMessage(),
                    "virtualenv is too old. Required: >=20.0.1, yours: 16.7.10");
        }
    }
}

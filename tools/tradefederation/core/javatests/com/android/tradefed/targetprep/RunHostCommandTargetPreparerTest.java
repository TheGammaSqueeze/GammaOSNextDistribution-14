/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.host.IHostOptions.PermitLimitType;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link RunHostCommandTargetPreparer}. */
@RunWith(JUnit4.class)
public final class RunHostCommandTargetPreparerTest {

    private static final String DEVICE_SERIAL = "123456";
    private static final String FULL_COMMAND = "command  argument $SERIAL";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    @Mock private RunHostCommandTargetPreparer.BgCommandLog mBgCommandLog;
    @Mock private IRunUtil mRunUtil;
    @Mock private IHostOptions mHostOptions;
    private RunHostCommandTargetPreparer mPreparer;

    @Before
    public void setUp() {
        mPreparer =
                new RunHostCommandTargetPreparer() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mRunUtil;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        return mHostOptions;
                    }

                    @Override
                    protected List<BgCommandLog> createBgCommandLogs() {
                        return Collections.singletonList(mBgCommandLog);
                    }
                };
        when(mTestInfo.getDevice().getSerialNumber()).thenReturn(DEVICE_SERIAL);
        // Default to successful execution.
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        when(mRunUtil.runTimedCmd(anyLong(), any())).thenReturn(result);
    }

    @Test
    public void testSetUp() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-setup-command", FULL_COMMAND);
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        // Verify timeout and command (split, removed whitespace, and device serial)
        mPreparer.setUp(mTestInfo);
        verify(mRunUtil).runTimedCmd(eq(10L), eq("command"), eq("argument"), eq(DEVICE_SERIAL));

        // No flashing permit taken/returned by default
        verify(mHostOptions, never()).takePermit(PermitLimitType.CONCURRENT_FLASHER);
        verify(mHostOptions, never()).returnPermit(PermitLimitType.CONCURRENT_FLASHER);
    }

    @Test
    public void testSetUp_withWorkDir() throws Exception {
        final OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("work-dir", "/working/directory");
        optionSetter.setOptionValue("host-setup-command", "command");
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        // Verify working directory and command execution
        mPreparer.setUp(mTestInfo);
        verify(mRunUtil).setWorkingDir(any());
        verify(mRunUtil).runTimedCmd(eq(10L), eq("command"));
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUp_withErrors() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-setup-command", "command");
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        // Verify that failed commands will throw exception during setup
        CommandResult result = new CommandResult(CommandStatus.FAILED);
        when(mRunUtil.runTimedCmd(anyLong(), any())).thenReturn(result);
        mPreparer.setUp(mTestInfo);
    }

    @Test
    public void testSetUp_flashingPermit() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-setup-command", FULL_COMMAND);
        optionSetter.setOptionValue("use-flashing-permit", "true");

        // Verify command ran with flashing permit
        mPreparer.setUp(mTestInfo);
        InOrder inOrder = inOrder(mRunUtil, mHostOptions);
        inOrder.verify(mHostOptions).takePermit(PermitLimitType.CONCURRENT_FLASHER);
        inOrder.verify(mRunUtil)
                .runTimedCmd(anyLong(), eq("command"), eq("argument"), eq(DEVICE_SERIAL));
        inOrder.verify(mHostOptions).returnPermit(PermitLimitType.CONCURRENT_FLASHER);
    }

    @Test
    public void testSetUp_quotationMarks() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-setup-command", "command \"group of arguments\"");

        // Verify that arguments surrounded by quotation marks are not split.
        mPreparer.setUp(mTestInfo);
        verify(mRunUtil).runTimedCmd(anyLong(), eq("command"), eq("group of arguments"));
    }

    @Test
    public void testTearDown() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-teardown-command", FULL_COMMAND);
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        // Verify timeout and command (split, removed whitespace, and device serial)
        mPreparer.tearDown(mTestInfo, null);
        verify(mRunUtil).runTimedCmd(eq(10L), eq("command"), eq("argument"), eq(DEVICE_SERIAL));

        // No flashing permit taken/returned by default
        verify(mHostOptions, never()).takePermit(PermitLimitType.CONCURRENT_FLASHER);
        verify(mHostOptions, never()).returnPermit(PermitLimitType.CONCURRENT_FLASHER);
    }

    @Test
    public void testTearDown_withError() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-teardown-command", "command");
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        // Verify that failed commands will NOT throw exception during teardown
        CommandResult result = new CommandResult(CommandStatus.FAILED);
        when(mRunUtil.runTimedCmd(anyLong(), any())).thenReturn(result);
        mPreparer.tearDown(mTestInfo, null);
    }

    @Test
    public void testTearDown_flashingPermit() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-teardown-command", FULL_COMMAND);
        optionSetter.setOptionValue("use-flashing-permit", "true");

        // Verify command ran with flashing permit
        mPreparer.tearDown(mTestInfo, null);
        InOrder inOrder = inOrder(mRunUtil, mHostOptions);
        inOrder.verify(mHostOptions).takePermit(PermitLimitType.CONCURRENT_FLASHER);
        inOrder.verify(mRunUtil)
                .runTimedCmd(anyLong(), eq("command"), eq("argument"), eq(DEVICE_SERIAL));
        inOrder.verify(mHostOptions).returnPermit(PermitLimitType.CONCURRENT_FLASHER);
    }

    @Test
    public void testBgCommand() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-background-command", FULL_COMMAND);

        when(mRunUtil.runCmdInBackground(anyList(), any())).thenReturn(mock(Process.class));
        OutputStream os = mock(OutputStream.class);
        when(mBgCommandLog.getOutputStream()).thenReturn(os);

        // Verify command (split, removed whitespace, and device serial) and output stream
        mPreparer.setUp(mTestInfo);
        verify(mRunUtil)
                .runCmdInBackground(
                        eq(Arrays.asList("command", "argument", DEVICE_SERIAL)), eq(os));
    }

    @Test
    public void testSetUp_extraFile() throws Exception {
        BuildInfo stubBuild = new BuildInfo("stub", "stub");
        File test1 = tmpDir.newFile("test1");
        stubBuild.setFile("test1", test1, "0");
        when(mTestInfo.getBuildInfo()).thenReturn(stubBuild);

        OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue(
                "host-setup-command", "command $EXTRA_FILE(test1) $EXTRA_FILE(test2)");

        // Absolute paths are used for existing files and $EXTRA_FILE for missing files.
        mPreparer.setUp(mTestInfo);
        verify(mRunUtil)
                .runTimedCmd(
                        anyLong(),
                        eq("command"),
                        eq(test1.getAbsolutePath()),
                        eq("$EXTRA_FILE(test2)"));
    }
}

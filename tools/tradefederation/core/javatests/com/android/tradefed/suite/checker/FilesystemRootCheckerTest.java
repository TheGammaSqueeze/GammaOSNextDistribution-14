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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link FilesystemRootChecker} */
@RunWith(JUnit4.class)
public class FilesystemRootCheckerTest {

    private FilesystemRootChecker mChecker;
    private ITestDevice mMockDevice;

    @Before
    public void setup() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        mChecker = new FilesystemRootChecker();

        CommandResult defaultResult = new CommandResult(CommandStatus.SUCCESS);
        defaultResult.setStdout("");
        defaultResult.setStderr("");
        defaultResult.setExitCode(0);
        when(mMockDevice.executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND)))
                .thenReturn(defaultResult);
    }

    /* Test that having no root files is successful */
    @Test
    public void testNoRootFiles() throws Exception {
        /*
         * Generated with:
         * $ adb unroot
         * $ adb shell find /data/local/tmp -user root
         */
        String findNoRoot = "";
        CommandResult findNoRootResult = new CommandResult(CommandStatus.SUCCESS);
        findNoRootResult.setStdout(findNoRoot);
        when(mMockDevice.executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND)))
                .thenReturn(findNoRootResult);

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2))
                .executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND));
    }

    /* Test that a module or test leaking a root-owned file is a failure */
    @Test
    public void testRootFile() throws Exception {
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        /*
         * Generated with:
         * $ adb root
         * $ adb shell touch /data/local/tmp/rootfile
         * $ adb shell find /data/local/tmp -user root
         */
        String findRootFile = "/data/local/tmp/rootfile";
        CommandResult findRootFileResult = new CommandResult(CommandStatus.SUCCESS);
        findRootFileResult.setStdout(findRootFile);
        when(mMockDevice.executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND)))
                .thenReturn(findRootFileResult);

        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2))
                .executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND));
    }

    /* Test that a module or test leaking a root-owned directory is a failure */
    @Test
    public void testRootDirectory() throws Exception {
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        /*
         * Generated with:
         * $ adb root
         * $ adb shell touch /data/local/tmp/rootfile
         * $ adb shell find /data/local/tmp -user root
         */
        String findRootDirectory = "/data/local/tmp/rootdirectory";
        CommandResult findRootDirectoryResult = new CommandResult(CommandStatus.SUCCESS);
        findRootDirectoryResult.setStdout(findRootDirectory);
        when(mMockDevice.executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND)))
                .thenReturn(findRootDirectoryResult);

        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2))
                .executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND));
    }

    /* Test that a module starting with root-owned files is a failure */
    @Test
    public void testModuleStartWithRootFile() throws Exception {
        /*
         * Generated with:
         * $ adb root
         * $ adb shell touch /data/local/tmp/rootfile
         * $ adb shell find /data/local/tmp -user root
         */
        String findRootFile = "/data/local/tmp/rootfile";
        CommandResult findRootFileResult = new CommandResult(CommandStatus.SUCCESS);
        findRootFileResult.setStdout(findRootFile);
        when(mMockDevice.executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND)))
                .thenReturn(findRootFileResult);

        assertEquals(CheckStatus.FAILED, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2))
                .executeShellV2Command(Mockito.eq(FilesystemRootChecker.FIND_ROOT_COMMAND));
    }
}

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
package com.android.csuite.core;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Jimfs;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public final class AppCrawlTesterHostPreparerTest {
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
    ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    TestInformation mTestInfo = createTestInfo();
    IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);

    @Test
    public void getSdkPath_wasSet_returnsPath() {
        Path path = Path.of("some");
        AppCrawlTesterHostPreparer.setSdkPath(mTestInfo, path);

        String result = AppCrawlTesterHostPreparer.getSdkPath(mTestInfo);

        assertThat(result).isEqualTo(path.toString());
    }

    @Test
    public void getSdkPath_wasNotSet_returnsNull() {
        String result = AppCrawlTesterHostPreparer.getSdkPath(mTestInfo);

        assertNull(result);
    }

    @Test
    public void getCrawlerBinPath_wasSet_returnsPath() {
        Path path = Path.of("some");
        AppCrawlTesterHostPreparer.setCrawlerBinPath(mTestInfo, path);

        String result = AppCrawlTesterHostPreparer.getCrawlerBinPath(mTestInfo);

        assertThat(result).isEqualTo(path.toString());
    }

    @Test
    public void getCrawlerBinPath_wasNotSet_returnsNull() {
        String result = AppCrawlTesterHostPreparer.getCrawlerBinPath(mTestInfo);

        assertNull(result);
    }

    @Test
    public void getCredentialPath_wasSet_returnsPath() {
        Path path = Path.of("some");
        AppCrawlTesterHostPreparer.setCredentialPath(mTestInfo, path);

        String result = AppCrawlTesterHostPreparer.getCredentialPath(mTestInfo);

        assertThat(result).isEqualTo(path.toString());
    }

    @Test
    public void getCredentialPath_wasNotSet_returnsNull() {
        String result = AppCrawlTesterHostPreparer.getCredentialPath(mTestInfo);

        assertNull(result);
    }

    @Test
    public void setUp_commandsFailed_throwsException() throws Exception {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createFailedCommandResult());
        AppCrawlTesterHostPreparer suj = createTestSubject();

        assertThrows(TargetSetupError.class, () -> suj.setUp(mTestInfo));
    }

    @Test
    public void isReady_setUpCommandsSucceed_returnsTrue() throws Exception {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        AppCrawlTesterHostPreparer suj = createTestSubject();
        suj.setUp(mTestInfo);

        boolean ready = AppCrawlTesterHostPreparer.isReady(mTestInfo);

        assertThat(ready).isTrue();
    }

    @Test
    public void isReady_setUpFailed_returnsFalse() throws Exception {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createFailedCommandResult());
        AppCrawlTesterHostPreparer suj = createTestSubject();
        assertThrows(TargetSetupError.class, () -> suj.setUp(mTestInfo));

        boolean ready = AppCrawlTesterHostPreparer.isReady(mTestInfo);

        assertThat(ready).isFalse();
    }

    @Test
    public void isReady_preparerNotExecuted_returnsFalse() throws Exception {
        boolean ready = AppCrawlTesterHostPreparer.isReady(mTestInfo);

        assertThat(ready).isFalse();
    }

    private AppCrawlTesterHostPreparer createTestSubject() throws Exception {
        AppCrawlTesterHostPreparer suj =
                new AppCrawlTesterHostPreparer(() -> mRunUtil, mFileSystem);
        OptionSetter optionSetter = new OptionSetter(suj);
        optionSetter.setOptionValue(
                AppCrawlTesterHostPreparer.SDK_TAR_OPTION,
                Files.createDirectories(mFileSystem.getPath("sdk")).toString());
        optionSetter.setOptionValue(
                AppCrawlTesterHostPreparer.CRAWLER_BIN_OPTION,
                Files.createDirectories(mFileSystem.getPath("bin")).toString());
        optionSetter.setOptionValue(
                AppCrawlTesterHostPreparer.CREDENTIAL_JSON_OPTION,
                Files.createDirectories(mFileSystem.getPath("cred.json")).toString());
        return suj;
    }

    private TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", mDevice);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout("");
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }
}

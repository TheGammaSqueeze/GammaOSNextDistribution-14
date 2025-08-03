/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;

import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RunWith(JUnit4.class)
public final class ApkInstallerTest {
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void install_failedToListApks_throwsException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("not_apk.file"));
        ApkInstaller sut =
                new ApkInstaller("serial", Mockito.mock(IRunUtil.class), apk -> "package.name");

        assertThrows(ApkInstallerException.class, () -> sut.install(root));
    }

    @Test
    public void install_installCommandFailed_throwsException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createFailedCommandResult());
        ApkInstaller sut = new ApkInstaller("serial", runUtil, apk -> "package.name");

        assertThrows(ApkInstallerException.class, () -> sut.install(root));
    }

    @Test
    public void install_installCommandSucceed_doesNotThrow() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        ApkInstaller sut = new ApkInstaller("serial", runUtil, apk -> "package.name");

        sut.install(root);
    }

    @Test
    public void install_parsePackageNameFailed_throwsException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        ApkInstaller sut =
                new ApkInstaller(
                        "serial",
                        runUtil,
                        apk -> {
                            throw new IOException();
                        });

        assertThrows(ApkInstallerException.class, () -> sut.install(root));
    }

    @Test
    public void install_obbExists_installObb() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Path apkPath = root.resolve("base.apk");
        Files.createFile(apkPath);
        Path obbPath = root.resolve("main.obb");
        Files.createFile(obbPath);
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        ApkInstaller sut = new ApkInstaller("serial", runUtil, apk -> "package.name");

        sut.install(root);

        ArgumentCaptor<String> cmdCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(runUtil, Mockito.atLeastOnce()).runTimedCmd(anyLong(), cmdCaptor.capture());
        List<String> capturedArgs = cmdCaptor.getAllValues();
        assertTrue(capturedArgs.stream().anyMatch(arg -> arg.contains("push")));
        assertTrue(capturedArgs.stream().anyMatch(arg -> arg.contains("rm")));
        assertTrue(capturedArgs.stream().anyMatch(arg -> arg.contains("mkdir")));
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
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

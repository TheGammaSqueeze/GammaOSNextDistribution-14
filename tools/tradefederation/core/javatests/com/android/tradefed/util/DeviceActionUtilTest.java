/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;

@RunWith(JUnit4.class)
public final class DeviceActionUtilTest {

    private static final String SERIAL = "serial";
    private static final long TIMEOUT = 3600 * 1000;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock IRunUtil mMockRuntil;
    @Mock CommandResult mCommandResult;

    private File mDeviceActionMainJar;
    private File mBundletoolJar;
    private File mCredential;
    private File mGenDir;
    private File mTmpDir;
    private File mAdb;
    private File mAapt;
    private DeviceActionUtil mDeviceActionUtil;

    @Before
    public void setUp() throws Exception {
        when(mMockRuntil.runTimedCmd(anyLong(), any())).thenReturn(mCommandResult);
        mDeviceActionMainJar = tempFolder.newFile("DeviceActionMain_deploy.jar");
        mBundletoolJar = tempFolder.newFile("bundletool.jar");
        mCredential = tempFolder.newFile("key.json");
        mTmpDir = tempFolder.newFolder("tmp-dir");
        mGenDir = tempFolder.newFolder("gen-dir");
        mAdb = tempFolder.newFile("adb");
        mAapt = tempFolder.newFile("aapt");
        mDeviceActionUtil =
                new DeviceActionUtil(
                        mDeviceActionMainJar,
                        DeviceActionUtil.createConfigFlags(
                                mBundletoolJar,
                                mCredential,
                                mGenDir,
                                mTmpDir,
                                mAdb,
                                mAapt,
                                SystemUtil.getRunningJavaBinaryPath()),
                        mMockRuntil,
                        mGenDir);
    }

    @Test
    public void execute_installMainline() {
        mDeviceActionUtil.execute(
                DeviceActionUtil.Command.INSTALL_MAINLINE,
                SERIAL,
                ImmutableList.of(
                        "enable_rollback",
                        "file_mainline_modules=apk1, file_mainline_modules=apk2"));

        verify(mMockRuntil)
                .runTimedCmd(
                        TIMEOUT,
                        SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                        "-Djava.util.logging.config.class=com.google.common.logging.GoogleConsoleLogConfig",
                        "-Dgoogle.debug_log_levels=*=INFO",
                        "-jar",
                        mDeviceActionMainJar.getAbsolutePath(),
                        "install_mainline",
                        "--da_bundletool",
                        mBundletoolJar.getAbsolutePath(),
                        "--da_cred_file",
                        mCredential.getAbsolutePath(),
                        "--da_gen_file_dir",
                        mGenDir.getAbsolutePath(),
                        "--tmp_dir_root",
                        mTmpDir.getAbsolutePath(),
                        "--adb",
                        mAdb.getAbsolutePath(),
                        "--aapt",
                        mAapt.getAbsolutePath(),
                        "--java_command_path",
                        SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                        "--device1",
                        "serial=serial",
                        "--action",
                        "enable_rollback",
                        "--action",
                        "file_mainline_modules=apk1, file_mainline_modules=apk2");
    }

    @Test
    public void execute_reset() {
        mDeviceActionUtil.execute(DeviceActionUtil.Command.RESET, SERIAL, ImmutableList.of());

        verify(mMockRuntil)
                .runTimedCmd(
                        TIMEOUT,
                        SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                        "-Djava.util.logging.config.class=com.google.common.logging.GoogleConsoleLogConfig",
                        "-Dgoogle.debug_log_levels=*=INFO",
                        "-jar",
                        mDeviceActionMainJar.getAbsolutePath(),
                        "reset",
                        "--da_bundletool",
                        mBundletoolJar.getAbsolutePath(),
                        "--da_cred_file",
                        mCredential.getAbsolutePath(),
                        "--da_gen_file_dir",
                        mGenDir.getAbsolutePath(),
                        "--tmp_dir_root",
                        mTmpDir.getAbsolutePath(),
                        "--adb",
                        mAdb.getAbsolutePath(),
                        "--aapt",
                        mAapt.getAbsolutePath(),
                        "--java_command_path",
                        SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                        "--device1",
                        "serial=serial");
    }
}

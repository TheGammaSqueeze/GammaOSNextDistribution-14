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

package com.android.tradefed.targetprep;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.DeviceActionUtil;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(JUnit4.class)
public final class DeviceActionTargetPreparerTest {

    private static final String SERIAL = "serial";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock DeviceActionUtil mockDeviceActionUtil;
    @Mock ITestLogger mockTestLogger;
    @Mock ITestDevice mockDevice;
    @Mock CommandResult mCommandResult;

    private OptionSetter mSetter;
    private TestInformation mTestInfo;
    private DeviceActionTargetPreparer mDeviceActionTargetPreparer;

    @Before
    public void setUp() throws Exception {
        File deviceActionJar = tempFolder.newFile("DeviceActionMainline_deploy.jar");
        File bundletoolJar = tempFolder.newFile("bundletool.jar");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice(SERIAL, mockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        when(mockDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mockDeviceActionUtil.execute(any(), anyString(), anyList()))
                .thenReturn(mCommandResult);
        when(mCommandResult.getStderr()).thenReturn("");
        when(mCommandResult.getStdout()).thenReturn("");
        when(mCommandResult.getStatus()).thenReturn(CommandStatus.SUCCESS);
        when(mCommandResult.getExitCode()).thenReturn(0);
        mDeviceActionTargetPreparer = new DeviceActionTargetPreparer();
        mSetter = new OptionSetter(mDeviceActionTargetPreparer);
        mSetter.setOptionValue("bundletool-jar", bundletoolJar.getAbsolutePath());
        mSetter.setOptionValue("device-action-jar", deviceActionJar.getAbsolutePath());
        mDeviceActionTargetPreparer.setDeviceActionUtil(mockDeviceActionUtil);
        mDeviceActionTargetPreparer.setTestLogger(mockTestLogger);
    }

    @Test
    public void setUp_resetSuccess() throws Exception {
        mSetter.setOptionValue("da-command", "RESET");

        mDeviceActionTargetPreparer.setUp(mTestInfo);

        verify(mockDeviceActionUtil)
                .execute(DeviceActionUtil.Command.RESET, SERIAL, ImmutableList.of());
    }

    @Test
    public void setUp_installMainlineModules() throws Exception {
        File apk1 = new File("apk1");
        File apk2 = new File("apk2");
        mSetter.setOptionValue("da-command", "INSTALL_MAINLINE");
        mSetter.setOptionValue("enable-rollback", "false");
        mSetter.setOptionValue("mainline-modules", apk1.getAbsolutePath());
        mSetter.setOptionValue("mainline-modules", apk2.getAbsolutePath());

        mDeviceActionTargetPreparer.setUp(mTestInfo);

        ArgumentCaptor<List> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockDeviceActionUtil)
                .execute(
                        eq(DeviceActionUtil.Command.INSTALL_MAINLINE),
                        eq(SERIAL),
                        argCaptor.capture());
        List<String> args = argCaptor.getValue();
        assertThat(args).hasSize(1);
        assertThat(args)
                .containsAnyOf(
                        String.format(
                                "file_mainline_modules=%s, file_mainline_modules=%s",
                                apk1.getAbsolutePath(), apk2.getAbsolutePath()),
                        String.format(
                                "file_mainline_modules=%s, file_mainline_modules=%s",
                                apk2.getAbsolutePath(), apk1.getAbsolutePath()));
    }

    @Test
    public void setUp_installApksZips() throws Exception {
        File zip1 = new File("zip1");
        File zip2 = new File("zip2");
        mSetter.setOptionValue("da-command", "INSTALL_MAINLINE");
        mSetter.setOptionValue("enable-rollback", "true");
        mSetter.setOptionValue("apks-zips", zip1.getAbsolutePath());
        mSetter.setOptionValue("apks-zips", zip2.getAbsolutePath());

        mDeviceActionTargetPreparer.setUp(mTestInfo);

        ArgumentCaptor<List> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockDeviceActionUtil)
                .execute(
                        eq(DeviceActionUtil.Command.INSTALL_MAINLINE),
                        eq(SERIAL),
                        argCaptor.capture());
        List<String> args = argCaptor.getValue();
        assertThat(args).hasSize(2);
        assertThat(args).contains("enable_rollback");
        assertThat(args)
                .containsAnyOf(
                        String.format(
                                "file_apks_zips=%s, file_apks_zips=%s",
                                zip1.getAbsolutePath(), zip2.getAbsolutePath()),
                        String.format(
                                "file_apks_zips=%s, file_apks_zips=%s",
                                zip2.getAbsolutePath(), zip1.getAbsolutePath()));
    }

    @Test
    public void setUp_installTrainFolder() throws Exception {
        File trainFolder = tempFolder.newFolder("train");
        mSetter.setOptionValue("da-command", "INSTALL_MAINLINE");
        mSetter.setOptionValue("dev-key-signed", "true");
        mSetter.setOptionValue("train-folder", trainFolder.getAbsolutePath());

        mDeviceActionTargetPreparer.setUp(mTestInfo);

        verify(mockDeviceActionUtil)
                .execute(
                        DeviceActionUtil.Command.INSTALL_MAINLINE,
                        SERIAL,
                        ImmutableList.of(
                                String.format(
                                        "file_train_folder=%s", trainFolder.getAbsolutePath()),
                                "enable_rollback",
                                "dev_key_signed"));
    }

    @Test
    public void setUp_doNothingIfNoModuleToInstall() throws Exception {
        mSetter.setOptionValue("da-command", "INSTALL_MAINLINE");

        mDeviceActionTargetPreparer.setUp(mTestInfo);

        verify(mockDeviceActionUtil, never())
                .execute(any(DeviceActionUtil.Command.class), anyString(), anyList());
    }

    @Test
    public void setUp_executeFailureThrowExecutionException() throws Exception {
        mSetter.setOptionValue("da-command", "RESET");
        when(mCommandResult.getExitCode()).thenReturn(1);

        TargetSetupError t =
                assertThrows(
                        TargetSetupError.class, () -> mDeviceActionTargetPreparer.setUp(mTestInfo));

        assertThat(t.getErrorId()).isEqualTo(DeviceErrorIdentifier.DEVICE_ACTION_EXECUTION_FAILURE);
        verify(mockDeviceActionUtil).saveToLogs(DeviceActionUtil.Command.RESET, mockTestLogger);
    }

    @Test
    public void setUp_saveLogErrorThrowInfraException() throws Exception {
        mSetter.setOptionValue("da-command", "RESET");
        doThrow(new IOException()).when(mockDeviceActionUtil).generateLogFile(mCommandResult);

        TargetSetupError t =
                assertThrows(
                        TargetSetupError.class, () -> mDeviceActionTargetPreparer.setUp(mTestInfo));

        assertThat(t.getErrorId()).isEqualTo(InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
    }

    @Test
    public void setUp_saveLogErrorAndExectionFailureThrowExecutionException() throws Exception {
        mSetter.setOptionValue("da-command", "RESET");
        when(mCommandResult.getExitCode()).thenReturn(1);
        doThrow(new IOException()).when(mockDeviceActionUtil).generateLogFile(mCommandResult);

        TargetSetupError t =
                assertThrows(
                        TargetSetupError.class, () -> mDeviceActionTargetPreparer.setUp(mTestInfo));

        assertThat(t.getErrorId()).isEqualTo(DeviceErrorIdentifier.DEVICE_ACTION_EXECUTION_FAILURE);
    }
}

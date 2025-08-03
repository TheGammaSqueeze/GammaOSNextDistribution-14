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
package com.android.tradefed.targetprep;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

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
import java.nio.file.Files;

/** Unit tests for {@link FeatureFlagTargetPreparer}. */
@RunWith(JUnit4.class)
public class FeatureFlagTargetPreparerTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final TemporaryFolder mTmpDir = new TemporaryFolder();

    @Mock private TestInformation mTestInfo;
    @Mock private ITestDevice mDevice;

    private static final String DEFAULT_CONFIG = "namespace/f=v\n";
    private FeatureFlagTargetPreparer mPreparer;
    private CommandResult mCommandResult;

    @Before
    public void setUp() throws Exception {
        mPreparer = new FeatureFlagTargetPreparer();
        when(mTestInfo.getDevice()).thenReturn(mDevice);
        // Default to successful command execution.
        mCommandResult = new CommandResult(CommandStatus.SUCCESS);
        when(mDevice.executeShellV2Command(anyString())).thenReturn(mCommandResult);
        // Defaults to rebooting after each file (to test individual file updates).
        new OptionSetter(mPreparer).setOptionValue("reboot-between-flag-files", "true");
    }

    @Test
    public void testSetUpAndTearDown_oneFlagFile_newAndUpdatedFlags() throws Exception {
        mCommandResult.setStdout("namespace/f=v\n");
        addFlagFile("namespace/f=v1\nnamespace/f1=v1\n");

        // Updates to parsed flags (modify f and add f1) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v1'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v1'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (revert f and delete f2) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v'"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f1'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_oneFlagFile_nullValueFlagInDeviceConfig() throws Exception {
        mCommandResult.setStdout("namespace/f=\n");
        addFlagFile("namespace/f=v\n");

        // Updates to parsed flags (modify f) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (revert f) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_oneFlagFile_nullValueFlagInFile() throws Exception {
        mCommandResult.setStdout("namespace/f=v\n");
        addFlagFile("namespace/f=\n");

        // Updates to parsed flags (modify f) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (revert f) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_twoFlagFiles_allDifferentFlags() throws Exception {
        setFlagFilesAndDeviceConfigLists(
                "namespace/f1=v1\nnamespace/f2=v2\nnamespace1/f3=v3\n",
                "namespace/f1=v2\nnamespace/f2=v3\nnamespace2/f3=v4\n");

        // Updates to parsed flags (add all flags from files) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(2)).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v1'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f2' 'v2'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace1' 'f3' 'v3'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v2'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f2' 'v3'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace2' 'f3' 'v4'"));
        verify(mDevice, times(2)).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (delete all flags from files) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace2' 'f3'"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace1' 'f3'"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f1'"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f2'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_twoFlagFiles_sameFlagInDeviceConfigAndFiles()
            throws Exception {
        setFlagFilesAndDeviceConfigLists(DEFAULT_CONFIG, DEFAULT_CONFIG, DEFAULT_CONFIG);

        // Updates to parsed flags (no change) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(2)).executeShellV2Command(eq("device_config list"));
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (no change and no reboot) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_twoFlagFiles_sameFlagInDeviceConfigAndSecondFile()
            throws Exception {
        setFlagFilesAndDeviceConfigLists("namespace/f1=v1\n", "namespace/f=v\nnamespace/f1=v2\n");

        // Updates to parsed flags (add f1 and update its value) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(2)).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v1'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v2'"));
        verify(mDevice, times(2)).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (delete f1 only) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f1'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_twoFlagFiles_updatedFlagInFirstFile() throws Exception {
        setFlagFilesAndDeviceConfigLists("namespace/f=v1\n", DEFAULT_CONFIG);

        // Updates to parsed flags (update f1 twice) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(2)).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v1'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v'"));
        verify(mDevice, times(2)).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (no change and no reboot) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_twoFlagFiles_updatedFlagInSecondFile() throws Exception {
        setFlagFilesAndDeviceConfigLists(DEFAULT_CONFIG, "namespace/f=v1\n", DEFAULT_CONFIG);

        // Updates to parsed flags (update f once) during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(2)).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v1'"));
        verify(mDevice, times(1)).reboot(); // 1 time to apply namespace/f=v2
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags (update f once) during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f' 'v'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUpAndTearDown_additionalFlagValues() throws Exception {
        mCommandResult.setStdout("namespace/f1=v1\n");
        new OptionSetter(mPreparer).setOptionValue("flag-value", "namespace/f1=v2");
        new OptionSetter(mPreparer).setOptionValue("flag-value", "namespace/f2=v3");

        // Updates according to additional flag values during setUp and reboots.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(1)).executeShellV2Command(eq("device_config list"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v2'"));
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f2' 'v3'"));
        verify(mDevice, times(1)).reboot();
        verifyNoMoreInteractions(mDevice);

        // Reverts to previous flags during tearDown and reboots.
        clearInvocations(mDevice);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice).executeShellV2Command(eq("device_config put 'namespace' 'f1' 'v1'"));
        verify(mDevice).executeShellV2Command(eq("device_config delete 'namespace' 'f2'"));
        verify(mDevice).reboot();
        verifyNoMoreInteractions(mDevice);
    }

    @Test
    public void testSetUp_withoutRebootBetweenFiles() throws Exception {
        mCommandResult.setStdout("namespace/f1=v1\n");
        new OptionSetter(mPreparer).setOptionValue("reboot-between-flag-files", "false");

        // No reboot if no flags are updated.
        addFlagFile("namespace/f1=v1\n");
        mPreparer.setUp(mTestInfo);
        verify(mDevice, never()).reboot();

        // Single reboot even with multiple files/values updated.
        addFlagFile("namespace/f2=v2\n");
        addFlagFile("namespace/f3=v3\n");
        new OptionSetter(mPreparer).setOptionValue("flag-value", "namespace/f4=v4");
        mPreparer.setUp(mTestInfo);
        verify(mDevice, times(1)).reboot();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUp_fileNotFound() throws Exception {
        File file = addFlagFile("");
        file.delete();
        // Throws if the flag file is not found.
        mPreparer.setUp(mTestInfo);
    }

    @Test
    public void testSetUp_commandWithoutOptions() throws Exception {
        // Preparer should succeed when no options given in the command.
        mPreparer.setUp(mTestInfo);
        verify(mDevice, never()).reboot();
    }

    @Test
    public void testSetUp_ignoreInvalid() throws Exception {
        mCommandResult.setStdout("");
        addFlagFile("invalid=data\n");

        // Invalid flag data is ignored, and reboot skipped (nothing to update/revert).
        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice, never()).executeShellV2Command(startsWith("device_config put"));
        verify(mDevice, never()).executeShellV2Command(eq("device_config delete"));
        verify(mDevice, never()).reboot();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUp_invalidAdditionalFlags() throws Exception {
        new OptionSetter(mPreparer).setOptionValue("flag-value", "invalid=data");
        mPreparer.setUp(mTestInfo);
    }

    @Test
    public void testSetUp_ignoreUnchanged() throws Exception {
        mCommandResult.setStdout("namespace/flag=value\n");
        addFlagFile("namespace/flag=value\n");
        new OptionSetter(mPreparer).setOptionValue("flag-value", "namespace/flag=value");

        // Unchanged flags are not updated/reverted, and reboot skipped (nothing to update/revert).
        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);
        verify(mDevice, never()).executeShellV2Command(startsWith("device_config put"));
        verify(mDevice, never()).executeShellV2Command(eq("device_config delete"));
        verify(mDevice, never()).reboot();
    }

    private File addFlagFile(String content) throws Exception {
        File file = mTmpDir.newFile();
        Files.writeString(file.toPath(), content);
        new OptionSetter(mPreparer).setOptionValue("flag-file", file.getAbsolutePath());
        return file;
    }

    private void setFlagFilesAndDeviceConfigLists(String flagFile1Content, String flagFile2Content)
            throws Exception {
        setFlagFilesAndDeviceConfigLists(
                flagFile1Content, flagFile2Content, DEFAULT_CONFIG + flagFile1Content);
    }

    private void setFlagFilesAndDeviceConfigLists(
            String flagFile1Content, String flagFile2Content, String listCommandResult2Stdout)
            throws Exception {
        addFlagFile(flagFile1Content);
        addFlagFile(flagFile2Content);
        CommandResult listCommandResult1 = new CommandResult(CommandStatus.SUCCESS);
        listCommandResult1.setStdout(DEFAULT_CONFIG);
        CommandResult listCommandResult2 = new CommandResult(CommandStatus.SUCCESS);
        listCommandResult2.setStdout(listCommandResult2Stdout);

        when(mDevice.executeShellV2Command("device_config list"))
                .thenReturn(listCommandResult1, listCommandResult2);
    }
}

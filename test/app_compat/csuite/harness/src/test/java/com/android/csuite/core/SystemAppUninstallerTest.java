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
package com.android.csuite.core;

import static org.testng.Assert.assertThrows;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;



import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SystemAppUninstallerTest {
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String SYSTEM_APP_INSTALL_DIRECTORY = "/system/app";
    private static final String CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX = "pm list packages ";
    private static final String GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX = "pm path ";
    private static final String REMOVE_SYSTEM_APP_COMMAND_PREFIX =
            "rm -r " + SYSTEM_APP_INSTALL_DIRECTORY;
    private static final String REMOVE_APP_DATA_COMMAND_PREFIX = "rm -r /data/data";
    private static final String MOUNT_COMMAND_PREFIX = "mount";

    @Test
    public void uninstallPackage_packageNameIsNull_throws() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        SystemPackageUninstaller.uninstallPackage(
                                null, createGoodDeviceWithAppNotInstalled()));
    }

    @Test
    public void uninstallPackage_frameworkNotRunning_startsFrameworkOrThrows() throws Exception {
        ITestDevice device = createGoodDeviceWithAppNotInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.eq(SystemPackageUninstaller.PM_CHECK_COMMAND)))
                .thenReturn(createFailedCommandResult());

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
        Mockito.verify(device, Mockito.times(1)).executeShellV2Command(Mockito.eq("start"));
    }

    @Test
    public void uninstallPackage_packageIsNotInstalled_doesNotRemove() throws Exception {
        ITestDevice device = createGoodDeviceWithAppNotInstalled();

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void uninstallPackage_differentPackageWithSameNamePrefixInstalled_doesNotRemove()
            throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        // Mock the device as if the test package does not exist on device
        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(String.format("package:%s_some_more_chars", TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void uninstallPackage_checkPackageInstalledCommandFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_getInstallDirectoryCommandFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_packageIsNotSystemApp_doesNotRemove() throws Exception {
        ITestDevice device = createGoodDeviceWithUserAppInstalled();

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void uninstallPackage_adbAlreadyRooted_doesNotRootAgain() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.isAdbRoot()).thenReturn(true);

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(0)).enableAdbRoot();
    }

    @Test
    public void uninstallPackage_adbNotAlreadyRooted_rootAdbAndThenUnroot() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.isAdbRoot()).thenReturn(false);

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(1)).enableAdbRoot();
        Mockito.verify(device, Mockito.times(1)).disableAdbRoot();
    }

    @Test
    public void uninstallPackage_adbRootCommandFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.enableAdbRoot()).thenThrow(new DeviceNotAvailableException("", ""));

        assertThrows(
                DeviceNotAvailableException.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_adbRootFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.enableAdbRoot()).thenReturn(false);

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_adbDisableRootCommandFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.disableAdbRoot()).thenThrow(new DeviceNotAvailableException("", ""));

        assertThrows(
                DeviceNotAvailableException.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_adbDisableRootFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.disableAdbRoot()).thenReturn(false);

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_adbRemountFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.doThrow(new DeviceNotAvailableException("", ""))
                .when(device)
                .remountSystemWritable();

        assertThrows(
                DeviceNotAvailableException.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_adbRemounted_mountReadOnlyAfterwards() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.doNothing().when(device).remountSystemWritable();

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(1)).remountSystemWritable();
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(MOUNT_COMMAND_PREFIX));
    }

    @Test
    public void uninstallPackage_mountReadOnlyFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(MOUNT_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_removePackageInstallDirectoryFailed_throws() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    @Test
    public void uninstallPackage_removePackageDataDirectoryFailed_doesNotThrow() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);
    }

    @Test
    public void uninstallPackage_packageIsSystemApp_appRemoved() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX));
    }

    @Test
    public void uninstallPackage_noUpdatePackagePresent_appRemoved() throws Exception {
        int numberOfUpdates = 0;
        ITestDevice device = createGoodDeviceWithSystemAppInstalled(numberOfUpdates);

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(numberOfUpdates + 1))
                .uninstallPackage(TEST_PACKAGE_NAME);
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
    }

    @Test
    public void uninstallPackage_someUpdatePackagesPresent_appRemoved() throws Exception {
        int numberOfUpdates = 2;
        ITestDevice device = createGoodDeviceWithSystemAppInstalled(numberOfUpdates);

        SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device);

        Mockito.verify(device, Mockito.times(numberOfUpdates + 1))
                .uninstallPackage(TEST_PACKAGE_NAME);
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
    }

    @Test
    public void uninstallPackage_tooManyUpdatePackagesPresent_throwsException() throws Exception {
        ITestDevice device =
                createGoodDeviceWithSystemAppInstalled(
                        SystemPackageUninstaller.MAX_NUMBER_OF_UPDATES + 1);

        assertThrows(
                TargetSetupError.class,
                () -> SystemPackageUninstaller.uninstallPackage(TEST_PACKAGE_NAME, device));
    }

    private ITestDevice createGoodDeviceWithUserAppInstalled() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();

        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(
                String.format("package:/data/app/%s/%s.apk", TEST_PACKAGE_NAME, TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        return device;
    }

    private ITestDevice createGoodDeviceWithAppNotInstalled() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout("");
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);
        return device;
    }

    private ITestDevice createGoodDeviceWithSystemAppInstalled() throws Exception {
        return createGoodDeviceWithSystemAppInstalled(1);
    }

    private ITestDevice createGoodDeviceWithSystemAppInstalled(int numberOfUpdatesInstalled)
            throws Exception {
        ITestDevice device = Mockito.mock(ITestDevice.class);
        CommandResult commandResult;

        // Is framework running
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.eq(SystemPackageUninstaller.PM_CHECK_COMMAND)))
                .thenReturn(createSuccessfulCommandResult());

        // Uninstall updates
        String uninstallFailureMessage = "Failure [DELETE_FAILED_INTERNAL_ERROR]";
        if (numberOfUpdatesInstalled == 0) {
            Mockito.when(device.uninstallPackage(TEST_PACKAGE_NAME))
                    .thenReturn(uninstallFailureMessage);
        } else {
            String[] uninstallResults = new String[numberOfUpdatesInstalled];
            uninstallResults[numberOfUpdatesInstalled - 1] = uninstallFailureMessage;
            Mockito.when(device.uninstallPackage(TEST_PACKAGE_NAME))
                    .thenReturn(null, uninstallResults);
        }

        // List package
        commandResult = createSuccessfulCommandResult();
        commandResult.setStdout("package:" + TEST_PACKAGE_NAME);
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        // Get package path
        commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(
                String.format(
                        "package:%s/%s/%s.apk",
                        SYSTEM_APP_INSTALL_DIRECTORY, TEST_PACKAGE_NAME, TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        // Adb root
        Mockito.when(device.isAdbRoot()).thenReturn(false);
        Mockito.when(device.enableAdbRoot()).thenReturn(true);

        // Adb remount
        Mockito.doNothing().when(device).remountSystemWritable();

        // Remove package install directory
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        // Remove package data directory
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        // Restart framework
        Mockito.when(device.executeShellV2Command(ArgumentMatchers.eq("start")))
                .thenReturn(createSuccessfulCommandResult());
        Mockito.when(device.executeShellV2Command(ArgumentMatchers.eq("stop")))
                .thenReturn(createSuccessfulCommandResult());

        // Disable adb root
        Mockito.when(device.disableAdbRoot()).thenReturn(true);

        // Remount read only
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(MOUNT_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        return device;
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        return commandResult;
    }
}

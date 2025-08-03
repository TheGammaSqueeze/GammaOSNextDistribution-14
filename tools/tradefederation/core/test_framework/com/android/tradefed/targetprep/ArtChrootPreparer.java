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
package com.android.tradefed.targetprep;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** Create chroot directory for ART tests. */
@OptionClass(alias = "art-chroot-preparer")
public class ArtChrootPreparer extends BaseTargetPreparer {

    // Predefined location of the chroot root directory.
    public static final String CHROOT_PATH = "/data/local/tmp/art-test-chroot";

    // Directories to create in the chroot.
    private static final String[] MKDIRS = {
        "/", "/apex", "/data", "/data/dalvik-cache", "/data/local/tmp", "/tmp",
    };

    // System mount points to replicate in the chroot.
    private static final String[] MOUNTS = {
        "/apex/com.android.conscrypt",
        "/apex/com.android.i18n",
        "/apex/com.android.os.statsd",
        "/apex/com.android.runtime",
        "/dev",
        "/dev/cpuset",
        "/etc",
        "/linkerconfig",
        "/proc",
        "/sys",
        "/system",
    };

    private List<String> mMountPoints = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();

        // Ensure there are no files left from previous runs.
        cleanup(device);

        // Create directories required for ART testing in chroot.
        for (String dir : MKDIRS) {
            adbShell(device, "mkdir -p %s%s", CHROOT_PATH, dir);
        }

        // Replicate system mount point in the chroot.
        for (String dir : MOUNTS) {
            adbShell(device, "mkdir -p %s%s", CHROOT_PATH, dir);
            adbShell(device, "mount --bind %s %s%s", dir, CHROOT_PATH, dir);
            mMountPoints.add(CHROOT_PATH + dir);
        }

        // Activate APEXes in the chroot.
        DeviceDescriptor deviceDesc = device.getDeviceDescriptor();
        File art_apex;
        File tempDir = null;
        try {
            art_apex = testInfo.getDependencyFile("com.android.art.testing.apex", /*target=*/ true);
        } catch (FileNotFoundException e) {
            throw new TargetSetupError("ART testing apex not found", e, deviceDesc);
        }
        try {
            tempDir = FileUtil.createTempDir("art-test-apex");
            activateApex(device, tempDir, art_apex);
        } catch (IOException e) {
            throw new TargetSetupError("Error when activating apex", e, deviceDesc);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    private void activateApex(ITestDevice device, File tempDir, File apex)
            throws TargetSetupError, IOException, DeviceNotAvailableException {
        CLog.i("Activate apex in ART chroot: %s", apex.getName());
        ZipFile apex_zip = new ZipFile(apex);
        ZipArchiveEntry apex_payload = apex_zip.getEntry("apex_payload.img");
        File temp = FileUtil.createTempFile("payload-", ".img", tempDir);
        FileUtil.writeToFile(apex_zip.getInputStream(apex_payload), temp);
        String deviceApexDir = String.format("%s/apex/%s", CHROOT_PATH, apex.getName());
        // Rename "com.android.art.testing.apex" to just "com.android.art.apex".
        deviceApexDir = deviceApexDir.replace(".testing.apex", "").replace(".apex", "");
        String deviceApexImg = deviceApexDir + ".img";
        if (!device.pushFile(temp, deviceApexImg)) {
            throw new TargetSetupError(
                    String.format("adb push failed for %s", apex.getName()),
                    device.getDeviceDescriptor());
        }
        // TODO(b/168048638): Work-around for cuttlefish: first losetup call always fails.
        device.executeShellV2Command("losetup -f");
        // Mount the apex file via a loopback device.
        String loopbackDevice = adbShell(device, "losetup -f -s %s", deviceApexImg);
        adbShell(device, "mkdir -p %s", deviceApexDir);
        adbShell(device, "mount -o loop,ro %s %s", loopbackDevice, deviceApexDir);
        mMountPoints.add(deviceApexDir);
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        try {
            cleanup(testInfo.getDevice());
        } catch (TargetSetupError ex) {
            CLog.e("Tear-down failed: %s", ex.toString());
        }
    }

    // Wrapper for executeShellV2Command that checks that the command succeeds.
    private String adbShell(ITestDevice device, String format, Object... args)
            throws TargetSetupError, DeviceNotAvailableException {
        String cmd = String.format(format, args);
        CommandResult result = device.executeShellV2Command(cmd);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(String.format("adb shell %s\n%s", cmd, result.toString()));
        }
        return result.getStdout();
    }

    private void cleanup(ITestDevice device) throws TargetSetupError, DeviceNotAvailableException {
        // Unmount in the reverse order because there are nested mount points.
        ListIterator listIterator = mMountPoints.listIterator(mMountPoints.size());
        while (listIterator.hasPrevious()) {
            adbShell(device, "umount %s", listIterator.previous());
        }
        adbShell(device, "rm -rf %s", CHROOT_PATH);
    }
}

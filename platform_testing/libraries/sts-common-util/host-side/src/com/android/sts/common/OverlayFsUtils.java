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

package com.android.sts.common;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static com.android.sts.common.CommandUtil.runAndCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.google.common.hash.Hashing;


/** TestWatcher that enables writing to read-only partitions and reboots device when done. */
public class OverlayFsUtils extends TestWatcher {
    private static final String OVERLAYFS_PREFIX = "sts_overlayfs_";
    private static final Path WRITABLE_DIR = Paths.get("/data", "local", "tmp");

    private final BaseHostJUnit4Test test;

    // output of `stat`, e.g. "root shell 755 u:object_r:vendor_file:s0"
    static final Pattern PERM_PATTERN =
            Pattern.compile(
                    "^(?<user>[a-zA-Z0-9_-]+) (?<group>[a-zA-Z0-9_-]+) (?<perm>[0-7]+)"
                            + " (?<secontext>.*)$");

    private Map<ITestDevice, List<String>> workingDirs = new HashMap<>();

    public OverlayFsUtils(BaseHostJUnit4Test test) {
        assertNotNull("Need to pass in a valid testcase object.", test);
        this.test = test;
    }

    /**
     * Mounts an OverlayFS dir over the top most common dir in the list.
     *
     * <p>The directory should be writable after this returns successfully. To cleanup, reboot the
     * device as unfortunately unmounting overlayfs is complicated.
     *
     * @param dir The directory to make writable. Directories with single quotes are not supported.
     */
    public void makeWritable(final String dir, int megabytes)
            throws DeviceNotAvailableException, IOException, IllegalStateException {
        ITestDevice device = test.getDevice();
        assertNotNull("device not set.", device);
        assertTrue("dir needs to be an absolute path.", dir.startsWith("/"));

        // Setup list of temp dirs to be deleted upon cleanup
        List<String> dirs;
        if (!workingDirs.containsKey(device)) {
            dirs = new ArrayList<>(2);
            workingDirs.put(device, dirs);
        } else {
            dirs = workingDirs.get(device);
        }

        // losetup doesn't work for image paths 64 bytes or longer, so we have to truncate
        String dirHash = Hashing.md5().hashString(dir, StandardCharsets.UTF_8).toString();
        int pathPrefixLength = WRITABLE_DIR.toString().length() + 1 + OVERLAYFS_PREFIX.length();
        int dirHashLength = Math.min(64 - pathPrefixLength - 5, dirHash.length());
        assertTrue("Can't fit overlayFS image path in 64 chars.", dirHashLength >= 5);
        String id = OVERLAYFS_PREFIX + dirHash.substring(0, dirHashLength);

        // Check and make sure we have not already mounted over this dir. We do that by hashing
        // the lower dir path and put that as part of the device ID for `mount`.
        CommandResult res = device.executeShellV2Command("mount | grep -q " + id);
        if (res.getStatus() == CommandStatus.SUCCESS) {
            // a mount with the same ID already exists
            throw new IllegalStateException(dir + " has already been made writable.");
        }

        assertTrue("Can't acquire root for " + device.getSerialNumber(), device.enableAdbRoot());

        // Match permissions of upper dir to lower dir
        String statOut = runAndCheck(device, "stat -c '%U %G %a %C' '" + dir + "'").getStdout();
        Matcher m = PERM_PATTERN.matcher(statOut);
        assertTrue("Bad stats output: " + statOut, m.find());
        String user = m.group("user");
        String group = m.group("group");
        String unixPerm = m.group("perm");
        String seContext = m.group("secontext");

        // Disable SELinux enforcement and mount a loopback ext4 image
        runAndCheck(device, "setenforce 0");
        Path tempdir = WRITABLE_DIR.resolve(id);
        Path tempimg = tempdir.getParent().resolve(tempdir.getFileName().toString() + ".img");
        dirs.add(tempimg.toString());
        dirs.add(tempdir.toString());

        runAndCheck(
                device,
                String.format("dd if=/dev/zero of='%s' bs=%dM count=1", tempimg, megabytes));
        runAndCheck(device, String.format("mkfs.ext4 '%s'", tempimg));
        runAndCheck(device, String.format("mkdir '%s'", tempdir));

        String loopbackDev =
                runAndCheck(device, String.format("losetup -f -s '%s'", tempimg), 3)
                        .getStdout()
                        .strip();
        runAndCheck(device, String.format("mount '%s' '%s'", loopbackDev, tempdir), 3);

        String upperdir = tempdir.resolve("upper").toString();
        String workdir = tempdir.resolve("workdir").toString();

        runAndCheck(device, String.format("mkdir -p '%s' '%s'", upperdir, workdir));
        runAndCheck(device, String.format("chown %s:%s '%s'", user, group, upperdir));
        runAndCheck(device, String.format("chcon '%s' '%s'", seContext, upperdir));
        runAndCheck(device, String.format("chmod %s '%s'", unixPerm, upperdir));

        String mountCmd =
                String.format(
                        "mount -t overlay '%s' -o lowerdir='%s',upperdir='%s',workdir='%s' '%s'",
                        id, dir, upperdir, workdir, dir);
        runAndCheck(device, mountCmd);
    }

    public boolean anyOverlayFsMounted() throws DeviceNotAvailableException {
        ITestDevice device = test.getDevice();
        assertNotNull("Device not set", device);
        CommandResult res = device.executeShellV2Command("mount | grep -q " + OVERLAYFS_PREFIX);
        return res.getStatus() == CommandStatus.SUCCESS;
    }

    @Override
    public void finished(Description d) {
        ITestDevice device = test.getDevice();
        assertNotNull("Device not set", device);
        try {
            // Since we can't umount an overlayfs cleanly, reboot the device to cleanup
            if (anyOverlayFsMounted()) {
                device.rebootUntilOnline();
                device.waitForDeviceAvailable();
            }

            // Remove upper and working dirs
            assertTrue("Can't acquire root: " + device.getSerialNumber(), device.enableAdbRoot());
            if (workingDirs.containsKey(device)) {
                for (String dir : workingDirs.get(device)) {
                    runAndCheck(device, String.format("rm -rf '%s'", dir));
                }
            }

            // Restore SELinux enforcement state
            runAndCheck(device, "setenforce 1");

            assertTrue("Can't remove root: " + device.getSerialNumber(), device.disableAdbRoot());
        } catch (DeviceNotAvailableException e) {
            throw new AssertionError("Device unavailable when cleaning up", e);
        }
    }
}

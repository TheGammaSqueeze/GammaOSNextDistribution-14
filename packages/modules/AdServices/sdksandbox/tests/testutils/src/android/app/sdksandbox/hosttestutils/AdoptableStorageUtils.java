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

package android.app.sdksandbox.hosttestutils;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;

public class AdoptableStorageUtils {

    private final BaseHostJUnit4Test mTest;

    private String mDiskId;

    public AdoptableStorageUtils(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public boolean isAdoptableStorageSupported() throws Exception {
        boolean hasFeature =
                mTest.getDevice().hasFeature("feature:android.software.adoptable_storage");
        boolean hasFstab =
                Boolean.parseBoolean(
                        mTest.getDevice().executeShellCommand("sm has-adoptable").trim());
        return hasFeature || hasFstab;
    }

    // Creates a new volume in adoptable storage and returns its uuid
    public String createNewVolume() throws Exception {
        mDiskId = getAdoptionDisk();
        assertEmpty(mTest.getDevice().executeShellCommand("sm partition " + mDiskId + " private"));
        final LocalVolumeInfo vol = getAdoptionVolume();
        return vol.uuid;
    }

    /**
     * Enable a virtual disk to give us the best shot at being able to pass various tests. This
     * helps verify devices that may not currently have an SD card inserted.
     */
    public void enableVirtualDisk() throws Exception {
        if (isAdoptableStorageSupported()) {
            mTest.getDevice().executeShellCommand("sm set-virtual-disk true");

            // Ensure virtual disk is mounted.
            int attempt = 0;
            boolean hasVirtualDisk = false;
            String result = "";
            while (!hasVirtualDisk && attempt++ < 50) {
                RunUtil.getDefault().sleep(1000);
                result = mTest.getDevice().executeShellCommand("sm list-disks adoptable").trim();
                hasVirtualDisk = result.startsWith("disk:");
            }
            assertTrue("Virtual disk is not ready: " + result, hasVirtualDisk);

            waitForVolumeReadyNoCheckingOrEjecting();
        }
    }

    // Destroy the volume created before
    public void cleanUpVolume() throws Exception {
        mTest.getDevice().executeShellCommand("sm partition " + mDiskId + " public");
        mTest.getDevice().executeShellCommand("sm forget all");
    }

    private String getAdoptionDisk() throws Exception {
        // In the case where we run multiple test we cleanup the state of the device. This
        // results in the execution of sm forget all which causes the MountService to "reset"
        // all its knowledge about available drives. This can cause the adoptable drive to
        // become temporarily unavailable.
        int attempt = 0;
        String disks = mTest.getDevice().executeShellCommand("sm list-disks adoptable");
        while ((disks == null || disks.isEmpty()) && attempt++ < 15) {
            Thread.sleep(1000);
            disks = mTest.getDevice().executeShellCommand("sm list-disks adoptable");
        }

        if (disks == null || disks.isEmpty()) {
            throw new AssertionError(
                    "Devices that claim to support adoptable storage must have "
                            + "adoptable media inserted during CTS to verify correct behavior");
        }
        return disks.split("\n")[0].trim();
    }

    private static void assertEmpty(String str) {
        if (str != null && str.trim().length() > 0) {
            throw new AssertionError("Expected empty string but found " + str);
        }
    }

    private static class LocalVolumeInfo {
        public String volId;
        public String state;
        public String uuid;

        LocalVolumeInfo(String line) {
            final String[] split = line.split(" ");
            volId = split[0];
            state = split[1];
            uuid = split[2];
        }
    }

    private LocalVolumeInfo getAdoptionVolume() throws Exception {
        String[] lines = null;
        int attempt = 0;
        int mounted_count = 0;
        while (attempt++ < 15) {
            lines = mTest.getDevice().executeShellCommand("sm list-volumes private").split("\n");
            LogUtil.CLog.w("getAdoptionVolume(): " + Arrays.toString(lines));
            for (String line : lines) {
                final LocalVolumeInfo info = new LocalVolumeInfo(line.trim());
                if (!"private".equals(info.volId)) {
                    if ("mounted".equals(info.state)) {
                        // make sure the storage is mounted and stable for a while
                        mounted_count++;
                        attempt--;
                        if (mounted_count >= 3) {
                            return waitForVolumeReady(info);
                        }
                    } else {
                        mounted_count = 0;
                    }
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Expected private volume; found " + Arrays.toString(lines));
    }

    private LocalVolumeInfo waitForVolumeReady(LocalVolumeInfo vol) throws Exception {
        int attempt = 0;
        while (attempt++ < 15) {
            if (mTest.getDevice()
                    .executeShellCommand("dumpsys package volumes")
                    .contains(vol.volId)) {
                return vol;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Volume not ready " + vol.volId);
    }

    /** Ensure no volume is in ejecting or checking state */
    private void waitForVolumeReadyNoCheckingOrEjecting() throws Exception {
        int attempt = 0;
        boolean noCheckingEjecting = false;
        String result = "";
        while (!noCheckingEjecting && attempt++ < 600) {
            result = mTest.getDevice().executeShellCommand("sm list-volumes");
            noCheckingEjecting = !result.contains("ejecting") && !result.contains("checking");
            RunUtil.getDefault().sleep(100);
        }
        assertTrue("Volumes are not ready: " + result, noCheckingEjecting);
    }
}

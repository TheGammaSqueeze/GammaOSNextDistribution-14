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
package com.android.tradefed.targetprep.sync;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil2;
import com.android.tradefed.util.executor.ParallelDeviceExecutor;
import com.android.tradefed.util.image.IncrementalImageUtil;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Basic test to start iterating on device incremental image. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class IncrementalImageFuncTest extends BaseHostJUnit4Test {

    @Option(name = "disable-verity")
    private boolean mDisableVerity = true;

    @Option(name = "apply-snapshot")
    private boolean mApplySnapshot = false;

    public static final Set<String> PARTITIONS_TO_DIFF =
            ImmutableSet.of(
                    "product.img",
                    "system.img",
                    "system_dlkm.img",
                    "system_ext.img",
                    "vendor.img",
                    "vendor_dlkm.img");

    public static class TrackResults {
        public String imageMd5;
        public String mountedBlock;

        @Override
        public String toString() {
            return "TrackResults [imageMd5=" + imageMd5 + ", mountedBlock=" + mountedBlock + "]";
        }
    }

    private Map<String, TrackResults> partitionToInfo = new ConcurrentHashMap<>();

    @After
    public void teardown() throws DeviceNotAvailableException {
        if (mDisableVerity) {
            getDevice().enableAdbRoot();
            // Reenable verity in case it was disabled.
            getDevice().executeAdbCommand("enable-verity");
            getDevice().reboot();
        }
    }

    @Test
    public void testBlockUtility() throws Throwable {
        String originalBuildId = getDevice().getBuildId();
        CLog.d("Original build id: %s", originalBuildId);

        IncrementalImageUtil.isSnapshotInUse(getDevice());
        IncrementalImageUtil updateUtil =
                new IncrementalImageUtil(
                        getDevice(),
                        getBuild().getFile("src-image"),
                        null,
                        null,
                        getBuild().getFile("target-image"),
                        getBuild().getFile("create_snapshot.zip"),
                        mApplySnapshot);
        try {
            updateUtil.updateDevice();

            String afterMountBuildId = getDevice().getBuildId();
            CLog.d(
                    "Original build id: %s. after mount build id: %s",
                    originalBuildId, afterMountBuildId);
        } finally {
            updateUtil.teardownDevice();
        }
        String afterRevert = getDevice().getBuildId();
        CLog.d("Original build id: %s. after unmount build id: %s", originalBuildId, afterRevert);
    }

    @Ignore
    @Test
    public void testBlockCompareUpdate() throws Throwable {
        String originalBuildId = getDevice().getBuildId();
        CLog.d("Original build id: %s", originalBuildId);

        File blockCompare = getCreateSnapshot();
        File srcImage = getBuild().getFile("src-image");
        File srcDirectory = ZipUtil2.extractZipToTemp(srcImage, "incremental_src");
        File targetImage = getBuild().getFile("target-image");
        File targetDirectory = ZipUtil2.extractZipToTemp(targetImage, "incremental_target");

        File workDir = FileUtil.createTempDir("block_compare_workdir");
        try (CloseableTraceScope e2e = new CloseableTraceScope("end_to_end_update")) {
            List<Callable<Boolean>> callableTasks = new ArrayList<>();
            for (String partition : srcDirectory.list()) {
                File possibleSrc = new File(srcDirectory, partition);
                File possibleTarget = new File(targetDirectory, partition);
                if (possibleSrc.exists() && possibleTarget.exists()) {
                    if (PARTITIONS_TO_DIFF.contains(partition)) {
                        callableTasks.add(
                                () -> {
                                    blockCompare(
                                            blockCompare, possibleSrc, possibleTarget, workDir);
                                    TrackResults newRes = new TrackResults();
                                    if (mDisableVerity) {
                                        newRes.imageMd5 = FileUtil.calculateMd5(possibleTarget);
                                    }
                                    partitionToInfo.put(FileUtil.getBaseName(partition), newRes);
                                    return true;
                                });
                    }
                } else {
                    CLog.e("Skipping %s no src or target", partition);
                }
            }
            ParallelDeviceExecutor<Boolean> executor =
                    new ParallelDeviceExecutor<Boolean>(callableTasks.size());
            executor.invokeAll(callableTasks, 0, TimeUnit.MINUTES);
            if (executor.hasErrors()) {
                throw executor.getErrors().get(0);
            }
            inspectCowPatches(workDir);

            getDevice().executeShellV2Command("mkdir -p /data/ndb");
            getDevice().executeShellV2Command("rm -rf /data/ndb/*.patch");

            // Ensure snapshotctl exists
            CommandResult whichOutput = getDevice().executeShellV2Command("which snapshotctl");
            CLog.e("stdout: %s, stderr: %s", whichOutput.getStdout(), whichOutput.getStderr());

            getDevice().executeShellV2Command("snapshotctl unmap-snapshots");
            getDevice().executeShellV2Command("snapshotctl delete-snapshots");

            List<Callable<Boolean>> pushTasks = new ArrayList<>();
            for (File f : workDir.listFiles()) {
                try (CloseableTraceScope ignored = new CloseableTraceScope("push:" + f.getName())) {
                    pushTasks.add(
                            () -> {
                                boolean success;
                                if (f.isDirectory()) {
                                    success = getDevice().pushDir(f, "/data/ndb/");
                                } else {
                                    success = getDevice().pushFile(f, "/data/ndb/" + f.getName());
                                }
                                CLog.e(
                                        "Push successful.: %s. %s->%s",
                                        success, f, "/data/ndb/" + f.getName());
                                assertTrue(success);
                                return true;
                            });
                }
            }
            ParallelDeviceExecutor<Boolean> pushExec =
                    new ParallelDeviceExecutor<Boolean>(pushTasks.size());
            pushExec.invokeAll(pushTasks, 0, TimeUnit.MINUTES);
            if (pushExec.hasErrors()) {
                throw pushExec.getErrors().get(0);
            }

            CommandResult mapOutput =
                    getDevice().executeShellV2Command("snapshotctl map-snapshots /data/ndb/");
            CLog.e("stdout: %s, stderr: %s", mapOutput.getStdout(), mapOutput.getStderr());
            if (!CommandStatus.SUCCESS.equals(mapOutput.getStatus())) {
                fail("Failed to map the snapshots.");
            }

            if (mDisableVerity) {
                getDevice().executeAdbCommand("disable-verity");
            }
            // flash all static partition in bootloader
            getDevice().rebootIntoBootloader();
            Map<String, String> envMap = new HashMap<>();
            envMap.put("ANDROID_PRODUCT_OUT", targetDirectory.getAbsolutePath());
            CommandResult fastbootResult =
                    getDevice()
                            .executeLongFastbootCommand(
                                    envMap,
                                    "flashall",
                                    "--exclude-dynamic-partitions",
                                    "--disable-super-optimization");
            CLog.d("Status: %s", fastbootResult.getStatus());
            CLog.d("stdout: %s", fastbootResult.getStdout());
            CLog.d("stderr: %s", fastbootResult.getStderr());
            getDevice().waitForDeviceAvailable(5 * 60 * 1000L);
            // Do Validation
            getDevice().enableAdbRoot();
            CommandResult psOutput = getDevice().executeShellV2Command("ps -ef | grep snapuserd");
            CLog.d("stdout: %s, stderr: %s", psOutput.getStdout(), psOutput.getStderr());

            listMappingAndCompare(partitionToInfo);

            String afterMountBuildId = getDevice().getBuildId();
            CLog.d(
                    "Original build id: %s. after mount build id: %s",
                    originalBuildId, afterMountBuildId);
        } finally {
            try (CloseableTraceScope rev = new CloseableTraceScope("revert_to_previous")) {
                revertToPreviousBuild(srcDirectory);
            } finally {
                FileUtil.recursiveDelete(workDir);
                FileUtil.recursiveDelete(srcDirectory);
                FileUtil.recursiveDelete(targetDirectory);
            }

            String afterRevert = getDevice().getBuildId();
            CLog.d(
                    "Original build id: %s. after unmount build id: %s",
                    originalBuildId, afterRevert);
        }
    }

    private void blockCompare(File blockCompare, File srcImage, File targetImage, File workDir) {
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("block_compare:" + srcImage.getName())) {
            IRunUtil runUtil = new RunUtil();
            runUtil.setWorkingDir(workDir);

            CommandResult result =
                    runUtil.runTimedCmd(
                            0L,
                            blockCompare.getAbsolutePath(),
                            "--source=" + srcImage.getAbsolutePath(),
                            "--target=" + targetImage.getAbsolutePath());
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new RuntimeException(
                        String.format("%s\n%s", result.getStdout(), result.getStderr()));
            }
            File[] listFiles = workDir.listFiles();
            CLog.e("%s", Arrays.asList(listFiles));
        }
    }

    private File getCreateSnapshot() throws IOException {
        File createSnapshotZip = getBuild().getFile("create_snapshot.zip");
        if (createSnapshotZip == null) {
            throw new RuntimeException("Cannot find create_snapshot.zip");
        }
        File destDir = ZipUtil2.extractZipToTemp(createSnapshotZip, "create_snapshot");
        File snapshot = FileUtil.findFile(destDir, "create_snapshot");
        FileUtil.chmodGroupRWX(snapshot);
        return snapshot;
    }

    private void inspectCowPatches(File workDir) throws IOException {
        File inspectZip = getBuild().getFile("inspect_cow.zip");
        if (inspectZip == null) {
            return;
        }
        File destDir = ZipUtil2.extractZipToTemp(inspectZip, "inspect_cow_unzip");
        File inspect = FileUtil.findFile(destDir, "inspect_cow");
        FileUtil.chmodGroupRWX(inspect);
        IRunUtil runUtil = new RunUtil();
        long sizeOfPatches = 0L;
        try (CloseableTraceScope ignored = new CloseableTraceScope("inspect_cow")) {
            for (File f : workDir.listFiles()) {
                CommandResult result =
                        runUtil.runTimedCmd(0L, inspect.getAbsolutePath(), f.getAbsolutePath());
                CLog.d("Status: %s", result.getStatus());
                CLog.d("Stdout: %s", result.getStdout());
                CLog.d("Stderr: %s", result.getStderr());
                CLog.d("Patch size: %s", f.length());
                sizeOfPatches += f.length();
            }
            CLog.d("Total size of patches: %s", sizeOfPatches);
        } finally {
            FileUtil.recursiveDelete(destDir);
        }
    }

    private void listMappingAndCompare(Map<String, TrackResults> partitionToInfo)
            throws DeviceNotAvailableException {
        CommandResult lsOutput = getDevice().executeShellV2Command("ls -l /dev/block/mapper/");
        CLog.d("stdout: %s, stderr: %s", lsOutput.getStdout(), lsOutput.getStderr());

        if (!mDisableVerity) {
            return;
        }
        String[] lineArray = lsOutput.getStdout().split("\n");
        for (int i = 0; i < lineArray.length; i++) {
            String lines = lineArray[i];
            if (!lines.contains("->")) {
                continue;
            }
            String[] pieces = lines.split("\\s+");
            String partition = pieces[7].substring(0, pieces[7].length() - 2);
            CLog.d("Partition extracted: %s", partition);
            if (partitionToInfo.containsKey(partition)) {
                // Since there is system_a/_b ensure we capture the right one
                // for md5 comparison
                if ("system".equals(partition)) {
                    if (!lineArray[i +2].contains("-cow-")) {
                        continue;
                    }
                }
                partitionToInfo.get(partition).mountedBlock = pieces[9];
            }
        }
        CLog.d("Infos: %s", partitionToInfo);

        StringBuilder errorSummary = new StringBuilder();
        for (Entry<String, TrackResults> res : partitionToInfo.entrySet()) {
            if (res.getValue().mountedBlock == null) {
                errorSummary.append(String.format("No partition found in mapping for %s", res));
                errorSummary.append("\n");
                continue;
            }
            TrackResults result = res.getValue();
            CommandResult md5Output =
                    getDevice().executeShellV2Command("md5sum " + result.mountedBlock);
            CLog.d("stdout: %s, stderr: %s", md5Output.getStdout(), md5Output.getStderr());
            if (!CommandStatus.SUCCESS.equals(md5Output.getStatus())) {
                fail("Fail to get md5sum from " + result.mountedBlock);
            }
            String md5device = md5Output.getStdout().trim().split("\\s+")[0];
            String message =
                    String.format(
                            "partition: %s. device md5: %s, file md5: %s",
                            res.getKey(), md5device, result.imageMd5);
            CLog.d(message);
            if (!md5device.equals(result.imageMd5)) {
                errorSummary.append(message);
                errorSummary.append("\n");
            }
        }
        if (!errorSummary.isEmpty()) {
            fail(errorSummary.toString());
        }
    }

    private void revertToPreviousBuild(File srcDirectory) throws DeviceNotAvailableException {
        CommandResult revertOutput =
                getDevice().executeShellV2Command("snapshotctl revert-snapshots");
        CLog.d("stdout: %s, stderr: %s", revertOutput.getStdout(), revertOutput.getStderr());
        getDevice().rebootIntoBootloader();
        Map<String, String> envMap = new HashMap<>();
        envMap.put("ANDROID_PRODUCT_OUT", srcDirectory.getAbsolutePath());
        CommandResult fastbootResult =
                getDevice()
                        .executeLongFastbootCommand(
                                envMap,
                                "flashall",
                                "--exclude-dynamic-partitions",
                                "--disable-super-optimization");
        CLog.d("Status: %s", fastbootResult.getStatus());
        CLog.d("stdout: %s", fastbootResult.getStdout());
        CLog.d("stderr: %s", fastbootResult.getStderr());
        getDevice().waitForDeviceAvailable(5 * 60 * 1000L);
    }
}

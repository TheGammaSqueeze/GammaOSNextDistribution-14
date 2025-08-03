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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Basic test to start iterating on device sync. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DeviceSyncHelperFuncTest extends BaseHostJUnit4Test {

    @Test
    public void testSyncDevice() throws DeviceNotAvailableException {
        String buildId = getDevice().getProperty("ro.system.build.version.incremental");
        CLog.d("%s / buildid: %s", getDevice().getProperty("ro.build.fingerprint"), buildId);
        printBuildProp(getDevice());

        File targetFiles = getBuild().getFile("target_files");
        if (targetFiles == null || !targetFiles.exists() || !targetFiles.isDirectory()) {
            throw new RuntimeException("Target files is invalid.");
        }

        DeviceSyncHelper syncHelper = new DeviceSyncHelper(getDevice(), targetFiles);
        boolean success = syncHelper.sync();
        assertTrue(success);

        String afterBuildId = getDevice().getProperty("ro.system.build.version.incremental");
        CLog.d("Initial build: %s. Final build: %s", buildId, afterBuildId);
        CLog.d("%s", getDevice().getProperty("ro.build.fingerprint"));
        printBuildProp(getDevice());

        Truth.assertThat(buildId).isNotEqualTo(afterBuildId);
    }

    private void printBuildProp(ITestDevice device) throws DeviceNotAvailableException {
        String output = device.executeAdbCommand("shell", "cat", "/system/build.prop");
        CLog.e("================ build.prop");
        CLog.e(output);
    }
}

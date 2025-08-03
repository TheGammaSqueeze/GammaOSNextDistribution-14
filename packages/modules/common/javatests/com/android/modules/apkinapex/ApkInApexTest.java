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

package com.android.modules.apkinapex;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertThrows;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.internal.util.test.SystemPreparer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import android.cts.install.lib.host.InstallUtilsHost;

import java.util.Set;

/**
 * Collection of tests to test functionality of APKs in apexes.
 *
 * <p>This test installs an apex which contains APKs and then performs the tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ApkInApexTest extends BaseHostJUnit4Test {

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private final SystemPreparer mPreparer = new SystemPreparer(mTemporaryFolder, this::getDevice);

    @Rule
    public final RuleChain ruleChain = RuleChain.outerRule(mTemporaryFolder).around(mPreparer);

    @Test
    public void installApexAndRunTests() throws Exception {
        if (!getDevice().isAdbRoot()) {
            getDevice().enableAdbRoot();
        }
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(getDevice());
        assumeTrue("Test requires atLeastT", deviceSdkLevel.isDeviceAtLeastT());

        String apex = "test_com.android.modules.apkinapex.apex";
        mPreparer.pushResourceFile(apex, "/system/apex/" + apex);
        mPreparer.reboot();

        Set<String> packages = getDevice().getInstalledPackageNames();

        assertThat(packages)
                .containsAtLeast(
                        "com.android.modules.apkinapex.apps.installable",
                        "com.android.modules.apkinapex.apps.futuretargetsdk"
                );

        assertThat(packages)
                .containsNoneOf(
                        "com.android.modules.apkinapex.apps.futureminsdk",
                        "com.android.modules.apkinapex.apps.pastmaxsdk"
                );
    }
}

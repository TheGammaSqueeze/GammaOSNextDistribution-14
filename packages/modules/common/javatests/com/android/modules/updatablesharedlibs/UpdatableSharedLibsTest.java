/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.modules.updatablesharedlibs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertThrows;

import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.internal.util.test.SystemPreparer;

import com.android.modules.utils.build.testing.DeviceSdkLevel;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import android.cts.install.lib.host.InstallUtilsHost;

@RunWith(DeviceJUnit4ClassRunner.class)
public class UpdatableSharedLibsTest extends BaseHostJUnit4Test {

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private final SystemPreparer mPreparer = new SystemPreparer(mTemporaryFolder, this::getDevice);

    @Rule
    public final RuleChain ruleChain = RuleChain.outerRule(mTemporaryFolder).around(mPreparer);

    @Test
    public void callOnDeviceApiFromHost() throws Exception {
        if (!getDevice().isAdbRoot()) {
            getDevice().enableAdbRoot();
        }
        assumeTrue("Device needs to run on at least T", isAtLeastT());
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Device requires root", getDevice().isAdbRoot());

        // install app requiring lib before the lib is installed
        assertInstalationFails();

        String apex = "test_com.android.modules.updatablesharedlibs.apex";
        mPreparer.pushResourceFile(apex, "/system/apex/" + apex);
        mPreparer.reboot();
        getDevice().disableAdbRoot();

        installPackage("com.android.modules.updatablesharedlibs.apps.targetS.apk");
        installPackage("com.android.modules.updatablesharedlibs.apps.targetT.apk");
        installPackage("com.android.modules.updatablesharedlibs.apps.targetTWithLib.apk");

        runDeviceTests("com.android.modules.updatablesharedlibs.apps.targetS", null);
        runDeviceTests("com.android.modules.updatablesharedlibs.apps.targetT", null);
        runDeviceTests("com.android.modules.updatablesharedlibs.apps.targetTWithLib", null);
    }

    private void assertInstalationFails() throws Exception {
        String packageName = "com.android.modules.updatablesharedlibs.apps.targetTWithLib";
        Exception e = assertThrows(
            TargetSetupError.class,
            () -> installPackage(packageName + ".apk"));
        assertThat(e).hasMessageThat().contains(
            "Package " + packageName + " requires "
                + "unavailable shared library "
                + "com.android.modules.updatablesharedlibs.libs.since.t");
    }

    protected boolean isAtLeastT() throws Exception {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(getDevice());
        return deviceSdkLevel.isDeviceAtLeastT();
    }
}

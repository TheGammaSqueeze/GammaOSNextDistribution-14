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
package com.android.rkpdapp.hosttest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.rkpd.RkpdExtensionAtoms;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AtomsHostTest extends BaseHostJUnit4Test {
    private final int[] mAtoms;

    AtomsHostTest(int... atoms) {
        mAtoms = atoms;
    }

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        String appPackageName = null;
        Set<ITestDevice.ApexInfo> mActiveApexes = getDevice().getActiveApexes();
        for (ITestDevice.ApexInfo ap : mActiveApexes) {
            if ("com.android.rkpd".equals(ap.name)) {
                appPackageName = "com.android.rkpdapp";
                break;
            } else if ("com.google.android.rkpd".equals(ap.name)) {
                appPackageName = "com.google.android.rkpdapp";
                break;
            }
        }
        if (appPackageName == null) {
            assertWithMessage("rkpd mainline module not installed").fail();
        }
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(), appPackageName, mAtoms);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    void runIntegrationTest(String testMethodName, String className) throws Exception {
        testMethodName = testMethodName + "[0: instanceName=default]";
        runTest("com.android.rkpdapp.e2etest", testMethodName, className);
    }

    void runUnitTest(String testMethodName, String className) throws Exception {
        runTest("com.android.rkpdapp.unittest", testMethodName, className);
    }

    <Type> List<Type> getAtoms(
            GeneratedMessage.GeneratedExtension<AtomsProto.Atom, Type> extension) throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        RkpdExtensionAtoms.registerAllExtensions(registry);
        List<Type> atoms = new ArrayList<>();
        for (EventMetricData event : ReportUtils.getEventMetricDataList(getDevice(), registry)) {
            atoms.add(event.getAtom().getExtension(extension));
        }
        return atoms;
    }

    private void runTest(String packageName, String testMethodName, String className)
            throws Exception {
        String testClassName = packageName + "." + className;
        assertThat(runDeviceTests(packageName, testClassName, testMethodName)).isTrue();
    }
}

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

package com.android.tests.apex.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.internal.util.test.SystemPreparer;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class ApkInApexTests extends BaseHostJUnit4Test {
    private static final String PRODUCT_APEX = "com.android.apex.product.test.apex";
    private static final String SYSTEM_APEX = "com.android.apex.system.test.apex";
    private static final String SYSTEM_EXT_APEX = "com.android.apex.system_ext.test.apex";
    private static final String VENDOR_APEX = "com.android.apex.vendor.test.apex";

    private static final String PRODUCT_PRIVAPP_XML = "com.android.apex.product.app.test.xml";
    private static final String SYSTEM_PRIVAPP_XML = "com.android.apex.system.app.test.xml";
    private static final String SYSTEM_EXT_PRIVAPP_XML = "com.android.apex.system_ext.app.test.xml";
    private static final String VENDOR_PRIVAPP_XML = "com.android.apex.vendor.app.test.xml";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private final SystemPreparer mPreparer = new SystemPreparer(mTemporaryFolder,
            this::getDevice);

    @Rule
    public final RuleChain ruleChain = RuleChain.outerRule(mTemporaryFolder).around(mPreparer);

    @Before
    public void setUp() throws Exception {
        assumeTrue("Updating APEX is not supported", mHostUtils.isApexUpdateSupported());
        mPreparer.pushResourceFile(PRODUCT_APEX,
                "/product/apex/" + PRODUCT_APEX);
        mPreparer.pushResourceFile(PRODUCT_PRIVAPP_XML,
                "/product/etc/permissions/" + PRODUCT_PRIVAPP_XML);
        mPreparer.pushResourceFile(SYSTEM_APEX,
                "/system/apex/" + SYSTEM_APEX);
        mPreparer.pushResourceFile(SYSTEM_PRIVAPP_XML,
                "/system/etc/permissions/" + SYSTEM_PRIVAPP_XML);
        mPreparer.pushResourceFile(SYSTEM_EXT_APEX,
                "/system_ext/apex/" + SYSTEM_EXT_APEX);
        mPreparer.pushResourceFile(SYSTEM_EXT_PRIVAPP_XML,
                "/system_ext/etc/permissions/" + SYSTEM_EXT_PRIVAPP_XML);
        mPreparer.pushResourceFile(VENDOR_APEX,
                "/vendor/apex/" + VENDOR_APEX);
        mPreparer.pushResourceFile(VENDOR_PRIVAPP_XML,
                "/vendor/etc/permissions/" + VENDOR_PRIVAPP_XML);
        mPreparer.reboot();
    }

    @After
    public void tearDown() throws Exception {
        getDevice().disableAdbRoot();
    }

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testApkOnlyEnableRollback");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests("com.android.tests.apex.apkinapex.app",
                "com.android.tests.apex.app.ApkInApexTests",
                phase)).isTrue();
    }
    @Test
    public void testPrivPermissionIsGranted() throws Exception {
        runPhase("testPrivPermissionIsGranted");
    }

    @Test
    public void testJniCalls() throws Exception {
        runPhase("testJniCalls");
    }
}

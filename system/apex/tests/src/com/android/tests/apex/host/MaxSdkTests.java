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
public class MaxSdkTests extends BaseHostJUnit4Test {

    private static final String APEX = "com.android.apex.maxsdk.test.apex";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private final SystemPreparer mPreparer = new SystemPreparer(mTemporaryFolder,
            this::getDevice);

    @Rule
    public final RuleChain ruleChain = RuleChain.outerRule(mTemporaryFolder).around(mPreparer);

    @Before
    public void setUp() throws Exception {
        assumeTrue("Updating APEX is not supported", mHostUtils.isApexUpdateSupported());
        mPreparer.pushResourceFile(APEX, "/product/apex/" + APEX);
        mPreparer.reboot();
    }

    @After
    public void tearDown() throws Exception {
        getDevice().disableAdbRoot();
    }

    @Test
    public void verifyMaxSdk() throws Exception {
        assertThat(
            runDeviceTests(
                "androidx.test.runner.AndroidJUnitRunner",
                "com.android.tests.apex.maxsdk.app",
                (String) null /* class */,
                (String) null /* method */
            )
        ).isTrue();
    }
}

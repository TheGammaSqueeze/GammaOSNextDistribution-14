/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.suite.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.targetprep.RunOnSdkSandboxTargetPreparer;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link RunOnSdkSandboxHandler}. */
@RunWith(JUnit4.class)
public class RunOnSdkSandboxHandlerTest {

    private RunOnSdkSandboxHandler mHandler;
    private IConfiguration mConfiguration;

    @Before
    public void setUp() {
        mHandler = new RunOnSdkSandboxHandler();
        mConfiguration = new Configuration("test", "test");
    }

    @Test
    public void testAddParameterSpecificConfig() {
        mHandler.addParameterSpecificConfig(mConfiguration);

        assertTrue(
                mConfiguration.getTargetPreparers().get(0)
                        instanceof RunOnSdkSandboxTargetPreparer);
    }

    /** Test that when a module configuration go through the handler it gets tuned properly. */
    @Test
    public void testApplySetup() {
        SuiteApkInstaller installer = new SuiteApkInstaller();
        assertFalse(installer.isInstantMode());
        TestFilterable test = new TestFilterable();
        assertEquals(0, test.getIncludeAnnotations().size());
        assertEquals(0, test.getExcludeAnnotations().size());
        mConfiguration.setTest(test);
        mConfiguration.setTargetPreparer(installer);
        mHandler.applySetup(mConfiguration);

        // SdkSandbox mode is included.
        assertEquals(1, test.getIncludeAnnotations().size());
        assertEquals(
                "android.platform.test.annotations.AppModeSdkSandbox",
                test.getIncludeAnnotations().iterator().next());
        // Full mode tests and tests not applicable for the sandbox are excluded.
        assertEquals(2, test.getExcludeAnnotations().size());
        List<String> expected =
                Arrays.asList(
                        "android.platform.test.annotations.AppModeFull",
                        "android.platform.test.annotations.AppModeNonSdkSandbox");
        assertTrue(test.getExcludeAnnotations().containsAll(expected));
    }
}

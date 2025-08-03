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
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InstantAppHandler}. */
@RunWith(JUnit4.class)
public class InstantAppHandlerTest {

    private InstantAppHandler mHandler;
    private IConfiguration mModuleConfig;

    @Before
    public void setUp() {
        mHandler = new InstantAppHandler();
        mModuleConfig = new Configuration("test", "test");
    }

    /** Test that when a module configuration go through the handler it gets tuned properly. */
    @Test
    public void testApplySetup() {
        SuiteApkInstaller installer = new SuiteApkInstaller();
        assertFalse(installer.isInstantMode());
        TestFilterable test = new TestFilterable();
        assertEquals(0, test.getExcludeAnnotations().size());
        mModuleConfig.setTest(test);
        mModuleConfig.setTargetPreparer(installer);
        mHandler.applySetup(mModuleConfig);

        // Instant mode gets turned on.
        assertTrue(installer.isInstantMode());
        // Full mode is filtered out.
        assertEquals(1, test.getExcludeAnnotations().size());
        assertEquals(
                "android.platform.test.annotations.AppModeFull",
                test.getExcludeAnnotations().iterator().next());
    }
}

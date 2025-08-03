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
package com.android.tradefed.testtype.suite.params;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.targetprep.CreateUserPreparer;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.RunCommandTargetPreparer;
import com.android.tradefed.targetprep.VisibleBackgroundUserPreparer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SecondaryUserOnSecondaryDisplayHandler}. */
@RunWith(JUnit4.class)
public final class SecondaryUserOnDefaultDisplayHandlerTest {

    private SecondaryUserOnSecondaryDisplayHandler mHandler;
    private IConfiguration mModuleConfig;

    @Before
    public void setUp() {
        mHandler = new SecondaryUserOnSecondaryDisplayHandler();
        mModuleConfig = new Configuration("test", "test");
    }

    /** Test that when a module configuration go through the handler it gets tuned properly. */
    @Test
    public void testApplySetup() {
        TestFilterable test = new TestFilterable();
        assertThat(test.getExcludeAnnotations()).isEmpty();
        mModuleConfig.setTest(test);
        mHandler.applySetup(mModuleConfig);

        // User zero is filtered
        assertThat(test.getExcludeAnnotations()).hasSize(1);
        assertThat(test.getExcludeAnnotations().iterator().next())
                .isEqualTo("android.platform.test.annotations.SystemUserOnly");
    }

    @Test
    public void testGetParameterIdentifier() {
        assertThat(mHandler.getParameterIdentifier())
                .isEqualTo("secondary_user_on_secondary_display");
    }

    /**
     * Test that when a module configuration goes through the handler's
     * addParameterSpecificConfiguration, {@link CreateUserPreparer} is added correctly.
     */
    @Test
    public void testAddParameterSpecificConfig() {
        mHandler.addParameterSpecificConfig(mModuleConfig);
        assertThat(mModuleConfig.getTargetPreparers()).hasSize(2);

        ITargetPreparer preparer1 = mModuleConfig.getTargetPreparers().get(0);
        assertThat(preparer1).isInstanceOf(VisibleBackgroundUserPreparer.class);
        VisibleBackgroundUserPreparer userPreparer = (VisibleBackgroundUserPreparer) preparer1;
        assertThat(userPreparer.getDisplayId())
                .isEqualTo(VisibleBackgroundUserPreparer.INVALID_DISPLAY);
        ITargetPreparer preparer2 = mModuleConfig.getTargetPreparers().get(1);
        assertThat(preparer2).isInstanceOf(RunCommandTargetPreparer.class);
        assertThat(((RunCommandTargetPreparer) preparer2).getCommands())
                .containsExactlyElementsIn(
                        SecondaryUserOnSecondaryDisplayHandler.LOCATION_COMMANDS);
    }
}

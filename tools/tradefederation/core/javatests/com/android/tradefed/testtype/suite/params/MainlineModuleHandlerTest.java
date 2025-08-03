/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.InstallApexModuleTargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link MainlineModuleHandler}. */
@RunWith(JUnit4.class)
public final class MainlineModuleHandlerTest {

    private MainlineModuleHandler mHandler;
    private IConfiguration mConfig;
    @Mock IBuildInfo mMockBuildInfo;
    private IInvocationContext mContext;
    private IAbi mAbi;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mConfig = new Configuration("test", "test");

        mContext = new InvocationContext();
        mAbi = new Abi("armeabi-v7a", "32");
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        when(mMockBuildInfo.getBuildId()).thenReturn("id");
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockBuildInfo);
    }

    /** Test that when a module configuration go through the handler it gets tuned properly. */
    @Test
    public void testApplySetup() {
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");

        mHandler = new MainlineModuleHandler("mod1.apk", mAbi, mContext, false);
        mHandler.applySetup(mConfig);

        assertTrue(mConfig.getTargetPreparers().get(0) instanceof InstallApexModuleTargetPreparer);
        InstallApexModuleTargetPreparer preparer =
                (InstallApexModuleTargetPreparer) mConfig.getTargetPreparers().get(0);
        assertEquals(preparer.getTestsFileName().size(), 1);
        assertEquals(preparer.getTestsFileName().get(0).getName(), "mod1.apk");
    }

    /** Test that when a module configuration go through the handler it gets tuned properly. */
    @Test
    public void testApplySetup_MultipleMainlineModules() {
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");

        mHandler = new MainlineModuleHandler("mod1.apk+mod2.apex", mAbi, mContext, false);
        mHandler.applySetup(mConfig);

        assertTrue(mConfig.getTargetPreparers().get(0) instanceof InstallApexModuleTargetPreparer);
        InstallApexModuleTargetPreparer preparer =
                (InstallApexModuleTargetPreparer) mConfig.getTargetPreparers().get(0);
        assertEquals(preparer.getTestsFileName().size(), 2);
        assertEquals(preparer.getTestsFileName().get(0).getName(), "mod1.apk");
        assertEquals(preparer.getTestsFileName().get(1).getName(), "mod2.apex");
    }

    /**
     * Test for {@link MainlineModuleHandler#buildDynamicBaseLink(IBuildInfo)} implementation to
     * throw an exception when the build information isn't correctly set.
     */
    @Test
    public void testBuildDynamicBaseLink_BranchIsNotSet() throws Exception {
        try {
            when(mMockBuildInfo.getBuildBranch()).thenReturn(null);

            mHandler = new MainlineModuleHandler("mod1.apk+mod2.apex", mAbi, mContext, false);
            fail("Should have thrown an exception.");
        } catch (IllegalArgumentException expected) {
            // expected
            assertEquals(
                    "Missing required information to build the dynamic base link.",
                    expected.getMessage());
        }
    }

    /**
     * Test for {@link MainlineModuleHandler#applySetup()} anticipate returning
     * ab://branch/flavor/id/mainline_module_{abi}/foo.apk when running in CI where
     * ANDROID_BUILD_TOP is null.
     */
    @Test
    public void testApplySetup_CompleteMainlineModulePath_inCI() {
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");

        // In CI the System.getenv("ANDROID_BUILD_TOP") is always null.
        mHandler = new MainlineModuleHandler("mod1.apk+mod2.apex", null, mAbi, mContext);
        mHandler.applySetup(mConfig);

        assertTrue(mConfig.getTargetPreparers().get(0) instanceof InstallApexModuleTargetPreparer);
        InstallApexModuleTargetPreparer preparer =
                (InstallApexModuleTargetPreparer) mConfig.getTargetPreparers().get(0);
        assertThat(preparer.getTestsFileName().get(0).getPath())
                .contains("ab:/branch/flavor/id/mainline_modules_arm/mod1.apk");
        assertThat(preparer.getTestsFileName().get(1).getPath())
                .contains("ab:/branch/flavor/id/mainline_modules_arm/mod2.apex");
    }

    /**
     * Test for {@link MainlineModuleHandler#applySetup()} anticipate returning
     * /android/buildtop/out/dist/mainline_modules_{abi}/foo.apk when running in local where
     * ANDROID_BUILD_TOP always has a value.
     */
    @Test
    public void testApplySetup_CompleteMainLineModulePath_inLocal() {
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");

        mHandler =
                new MainlineModuleHandler(
                        "mod1.apk+mod2.apex", "/android/build/top", mAbi, mContext);
        mHandler.applySetup(mConfig);

        assertTrue(mConfig.getTargetPreparers().get(0) instanceof InstallApexModuleTargetPreparer);
        InstallApexModuleTargetPreparer preparer =
                (InstallApexModuleTargetPreparer) mConfig.getTargetPreparers().get(0);
        assertThat(preparer.getTestsFileName().get(0).getPath())
                .contains("/android/build/top/out/dist/mainline_modules_arm/mod1.apk");
        assertThat(preparer.getTestsFileName().get(1).getPath())
                .contains("/android/build/top/out/dist/mainline_modules_arm/mod2.apex");
    }
}

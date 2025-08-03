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

package android.cts.install.host;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import android.cts.install.INSTALL_TYPE;
import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.util.CpuFeatures;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(DeviceParameterized.class)
@UseParametersRunnerFactory(DeviceParameterized.RunnerFactory.class)
public final class InstallTest extends BaseHostJUnit4Test {
    private static final String PACKAGE_NAME = "android.cts.install";
    private static final String CUSTOMIZED_LAUNCHER_COMPONENT =
            PACKAGE_NAME + "/" + PACKAGE_NAME + ".LauncherActivity";
    private static final String PHASE_FORMAT_SUFFIX = "[%s_Staged%b_Rollback%b]";

    private static final String CLEAN_UP_PHASE = "cleanUp_phase";
    private static final String ACTION_PHASE = "action_phase";
    private static final String ACTION_ABANDON_SESSION_PHASE = "action_abandonSession_phase";
    private static final String ASSERT_PHASE = "assert_phase";
    private static final String ASSERT_COMMIT_FAILURE_PHASE = "assert_commitFailure_phase";
    private static final String ASSERT_PRE_REBOOT_PHASE = "assert_preReboot_phase";
    private static final String ASSERT_POST_REBOOT_PHASE = "assert_postReboot_phase";
    private static final String ASSERT_ABANDON_SESSION = "assert_abandonSession_phase";

    @Rule
    public ShimApexRule mShimApexRule = new ShimApexRule(this);

    @Rule
    public LauncherRule mLauncherRule = new LauncherRule(this, CUSTOMIZED_LAUNCHER_COMPONENT);

    @Parameter(0)
    public INSTALL_TYPE mInstallType;

    @Parameter(1)
    public boolean mEnableRollback;

    @Parameters(name = "{0}_Rollback{1}")
    public static Collection<Object[]> combinations() {
        boolean[] booleanValues = new boolean[]{true, false};
        List<Object[]> temp = new ArrayList<>();
        for (INSTALL_TYPE installType: INSTALL_TYPE.values()) {
            for (boolean enableRollback: booleanValues) {
                temp.add(new Object[]{installType, enableRollback});
            }
        }
        return temp;
    }

    private boolean mStaged;

    @Before
    @After
    public void cleanUp() throws Exception {
        runPhase(CLEAN_UP_PHASE);
    }

    @Before
    public void assumeNotNativeBridgeWithApex() throws Exception {
        if (!CpuFeatures.isNativeAbi(getDevice(), getAbi().getName())) {
            assumeFalse("APEX packages do not work with native bridge",
                    mInstallType.containsApex());
        }
    }

    @Test
    public void testInstall() throws Exception {
        mStaged = false;
        if (mInstallType.containsApex()) {
            runPhase(ASSERT_COMMIT_FAILURE_PHASE);
            return;
        }
        runPhase(ACTION_PHASE);
        runPhase(ASSERT_PHASE);
    }

    @Test
    @LargeTest
    public void testStagedInstall() throws Exception {
        mStaged = true;
        runPhase(ACTION_PHASE);
        runPhase(ASSERT_PRE_REBOOT_PHASE);
        getDevice().reboot();
        runPhase(ASSERT_POST_REBOOT_PHASE);
    }

    @Test
    public void testAbandonStagedSessionBeforeReboot() throws Exception {
        mStaged = true;
        runPhase(ACTION_PHASE);
        runPhase(ASSERT_PRE_REBOOT_PHASE);
        runPhase(ACTION_ABANDON_SESSION_PHASE);
        runPhase(ASSERT_ABANDON_SESSION);
    }

    @Test
    @LargeTest
    public void testAbandonStagedSessionAfterReboot() throws Exception {
        mStaged = true;
        runPhase(ACTION_PHASE);
        getDevice().reboot();
        runPhase(ACTION_ABANDON_SESSION_PHASE);
        runPhase(ASSERT_POST_REBOOT_PHASE);
    }

    /**
     * Runs the given phase of a test with parameters by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("action_phase");</code>
     */
    private void runPhase(String phase) throws DeviceNotAvailableException {
        assertThat(runDeviceTests(PACKAGE_NAME,
                String.format("%s.%s", PACKAGE_NAME, this.getClass().getSimpleName()),
                String.format(phase + PHASE_FORMAT_SUFFIX, mInstallType, mStaged, mEnableRollback)))
                .isTrue();
    }
}

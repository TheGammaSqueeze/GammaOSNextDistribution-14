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
import static org.junit.Assume.assumeTrue;

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
public final class SamegradeTest extends BaseHostJUnit4Test {
    private static final String PACKAGE_NAME = "android.cts.install";
    private static final String PHASE_FORMAT_SUFFIX = "[%s_Staged%b_Rollback%b]";
    private static final String ARRANGE_PHASE = "arrange_phase";
    private static final String ASSERT_POST_ARRANGE_PHASE = "assert_postArrange_phase";
    private static final String ACTION_PHASE = "action_phase";
    private static final String ACTION_SYSTEMAPEX_PHASE = "action_systemApex_phase";
    private static final String ASSERT_PRE_REBOOT_PHASE = "assert_preReboot_phase";
    private static final String ASSERT_POST_REBOOT_PHASE = "assert_postReboot_phase";
    private static final String ASSERT_SYSTEMAPEX_REBOOT_PHASE =
            "assert_systemApex_postReboot_phase";
    private static final String ASSERT_PHASE = "assert_phase";
    private static final String CLEAN_UP_PHASE = "cleanUp_phase";

    @Rule
    public ShimApexRule mShimApexRule = new ShimApexRule(this);

    @Parameter(0)
    public INSTALL_TYPE mInstallType;

    @Parameter(1)
    public boolean mEnableRollback;

    @Parameters(name = "{0}_Rollback{1}")
    public static Collection<Object[]> combinations() {
        boolean[] booleanValues = new boolean[]{true, false};
        List<Object[]> temp = new ArrayList<>();
        for (INSTALL_TYPE installType : INSTALL_TYPE.values()) {
            for (boolean enableRollback : booleanValues) {
                temp.add(new Object[]{installType, enableRollback});
            }
        }
        return temp;
    }

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

    /**
     * Samegrading on a non-APEX install type should be success.
     */
    @Test
    public void testNonStagedSamegrade() throws Exception {
        // Apex should not be committed in non-staged install, such logic covered in InstallTest.
        assumeFalse(mInstallType.containsApex());

        runPhase(ARRANGE_PHASE);
        runPhase(ASSERT_POST_ARRANGE_PHASE);

        runPhase(ACTION_PHASE);

        runPhase(ASSERT_PHASE);
    }

    @Test
    @LargeTest
    public void testStagedSameGrade() throws Exception {
        assumeTrue(mInstallType.containsApex());
        runStagedPhase(ARRANGE_PHASE);
        getDevice().reboot();
        runStagedPhase(ASSERT_POST_ARRANGE_PHASE);

        runStagedPhase(ACTION_PHASE);

        runStagedPhase(ASSERT_PRE_REBOOT_PHASE);
        getDevice().reboot();
        runStagedPhase(ASSERT_POST_REBOOT_PHASE);
    }

    @Test
    @LargeTest
    public void testStagedSamegradeSystemApex() throws Exception {
        assumeTrue(mInstallType.containsApex());

        runStagedPhase(ACTION_SYSTEMAPEX_PHASE);
        getDevice().reboot();

        runStagedPhase(ASSERT_SYSTEMAPEX_REBOOT_PHASE);
    }

    private void runPhase(String phase) throws DeviceNotAvailableException {
        runPhase(phase, false /* staged */);
    }

    private void runStagedPhase(String phase) throws DeviceNotAvailableException {
        runPhase(phase, true /* staged */);
    }

    /**
     * Runs the given phase of a test with parameters by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("action_phase", true);</code>
     */
    private void runPhase(String phase, boolean staged) throws DeviceNotAvailableException {
        assertThat(runDeviceTests(PACKAGE_NAME,
                String.format("%s.%s", PACKAGE_NAME, this.getClass().getSimpleName()),
                String.format(phase + PHASE_FORMAT_SUFFIX, mInstallType, staged, mEnableRollback)))
                .isTrue();
    }
}

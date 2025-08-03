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

package android.extractnativelibs.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;
import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.util.AbiUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.ArrayList;
import java.util.Collection;
/**
 * Host test to update test apps with different ABIs.
 */
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
public class CtsExtractNativeLibsHostTestUpdates extends CtsExtractNativeLibsHostTestBase {
    @Parameter(0)
    public boolean mIsExtractNativeLibs;

    @Parameter(1)
    public boolean mIsIncremental;

    @Parameter(2)
    public String mFirstAbiSuffix;

    @Parameter(3)
    public String mSecondAbiSuffix;

    /**
     * Generate parameters for mutations of extract/embedded, incremental/legacy,
     * and apps of 32/64/Both ABIs updating to a ABI
     */
    @Parameterized.Parameters(name = "{index}: Test with mIsExtractNativeLibs={0}, "
            + "mIsIncremental={1}, mFirstAbiSuffix={2}, mSecondAbiSuffix={3}")
    public static Collection<Object[]> data() {
        final boolean[] isExtractNativeLibsParams = new boolean[]{false, true};
        final boolean[] isIncrementalParams = new boolean[]{false, true};
        final String[] abiSuffixParams = new String[]{"32", "64", "Both"};
        ArrayList<Object[]> params = new ArrayList<>();
        for (boolean isExtractNativeLibs : isExtractNativeLibsParams) {
            for (boolean isIncremental : isIncrementalParams) {
                for (String firstAbiSuffix : abiSuffixParams) {
                    for (String secondAbiSuffix : abiSuffixParams) {
                        if (!firstAbiSuffix.equals(secondAbiSuffix)) {
                            params.add(new Object[]{isExtractNativeLibs, isIncremental,
                                    firstAbiSuffix, secondAbiSuffix});
                        }
                    }
                }
            }
        }
        return params;
    }

    @Override
    public void setUp() throws Exception {
        // Skip the test if the requested test ABI is 32 bit (that defeats the purpose of this test)
        assumeTrue(AbiUtils.getBitness(getAbi().getName()).equals("64"));
        if (mIsIncremental) {
            // Skip incremental installations for non-incremental devices
            assumeTrue(isIncrementalInstallSupported());
        }
        // Skip unsupported abi suffixes.
        if (!mFirstAbiSuffix.equals("Both")) {
            assumeTrue(getDeviceAbiSuffixes().contains(mFirstAbiSuffix));
        }
        if (!mSecondAbiSuffix.equals("Both")) {
            assumeTrue(getDeviceAbiSuffixes().contains(mSecondAbiSuffix));
        }
        super.setUp();
    }

    /**
     * Test update installs and runs. Verify native lib dir layout.
     */
    @Test
    @AppModeFull
    public void testUpdateAndRunSuccess() throws Exception {
        final String testPackageName = getTestPackageName(mIsExtractNativeLibs);
        final String testClassName = getTestClassName(mIsExtractNativeLibs);
        installPackage(mIsIncremental, getTestApkName(mIsExtractNativeLibs, mFirstAbiSuffix));
        assertTrue(isPackageInstalled(testPackageName));
        installPackage(mIsIncremental, getTestApkName(mIsExtractNativeLibs, mSecondAbiSuffix));
        assertTrue(runDeviceTests(testPackageName, testClassName, TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkNativeLibDir(mIsExtractNativeLibs, mSecondAbiSuffix));
    }
}

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

import static org.junit.Assert.assertEquals;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Host test to update test apps with different ABIs.
 */
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
public class CtsExtractNativeLibsHostTestAbiOverride extends CtsExtractNativeLibsHostTestBase {
    @Parameter(0)
    public boolean mIsExtractNativeLibs;

    @Parameter(1)
    public boolean mIsIncremental;

    @Parameter(2)
    public String mFirstAbi;

    @Parameter(3)
    public String mSecondAbi;

    @Override
    public void setUp() throws Exception {
        // Skip the test if the requested test ABI is 32 bit (that defeats the purpose of this test)
        assumeTrue(AbiUtils.getBitness(getAbi().getName()).equals("64"));
        final String deviceAbi = getDeviceAbi();
        final Set<String> deviceAbis = getDeviceAbis();
        final Set<String> apkAbis = getApkAbis();
        // Only run these tests for supported ABIs
        assumeTrue(deviceAbis.contains(mFirstAbi));
        assumeTrue(apkAbis.contains(mFirstAbi));
        if (!mSecondAbi.equals("-")) {
            assumeTrue(deviceAbis.contains(mSecondAbi));
            assumeTrue(apkAbis.contains(mSecondAbi));
        }
        assumeTrue(AbiUtils.getBaseArchForAbi(deviceAbi).equals(
                AbiUtils.getBaseArchForAbi(mFirstAbi)));
        if (mIsIncremental) {
            // Skip incremental installations for non-incremental devices
            assumeTrue(isIncrementalInstallSupported());
        }
        super.setUp();
    }

    /**
     * Generate parameters for mutations of extract/embedded, incremental/legacy,
     * and apps of an abi override updating to another abi override
     */
    @Parameterized.Parameters(name = "{index}: Test with mIsExtractNativeLibs={0}, "
            + "mIsIncremental={1}, mFirstAbi={2}, mSecondAbi={3}")
    public static Collection<Object[]> data() {
        final boolean[] isExtractNativeLibsParams = new boolean[]{false, true};
        final boolean[] isIncrementalParams = new boolean[]{false, true};
        // We don't know the supported ABIs ahead of the time, here we enumerate all possible ones
        // and filter unsupported ones during tests
        final Set<String> supportedAbis = AbiUtils.getAbisSupportedByCompatibility();
        ArrayList<Object[]> params = new ArrayList<>();
        for (boolean isExtractNativeLibs : isExtractNativeLibsParams) {
            for (boolean isIncremental : isIncrementalParams) {
                for (String firstAbi : supportedAbis) {
                    for (String secondAbi : supportedAbis) {
                        if (!firstAbi.equals(secondAbi)
                                && AbiUtils.getBaseArchForAbi(firstAbi).equals(
                                        AbiUtils.getBaseArchForAbi(secondAbi))) {
                            params.add(new Object[]{isExtractNativeLibs, isIncremental,
                                    firstAbi, secondAbi});
                        }
                    }
                    params.add(new Object[]{isExtractNativeLibs, isIncremental,
                            firstAbi, "-"});
                }
            }
        }
        return params;
    }

    /**
     * Test update installs with abi override and runs. Verify native lib dir layout.
     */
    @Test
    @AppModeFull
    public void testAbiOverrideAndRunSuccess() throws Exception {
        final String testPackageName = getTestPackageName(mIsExtractNativeLibs);
        final String testClassName = getTestClassName(mIsExtractNativeLibs);
        // First install with one abi override
        installPackage(mIsIncremental, getTestApkName(mIsExtractNativeLibs, "Both"), mFirstAbi);
        assertTrue(isPackageInstalled(testPackageName));
        assertTrue(runDeviceTests(testPackageName, testClassName, TEST_NATIVE_LIB_LOADED_TEST));
        assertEquals(mFirstAbi, getPackageAbi(testPackageName));
        assertTrue(checkNativeLibDir(mIsExtractNativeLibs, AbiUtils.getBitness(mFirstAbi)));
        // Then update with another abi override
        installPackage(mIsIncremental, getTestApkName(mIsExtractNativeLibs, "Both"), mSecondAbi);
        assertTrue(runDeviceTests(testPackageName, testClassName, TEST_NATIVE_LIB_LOADED_TEST));
        final String expectedAbi;
        if (mSecondAbi.equals("-")) {
            expectedAbi = getExpectedLibAbi("Both");
        } else {
            expectedAbi = mSecondAbi;
        }
        assertEquals(expectedAbi, getPackageAbi(testPackageName));
        assertTrue(checkNativeLibDir(mIsExtractNativeLibs, AbiUtils.getBitness(expectedAbi)));
    }

    private String getPackageAbi(String testPackageName) throws Exception {
        String commandResult = getDevice().executeShellCommand("pm dump " + testPackageName);
        Optional<String> maybePrimaryCpuAbiStr = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.contains("primaryCpuAbi"))
                .findFirst();
        assertTrue(maybePrimaryCpuAbiStr.isPresent());
        return maybePrimaryCpuAbiStr.get().substring(maybePrimaryCpuAbiStr.get().indexOf("=") + 1);
    }
}

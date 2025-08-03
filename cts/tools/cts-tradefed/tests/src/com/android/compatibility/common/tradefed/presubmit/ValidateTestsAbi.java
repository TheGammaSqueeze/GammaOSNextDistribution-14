/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.presubmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tests to validate that the build is containing usable test artifact.
 */
@RunWith(JUnit4.class)
public class ValidateTestsAbi {

    private static final Set<String> APK_EXCEPTIONS = new HashSet<>();
    static {
        /**
         *  This particular module is shipping all its dependencies in all abis with prebuilt stuff.
         *  Excluding it for now to have the test setup.
         */
        APK_EXCEPTIONS.add("CtsSplitApp");

        /**
         *  This module tests for security vulnerabilities when installing attacker-devised APKs.
         */
        APK_EXCEPTIONS.add("CtsCorruptApkTests");

        /**
         * This module tests for installations of packages that have only 32-bit native libraries
         * and extract native libraries.
         */
        APK_EXCEPTIONS.add("CtsExtractNativeLibsAppTrue32");

        /**
         * This module tests for installations of packages that have only 64-bit native libraries
         * and extract native libraries.
         */
        APK_EXCEPTIONS.add("CtsExtractNativeLibsAppTrue64");
        /**
         * This module tests for installations of packages that have only 32-bit native libraries
         * and embed native libraries.
         */
        APK_EXCEPTIONS.add("CtsExtractNativeLibsAppFalse32");

        /**
         * This module tests for installations of packages that have only 64-bit native libraries
         * and embed native libraries.
         */
        APK_EXCEPTIONS.add("CtsExtractNativeLibsAppFalse64");

        /**
         * These apks are prebuilts needed for some tests
         */
        APK_EXCEPTIONS.add("CtsApkVerityTestAppPrebuilt");
        APK_EXCEPTIONS.add("CtsApkVerityTestApp2Prebuilt");

        /**
         * Data apk used by SimpleperfTestCases
         */
        APK_EXCEPTIONS.add("base");

        /**
         * This module tests that packages with only 32-bit native libraries will receive a
         * warning message when running on devices that support both 32-bit and 64-bit ABIs.
         */
        APK_EXCEPTIONS.add("CtsDeviceDeprecatedAbiApp");
    }

    private static final Set<String> BINARY_EXCEPTIONS = new HashSet<>();
    static {
        /**
         * These binaries are host side helpers, so we do not need to check them.
         */
        BINARY_EXCEPTIONS.add("sepolicy-analyze");
        BINARY_EXCEPTIONS.add("avbtool");
        BINARY_EXCEPTIONS.add("img2simg");
        BINARY_EXCEPTIONS.add("initrd_bootconfig");
        BINARY_EXCEPTIONS.add("lpmake");
        BINARY_EXCEPTIONS.add("lpunpack");
        BINARY_EXCEPTIONS.add("mk_payload");
        BINARY_EXCEPTIONS.add("sign_virt_apex");
        BINARY_EXCEPTIONS.add("simg2img");
        BINARY_EXCEPTIONS.add("dtdiff");
        BINARY_EXCEPTIONS.add("dtc");
        BINARY_EXCEPTIONS.add("lz4");

        /**
         * These binaries are testing components with no 32-bit variant, which
         * means their dependent libraries by default will not have 32-bit
         * variants on the device, and which gain no additional testing coverage
         * by forcing those variants to be available.
         */
        BINARY_EXCEPTIONS.add("CtsInitTestCases");
    }

    private static final String BINARY_EXCEPTIONS_REGEX [] = {
        /**
         * This regular expression matches any binary of the form 'CVE-xxxx-yyyyyy'.
         * Hence this can be used for tests that build for either 32 bit or 64 bit only.
         */
        "^CVE-\\d{4}-.+$"
    };

    private static final String[] BINARY_SUFFIX_EXCEPTIONS = {
        /**
         * All STS test binaries rvc+ are in the form of *_sts32 or *_sts64.
         *
         * Many STS binaries are only feasible on a specific bitness so STS
         * pushes the appropriate binary to compatible devices.
         */
        "_sts32", "_sts64",
    };

    /**
     * Test that all apks have the same supported abis.
     * Sometimes, if a module is missing LOCAL_MULTILIB := both, we will end up with only one of
     * the two abis required and the second one will fail.
     */
    @Ignore // TODO(b/1555499)
    @Test
    public void testApksAbis() throws IOException {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exists", testcases));
            return;
        }
        Set<File> listApks = FileUtil.findFilesObject(testcases, ".*\\.apk");
        listApks.removeIf(
                a -> {for (String apk : APK_EXCEPTIONS) {
                    if (a.getName().startsWith(apk)) {
                        return true;
                    }
                }
                return false;});
        assertTrue(listApks.size() > 0);
        int maxAbi = 0;
        Map<String, Integer> apkToAbi = new HashMap<>();

        for (File testApk : listApks) {
            AaptParser result = AaptParser.parse(testApk);
            // Retry as we have seen flake with aapt sometimes.
            if (result == null) {
                for (int i = 0; i < 2; i++) {
                    result = AaptParser.parse(testApk);
                    if (result != null) {
                        break;
                    }
                }
                // If still couldn't parse the apk
                if (result == null) {
                    fail(String.format("Fail to run 'aapt dump badging %s'",
                            testApk.getAbsolutePath()));
                }
            }
            // We only check the apk that have native code
            if (!result.getNativeCode().isEmpty()) {
                List<String> supportedAbiApk = result.getNativeCode();
                Set<String> buildTarget = AbiUtils.getAbisForArch(
                        TestSuiteInfo.getInstance().getTargetArchs().get(0));
                // first check, all the abis in the buildTarget are supported
                for (String abiBT : buildTarget) {
                    Boolean findMatch = false;
                    for (String abiApk : supportedAbiApk) {
                        if (abiApk.equals(abiBT)) {
                            findMatch = true;
                            break;
                        }
                    }
                    if (!findMatch) {
                        fail(String.format("apk %s %s does not support our abis [%s]",
                                testApk.getName(), supportedAbiApk, buildTarget));
                    }
                }
                apkToAbi.put(testApk.getName(), supportedAbiApk.size());
                maxAbi = Math.max(maxAbi, buildTarget.size());
            }
        }

        // We do a second pass to make sure nobody is short on abi
        for (Entry<String, Integer> apk : apkToAbi.entrySet()) {
            if (apk.getValue() < maxAbi) {
                fail(String.format("apk %s only has %s abi when it should have %s", apk.getKey(),
                        apk.getValue(), maxAbi));
            }
        }
    }

    /**
     * Test that when CTS has multiple abis, we have binary for each ABI. In this case the abi will
     * be the same with different bitness (only case supported by build system).
     * <p/>
     * If there is only one bitness, then we check that it's the right one.
     */
    @Test
    public void testBinariesAbis() throws IOException {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exist", testcases));
            return;
        }
        Set<File> listBinaries = FileUtil.findFilesObject(testcases, ".*");
        listBinaries.removeIf(f -> {
                String name = f.getName();
                if (name.contains(".")) {
                    return true;
                }
                if (BINARY_EXCEPTIONS.contains(name)) {
                    return true;
                }
                for (String suffixException : BINARY_SUFFIX_EXCEPTIONS) {
                    if (name.endsWith(suffixException)) {
                        return true;
                    }
                }
                if (f.isDirectory()) {
                    return true;
                }
                if (!f.canExecute()) {
                    return true;
                }
                try {
                    // Ignore python binaries
                    String content = FileUtil.readStringFromFile(f);
                    if (content.startsWith("#!/usr/bin/env python")) {
                        return true;
                    }
                    if (content.contains("mobly/__init__.py")) {
                        return true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                for(String pattern: BINARY_EXCEPTIONS_REGEX) {
                    Matcher matcher = Pattern.compile(pattern).matcher(name);
                    if (matcher.matches()) {
                        return true;
                    }
                }
                return false;
        });
        assertTrue(listBinaries.size() > 0);
        List<String> orderedList = listBinaries.stream().map(f->f.getName()).collect(Collectors.toList());
        // we sort to have binary starting with same name, next to each other. The last two
        // characters of their name with be the bitness (32 or 64).
        Collections.sort(orderedList);
        Set<String> buildTarget = AbiUtils.getAbisForArch(
                TestSuiteInfo.getInstance().getTargetArchs().get(0));
        // We expect one binary per abi of CTS, they should be appended with 32 or 64
        for (int i = 0; i < orderedList.size(); i=i + buildTarget.size()) {
            List<String> subSet = orderedList.subList(i, i + buildTarget.size());
            if (subSet.size() > 1) {
                String base = subSet.get(0).substring(0, subSet.get(0).length() - 2);
                for (int j = 0; j < subSet.size(); j++) {
                    assertEquals(base, subSet.get(j).substring(0, subSet.get(j).length() - 2));
                }
            } else {
                String bitness = AbiUtils.getBitness(buildTarget.iterator().next());
                assertTrue(subSet.get(i).endsWith(bitness));
            }
        }
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.cts.webkit;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.Set;

/** Tests for {@link android.webkit.URLUtil} */
public class WebViewHostSideURLUtilTest extends CompatChangeGatingTestCase {
    private static final long PARSE_CONTENT_DISPOSITION_USING_RFC_6266 = 319400769L;

    private static final String TEST_APK = "CtsWebViewCompatChangeApp.apk";
    private static final String TEST_PKG = "com.android.cts.webkit.compatchange";
    private static final String TEST_CLASS = ".WebViewDeviceSideURLUtilTest";

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isVanillaIceCreamBuildFlagEnabled() throws DeviceNotAvailableException {
        String output =
                runCommand("device_config get build android.os.android_os_build_vanilla_ice_cream");
        return Boolean.parseBoolean(output);
    }

    public void testGuessFileNameChangeDisabled() throws Exception {
        if (!isVanillaIceCreamBuildFlagEnabled()) {
            return; // Feature only supported on V+
        }
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS,
                "guessFileName_legacyBehavior",
                /*enabledChanges*/ Set.of(),
                /*disabledChanges*/ Set.of(PARSE_CONTENT_DISPOSITION_USING_RFC_6266));
    }

    public void testGuessFileNameChangeEnabled() throws Exception {
        if (!isVanillaIceCreamBuildFlagEnabled()) {
            return; // Feature only supported on V+
        }
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS,
                "guessFileName_usesRfc6266",
                /*enabledChanges*/ Set.of(PARSE_CONTENT_DISPOSITION_USING_RFC_6266),
                /*disabledChanges*/ Set.of());
    }
}

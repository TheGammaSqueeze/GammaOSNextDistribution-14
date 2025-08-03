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

package android.cts.statsdatom.voiceinteraction;

import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.DeviceUtils;

import com.android.tradefed.device.ITestDevice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An util class that provides methods for Hotword metrics logging test.
 */
public class HotwordMetricsTestUtils {

    public static final String TEST_PKG = "android.voiceinteraction.cts";
    public static final String TEST_APK = "CtsVoiceInteractionTestCases.apk";
    public static final String TEST_CLASS =
            "android.voiceinteraction.cts.HotwordDetectionServiceBasicTest";

    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";

    public static int getTestAppUid(ITestDevice device) throws Exception {
        final int currentUser = device.getCurrentUser();
        final String uidLine = device.executeShellCommand(
                "cmd package list packages -U --user " + currentUser + " " + TEST_PKG);
        final Pattern pattern = Pattern.compile("package:" + TEST_PKG + " uid:(\\d+)");
        final Matcher matcher = pattern.matcher(uidLine);
        assertWithMessage("Pkg not found: " + TEST_PKG).that(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Check whether the device is supported or not. Currently, the device needs to have
     * FEATURE_MICROPHONE.
     *
     * @param device the device
     * @return {@code True} if the device is supported. Otherwise, return {@code false}.
     * @throws Exception
     */
    public static boolean isSupportedDevice(ITestDevice device) throws Exception {
        return DeviceUtils.hasFeature(device, FEATURE_MICROPHONE);
    }
}

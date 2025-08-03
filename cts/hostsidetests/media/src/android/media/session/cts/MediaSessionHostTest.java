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

package android.media.session.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;

import com.google.common.collect.ImmutableSet;

import java.io.FileNotFoundException;
import java.util.Collections;

public class MediaSessionHostTest extends CompatChangeGatingTestCase {
    /** Package name of device-side tests. */
    private static final String DEVICE_SIDE_TEST_PKG = "android.media.session.cts";
    /** Package file name (.apk) of device-side tests. */
    private static final String DEVICE_SIDE_TEST_APK = "CtsMediaSessionHostTestApp.apk";
    /** Fully qualified class name for device-side tests. */
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.media.session.cts.MediaSessionTest";

    /**
     * Change id from {@link
     * com.android.server.media.MediaSessionRecord#THROW_FOR_INVALID_BROADCAST_RECEIVER}.
     */
    private static final long THROW_FOR_INVALID_BROADCAST_RECEIVER = 270049379L;
    /**
     * Change id from {@link
     * com.android.server.media.MediaSessionRecord#THROW_FOR_ACTIVITY_MEDIA_BUTTON_RECEIVER}.
     */
    private static final long THROW_FOR_ACTIVITY_MEDIA_BUTTON_RECEIVER = 272737196L;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installTestPackage(DEVICE_SIDE_TEST_APK, true);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        uninstallPackage(DEVICE_SIDE_TEST_PKG, true);
    }

    private void installTestPackage(String appFileName, boolean grantPermissions)
            throws FileNotFoundException, DeviceNotAvailableException {
        // Workaround due to b/277900877.
        LogUtil.CLog.d("Installing app " + appFileName);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final String result =
                getDevice()
                        .installPackage(
                                buildHelper.getTestFile(appFileName), true, grantPermissions, "-t");
        assertWithMessage("Failed to install %s: %s", appFileName, result).that(result).isNull();
    }

    public void testSetMediaButtonBroadcastReceiverChangeEnabled() throws Exception {
        runDeviceCompatTest(
                DEVICE_SIDE_TEST_PKG,
                DEVICE_SIDE_TEST_CLASS,
                "setMediaButtonBroadcastReceiver_withFakeReceiver_changeEnabled_throwsIAE",
                /* Enabled changes */ ImmutableSet.of(THROW_FOR_INVALID_BROADCAST_RECEIVER),
                /* Disabled changes */ Collections.emptySet());
    }

    public void testSetMediaButtonBroadcastReceiverChangeDisabled() throws Exception {
        runDeviceCompatTest(
                DEVICE_SIDE_TEST_PKG,
                DEVICE_SIDE_TEST_CLASS,
                "setMediaButtonBroadcastReceiver_withFakeReceiver_changeDisabled_isIgnored",
                /* Enabled changes */ Collections.emptySet(),
                /* Disabled changes */ ImmutableSet.of(THROW_FOR_INVALID_BROADCAST_RECEIVER));
    }

    public void testSetMediaButtonReceiverChangeEnabled() throws Exception {
        // TargetSDK threshold is set to V+, so trying to enable the compat change would fail on
        // some device configurations.
        DeviceSdkLevel sdkLevel = new DeviceSdkLevel(getDevice());
        if (!sdkLevel.isDeviceAtLeastV()) {
            LogUtil.CLog.w("SDK level is not V+. Ignoring test.");
            return;
        }

        runDeviceCompatTest(
                DEVICE_SIDE_TEST_PKG,
                DEVICE_SIDE_TEST_CLASS,
                "setMediaButtonReceiver_withActivity_changeEnabled_throwsIAE",
                /* Enabled changes */ ImmutableSet.of(THROW_FOR_ACTIVITY_MEDIA_BUTTON_RECEIVER),
                /* Disabled changes */ Collections.emptySet());
    }

    public void testSetMediaButtonReceiverChangeDisabled() throws Exception {
        runDeviceCompatTest(
                DEVICE_SIDE_TEST_PKG,
                DEVICE_SIDE_TEST_CLASS,
                "setMediaButtonReceiver_withActivity_changeDisabled_isIgnored",
                /* Enabled changes */ Collections.emptySet(),
                /* Disabled changes */ ImmutableSet.of(THROW_FOR_ACTIVITY_MEDIA_BUTTON_RECEIVER));
    }
}

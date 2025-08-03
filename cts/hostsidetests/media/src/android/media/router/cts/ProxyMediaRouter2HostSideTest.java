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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_SECONDARY_USER_HELPER_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_APK;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_APK;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS;
import static android.media.cts.MediaRouterTestConstants.TARGET_USER_ID_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import com.google.common.truth.Expect;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@RunWith(DeviceJUnit4ClassRunner.class)
public class ProxyMediaRouter2HostSideTest extends BaseHostJUnit4Test {

    @ClassRule public static final Expect expect = Expect.create();
    private static int secondaryUser = -1;

    @BeforeClassWithInfo
    public static void installApps(TestInformation testInformation)
            throws DeviceNotAvailableException, FileNotFoundException {
        ITestDevice device = testInformation.getDevice();
        secondaryUser = device.createUser("TEST_USER", false, false, true);

        installTestAppAsUser(testInformation, MEDIA_ROUTER_PROVIDER_1_APK, secondaryUser);
        installTestAppAsUser(
                testInformation, MEDIA_ROUTER_SECONDARY_USER_HELPER_APK, secondaryUser);
        assertThat(secondaryUser).isNotEqualTo(-1);
        assertThat(secondaryUser).isNotEqualTo(device.getCurrentUser());

        installTestAppAsUser(testInformation, MEDIA_ROUTER_PROVIDER_1_APK, device.getCurrentUser());

        installTestAppAsUser(
                testInformation,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_APK,
                device.getCurrentUser());

        installTestAppAsUser(
                testInformation,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_APK,
                device.getCurrentUser());
    }

    @AfterClassWithInfo
    public static void uninstallApps(TestInformation testInformation)
            throws DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();

        expect.that(device.uninstallPackage(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE)).isNull();

        expect.that(
                        device.uninstallPackageForUser(
                                MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE, secondaryUser))
                .isNull();

        // This uninstalls package across all users.
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_1_PACKAGE)).isNull();

        assertThat(device.removeUser(secondaryUser)).isTrue();
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withScreenOnScanning_triggersScanning()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS,
                "requestScan_withScreenOnScanning_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS,
                "requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void cancelScanRequest_callTwice_throwsIllegalArgumentException()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS,
                "cancelScanRequest_callTwice_throwsIllegalArgumentException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_acrossUsers_withInteractAcrossUsersFull_returnsInstance()
            throws DeviceNotAvailableException {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(
                        PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE)
                        .setTestClassName(
                                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS)
                        .setTestMethodName(
                                "getInstance_acrossUsers_withInteractAcrossUsersFull_"
                                        + "returnsInstance")
                        .addInstrumentationArg(TARGET_USER_ID_KEY, String.valueOf(secondaryUser));

        runDeviceTests(options);
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withMediaRoutingControl_withScreenOff_triggersScanning()
            throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "requestScan_withScreenOff_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_acrossUsers_withoutInteractAcrossUsersFull_throwsSecurityException()
            throws DeviceNotAvailableException {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(
                        PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE)
                        .setTestClassName(
                                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS)
                        .setTestMethodName(
                                "getInstance_acrossUsers_withoutInteractAcrossUsersFull_"
                                        + "throwsSecurityException")
                        .addInstrumentationArg(TARGET_USER_ID_KEY, String.valueOf(secondaryUser));

        runDeviceTests(options);
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withoutMediaRoutingControl_throwsSecurityException()
            throws DeviceNotAvailableException, FileNotFoundException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "getInstance_withoutMediaRoutingControl_throwsSecurityException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_acrossUsers_withFakePackageName_throwsIAE()
            throws DeviceNotAvailableException {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(
                        PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE)
                        .setTestClassName(
                                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS)
                        .setTestMethodName("getInstance_acrossUsers_withFakePackageName_throwsIAE")
                        .addInstrumentationArg(TARGET_USER_ID_KEY, String.valueOf(secondaryUser));

        runDeviceTests(options);
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withinUser_withoutMediaRoutingControl_throwsSecurityException()
            throws DeviceNotAvailableException, FileNotFoundException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "getInstance_withinUser_withoutMediaRoutingControl_throwsSecurityException");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withinUser_withMediaRoutingControl_flagEnabled_returnsInstance()
            throws DeviceNotAvailableException, FileNotFoundException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS,
                "getInstance_withinUser_withMediaRoutingControl_flagEnabled_returnsInstance");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getInstance_withinUser_returnsInstance() throws DeviceNotAvailableException {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(
                        PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE)
                        .setTestClassName(
                                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS)
                        .setTestMethodName("getInstance_withinUser_returnsInstance")
                        .addInstrumentationArg(TARGET_USER_ID_KEY, String.valueOf(secondaryUser));

        runDeviceTests(options);
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getAllRoutes_returnsAtLeastOneSystemRoute() throws DeviceNotAvailableException {
        runDeviceTests(
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE,
                PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS,
                "getAllRoutes_returnsAtLeastOneSystemRoute");
    }

    private static void installTestAppAsUser(
            TestInformation testInformation, String apkName, int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        LogUtil.CLog.d("Installing app " + apkName);
        CompatibilityBuildHelper buildHelper =
                new CompatibilityBuildHelper(testInformation.getBuildInfo());
        final String result =
                testInformation
                        .getDevice()
                        .installPackageForUser(
                                buildHelper.getTestFile(apkName),
                                /* reinstall= */ true,
                                /* grantPermissions= */ true,
                                userId,
                                /*allow test apps*/ "-t");
        assertWithMessage("Failed to install " + apkName + ": " + result).that(result).isNull();
    }
}

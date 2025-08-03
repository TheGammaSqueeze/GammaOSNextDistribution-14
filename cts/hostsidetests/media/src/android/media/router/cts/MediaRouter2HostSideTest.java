/*
 * Copyright 2022 The Android Open Source Project
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

import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_CLASS;
import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.ApiTest;
import com.android.media.flags.Flags;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import com.google.common.truth.Expect;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/** Installs route provider apps and runs tests in {@link MediaRouter2DeviceTest}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MediaRouter2HostSideTest extends BaseHostJUnit4Test {

    /** The maximum period of time to wait for a scan request to take effect, in milliseconds. */
    private static final long WAIT_MS_SCAN_PROPAGATION = 3000;

    @ClassRule public static final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @BeforeClassWithInfo
    public static void installApps(TestInformation testInfo)
            throws DeviceNotAvailableException, FileNotFoundException {
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_1_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_2_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_3_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_APK);
        installTestApp(testInfo, MEDIA_ROUTER_TEST_APK);
        installTestApp(testInfo, MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_APK);
    }

    @AfterClassWithInfo
    public static void uninstallApps(TestInformation testInfo) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_1_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_2_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_3_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_TEST_PACKAGE)).isNull();
        expect.that(device.uninstallPackage(MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE))
                .isNull();
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getSystemController_withModifyAudioRouting_returnsDeviceRoute() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE,
                DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING,
                "getSystemController_withModifyAudioRouting_returnsDeviceRoute");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOffScreenScan_triggersScanning() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOffScreenScan_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOnScreenScan_triggersScanning() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOnScreenScan_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOnScreenScan_withScreenOff_doesNotScan() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOnScreenScan_withScreenOff_doesNotScan");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getRoutes_withModifyAudioRouting_returnsDeviceRoute() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE,
                DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING,
                "getRoutes_withModifyAudioRouting_returnsDeviceRoute");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testDeduplicationIds_propagateAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "deduplicationIds_propagateAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testDeviceType_propagatesAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "deviceType_propagatesAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testSetRouteListingPreference_propagatesToManager() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "setRouteListingPreference_propagatesToManager");
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void testSetInstance_findsExternalPackage() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getInstance_findsExternalPackage");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testVisibilityAndAllowedPackages_propagateAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "visibilityAndAllowedPackages_propagateAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void setRouteListingPreference_withCustomDisableReason_propagatesCorrectly()
            throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "setRouteListingPreference_withCustomDisableReason_propagatesCorrectly");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void newRouteListingPreference_withInvalidCustomSubtext_throws() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "newRouteListingPreference_withInvalidCustomSubtext_throws");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void getRoutes_returnsExpectedSystemRoutes_dependingOnPermissions() throws Exception {
        // Bluetooth permissions must be manually granted.
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ true);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ true);
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getRoutes_withBTPermissions_returnsDeviceRoute");
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getSystemController_withBTPermissions_returnsDeviceRoute");
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ false);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ false);
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getRoutes_withoutBTPermissions_returnsDefaultRoute");
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void getSystemController_withBTPermissions_returnsDeviceRoute() throws Exception {
        // Bluetooth permissions must be manually granted.
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ true);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ true);
        try {
            runDeviceTests(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    DEVICE_SIDE_TEST_CLASS,
                    "getSystemController_withBTPermissions_returnsDeviceRoute");
        } finally {
            setPermissionEnabled(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    "android.permission.BLUETOOTH_SCAN",
                    /* enabled= */ false);
            setPermissionEnabled(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    "android.permission.BLUETOOTH_CONNECT",
                    /* enabled= */ false);
        }
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void getSystemController_withoutBTPermissions_returnsDefaultRoute() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getSystemController_withoutBTPermissions_returnsDefaultRoute");
    }

    @ApiTest(apis = {"android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void selfScanOnlyProvider_notScannedByAnotherApp() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "selfScanOnlyProvider_notScannedByAnotherApp");
    }

    @ApiTest(apis = {"android.media.MediaRouter2ProviderService#onBind"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_KEEP_ALIVE_ROUTE_PROVIDERS)
    @Test
    public void providerService_doesNotAutoBindAfterCrashing() throws Throwable {
        String startActivityCommand =
                "am start %s/.ScanningActivity".formatted(MEDIA_ROUTER_TEST_PACKAGE);
        getDevice().executeShellCommand(startActivityCommand);

        boolean providerStarted =
                waitForPackageRunningStatus(
                        MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ true);
        assertWithMessage("Provider did not start after starting the scanning activity.")
                .that(providerStarted)
                .isTrue();

        getDevice().executeShellCommand("am force-stop " + MEDIA_ROUTER_PROVIDER_1_PACKAGE);

        boolean providerStopped =
                waitForPackageRunningStatus(
                        MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ false);
        assertWithMessage("Provider did not stop after force-stopping it.")
                .that(providerStopped)
                .isTrue();

        boolean providerRestarted =
                waitForPackageRunningStatus(
                        MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ true);
        assertWithMessage("Provider restarted after force-stopping it.")
                .that(providerRestarted)
                .isFalse();
    }

    @ApiTest(apis = {"android.media.MediaRouter2ProviderService#onBind"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_KEEP_ALIVE_ROUTE_PROVIDERS)
    @Test
    public void packageManagerSpammingProviderService_doesnNotAutoBindAfterCrashing()
            throws Throwable {
        // Note that this apk is not installed with the other apks in this test, and this should not
        // change. This apk messes up with the proxy watcher by spamming PACKAGE_CHANGED events, so
        // we avoid having it installed while running other tests.
        installTestApp(getTestInformation(), MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_APK);

        try {
            String startActivityCommand =
                    "am start %s/.ScanningActivity".formatted(MEDIA_ROUTER_TEST_PACKAGE);
            getDevice().executeShellCommand(startActivityCommand);

            boolean providerStarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider did not start after starting the scanning activity.")
                    .that(providerStarted)
                    .isTrue();

            getDevice()
                    .executeShellCommand(
                            "am force-stop "
                                    + MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE);

            boolean providerStopped =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ false);
            assertWithMessage("Provider did not stop after force-stopping it.")
                    .that(providerStopped)
                    .isTrue();

            boolean providerRestarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider restarted after force-stopping it.")
                    .that(providerRestarted)
                    .isFalse();
        } finally {
            uninstallPackage(MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE);
        }
    }

    private void setPermissionEnabled(String packageName, String permission, boolean enabled)
            throws DeviceNotAvailableException {
        String action = enabled ? "grant" : "revoke";
        String result =
                getDevice()
                        .executeShellCommand(
                                "pm %s %s %s".formatted(action, packageName, permission));
        if (!result.isEmpty()) {
            assertWithMessage("Setting permission %s failed: %s".formatted(permission, result))
                    .fail();
        }
    }

    /**
     * Blocks execution until the package with the given name has the given running status.
     *
     * @param packageName The name of the package to check the running status for.
     * @param isPackageExpectedToRun True if the expected running status is "running", and false if
     *     the expected running status is "not running".
     */
    private boolean waitForPackageRunningStatus(String packageName, boolean isPackageExpectedToRun)
            throws Throwable {
        long start = System.currentTimeMillis();
        while (isPackageRunning(packageName) != isPackageExpectedToRun) {
            if (System.currentTimeMillis() - start > WAIT_MS_SCAN_PROPAGATION) {
                return false;
            }
            Thread.sleep(/* millis= */ 200); // Wait a bit before we call adb again.
        }
        return true;
    }

    private boolean isPackageRunning(String packageName) throws DeviceNotAvailableException {
        return !getDevice().executeShellCommand("pidof " + packageName).isEmpty();
    }

    private static void installTestApp(TestInformation testInfo, String apkName)
            throws FileNotFoundException, DeviceNotAvailableException {
        LogUtil.CLog.d("Installing app " + apkName);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(
                testInfo.getBuildInfo());
        final String result = testInfo.getDevice().installPackage(
                buildHelper.getTestFile(apkName), /*reinstall=*/true, /*grantPermissions=*/true,
                /*allow test apps*/"-t");
        assertWithMessage("Failed to install " + apkName + ": " + result).that(result).isNull();
    }
}

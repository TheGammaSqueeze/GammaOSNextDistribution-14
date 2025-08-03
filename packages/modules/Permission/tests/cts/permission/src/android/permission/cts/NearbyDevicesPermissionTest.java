/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.revokePermission;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.cts.EnableBluetoothRule;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.EnableLocationRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the behavior of the
 * {@link android.Manifest.permission_group#NEARBY_DEVICES} permission group
 * under various permutations of grant states.
 *
 * Note that some tests will be recognized as known failures with the new permission subsystem
 * until b/273999500 is fixed.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
public class NearbyDevicesPermissionTest {
    private static final String TEST_APP_PKG = "android.permission.cts.appthatrequestpermission";
    private static final String TEST_APP_AUTHORITY = "appthatrequestpermission";
    private static final String DISAVOWAL_APP_PKG = "android.permission.cts.appneverforlocation";

    private static final String TMP_DIR = "/data/local/tmp/cts-permission/";
    private static final String APK_BLUETOOTH_30 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermission30.apk";
    private static final String APK_BLUETOOTH_31 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermission31.apk";
    private static final String APK_BLUETOOTH_NEVER_FOR_LOCATION_31 = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermissionNeverForLocation31.apk";
    private static final String APK_BLUETOOTH_NEVER_FOR_LOCATION_NO_PROVIDER = TMP_DIR
            + "CtsAppThatRequestsBluetoothPermissionNeverForLocationNoProvider.apk";

    private enum Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @ClassRule
    public static final EnableBluetoothRule sEnableBluetoothRule = new EnableBluetoothRule(true);

    @Rule
    public final EnableLocationRule enableLocationRule = new EnableLocationRule();

    public void uninstallTestApp() {
        uninstallApp(TEST_APP_PKG);
        uninstallApp(DISAVOWAL_APP_PKG);
    }

    @Before
    public void setUp() throws Exception {
        uninstallTestApp();
    }

    @After
    public void tearDown() throws Exception {
        uninstallTestApp();
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission30_Default() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_30);
        assertScanBluetoothResult(Result.EMPTY);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission30_GrantLocation() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_30);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission31_Default() throws Throwable {
        install(APK_BLUETOOTH_31);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission31_GrantNearby() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.EMPTY);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission31_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermission31_GrantNearby_GrantLocation() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermissionNeverForLocation31_Default() throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermissionNeverForLocation31_GrantNearby() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.FILTERED);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermissionNeverForLocation31_GrantLocation() throws Throwable {
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    @Test
    @CddTest(requirement = "7.4.3/C-6-1")
    public void testRequestBluetoothPermissionNeverForLocation31_GrantNearby_GrantLocation()
            throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FILTERED);
    }

    @Test
    public void testRequestBluetoothPermission31_OnBehalfOfDisavowingApp() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_31);
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_NO_PROVIDER);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(DISAVOWAL_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(DISAVOWAL_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult("PROXY", Result.FILTERED);
    }

    /**
     * Verify that a legacy app that was unable to interact with Bluetooth
     * devices is still unable to interact with them after updating to a modern
     * SDK; they'd always need to involve the user to gain permissions.
     */
    @Test
    public void testRequestBluetoothPermission_Default_Upgrade() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_30);
        assertScanBluetoothResult(Result.EMPTY);

        // Upgrading to target a new SDK level means they need to explicitly
        // request the new runtime permission; by default it's denied
        install(APK_BLUETOOTH_NEVER_FOR_LOCATION_31);
        assertScanBluetoothResult(Result.EXCEPTION);

        // If the user does grant it, they can scan again
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.FILTERED);
    }

    /**
     * Verify that a legacy app that was able to interact with Bluetooth devices
     * is still able to interact with them after updating to a modern SDK.
     */
    @Test
    public void testRequestBluetoothPermission_GrantLocation_Upgrade() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_30);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);

        // Upgrading to target a new SDK level means they still have the access
        // they enjoyed as a legacy app
        install(APK_BLUETOOTH_31);
        assertScanBluetoothResult(Result.FULL);
    }

    /**
     * Verify that downgrading an app doesn't gain them any access to Bluetooth
     * scan results; they'd always need to involve the user to gain permissions.
     */
    @Test
    public void testRequestBluetoothPermission_Downgrade() throws Throwable {
        assumeTrue(supportsBluetoothLe());

        install(APK_BLUETOOTH_31);
        grantPermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        grantPermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        grantPermission(TEST_APP_PKG, ACCESS_FINE_LOCATION);
        grantPermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION);
        assertScanBluetoothResult(Result.FULL);

        // Revoking nearby permission means modern app can't scan
        revokePermission(TEST_APP_PKG, BLUETOOTH_CONNECT);
        revokePermission(TEST_APP_PKG, BLUETOOTH_SCAN);
        assertScanBluetoothResult(Result.EXCEPTION);

        // And if they attempt to downgrade, confirm that they can't obtain the
        // split-permission grant from the older non-runtime permissions
        install(APK_BLUETOOTH_30);
        assertScanBluetoothResult(Result.EXCEPTION);
    }

    private void assertScanBluetoothResult(Result expected) {
        assertScanBluetoothResult(null, expected);
    }

    private void assertScanBluetoothResult(String arg, Result expected) {
        SystemClock.sleep(1000); // Wait for location permissions to propagate
        final ContentResolver resolver = InstrumentationRegistry.getTargetContext()
                .getContentResolver();
        final Bundle res = resolver.call(TEST_APP_AUTHORITY, "", arg, null);
        Result actual = Result.values()[res.getInt(Intent.EXTRA_INDEX)];
        assertEquals(expected, actual);
    }

    private boolean supportsBluetoothLe() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

}

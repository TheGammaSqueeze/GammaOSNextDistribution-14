/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.NEARBY_WIFI_DEVICES;
import static android.Manifest.permission.RENOUNCE_PERMISSIONS;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.LocationManager;
import android.net.NetworkStack;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionManager;
import android.provider.Settings;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.BinderUtil;
import com.android.server.wifi.FakeWifiLog;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiInjector;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/** Unit tests for {@link WifiPermissionsUtil}. */
@RunWith(JUnit4.class)
@SmallTest
public class WifiPermissionsUtilTest extends WifiBaseTest {
    public static final String TAG = "WifiPermissionsUtilTest";

    // Mock objects for testing
    @Mock private WifiPermissionsWrapper mMockPermissionsWrapper;
    @Mock private Context mMockContext;
    @Mock private FrameworkFacade mMockFrameworkFacade;
    @Mock private PackageManager mMockPkgMgr;
    @Mock private ApplicationInfo mMockApplInfo;
    @Mock private PackageInfo mPackagePermissionInfo;
    @Mock private AppOpsManager mMockAppOps;
    @Mock private UserManager mMockUserManager;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private WifiInjector mWifiInjector;
    @Mock private LocationManager mLocationManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private PermissionManager mPermissionManager;
    @Mock private PackageManager mPackageManager;
    @Spy private FakeWifiLog mWifiLog;
    @Mock private Context mUserContext;

    private static final String TEST_WIFI_STACK_APK_NAME = "com.android.wifi";
    private static final String TEST_PACKAGE_NAME = "com.google.somePackage";
    private static final String TEST_FEATURE_ID = "com.google.someFeature";
    private static final String TEST_SSID = "\"GoogleGuest\"";
    private static final String INVALID_PACKAGE  = "BAD_PACKAGE";
    private static final int MANAGED_PROFILE_UID = 1100000;
    private static final int OTHER_USER_UID = 1200000;
    private static final int TEST_NETWORK_ID = 54;
    private static final boolean CHECK_LOCATION_SETTINGS = false;
    private static final boolean IGNORE_LOCATION_SETTINGS = true;
    private static final boolean DONT_HIDE_FROM_APP_OPS = false;
    private static final boolean HIDE_FROM_APP_OPS = true;
    private static final int TEST_CALLING_UID = 1000;

    private final String mMacAddressPermission = "android.permission.PEERS_MAC_ADDRESS";
    private final String mInteractAcrossUsersFullPermission =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private final String mManifestStringCoarse =
            Manifest.permission.ACCESS_COARSE_LOCATION;
    private final String mManifestStringFine =
            Manifest.permission.ACCESS_FINE_LOCATION;
    private final String mManifestStringHardware =
            Manifest.permission.LOCATION_HARDWARE;
    private final String mScanWithoutLocation =
            Manifest.permission.RADIO_SCAN_WITHOUT_LOCATION;

    // Test variables
    private int mWifiScanAllowApps;
    private int mUid;
    private int mCoarseLocationPermission;
    private int mAllowCoarseLocationApps;
    private int mFineLocationPermission;
    private int mAllowFineLocationApps;
    private int mHardwareLocationPermission;
    private int mCurrentUser;
    private boolean mIsLocationEnabled;
    private boolean mThrowSecurityException;
    private Answer<Integer> mReturnPermission;
    private HashMap<String, Integer> mPermissionsList = new HashMap<String, Integer>();

    /**
    * Set up Mockito tests
    */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        initTestVars();
    }

    private void setupTestCase() throws Exception {
        setupMocks();
        setupMockInterface();
    }

    /**
     * Verify we return true when the UID does have the override config permission
     */
    @Test
    public void testCheckConfigOverridePermissionApproved() throws Exception {
        mUid = MANAGED_PROFILE_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        when(mMockPermissionsWrapper.getOverrideWifiConfigPermission(anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(codeUnderTest.checkConfigOverridePermission(mUid));
    }

    /**
     * Verify we return false when the UID does not have the override config permission.
     */
    @Test
    public void testCheckConfigOverridePermissionDenied() throws Exception {
        mUid = OTHER_USER_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        when(mMockPermissionsWrapper.getOverrideWifiConfigPermission(anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(codeUnderTest.checkConfigOverridePermission(mUid));
    }

    /**
     * Test case setting: Package is valid
     *                    Location mode is enabled
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User is current
     * Validate no Exceptions are thrown
     * - User has all the permissions
     */
    @Test
    public void testCanReadPeersMacAddressCurrentUserAndAllPermissions() throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mCurrentUser = UserHandle.USER_SYSTEM;
        mIsLocationEnabled = true;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location mode is enabled
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User profile is current
     * Validate no Exceptions are thrown
     * - User has all the permissions
     */
    @Test
    public void testCanReadPeersMacAddressCurrentProfileAndAllPermissions() throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mIsLocationEnabled = true;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     * Validate that a SecurityException is thrown
     * - This App doesn't have permission to request Wifi Scan
     */
    @Test
    public void testCannotAccessScanResult_AppNotAllowed() throws Exception {
        mThrowSecurityException = false;
        mPermissionsList.put(mMacAddressPermission, mUid);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location mode is enabled
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User or profile is not current but the uid has
     *                    permission to INTERACT_ACROSS_USERS_FULL
     * Validate no Exceptions are thrown
     * - User has all the permissions
     */
    @Test
    public void testenforceCanAccessScanResults_UserOrProfileNotCurrent() throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = true;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User or profile is not Current
     * Validate that a SecurityException is thrown
     * - Calling uid doesn't have INTERACT_ACROSS_USERS_FULL permission
     */
    @Test
    public void testCannotAccessScanResults_NoInteractAcrossUsersFullPermission() throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case Setting: Package is valid
     *                    Foreground
     *                    This App has permission to request WIFI_SCAN
     *                    User is current
     *  Validate that a SecurityException is thrown - app does not have location permission
     */
    @Test
    public void testLegacyForegroundAppWithOtherPermissionsDenied() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        mCurrentUser = UserHandle.USER_SYSTEM;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case Setting: Package is valid
     *                    Location Mode Enabled
     *                    Coarse Location Access
     *                    This App has permission to request WIFI_SCAN
     *                    User profile is current
     *  Validate no Exceptions are thrown - has all permissions
     */
    @Test
    public void testLegacyAppHasLocationAndAllPermissions() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mIsLocationEnabled = true;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     * Validate that a SecurityException is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is enabled but the uid
     * - doesn't have Coarse Location Access
     * - which implies No Location Permission
     */
    @Test
    public void testCannotAccessScanResults_NoCoarseLocationPermission() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permission
     * Validate an Exception is thrown
     * - Uid is not an active network scorer
     * - Uid doesn't have Coarse Location Access
     * - which implies No Location Permission
     */
    @Test
    public void testCannotAccessScanResults_LocationModeDisabled() throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller has CHANGE_WIFI_STATE
     * Validate SecurityException is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCannotAccessScanResults_LocationModeDisabledHasChangeWifiState()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller has ACCESS_WIFI_STATE
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCannotAccessScanResults_LocationModeDisabledHasAccessWifiState()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller does not have NETWORK_SETTINGS
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCannotAccessScanResults_LocationModeDisabledHasNoNetworkSettings()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETTINGS, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller has NETWORK_SETTINGS
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCanAccessScanResults_LocationModeDisabledHasNetworkSettings()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETTINGS, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller does not have NETWORK_SETUP_WIZARD
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCannotAccessScanResults_LocationModeDisabledHasNoNetworkSetupWizard()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has no location permisson
     *                    Caller has NETWORK_SETUP_WIZARD
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCanAccessScanResults_LocationModeDisabledHasNetworkSetupWizard()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has no location permisson
     *                    Caller has NETWORK_MANAGED_PROVISIONING
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCanAccessScanResults_LocationModeDisabledHasNetworkManagedProvisioning()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_MANAGED_PROVISIONING, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has no location permisson
     *                    Caller has NETWORK_STACK
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCanAccessScanResults_LocationModeDisabledHasNetworkStack()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_STACK, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has no location permisson
     *                    Caller has MAINLINE_NETWORK_STACK
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testEnforceCanAccessScanResults_LocationModeDisabledHasMainlineNetworkStack()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, null);
    }

    /**
     * Test case setting: Invalid Package
     * Expect a securityException
     */
    @Test
    public void testInvalidPackage() throws Exception {
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResults(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid,
                    null);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: legacy caller does have Coarse Location permission.
     * A SecurityException should not be thrown.
     */
    @Test
    public void testEnforceCoarseLocationPermissionLegacyApp() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mIsLocationEnabled = true;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);

        // verify that checking FINE for legacy apps!
        verify(mMockAppOps).noteOpNoThrow(eq(AppOpsManager.OPSTR_FINE_LOCATION), anyInt(),
                anyString(), any(), any());
    }

    /**
     * Test case setting: legacy caller does have Coarse Location permission.
     * A SecurityException should not be thrown.
     */
    @Test
    public void testEnforceFineLocationPermissionNewQApp() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);
        verify(mMockAppOps)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_FINE_LOCATION), anyInt(), anyString(),
                        any(), any());
    }

    /**
     * Test case setting: legacy caller does have Coarse Location permission.
     * A SecurityException should not be thrown.
     */
    @Test
    public void testEnforceFailureFineLocationPermissionNewQApp() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        mIsLocationEnabled = true;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mFineLocationPermission = PackageManager.PERMISSION_DENIED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mAllowFineLocationApps = AppOpsManager.MODE_ERRORED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);
            fail("Expected SecurityException not thrown");
        } catch (SecurityException e) {
            // empty
        }
        verify(mMockAppOps, never()).noteOpNoThrow(anyInt(), anyInt(), anyString());
    }

    /**
     * Verifies the helper method exposed for checking NETWORK_SETUP_WIZARD permission.
     */
    @Test
    public void testCheckNetworkSetupWizard() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(wifiPermissionsUtil.checkNetworkSetupWizardPermission(MANAGED_PROFILE_UID));

        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(wifiPermissionsUtil.checkNetworkSetupWizardPermission(MANAGED_PROFILE_UID));
    }

    /**
     * Verifies the helper method exposed for checking NETWORK_MANAGED_PROVISIONING permission.
     */
    @Test
    public void testCheckNetworkManagedProvisioning() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_MANAGED_PROVISIONING, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(wifiPermissionsUtil.checkNetworkManagedProvisioningPermission(
                MANAGED_PROFILE_UID));

        when(mMockPermissionsWrapper.getUidPermission(
                android.Manifest.permission.NETWORK_MANAGED_PROVISIONING, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(wifiPermissionsUtil.checkNetworkManagedProvisioningPermission(
                MANAGED_PROFILE_UID));
    }

    /**
     * Verifies the helper method exposed for checking SYSTERM_ALERT_WINDOW permission.
     */
    @Test
    public void testCheckSystemAlertWindowPermissionWithModeDefaultAppOps() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockAppOps.noteOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, MANAGED_PROFILE_UID,
                TEST_PACKAGE_NAME, null, null))
                .thenReturn(AppOpsManager.MODE_DEFAULT);
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.SYSTEM_ALERT_WINDOW, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(wifiPermissionsUtil.checkSystemAlertWindowPermission(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        when(mMockAppOps.noteOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, MANAGED_PROFILE_UID,
                TEST_PACKAGE_NAME, null, null))
                .thenReturn(AppOpsManager.MODE_DEFAULT);
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.SYSTEM_ALERT_WINDOW, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(wifiPermissionsUtil.checkSystemAlertWindowPermission(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));
    }

    /**
     * Verifies the helper method exposed for checking SYSTERM_ALERT_WINDOW permission.
     */
    @Test
    public void testCheckSystemAlertWindowPermissionWithModeAllowedAppOps() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockAppOps.noteOpNoThrow(
                AppOpsManager.OP_SYSTEM_ALERT_WINDOW, MANAGED_PROFILE_UID, TEST_PACKAGE_NAME))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(wifiPermissionsUtil.checkSystemAlertWindowPermission(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));
    }

    /**
     * Verifies the helper method exposed for checking if the app is a DeviceOwner.
     */
    @Test
    public void testIsDeviceOwnerByPackageName() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        assertTrue(wifiPermissionsUtil.isDeviceOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));


        // userId does not match
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(OTHER_USER_UID));
        assertFalse(wifiPermissionsUtil.isDeviceOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // Package Name does not match
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(INVALID_PACKAGE, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        assertFalse(wifiPermissionsUtil.isDeviceOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // No device owner.
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isDeviceOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // DevicePolicyManager does not exist.
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isDeviceOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));
    }

    /**
     * Verifies the helper method exposed for checking if UID is a DeviceOwner.
     */
    @Test
    public void testIsDeviceOwnerByUid() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);

        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        when(mPackageManager.getPackagesForUid(MANAGED_PROFILE_UID)).thenReturn(
                new String[] { TEST_PACKAGE_NAME });
        assertTrue(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));

        // userId does not match
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(OTHER_USER_UID));
        assertFalse(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));

        // uid does not match
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        when(mPackageManager.getPackagesForUid(MANAGED_PROFILE_UID)).thenReturn(
                new String[] { TEST_FEATURE_ID });
        assertFalse(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));

        // no packages for uid
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, new String()));
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        when(mPackageManager.getPackagesForUid(MANAGED_PROFILE_UID)).thenReturn(null);
        assertFalse(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));

        // No device owner.
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));

        // DevicePolicyManager does not exist.
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isDeviceOwner(MANAGED_PROFILE_UID));
    }

    /**
     * Verifies the helper method exposed for checking if the app is a ProfileOwner.
     */
    @Test
    public void testIsProfileOwnerApp() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockContext.createPackageContextAsUser(
                TEST_WIFI_STACK_APK_NAME, 0, UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID)))
                .thenReturn(mMockContext);
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        when(mDevicePolicyManager.isProfileOwnerApp(TEST_PACKAGE_NAME))
                .thenReturn(true);
        assertTrue(wifiPermissionsUtil.isProfileOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        when(mDevicePolicyManager.isProfileOwnerApp(TEST_PACKAGE_NAME))
                .thenReturn(false);
        assertFalse(wifiPermissionsUtil.isProfileOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // DevicePolicyManager does not exist.
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isProfileOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // Invalid package name.
        doThrow(new PackageManager.NameNotFoundException())
                .when(mMockContext).createPackageContextAsUser(
                        TEST_WIFI_STACK_APK_NAME, 0,
                UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        assertFalse(wifiPermissionsUtil.isProfileOwner(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));
    }

    @Test
    public void testIsLegacyDeviceAdmin() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockContext.createPackageContextAsUser(
                TEST_WIFI_STACK_APK_NAME, 0, UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID)))
                .thenReturn(mMockContext);
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        when(mDevicePolicyManager.packageHasActiveAdmins(TEST_PACKAGE_NAME))
                .thenReturn(true);
        assertTrue(wifiPermissionsUtil.isLegacyDeviceAdmin(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        when(mDevicePolicyManager.packageHasActiveAdmins(TEST_PACKAGE_NAME))
                .thenReturn(false);
        assertFalse(wifiPermissionsUtil.isLegacyDeviceAdmin(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // DevicePolicyManager does not exist.
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isLegacyDeviceAdmin(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));

        // Invalid package name.
        doThrow(new PackageManager.NameNotFoundException())
                .when(mMockContext).createPackageContextAsUser(
                        TEST_WIFI_STACK_APK_NAME, 0,
                        UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID));
        assertFalse(wifiPermissionsUtil.isLegacyDeviceAdmin(
                MANAGED_PROFILE_UID, TEST_PACKAGE_NAME));
    }

    /**
     * Verifies the helper method exposed for checking if the calling uid is a ProfileOwner
     * of an organization owned device.
     */
    @Test
    public void testIsProfileOwnerOfOrganizationOwnedDevice() throws Exception {
        setupMocks();
        WifiPermissionsUtil wifiPermissionsUtil = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        wifiPermissionsUtil.enableVerboseLogging(true);

        when(mMockContext.createPackageContextAsUser(
                TEST_WIFI_STACK_APK_NAME, 0, UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID)))
                .thenReturn(mMockContext);
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile())
                .thenReturn(true);
        String[] packageNames = {TEST_PACKAGE_NAME};
        when(mMockContext.getPackageManager().getPackagesForUid(MANAGED_PROFILE_UID))
                .thenReturn(packageNames);

        when(mDevicePolicyManager.isProfileOwnerApp(TEST_PACKAGE_NAME)).thenReturn(true);
        assertTrue(wifiPermissionsUtil.isProfileOwnerOfOrganizationOwnedDevice(
                MANAGED_PROFILE_UID));

        when(mDevicePolicyManager.isProfileOwnerApp(TEST_PACKAGE_NAME)).thenReturn(false);
        assertFalse(wifiPermissionsUtil.isProfileOwnerOfOrganizationOwnedDevice(
                MANAGED_PROFILE_UID));
        when(mDevicePolicyManager.isProfileOwnerApp(TEST_PACKAGE_NAME)).thenReturn(true);

        // Package does not exist for uid.
        when(mMockContext.getPackageManager().getPackagesForUid(MANAGED_PROFILE_UID))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isProfileOwnerOfOrganizationOwnedDevice(
                MANAGED_PROFILE_UID));
        when(mMockContext.getPackageManager().getPackagesForUid(MANAGED_PROFILE_UID))
                .thenReturn(packageNames);

        // DevicePolicyManager does not exist.
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(null);
        assertFalse(wifiPermissionsUtil.isProfileOwnerOfOrganizationOwnedDevice(
                MANAGED_PROFILE_UID));
    }

    /**
     * Test case setting: caller does not have Location permission.
     * Expect a SecurityException
     */
    @Test(expected = SecurityException.class)
    public void testEnforceLocationPermissionExpectSecurityException() throws Exception {
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Fine Location Access
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     *  Validate no Exceptions are thrown - has all permissions
     */
    @Test
    public void testCanAccessScanResultsForWifiScanner() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);

        verify(mMockAppOps, never())
                .unsafeCheckOp(AppOpsManager.OPSTR_FINE_LOCATION, mUid, TEST_PACKAGE_NAME);
        verify(mMockAppOps).noteOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, mUid,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, null);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller does not have NETWORK_SETTINGS or NETWORK_SETUP_WIZARD
     * Validate Exception is thrown
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is disabled
     * - doesn't have Coarse Location Access
     * - which implies no scan result access
     */
    @Test
    public void testAccessScanResultsForWifiScanner_LocationModeDisabledHasNoNetworkSettingsOrSuw()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETTINGS, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(
                    TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, false, false);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller has NETWORK_SETTINGS
     * Validate no exception thrown.
     */
    @Test
    public void testAccessScanResultsForWifiScanner_LocationModeDisabledHasNetworkSettings()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETTINGS, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResultsForWifiScanner(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, false, false);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Caller has location permisson
     *                    Caller has NETWORK_SETUP_WIZARD
     * Validate no exception thrown.
     */
    @Test
    public void testAccessScanResultsForWifiScanner_LocationModeDisabledHasNetworkSuw()
            throws Exception {
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mIsLocationEnabled = false;

        setupTestCase();
        when(mMockPermissionsWrapper.getUidPermission(
                Manifest.permission.NETWORK_SETUP_WIZARD, MANAGED_PROFILE_UID))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResultsForWifiScanner(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid, false, false);    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Fine Location Access
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate no Exceptions are thrown - has all permissions & don't note in app-ops.
     */
    @Test
    public void testCanAccessScanResultsForWifiScanner_HideFromAppOps()
            throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                mUid, IGNORE_LOCATION_SETTINGS, HIDE_FROM_APP_OPS);

        verify(mMockAppOps).unsafeCheckOp(AppOpsManager.OPSTR_FINE_LOCATION, mUid,
                TEST_PACKAGE_NAME);
        verify(mMockAppOps, never()).noteOpNoThrow(
                AppOpsManager.OPSTR_FINE_LOCATION, mUid, TEST_PACKAGE_NAME, null, null);
    }


    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate that a SecurityException is thrown
     * - Doesn't have fine location permission.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_NoFineLocationPermission()
            throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_DENIED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Fine Location Access
     *                    This App has permission to request WIFI_SCAN
     * Validate that a SecurityException is thrown
     * - Doesn't have fine location app-ops.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_NoFineLocationAppOps() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_IGNORED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate that a SecurityException is thrown
     * - Doesn't have hardware location permission.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_NoHardwareLocationPermission()
            throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_DENIED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     *                    Fine Location Access
     *                    Location hardware Access
     * Validate that a SecurityException is thrown
     * - This App does not have permission to request WIFI_SCAN.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_NoWifiScanAppOps() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_IGNORED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Fine Location Access
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate an Exception is thrown
     * - Location is not enabled.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_LocationModeDisabled() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = false;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Disabled
     *                    Fine Location Access
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate no Exceptions are thrown - has all permissions & ignores location settings.
     */
    @Test
    public void testCanAccessScanResultsForWifiScanner_IgnoreLocationSettings()
            throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = false;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_IGNORED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                mUid, IGNORE_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
        verify(mMockAppOps).noteOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, mUid,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, null);
    }

    /**
     * Test case setting: Location Mode Enabled
     *                    Fine Location Access
     *                    Location hardware Access
     *                    This App has permission to request WIFI_SCAN
     * Validate an Exception is thrown
     * - Invalid package name.
     */
    @Test
    public void testCannotAccessScanResultsForWifiScanner_InvalidPackage() throws Exception {
        mThrowSecurityException = true;
        mIsLocationEnabled = true;
        mFineLocationPermission = PackageManager.PERMISSION_GRANTED;
        mHardwareLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowFineLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        try {
            codeUnderTest.enforceCanAccessScanResultsForWifiScanner(TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID, mUid, CHECK_LOCATION_SETTINGS, DONT_HIDE_FROM_APP_OPS);
            fail("Expected SecurityException is not thrown");
        } catch (SecurityException e) {
        }
    }

    /**
     * Verify that we handle failures when trying to fetch location mode using LocationManager API.
     * We should use the legacy setting to read the value if we encounter any failure.
     */
    @Test
    public void testIsLocationEnabledFallbackToLegacySetting() throws Exception {
        mUid = OTHER_USER_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        doThrow(new RuntimeException()).when(mLocationManager).isLocationEnabledForUser(any());

        when(mMockFrameworkFacade.getIntegerSetting(
                any(Context.class), eq(Settings.Secure.LOCATION_MODE), anyInt()))
                .thenReturn(Settings.Secure.LOCATION_MODE_OFF);
        assertFalse(codeUnderTest.isLocationModeEnabled());

        when(mMockFrameworkFacade.getIntegerSetting(
                any(Context.class), eq(Settings.Secure.LOCATION_MODE), anyInt()))
                .thenReturn(Settings.Secure.LOCATION_MODE_ON);
        assertTrue(codeUnderTest.isLocationModeEnabled());

        verify(mMockFrameworkFacade, times(2)).getIntegerSetting(
                any(Context.class), eq(Settings.Secure.LOCATION_MODE), anyInt());
    }

    @Test(expected = SecurityException.class)
    public void testEnforceNearbyDevicesPermission_InvalidAttributionSourceFail() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(false);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, false, "");
    }

    @Test(expected = SecurityException.class)
    public void testEnforceNearbyDevicesPermission_NearbyDevicesNotGrantedFail() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(mPermissionManager.checkPermissionForDataDelivery(eq(NEARBY_WIFI_DEVICES),
                eq(attributionSource), any())).thenReturn(PermissionManager.PERMISSION_SOFT_DENIED);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, false, "");
    }

    /**
     * Verify that when checkForLocation = true, a security Exception will get thrown if the calling
     * app has no location permission and doesn't disavow location.
     * @throws Exception
     */
    @Test(expected = SecurityException.class)
    public void testEnforceNearbyDevicesPermission_LocationCheckFail() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.getRenouncedPermissions()).thenReturn(Collections.EMPTY_SET);
        mPackagePermissionInfo.requestedPermissions = new String[0];
        mPackagePermissionInfo.requestedPermissionsFlags = new int[0];
        when(mPermissionManager.checkPermissionForDataDelivery(eq(NEARBY_WIFI_DEVICES),
                eq(attributionSource), any())).thenReturn(PermissionManager.PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(attributionSource), any()))
                .thenReturn(PermissionManager.PERMISSION_SOFT_DENIED);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, true, "");
    }

    @Test
    public void testEnforceNearbyDevicesPermission_RenounceLocationBypassLocationCheck()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // disable location mode
        mIsLocationEnabled = false;

        int uid1 = 1000;
        int uid2 = 1001;
        // Only allow uid2 to renounce permissions
        mPermissionsList.put(RENOUNCE_PERMISSIONS, uid2);

        // mock uid1 renouncing location and expect the call to fail due to location mode being
        // disabled since uid1 does not have permissions to renounce permissions.
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.getUid()).thenReturn(uid1);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.getRenouncedPermissions()).thenReturn(Set.of(ACCESS_FINE_LOCATION));

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertFalse(codeUnderTest.checkNearbyDevicesPermission(attributionSource, true, ""));
        verify(mMockAppOps).checkPackage(uid1, TEST_PACKAGE_NAME);

        // now attach AttributionSource2 with uid2 to the list of AttributionSource and then
        // verify the location check is now bypassed.
        AttributionSource attributionSource2 = mock(AttributionSource.class);
        when(attributionSource2.getUid()).thenReturn(uid2);
        when(attributionSource2.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource2.getRenouncedPermissions()).thenReturn(Set.of(ACCESS_FINE_LOCATION));
        when(attributionSource.getNext()).thenReturn(attributionSource2);
        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, true, "");
    }

    /**
     * Verify that when checkForLocation = true, the calling app can disavow location to bypass
     * the location check.
     * @throws Exception
     */
    @Test
    public void testEnforceNearbyDevicesPermission_LocationCheckDisavowPass() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // bypass checkPackage
        mThrowSecurityException = false;
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.getUid()).thenReturn(mUid);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.getRenouncedPermissions()).thenReturn(Collections.EMPTY_SET);
        // mock caller disavowing location
        mPackagePermissionInfo.requestedPermissions = new String[] {NEARBY_WIFI_DEVICES};
        mPackagePermissionInfo.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION};
        when(mPermissionManager.checkPermissionForDataDelivery(eq(NEARBY_WIFI_DEVICES),
                eq(attributionSource), any())).thenReturn(PermissionManager.PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(attributionSource), any()))
                .thenReturn(PermissionManager.PERMISSION_SOFT_DENIED);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, true, "");
        verify(mMockAppOps).checkPackage(mUid, TEST_PACKAGE_NAME);

        // It's important to verify that ACCESS_FINE_LOCATION never gets checked so the caller
        // does not get blamed for location access when they already disavowed location.
        verify(mPermissionManager, never()).checkPermissionForDataDelivery(
                eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any());
    }

    /**
     * Verify that when checkForLocation = true, and the calling app does not disavow location,
     * location permission will get checked.
     * @throws Exception
     */
    @Test
    public void testEnforceNearbyDevicesPermission_LocationCheckWithoutDisavowPass()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // bypass checkPackage
        mThrowSecurityException = false;
        // Set location mode off and grant app location permission
        when(mLocationManager.isLocationEnabledForUser(any())).thenReturn(false);
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.getUid()).thenReturn(mUid);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.getRenouncedPermissions()).thenReturn(Collections.EMPTY_SET);
        mPackagePermissionInfo.requestedPermissions = new String[0];
        when(mPermissionManager.checkPermissionForDataDelivery(eq(NEARBY_WIFI_DEVICES),
                eq(attributionSource), any())).thenReturn(PermissionManager.PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                eq(Manifest.permission.ACCESS_FINE_LOCATION), eq(attributionSource), any()))
                .thenReturn(PermissionManager.PERMISSION_GRANTED);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        // Test should fail because location mode is off
        assertFalse(codeUnderTest.checkNearbyDevicesPermission(attributionSource, true, ""));
        verify(mMockAppOps).checkPackage(mUid, TEST_PACKAGE_NAME);

        // Now enable location mode and the call should pass
        when(mLocationManager.isLocationEnabledForUser(any())).thenReturn(true);
        assertTrue(codeUnderTest.checkNearbyDevicesPermission(attributionSource, true, ""));

        // verify that location check is performed since the caller did not disavow location.
        verify(mPermissionManager).checkPermissionForDataDelivery(
                eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any());
    }

    /**
     * Verify that the nearby device permission check can be bypassed by very privileged apps.
     */
    @Test
    public void testEnforceNearbyDevicesPermission_BypassCheckWithPrivilegedPermission()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        // bypass checkPackage
        mThrowSecurityException = false;
        AttributionSource attributionSource = mock(AttributionSource.class);
        when(attributionSource.getUid()).thenReturn(mUid);
        when(attributionSource.checkCallingUid()).thenReturn(true);
        when(attributionSource.getPackageName()).thenReturn(TEST_PACKAGE_NAME);
        when(attributionSource.getUid()).thenReturn(mUid);
        // caller no nearby permission
        when(mPermissionManager.checkPermissionForDataDelivery(eq(NEARBY_WIFI_DEVICES),
                eq(attributionSource), any())).thenReturn(PermissionManager.PERMISSION_SOFT_DENIED);

        // but caller has another permission that allows bypassing nearby permission check.
        mPermissionsList.put(mScanWithoutLocation, mUid);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceNearbyDevicesPermission(attributionSource, true, "");
        verify(mMockAppOps).checkPackage(mUid, TEST_PACKAGE_NAME);
    }

    private Answer<Integer> createPermissionAnswer() {
        return new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) {
                int myUid = (int) invocation.getArguments()[1];
                String myPermission = (String) invocation.getArguments()[0];
                if (mPermissionsList.containsKey(myPermission)) {
                    int uid = mPermissionsList.get(myPermission);
                    if (myUid == uid) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
                return PackageManager.PERMISSION_DENIED;
            }
        };
    }

    private void setupMocks() throws Exception {
        when(mMockPkgMgr.getApplicationInfoAsUser(eq(TEST_PACKAGE_NAME), eq(0), any()))
            .thenReturn(mMockApplInfo);
        when(mMockContext.createPackageContextAsUser(any(), anyInt(), any()))
                .thenReturn(mMockContext);
        if (SdkLevel.isAtLeastS()) {
            when(mMockPkgMgr.getTargetSdkVersion(TEST_PACKAGE_NAME))
                    .thenReturn(mMockApplInfo.targetSdkVersion);
        }
        when(mMockPkgMgr.getApplicationInfoAsUser(eq(TEST_PACKAGE_NAME), eq(0), any()))
                .thenReturn(mMockApplInfo);
        when(mMockPkgMgr.getPackageInfo((String) any(),
                eq(GET_PERMISSIONS | MATCH_UNINSTALLED_PACKAGES))).thenReturn(
                        mPackagePermissionInfo);
        when(mMockContext.getPackageManager()).thenReturn(mMockPkgMgr);
        when(mMockAppOps.noteOpNoThrow(AppOpsManager.OPSTR_WIFI_SCAN, mUid, TEST_PACKAGE_NAME,
                TEST_FEATURE_ID, null)).thenReturn(mWifiScanAllowApps);
        when(mMockAppOps.noteOpNoThrow(eq(AppOpsManager.OPSTR_COARSE_LOCATION), eq(mUid),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), nullable(String.class)))
                .thenReturn(mAllowCoarseLocationApps);
        when(mMockAppOps.noteOpNoThrow(eq(AppOpsManager.OPSTR_FINE_LOCATION), eq(mUid),
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), nullable(String.class)))
                .thenReturn(mAllowFineLocationApps);
        when(mMockAppOps.unsafeCheckOp(AppOpsManager.OPSTR_FINE_LOCATION, mUid, TEST_PACKAGE_NAME))
                .thenReturn(mAllowFineLocationApps);
        if (mThrowSecurityException) {
            doThrow(new SecurityException("Package " + TEST_PACKAGE_NAME + " doesn't belong"
                    + " to application bound to user " + mUid))
                    .when(mMockAppOps).checkPackage(mUid, TEST_PACKAGE_NAME);
        }
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE))
            .thenReturn(mMockAppOps);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getSystemService(Context.USER_SERVICE))
            .thenReturn(mMockUserManager);
        when(mWifiInjector.makeLog(anyString())).thenReturn(mWifiLog);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mMockFrameworkFacade);
        when(mMockContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mLocationManager);
        when(mMockContext.getPackageName()).thenReturn(TEST_WIFI_STACK_APK_NAME);
        when(mMockContext.getSystemService(PermissionManager.class)).thenReturn(mPermissionManager);
    }

    private void initTestVars() {
        mPermissionsList.clear();
        mReturnPermission = createPermissionAnswer();
        mWifiScanAllowApps = AppOpsManager.MODE_ERRORED;
        mUid = OTHER_USER_UID;
        mThrowSecurityException = true;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.M;
        mIsLocationEnabled = false;
        mCurrentUser = UserHandle.USER_SYSTEM;
        mCoarseLocationPermission = PackageManager.PERMISSION_DENIED;
        mFineLocationPermission = PackageManager.PERMISSION_DENIED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ERRORED;
        mAllowFineLocationApps = AppOpsManager.MODE_ERRORED;
    }

    private void setupMockInterface() {
        BinderUtil.setUid(mUid);
        doAnswer(mReturnPermission).when(mMockPermissionsWrapper).getUidPermission(
                        anyString(), anyInt());
        when(mMockUserManager.isSameProfileGroup(
                UserHandle.getUserHandleForUid(MANAGED_PROFILE_UID),
                UserHandle.SYSTEM))
                .thenReturn(true);
        when(mMockPermissionsWrapper.getCurrentUser()).thenReturn(mCurrentUser);
        when(mMockPermissionsWrapper.getUidPermission(mManifestStringCoarse, mUid))
            .thenReturn(mCoarseLocationPermission);
        when(mMockPermissionsWrapper.getUidPermission(mManifestStringFine, mUid))
                .thenReturn(mFineLocationPermission);
        when(mMockPermissionsWrapper.getUidPermission(mManifestStringHardware, mUid))
                .thenReturn(mHardwareLocationPermission);
        when(mLocationManager.isLocationEnabledForUser(any())).thenReturn(mIsLocationEnabled);
    }

    /**
     * Test case setting: caller does not have Coarse Location permission.
     * Expect a SecurityException
     */
    @Test(expected = SecurityException.class)
    public void testEnforceCoarseLocationPermissionExpectSecurityException() throws Exception {
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCoarseLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);
    }

    /**
     * Test case setting: caller does have Coarse Location permission.
     * A SecurityException should not be thrown.
     */
    @Test
    public void testEnforceCoarseLocationPermission() throws Exception {
        mThrowSecurityException = false;
        mIsLocationEnabled = true;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        codeUnderTest.enforceCoarseLocationPermission(TEST_PACKAGE_NAME, TEST_FEATURE_ID, mUid);
        // verify that checking Coarse for apps!
        verify(mMockAppOps).noteOpNoThrow(eq(AppOpsManager.OPSTR_COARSE_LOCATION), anyInt(),
                anyString(), any(), any());
    }

    @Test
    public void testIsOemPrivilegedAdmin_notAllowListed() throws Exception {
        setupTestCase();
        setupOemAdmins(false, false);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertFalse(codeUnderTest.isOemPrivilegedAdmin(TEST_CALLING_UID));
    }

    @Test
    public void testIsOemPrivilegedAdmin_allowListedNotSigned() throws Exception {
        setupTestCase();
        setupOemAdmins(true, false);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertFalse(codeUnderTest.isOemPrivilegedAdmin(TEST_CALLING_UID));
    }

    @Test
    public void testIsOemPrivilegedAdmin_allowListedAndSigned() throws Exception {
        setupTestCase();
        setupOemAdmins(true, true);

        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertTrue(codeUnderTest.isOemPrivilegedAdmin(TEST_CALLING_UID));
    }

    private void setupOemAdmins(boolean allowlisted, boolean platformSigned) {
        Resources mockResources = mock(Resources.class);
        when(mockResources.getStringArray(R.array.config_oemPrivilegedWifiAdminPackages))
                .thenReturn(allowlisted ? new String[]{TEST_PACKAGE_NAME} : new String[0]);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mMockPkgMgr.getPackagesForUid(TEST_CALLING_UID))
                .thenReturn(new String[]{TEST_PACKAGE_NAME});

        when(mMockPkgMgr.checkSignatures(anyInt(), anyInt()))
                .thenReturn(platformSigned
                        ? PackageManager.SIGNATURE_MATCH
                        : PackageManager.SIGNATURE_NO_MATCH);
    }

    @Test
    public void testIsGuestUser() throws Exception {
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mUserContext);
        when(mUserContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);
        assertTrue(codeUnderTest.isGuestUser());
    }

    /**
     * Test that isAdminRestrictedNetwork returns true due to SSID allowlist restriction
     */
    @Test
    public void testIsAdminRestrictedNetworkSsidAllowlist() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        config.SSID = TEST_SSID;

        WifiSsidPolicy policy = new WifiSsidPolicy(
                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST,
                new ArraySet<>(Arrays.asList(WifiSsid.fromUtf8Text("test1"),
                        WifiSsid.fromUtf8Text("test2"))));
        when(mDevicePolicyManager.getWifiSsidPolicy()).thenReturn(policy);
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertTrue(codeUnderTest.isAdminRestrictedNetwork(config));
    }

    /**
     * Test that isAdminRestrictedNetwork returns true due to SSID denylist restriction
     */
    @Test
    public void testIsAdminRestrictedNetworkSsidDenylist() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        config.SSID = TEST_SSID;

        WifiSsidPolicy policy = new WifiSsidPolicy(
                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST,
                new ArraySet<>(Arrays.asList(WifiSsid.fromUtf8Text("GoogleGuest"),
                        WifiSsid.fromUtf8Text("test2"))));
        when(mDevicePolicyManager.getWifiSsidPolicy()).thenReturn(policy);
        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertTrue(codeUnderTest.isAdminRestrictedNetwork(config));
    }

    /**
     * Test that isAdminRestrictedNetwork returns true due to minimum security level restriction
     */
    @Test
    public void testIsAdminRestrictedNetworkSecurityLevelRestriction()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        config.SSID = TEST_SSID;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);

        when(mMockContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        when(mDevicePolicyManager.getMinimumRequiredWifiSecurityLevel()).thenReturn(
                DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP);

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        codeUnderTest.enableVerboseLogging(true);

        assertTrue(codeUnderTest.isAdminRestrictedNetwork(config));
    }

    /**
     * Test isSignedWithPlatformKey
     */
    @Test
    public void testIsSignedWithPlatformKey() throws Exception {
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockUserManager, mWifiInjector);
        when(mMockPkgMgr.checkSignatures(anyInt(), eq(Process.SYSTEM_UID)))
                .thenReturn(PackageManager.SIGNATURE_MATCH);
        assertTrue(codeUnderTest.isSignedWithPlatformKey(TEST_CALLING_UID));
        when(mMockPkgMgr.checkSignatures(anyInt(), eq(Process.SYSTEM_UID)))
                .thenReturn(PackageManager.SIGNATURE_NO_MATCH);
        assertFalse(codeUnderTest.isSignedWithPlatformKey(TEST_CALLING_UID));
    }
}

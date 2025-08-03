/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.model;

import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_CLOUD_ENROLLMENT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;

import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static com.android.managedprovisioning.model.ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED;
import static com.android.managedprovisioning.model.ProvisioningParams.FLOW_TYPE_LEGACY;
import static com.android.managedprovisioning.model.ProvisioningParams.FLOW_TYPE_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Parcel;
import android.os.UserHandle;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.Utils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tests for {@link ProvisioningParams} */
public class ProvisioningParamsTest extends AndroidTestCase {
    private static final String TEST_PROVISIONING_ACTION =
            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final boolean TEST_IS_NFC = true;
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");
    private static final boolean TEST_USE_MOBILE_DATA = true;

    // Wifi info
    private static final String TEST_SSID = "\"TestWifi\"";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
    private static final WifiInfo TEST_WIFI_INFO = WifiInfo.Builder.builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN)
            .setSecurityType(TEST_SECURITY_TYPE)
            .setPassword(TEST_PASSWORD)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
            .setPacUrl(TEST_PAC_URL)
            .build();

    // Device admin package download info
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final int TEST_MIN_SUPPORT_VERSION = 17689;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                    .build();
    private static final PackageDownloadInfo ROLE_HOLDER_DOWNLOAD_INFO_LOCATION_AND_SIGNATURE =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .build();
    private static final PackageDownloadInfo ROLE_HOLDER_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .build();

    @Mock
    private Utils mUtils;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    @SmallTest
    public void testFailToConstructProvisioningParamsWithoutPackageNameOrComponentName() {
        // WHEN the ProvisioningParams is constructed by with neither a package name nor a component
        // name
        try {
            ProvisioningParams provisioningParams = ProvisioningParams.Builder.builder()
                    .setProvisioningAction(TEST_PROVISIONING_ACTION)
                    .build();
            fail("Package name or component name is mandatory.");
        } catch (IllegalArgumentException e) {
            // THEN the ProvisioningParams fails to construct.
        }
    }

    @SmallTest
    public void testFailToConstructProvisioningParamsWithoutProvisioningAction() {
        // WHEN the ProvisioningParams is constructed by without a provisioning action.
        try {
            ProvisioningParams provisioningParams = ProvisioningParams.Builder.builder()
                    .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                    .build();
            fail("Provisioning action is mandatory");
        } catch (NullPointerException e) {
            // THEN the ProvisioningParams fails to construct.
        }
    }

    @SmallTest
    public void testEquals() {
        // GIVEN 2 ProvisioningParams objects created by the same set of parameters
        ProvisioningParams provisioningParams1 = getCompleteProvisioningParams();
        ProvisioningParams provisioningParams2 = getCompleteProvisioningParams();

        // WHEN these two objects compare.
        // THEN they are the same.
        assertThat(provisioningParams1).isEqualTo(provisioningParams2);
    }

    @SmallTest
    public void testNotEquals() {
        // GIVEN 2 ProvisioningParams objects created by different sets of parameters
        ProvisioningParams provisioningParams1 = ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setUseMobileData(TEST_USE_MOBILE_DATA)
                .setAdminExtrasBundle(createTestAdminExtras())
                .build();
        ProvisioningParams provisioningParams2 = ProvisioningParams.Builder.builder()
                .setProvisioningAction("different.action")
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setUseMobileData(TEST_USE_MOBILE_DATA)
                .setAdminExtrasBundle(createTestAdminExtras())
                .build();

        // WHEN these two objects compare.
        // THEN they are not the same.
        assertThat(provisioningParams1).isNotEqualTo(provisioningParams2);
    }

    @SmallTest
    public void testSaveAndRestoreComplete() throws Exception {
        testSaveAndRestore(getCompleteProvisioningParams());
    }

    // Testing with a minimum set of parameters to cover all null use cases.
    @SmallTest
    public void testSaveAndRestoreMinimalist() throws Exception {
        testSaveAndRestore(ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .build());
    }

    private void testSaveAndRestore(ProvisioningParams original) {
        // GIVEN a ProvisioningParams object
        // WHEN the ProvisioningParams is written to xml and then read back
        File file = new File(mContext.getFilesDir(), "test_store.xml");
        original.save(file);
        ProvisioningParams copy = ProvisioningParams.load(file);
        // THEN the same ProvisioningParams is obtained
        assertThat(original).isEqualTo(copy);
    }

    @SmallTest
    public void testParceable() {
        // GIVEN a ProvisioningParams object.
        ProvisioningParams expectedProvisioningParams = getCompleteProvisioningParams();

        // WHEN the ProvisioningParams is written to parcel and then read back.
        Parcel parcel = Parcel.obtain();
        expectedProvisioningParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ProvisioningParams actualProvisioningParams =
                ProvisioningParams.CREATOR.createFromParcel(parcel);

        // THEN the same ProvisioningParams is obtained.
        assertThat(expectedProvisioningParams).isEqualTo(actualProvisioningParams);
    }

    @SmallTest
    public void testInferDeviceAdminComponentName_componentNameIsGiven()
            throws IllegalProvisioningArgumentException {
        ProvisioningParams provisioningParams = new
                ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setProvisioningAction(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                .build();

        assertThat(TEST_COMPONENT_NAME)
                .isEqualTo(provisioningParams.inferDeviceAdminComponentName(
                        mUtils, mContext, UserHandle.myUserId()));
    }

    @SmallTest
    public void testInferDeviceAdminComponentName_componentNameIsNotGiven()
            throws IllegalProvisioningArgumentException {
        ProvisioningParams provisioningParams = new
                ProvisioningParams.Builder()
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setProvisioningAction(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                .build();

        when(mUtils.findDeviceAdmin(eq(TEST_PACKAGE_NAME), nullable(ComponentName.class),
                eq(mContext), eq(UserHandle.myUserId()))).thenReturn(TEST_COMPONENT_NAME);

        assertThat(TEST_COMPONENT_NAME)
                .isEqualTo(provisioningParams.inferDeviceAdminComponentName(
                        mUtils, mContext, UserHandle.myUserId()));
    }

    @SmallTest
    public void testSetUseMobileData_true() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder().setUseMobileData(true).build();
        assertThat(provisioningParams.useMobileData).isTrue();
    }

    @SmallTest
    public void testSetUseMobileData_false() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder().setUseMobileData(false).build();
        assertThat(provisioningParams.useMobileData).isFalse();
    }

    @SmallTest
    public void testSetUseMobileData_defaultsToFalse() {
        assertThat(createDefaultProvisioningParamsBuilder().build().useMobileData).isFalse();
    }

    @SmallTest
    public void testSetFlowType_legacy_areEqual() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setFlowType(FLOW_TYPE_LEGACY).build();

        assertThat(provisioningParams.flowType).isEqualTo(FLOW_TYPE_LEGACY);
    }

    @SmallTest
    public void testSetFlowType_adminIntegrated_areEqual() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setFlowType(FLOW_TYPE_ADMIN_INTEGRATED).build();

        assertThat(provisioningParams.flowType).isEqualTo(FLOW_TYPE_ADMIN_INTEGRATED);
    }

    @SmallTest
    public void testSetFlowType_defaultsToUnspecified() {
        assertThat(createDefaultProvisioningParamsBuilder().build().flowType)
                .isEqualTo(FLOW_TYPE_UNSPECIFIED);
    }

    @SmallTest
    public void testSetProvisioningTrigger_cloudEnrollment_areEqual() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setProvisioningTrigger(PROVISIONING_TRIGGER_CLOUD_ENROLLMENT).build();

        assertThat(provisioningParams.provisioningTrigger)
                .isEqualTo(PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);
    }

    @SmallTest
    public void testSetProvisioningTrigger_qrCode_areEqual() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setProvisioningTrigger(PROVISIONING_TRIGGER_QR_CODE).build();

        assertThat(provisioningParams.provisioningTrigger).isEqualTo(PROVISIONING_TRIGGER_QR_CODE);
    }

    @SmallTest
    public void testSetProvisioningTrigger_persistentDeviceOwner_areEqual() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setProvisioningTrigger(PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER)
                        .build();

        assertThat(provisioningParams.provisioningTrigger)
                .isEqualTo(PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER);
    }

    @SmallTest
    public void testSetProvisioningTrigger_defaultsToUnspecified() {
        assertThat(createDefaultProvisioningParamsBuilder().build().provisioningTrigger)
                .isEqualTo(PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    @SmallTest
    public void testSetAllowedProvisioningModes_defaultsToEmptyArray() {
        assertThat(createDefaultProvisioningParamsBuilder().build().allowedProvisioningModes)
                .isEmpty();
    }

    @SmallTest
    public void testSetAllowedProvisioningModes_personallyOwned_areEqual() {
        ProvisioningParams params =
                createDefaultProvisioningParamsBuilder()
                        .setAllowedProvisioningModes(new ArrayList<>(List.of(
                                PROVISIONING_MODE_MANAGED_PROFILE)))
                        .build();

        assertThat(params.allowedProvisioningModes)
                .containsExactly(PROVISIONING_MODE_MANAGED_PROFILE);
    }

    @SmallTest
    public void testSetAllowedProvisioningModes_organizationOwned_areEqual() {
        ProvisioningParams params =
                createDefaultProvisioningParamsBuilder()
                        .setAllowedProvisioningModes(new ArrayList<>(List.of(
                                PROVISIONING_MODE_MANAGED_PROFILE,
                                PROVISIONING_MODE_FULLY_MANAGED_DEVICE)))
                        .build();

        assertThat(params.allowedProvisioningModes).containsExactly(
                PROVISIONING_MODE_MANAGED_PROFILE,
                PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
    }

    @SmallTest
    public void testSetAllowedProvisioningModes_organizationAndPersonallyOwned_areEqual() {
        ProvisioningParams params =
                createDefaultProvisioningParamsBuilder()
                        .setAllowedProvisioningModes(new ArrayList<>(List.of(
                                PROVISIONING_MODE_MANAGED_PROFILE,
                                PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                                PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE)))
                        .build();

        assertThat(params.allowedProvisioningModes).containsExactly(
                PROVISIONING_MODE_MANAGED_PROFILE,
                PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE);
    }

    @SmallTest
    public void testSetSkipOwnershipDisclaimer_setTrue_isTrue() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setSkipOwnershipDisclaimer(true)
                        .build();

        assertThat(provisioningParams.skipOwnershipDisclaimer).isTrue();
    }

    @SmallTest
    public void testSetSkipOwnershipDisclaimer_setFalse_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setSkipOwnershipDisclaimer(false)
                        .build();

        assertThat(provisioningParams.skipOwnershipDisclaimer).isFalse();
    }

    @SmallTest
    public void testSetSkipOwnershipDisclaimer_notSet_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .build();

        assertThat(provisioningParams.skipOwnershipDisclaimer).isFalse();
    }

    @SmallTest
    public void testSetReturnBeforePolicyCompliance_setTrue_isTrue() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setReturnBeforePolicyCompliance(true)
                        .build();

        assertThat(provisioningParams.returnBeforePolicyCompliance).isTrue();
    }

    @SmallTest
    public void testSetReturnBeforePolicyCompliance_setFalse_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setReturnBeforePolicyCompliance(false)
                        .build();

        assertThat(provisioningParams.returnBeforePolicyCompliance).isFalse();
    }

    @SmallTest
    public void testSetReturnBeforePolicyCompliance_notSet_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .build();

        assertThat(provisioningParams.returnBeforePolicyCompliance).isFalse();
    }

    @SmallTest
    public void testDeviceOwnerDoesNotOptOutOfSensorsPermissionGrantsByDefault() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder().build();
        assertThat(params.deviceOwnerPermissionGrantOptOut).isFalse();
    }

    @SmallTest
    public void testDeviceOwnerCanOptOutOfSensorsPermissionGrants() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setDeviceOwnerPermissionGrantOptOut(true).build();
        assertThat(params.deviceOwnerPermissionGrantOptOut).isTrue();
    }

    @SmallTest
    public void testSetAllowProvisioningAfterUserSetupComplete_setTrue_isTrue() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setAllowProvisioningAfterUserSetupComplete(true)
                        .build();

        assertThat(provisioningParams.allowProvisioningAfterUserSetupComplete).isTrue();
    }

    @SmallTest
    public void testSetAllowProvisioningAfterUserSetupComplete_setFalse_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .setAllowProvisioningAfterUserSetupComplete(false)
                        .build();

        assertThat(provisioningParams.allowProvisioningAfterUserSetupComplete).isFalse();
    }

    @SmallTest
    public void testSetAllowProvisioningAfterUserSetupComplete_notSet_isFalse() {
        ProvisioningParams provisioningParams =
                createDefaultProvisioningParamsBuilder()
                        .build();

        assertThat(provisioningParams.allowProvisioningAfterUserSetupComplete).isFalse();
    }

    @SmallTest
    public void testAllowOffline_setTrue_isTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setAllowOffline(true)
                .build();

        assertThat(params.allowOffline).isTrue();
    }

    @SmallTest
    public void testAllowOffline_setFalse_isFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setAllowOffline(false)
                .build();

        assertThat(params.allowOffline).isFalse();
    }

    @SmallTest
    public void testProvisioningShouldLaunchResultIntent_notSet_isFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .build();

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    @SmallTest
    public void testProvisioningShouldLaunchResultIntent_setTrue_isTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setProvisioningShouldLaunchResultIntent(true)
                .build();

        assertThat(params.provisioningShouldLaunchResultIntent).isTrue();
    }

    @SmallTest
    public void testProvisioningShouldLaunchResultIntent_setFalse_isFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setProvisioningShouldLaunchResultIntent(false)
                .build();

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    @SmallTest
    public void testRoleHolderDownload_roleHolderDownloadInfoSet_notNull() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setRoleHolderDownloadInfo(ROLE_HOLDER_DOWNLOAD_INFO)
                .build();

        assertThat(params.roleHolderDownloadInfo).isNotNull();
    }

    @SmallTest
    public void testRoleHolderDownload_roleHolderDownloadInfoWithLocationAndSignature_works() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setRoleHolderDownloadInfo(ROLE_HOLDER_DOWNLOAD_INFO_LOCATION_AND_SIGNATURE)
                .build();

        assertThat(params.roleHolderDownloadInfo.location).isEqualTo(TEST_DOWNLOAD_LOCATION);
        assertThat(params.roleHolderDownloadInfo.signatureChecksum).isEqualTo(
                TEST_SIGNATURE_CHECKSUM);
    }

    @SmallTest
    public void testRoleHolderDownload_roleHolderDownloadInfo_works() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setRoleHolderDownloadInfo(ROLE_HOLDER_DOWNLOAD_INFO)
                .build();

        assertThat(params.roleHolderDownloadInfo.location).isEqualTo(TEST_DOWNLOAD_LOCATION);
        assertThat(params.roleHolderDownloadInfo.signatureChecksum).isEqualTo(
                TEST_SIGNATURE_CHECKSUM);
        assertThat(params.roleHolderDownloadInfo.cookieHeader).isEqualTo(TEST_COOKIE_HEADER);
    }

    @SmallTest
    public void testRoleHolderDownload_defaultCookieHeaderNull() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setRoleHolderDownloadInfo(ROLE_HOLDER_DOWNLOAD_INFO_LOCATION_AND_SIGNATURE)
                .build();

        assertThat(params.roleHolderDownloadInfo.cookieHeader).isNull();
    }

    private ProvisioningParams.Builder createDefaultProvisioningParamsBuilder() {
        return ProvisioningParams.Builder
                .builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME);
    }

    private ProvisioningParams getCompleteProvisioningParams() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setIsNfc(TEST_IS_NFC)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setUseMobileData(TEST_USE_MOBILE_DATA)
                .setAdminExtrasBundle(createTestAdminExtras())
                .setIsOrganizationOwnedProvisioning(true)
                .setFlowType(FLOW_TYPE_ADMIN_INTEGRATED)
                .setProvisioningTrigger(DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE)
                .setAllowProvisioningAfterUserSetupComplete(true)
                .build();
    }
}

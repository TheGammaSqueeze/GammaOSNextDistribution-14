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
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ORGANIZATION_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORTED_MODES;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORT_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_USE_MOBILE_DATA;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_DOMAIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_EAP_METHOD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PHASE2_AUTH;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_PERSONALLY_OWNED;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_MANAGED_ACCOUNT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMERS_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCALE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCAL_TIME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SUPPORT_URL_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_TIME_ZONE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_SSID_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.util.Base64;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Tests for {@link ExtrasProvisioningDataParser}. */
@SmallTest
public class ExtrasProvisioningDataParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final ComponentName TEST_COMPONENT_NAME_2 =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc2/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_KEEP_ACCOUNT_MIGRATED = true;
    private static final long TEST_PROVISIONING_ID = 1000L;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");
    private static final String TEST_ORGANIZATION_NAME = "TestOrganizationName";
    private static final String TEST_SUPPORT_URL = "https://www.support.url/";
    private static final String TEST_ILL_FORMED_LOCALE = "aaa_";

    // Wifi info
    private static final String TEST_SSID = "\"TestWifi\"";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_SECURITY_TYPE_EAP = "EAP";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
    private static final String TEST_EAP_METHOD = "TTLS";
    private static final String TEST_PHASE2_AUTH = "PAP";
    private static final String TEST_CA_CERT = "certificate";
    private static final String TEST_USER_CERT = "certificate";
    private static final String TEST_IDENTITY = "TestUser";
    private static final String TEST_ANONYMOUS_IDENTITY = "TestAUser";
    private static final String TEST_DOMAIN = "google.com";
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
    private static final String TEST_SIGNATURE_CHECKSUM_STRING = buildTestSignatureChecksum();
    private static final int TEST_MIN_SUPPORT_VERSION = 17689;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                    .build();
    private static final boolean TEST_USE_MOBILE_DATA = true;
    private static final Uri TEST_URI = Uri.parse("https://www.google.com/");
    private static final String TEST_DISCLAMER_HEADER = "Google";
    private static final int INVALID_SUPPORTED_MODES = 123;

    @Mock
    private Context mContext;

    @Mock
    private DevicePolicyManager mDpm;

    @Mock
    private ManagedProvisioningSharedPreferences mSharedPreferences;

    @Mock
    private SettingsFacade mSettingsFacade;

    @Mock
    private PackageManager mPackageManager;

    private ExtrasProvisioningDataParser mExtrasProvisioningDataParser;

    private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(mDpm);
        when(mContext.getContentResolver()).thenReturn(getContext().getContentResolver());
        when(mContext.getFilesDir()).thenReturn(getContext().getFilesDir());
        when(mSharedPreferences.incrementAndGetProvisioningId()).thenReturn(TEST_PROVISIONING_ID);
        when(mPackageManager.hasSystemFeature(eq(FEATURE_MANAGED_USERS))).thenReturn(true);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mUtils = spy(new Utils());
        mExtrasProvisioningDataParser = new ExtrasProvisioningDataParser(mContext, mUtils,
                new ParserUtils(), mSettingsFacade, mSharedPreferences);
    }

    public void testParse_trustedSourceProvisioningIntent() throws Exception {
        // GIVEN parsing happens during the setup wizard
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        // GIVEN a ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE is translated to
                        // ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        // THEN the trusted source is set to true.
                        .setStartedByTrustedSource(true)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN keep account migrated flag is ignored
                        .setKeepAccountMigrated(false)
                        .setLeaveAllSystemAppsEnabled(true)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .setOrganizationName(TEST_ORGANIZATION_NAME)
                        .setSupportUrl(TEST_SUPPORT_URL)
                        .setInitiatorRequestedProvisioningModes(
                                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                        .setAllowedProvisioningModes(new ArrayList<>(List.of(
                                PROVISIONING_MODE_MANAGED_PROFILE,
                                PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                        )))
                        .setReturnBeforePolicyCompliance(true)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_financedDeviceProvisioningIntent() throws Exception {
        // GIVEN a ACTION_PROVISION_FINANCED_DEVICE intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_FINANCED_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                    TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        .setProvisioningAction(ACTION_PROVISION_FINANCED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN keep account migrated flag is ignored
                        .setKeepAccountMigrated(false)
                        // THEN leave all system apps is always true
                        .setLeaveAllSystemAppsEnabled(true)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setOrganizationName(TEST_ORGANIZATION_NAME)
                        .setSupportUrl(TEST_SUPPORT_URL)
                        .setReturnBeforePolicyCompliance(true)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_resumeProvisioningIntent() throws Exception {
        // GIVEN a ProvisioningParams stored in an intent
        ProvisioningParams expected = ProvisioningParams.Builder.builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .build();
        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING)
                .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, expected);
        // WHEN the intent is parsed by the parser
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
        // THEN we get back the original ProvisioningParams.
        assertThat(expected).isEqualTo(params);
    }

    public void testParse_managedProfileIntent() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForTestPackageName();

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_PROFILE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN leave all system apps flag is ignored
                        .setLeaveAllSystemAppsEnabled(false)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedProfileIntent_CompProvisioning() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = buildTestManagedProfileIntent();

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForTestPackageName();

        // GIVEN the device admin is also device owner in primary user.
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(TEST_COMPONENT_NAME);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_PROFILE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedProfileIntent_DeviceOwnerWithByodProvisioning() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED);

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForNullPackageName();

        // GIVEN a different device admin is a device owner in primary user.
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(TEST_COMPONENT_NAME_2);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_shortExtras_sameAsLongExtras() throws Exception {
        assertThat(mExtrasProvisioningDataParser.parse(buildIntentWithAllLongExtras()))
            .isEqualTo(mExtrasProvisioningDataParser.parse(buildIntentWithAllShortExtras()));
    }

    public void testParse_managedDeviceIntent() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN Device Admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setLeaveAllSystemAppsEnabled(true)
                        // THEN wifi configuration is not supported.
                        .setWifiInfo(null)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .setReturnBeforePolicyCompliance(true)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_nfcProvisioningIntentThrowsException() {
        // GIVEN a NFC provisioning intent and other extras.
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
            fail("ExtrasProvisioningDataParser doesn't support NFC intent. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testParse_illFormedLocaleThrowsException() throws Exception {
        // GIVEN parsing happens during the setup wizard
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);

        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                // GIVEN a ill formed locale string.
                .putExtras(getTestTimeTimeZoneAndLocaleExtras(TEST_ILL_FORMED_LOCALE))
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
            fail("ExtrasProvisioningDataParser parsing an ill formed locale string. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testSetUseMobileData_forManagedProfile_alwaysFalse() throws Exception {
        Intent intent =
                buildTestManagedProfileIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testSetUseMobileData_fromTrustedSource_toFalse() throws Exception {
        Intent intent =
                buildTestTrustedSourceIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isTrue();
    }

    public void testSetUseMobileData_fromTrustedSource_toTrue() throws Exception {
        Intent intent =
                buildTestTrustedSourceIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isTrue();
    }

    public void testSetUseMobileData_fromTrustedSource_defaultsToFalse() throws Exception {
        Intent intent = buildTestTrustedSourceIntent();
        intent.removeExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testParse_WifiInfoWithCertificates() throws Exception {
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID)
                .putExtra(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN)
                .putExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE_EAP)
                .putExtra(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD)
                .putExtra(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD)
                .putExtra(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH)
                .putExtra(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT)
                .putExtra(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT)
                .putExtra(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY)
                .putExtra(EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY)
                .putExtra(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS)
                .putExtra(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params).isEqualTo(createTestProvisioningParamsBuilder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setStartedByTrustedSource(true)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE)))
                .setReturnBeforePolicyCompliance(true)
                .setWifiInfo(WifiInfo.Builder.builder()
                        .setSsid(TEST_SSID)
                        .setHidden(TEST_HIDDEN)
                        .setSecurityType(TEST_SECURITY_TYPE_EAP)
                        .setPassword(TEST_PASSWORD)
                        .setEapMethod(TEST_EAP_METHOD)
                        .setPhase2Auth(TEST_PHASE2_AUTH)
                        .setCaCertificate(TEST_CA_CERT)
                        .setUserCertificate(TEST_USER_CERT)
                        .setIdentity(TEST_IDENTITY)
                        .setAnonymousIdentity(TEST_ANONYMOUS_IDENTITY)
                        .setDomain(TEST_DOMAIN)
                        .setProxyHost(TEST_PROXY_HOST)
                        .setProxyPort(TEST_PROXY_PORT)
                        .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                        .setPacUrl(TEST_PAC_URL)
                        .build())
                .build());
    }

    public void testParse_PermissionGrantOptOut() throws IllegalProvisioningArgumentException {
        Intent provisionIntent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT,
                        true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(provisionIntent);
        assertThat(params.deviceOwnerPermissionGrantOptOut).isEqualTo(true);
    }

    public void testShortNamesOfExtrasAreUnique() {
        assertEquals(buildAllShortExtras().distinct().count(), buildAllShortExtras().count());
    }

    public void testParse_organizationOwnedIsFalse() throws Exception {
        Intent intent = buildTestIntent();
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent)
                .isOrganizationOwnedProvisioning).isFalse();
    }


    public void
            testParse_managedAccountProvisioningWithSkipEduExtra_skipEdu() throws Exception {
        Intent intent = buildTestProvisionManagedAccountIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipEducationScreens).isTrue();
    }

    public void
            testParse_managedAccountProvisioningWithoutSkipEduExtra_noSkipEdu() throws Exception {
        Intent intent = buildTestProvisionManagedAccountIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipEducationScreens).isFalse();
    }

    public void
            testParse_qrProvisioningWithSkipEduExtra_noSkipEdu() throws Exception {
        Intent intent = buildTestQrCodeIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipEducationScreens).isFalse();
    }

    public void
            testParse_managedProfileProvisioningWithSkipEduExtra_noSkipEdu() throws Exception {
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipEducationScreens).isFalse();
    }

    public void
            testParse_trustedSourceWithPersonallyOwnedSupportedModes_areEqual() throws Exception {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES,
                        FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
    }

    public void
            testParse_trustedSourceWithOrganizationOwnedSupportedModes_areEqual() throws Exception {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES,
                        FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED);
    }

    public void testParse_trustedSourceWithOrganizationAndPersonallyOwnedSupportedModes_areEqual()
            throws Exception {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES,
                        FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED
                                | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED
                        | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
    }

    public void testParse_trustedSourceWithDeviceOwnerSupportedMode_areEqual()
            throws Exception {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES, FLAG_SUPPORTED_MODES_DEVICE_OWNER);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(FLAG_SUPPORTED_MODES_DEVICE_OWNER);
    }

    public void
    testParse_nonTrustedSourceIntentWithOrganizationOwnedSupportedModes_hasDefaultValue()
            throws Exception {
        Intent intent = bildTestNonTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES,
                        FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES);
    }

    public void testParse_trustedSourceWithoutSupportedModes_defaultsToOrganizationOwned()
            throws Exception {
        Intent intent = buildTestTrustedSourceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.initiatorRequestedProvisioningModes)
                .isEqualTo(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED);
    }

    public void testParse_trustedSourceWithInvalidSupportedModes_throwsException()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SUPPORTED_MODES, INVALID_SUPPORTED_MODES);
        mockInstalledDeviceAdminForTestPackageName();

        assertThrows(
                IllegalProvisioningArgumentException.class,
                () -> mExtrasProvisioningDataParser.parse(intent));
    }

    public void testParse_trustedSourceWithSkipOwnershipDisclaimerTrue_areEqual()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipOwnershipDisclaimer).isTrue();
    }

    public void testParse_trustedSourceWithSkipOwnershipDisclaimerFalse_areEqual()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER, false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipOwnershipDisclaimer).isFalse();
    }

    public void testParse_trustedSourceWithoutSkipOwnershipDisclaimer_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipOwnershipDisclaimer).isFalse();
    }

    public void testParse_managedProfileWithSkipOwnershipDisclaimerTrue_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.skipOwnershipDisclaimer).isFalse();
    }

    public void testParse_trustedSourceWithReturnBeforePolicyComplianceTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_trustedSourceWithReturnBeforePolicyComplianceFalse_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isFalse();
    }

    public void testParse_trustedSourceWithReturnBeforePolicyComplianceNotSet_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_managedProfileWithReturnBeforePolicyComplianceTrue_afterSetupWizard_isFalse()
            throws IllegalProvisioningArgumentException {
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(false);
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isFalse();
    }

    public void testParse_managedProfileWithReturnBeforePolicyComplianceTrue_duringSetupWizard_isTrue()
            throws IllegalProvisioningArgumentException {
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_managedProfileWithReturnBeforePolicyComplianceNotSet_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestManagedProfileIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isFalse();
    }

    public void testParse_financedDeviceProvisioningWithReturnBeforePolicyComplianceTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_financedDeviceProvisioningWithReturnBeforePolicyComplianceFalse_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE, false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_financedDeviceProvisioningWithReturnBeforePolicyComplianceNotSet_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.returnBeforePolicyCompliance).isTrue();
    }

    public void testParse_trustedSourceProvisioningWithAllowOfflineTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE,
                        /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.allowOffline).isTrue();
    }

    public void testParse_trustedSourceProvisioningWithAllowOfflineFalse_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE,
                        /* value= */ false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.allowOffline).isFalse();
    }

    public void testParse_trustedSourceProvisioningWithAllowOfflineNotSet_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.allowOffline).isFalse();
    }

    public void testParse_managedProfileProvisioningWithAllowOfflineTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE, /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.allowOffline).isTrue();
    }

    public void testParse_financedDeviceProvisioningWithAllowOfflineTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE, /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.allowOffline).isTrue();
    }

    public void testParse_trustedSourceProvisioningWithProvisioningShouldLaunchResultIntentTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                        /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isTrue();
    }

    public void testParse_trustedSourceProvisioningWithProvisioningShouldLaunchResultIntentFalse_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                        /* value= */ false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    public void testParse_trustedSourceProvisioningWithProvisioningShouldLaunchResultIntentNotSet_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent();
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    public void testParse_managedProfileProvisioningWithProvisioningShouldLaunchResultIntentTrue_isTrue()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                        /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isTrue();
    }

    public void testDoesNotParse_financedDeviceProvisioningWithProvisioningShouldLaunchResultIntentTrue_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                        /* value= */ true);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    public void testDoesNotParse_financedDeviceProvisioningWithProvisioningShouldLaunchResultIntentFalse_isFalse()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                        /* value= */ false);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.provisioningShouldLaunchResultIntent).isFalse();
    }

    public void testParse_trustedSourceProvisioningWithRoleHolderDownloadInfo_works()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_LOCATION,
                        TEST_DOWNLOAD_LOCATION)
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                        TEST_COOKIE_HEADER)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_SIGNATURE_CHECKSUM,
                        TEST_SIGNATURE_CHECKSUM_STRING);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.roleHolderDownloadInfo).isNotNull();
        assertThat(params.roleHolderDownloadInfo.location).isEqualTo(TEST_DOWNLOAD_LOCATION);
        assertThat(params.roleHolderDownloadInfo.signatureChecksum)
                .isEqualTo(TEST_SIGNATURE_CHECKSUM);
        assertThat(params.roleHolderDownloadInfo.cookieHeader).isEqualTo(TEST_COOKIE_HEADER);
    }

    public void testParse_managedProfileProvisioningWithRoleHolderDownloadInfo_notParsed()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestManagedProfileIntent()
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_LOCATION,
                        TEST_DOWNLOAD_LOCATION)
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                        TEST_COOKIE_HEADER)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_SIGNATURE_CHECKSUM,
                        TEST_SIGNATURE_CHECKSUM_STRING);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.roleHolderDownloadInfo).isNull();
    }

    public void testParse_financedDeviceProvisioningWithRoleHolderDownloadInfo_notParsed()
            throws IllegalProvisioningArgumentException {
        Intent intent = buildTestFinancedDeviceIntent()
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_LOCATION,
                        TEST_DOWNLOAD_LOCATION)
                .putExtra(EXTRA_PROVISIONING_ROLE_HOLDER_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                        TEST_COOKIE_HEADER)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_SIGNATURE_CHECKSUM,
                        TEST_SIGNATURE_CHECKSUM_STRING);
        mockInstalledDeviceAdminForTestPackageName();

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params.roleHolderDownloadInfo).isNull();
    }

    private Stream<Field> buildAllShortExtras() {
        Field[] fields = ExtrasProvisioningDataParser.class.getDeclaredFields();
        return Arrays.stream(fields)
                .filter(field -> field.getName().startsWith("EXTRA_")
                        && field.getName().endsWith("_SHORT"));
    }

    private ProvisioningParams.Builder createTestProvisioningParamsBuilder() {
        return ProvisioningParams.Builder.builder().setProvisioningId(TEST_PROVISIONING_ID);
    }

    private Intent buildIntentWithAllShortExtras() {
        Bundle bundleShort = new Bundle();
        bundleShort.putString(
                EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT, TEST_DISCLAMER_HEADER);
        bundleShort.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT, TEST_URI);
        Parcelable[] parcelablesShort = {bundleShort};
        return new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT,
                        TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT,
                        TEST_COMPONENT_NAME)
                .putExtras(getShortTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getAllShortTestWifiInfoExtras())
                .putExtras(getShortTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT,
                        createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT,
                        TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT,
                        TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL_SHORT, TEST_SUPPORT_URL)
                .putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT,
                        TEST_USE_MOBILE_DATA)
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS_SHORT, parcelablesShort)
                .putExtra(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT_SHORT, true);
    }

    private Intent buildIntentWithAllLongExtras() {
        Bundle bundleLong = new Bundle();
        bundleLong.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, TEST_DISCLAMER_HEADER);
        bundleLong.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, TEST_URI);
        Parcelable[] parcelablesLong = {bundleLong};
        return new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getAllTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL)
                .putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, TEST_USE_MOBILE_DATA)
                .putExtra(EXTRA_PROVISIONING_LOGO_URI, TEST_URI)
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS, parcelablesLong)
                .putExtra(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT, true);
    }

    private static Intent buildTestManagedProfileIntent() {
        return new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);
    }

    private static Intent buildTestFinancedDeviceIntent() {
        return new Intent(ACTION_PROVISION_FINANCED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME);
    }

    private static Intent bildTestNonTrustedSourceIntent() {
        return buildTestManagedProfileIntent();
    }

    private static Intent buildTestIntent() {
        return buildTestTrustedSourceIntent();
    }

    private static Intent buildTestTrustedSourceIntent() {
        return  new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);
    }

    private Intent buildTestQrCodeIntent() {
        return buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);
    }

    private Intent buildTestProvisionManagedAccountIntent() {
        return buildTestTrustedSourceIntent()
                .putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_MANAGED_ACCOUNT);
    }

    private static Bundle getTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private static Bundle getAllTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private static Bundle getAllShortTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID_SHORT, TEST_SSID);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT, TEST_EAP_METHOD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT, TEST_PHASE2_AUTH);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT, TEST_CA_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT, TEST_USER_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT, TEST_IDENTITY);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT, TEST_ANONYMOUS_IDENTITY);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT, TEST_DOMAIN);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT, TEST_PROXY_HOST);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private void mockInstalledDeviceAdminForTestPackageName()
            throws IllegalProvisioningArgumentException {
        mockInstalledDeviceAdmin(TEST_PACKAGE_NAME);
    }

    private void mockInstalledDeviceAdminForNullPackageName()
            throws IllegalProvisioningArgumentException {
        mockInstalledDeviceAdmin(null);
    }

    private void mockInstalledDeviceAdmin(String packageName)
            throws IllegalProvisioningArgumentException {
        doReturn(TEST_COMPONENT_NAME)
                .when(mUtils)
                .findDeviceAdmin(packageName, TEST_COMPONENT_NAME, mContext, UserHandle.myUserId());
    }

    private static String buildTestLocaleString() {
        return StoreUtils.localeToString(TEST_LOCALE);
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtras() {
        return getTestTimeTimeZoneAndLocaleExtrasInternal(buildTestLocaleString());
    }

    private static Bundle getShortTestTimeTimeZoneAndLocaleExtras() {
        return getShortTestTimeTimeZoneAndLocaleExtrasInternal(buildTestLocaleString());
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtras(String locale) {
        return getTestTimeTimeZoneAndLocaleExtrasInternal(locale);
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtrasInternal(String locale){
        Bundle timeTimezoneAndLocaleExtras = new Bundle();
        timeTimezoneAndLocaleExtras.putLong(EXTRA_PROVISIONING_LOCAL_TIME, TEST_LOCAL_TIME);
        timeTimezoneAndLocaleExtras.putString(EXTRA_PROVISIONING_TIME_ZONE, TEST_TIME_ZONE);
        timeTimezoneAndLocaleExtras.putString(EXTRA_PROVISIONING_LOCALE, locale);
        return timeTimezoneAndLocaleExtras;
    }

    private static Bundle getShortTestTimeTimeZoneAndLocaleExtrasInternal(String locale){
        Bundle timeTimezoneAndLocaleExtras = new Bundle();
        timeTimezoneAndLocaleExtras.putLong(
                EXTRA_PROVISIONING_LOCAL_TIME_SHORT, TEST_LOCAL_TIME);
        timeTimezoneAndLocaleExtras.putString(
                EXTRA_PROVISIONING_TIME_ZONE_SHORT, TEST_TIME_ZONE);
        timeTimezoneAndLocaleExtras.putString(
                EXTRA_PROVISIONING_LOCALE_SHORT, locale);
        return timeTimezoneAndLocaleExtras;
    }

    private static Bundle getTestDeviceAdminDownloadExtras() {
        Bundle downloadInfoExtras = new Bundle();
        downloadInfoExtras.putInt(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE, TEST_MIN_SUPPORT_VERSION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, TEST_DOWNLOAD_LOCATION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER, TEST_COOKIE_HEADER);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                buildTestPackageChecksum());
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                buildTestSignatureChecksum());
        return downloadInfoExtras;
    }

    private static String buildTestPackageChecksum() {
        return Base64.encodeToString(TEST_PACKAGE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static Bundle getShortTestDeviceAdminDownloadExtras() {
        Bundle downloadInfoExtras = new Bundle();
        downloadInfoExtras.putInt(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT,
                TEST_MIN_SUPPORT_VERSION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT,
                TEST_DOWNLOAD_LOCATION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT,
                TEST_COOKIE_HEADER);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT,
                buildTestPackageChecksum());
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT,
                buildTestSignatureChecksum());
        return downloadInfoExtras;
    }

    private static String buildTestSignatureChecksum() {
        return Base64.encodeToString(TEST_SIGNATURE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}

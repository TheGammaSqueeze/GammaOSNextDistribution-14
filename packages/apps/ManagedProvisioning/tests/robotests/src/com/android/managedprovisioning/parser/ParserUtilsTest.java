/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_PERSONALLY_OWNED;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_CLOUD_ENROLLMENT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import static com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for {@link ParserUtils}.
 */
@RunWith(RobolectricTestRunner.class)
public class ParserUtilsTest {

    private static final int INVALID_PROVISIONING_MODES = -1;
    private static final int  INVALID_PROVISIONING_MODE_COMBINATION =
            FLAG_SUPPORTED_MODES_PERSONALLY_OWNED | FLAG_SUPPORTED_MODES_DEVICE_OWNER;
    private final ParserUtils mParserUtils = new ParserUtils();
    private final SettingsFacade mSettingsFacade = new SettingsFacade();
    private final Context mContext = RuntimeEnvironment.application;
    private final Utils mUtils = new Utils();

    @Before
    public void setUp() {
        shadowOf(mContext.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_MANAGED_USERS, /* supported */ true);
    }

    @Test
    public void extractProvisioningTrigger_provisionManagedDeviceIntent_returnsUnspecified() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceIntentWithNoProvisioningTrigger_returnsUnspecified() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    @Test
    public void
    extractProvisioningTrigger_provisionTrustedSourceWithQrProvisioningTrigger_returnsQr() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_QR_CODE);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceWithCloudEnrollmentProvisioningTrigger_returnsCloudEnrollment() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceWithDeviceOwnerProvisioningTrigger_returnsPersistentDeviceOwner() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedDeviceIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE);

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedProfileIntent_returnsProvisionManagedProfile()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_PROFILE);
    }

    @Test
    public void
    extractProvisioningAction_provisionFinancedDeviceIntent_returnsProvisionFinancedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_FINANCED_DEVICE);

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_FINANCED_DEVICE);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedDeviceSilentlyIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_SILENTLY);

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test
    public void extractProvisioningAction_nfcIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);
        intent.setType(MIME_TYPE_PROVISIONING_NFC);

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test(expected = IllegalProvisioningArgumentException.class)
    public void testExtractProvisioningAction_nfcIntentWithNoMimeType_throwsException()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);

        mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext);
    }

    @Test(expected = IllegalProvisioningArgumentException.class)
    public void extractProvisioningAction_nfcIntentWithWrongMimeType_throwsException()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);
        intent.setType("wrongMimeType");

        mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext);
    }

    @Test
    public void
            extractProvisioningAction_trustedSourceDuringSetupWizard_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        markDuringSetupWizard();

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test
    public void
            extractProvisioningAction_trustedSourceAfterSetupWizard_returnsProvisionManagedProfile()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        markAfterSetupWizard();

        assertThat(mParserUtils.extractProvisioningAction(intent, mSettingsFacade, mContext))
                .isEqualTo(ACTION_PROVISION_MANAGED_PROFILE);
    }

    @Test
    public void
            getAllowedProvisioningModes_organizationOwned_returnsManagedProfileAndManagedDevice() {
        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED, mUtils)).containsExactly(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
    }

    @Test
    public void
            getAllowedProvisioningModes_personallyOwned_returnsManagedProfileOnly() {
        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                FLAG_SUPPORTED_MODES_PERSONALLY_OWNED, mUtils)).containsExactly(
                        PROVISIONING_MODE_MANAGED_PROFILE);
    }

    @Test
    public void getAllowedProvisioningModes_organizationAndPersonallyOwned_returnsManagedProfileManagedDevicePersonalManagedProfile() {
        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED
                        | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED, mUtils)).containsExactly(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                        PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE);
    }

    @Test
    public void getAllowedProvisioningModes_deviceOwner_returnsManagedDevice() {
        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                FLAG_SUPPORTED_MODES_DEVICE_OWNER, mUtils)).containsExactly(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
    }

    @Test
    public void getAllowedProvisioningModes_invalidValue_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mParserUtils
                        .getAllowedProvisioningModes(mContext, INVALID_PROVISIONING_MODES, mUtils));
    }

    @Test
    public void getAllowedProvisioningModes_invalidBinaryCombination_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mParserUtils.getAllowedProvisioningModes(mContext,
                        INVALID_PROVISIONING_MODE_COMBINATION, mUtils));
    }

    @Test
    public void
            getAllowedProvisioningModes_organizationOwnedAndManagedUsersNotSupported_returnsManagedDeviceOnly() {
        shadowOf(mContext.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_MANAGED_USERS, /* supported = */ false);

        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED, mUtils)).containsExactly(
                PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
    }

    @Test
    public void
            getAllowedProvisioningModes_personallyOwnedAndManagedUsersNotSupported_throwsException() {
        shadowOf(mContext.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_MANAGED_USERS, /* supported */ false);

        assertThrows(
                IllegalArgumentException.class,
                () -> mParserUtils
                        .getAllowedProvisioningModes(mContext,
                                FLAG_SUPPORTED_MODES_PERSONALLY_OWNED, mUtils));
    }

    @Test
    public void getAllowedProvisioningModes_defaultValue_returnsEmptyArray() {
        assertThat(mParserUtils.getAllowedProvisioningModes(mContext,
                DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES, mUtils)).isEmpty();
    }

    @Test
    public void validateSupportedModes_personallyOwned_doesNothing() {
        mParserUtils.getAllowedProvisioningModes(
                mContext, FLAG_SUPPORTED_MODES_PERSONALLY_OWNED, mUtils);
    }

    @Test
    public void validateSupportedModes_organizationOwned_doesNothing() {
        mParserUtils.getAllowedProvisioningModes(
                mContext, FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED, mUtils);
    }

    @Test
    public void validateSupportedModes_deviceOwner_doesNothing() {
        mParserUtils.getAllowedProvisioningModes(
                mContext, FLAG_SUPPORTED_MODES_DEVICE_OWNER, mUtils);
    }

    @Test
    public void validateSupportedModes_personalAndOrganizationOwned_doesNothing() {
        mParserUtils.getAllowedProvisioningModes(
                mContext,
                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED,
                mUtils);
    }

    @Test
    public void validateSupportedModes_invalidValue_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mParserUtils
                        .getAllowedProvisioningModes(mContext, INVALID_PROVISIONING_MODES, mUtils));
    }

    @Test
    public void validateSupportedModes_invalidBinaryCombination_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mParserUtils.validateSupportedModes(INVALID_PROVISIONING_MODE_COMBINATION));
    }

    private boolean markDuringSetupWizard() {
        return Settings.Secure.putInt(mContext.getContentResolver(), USER_SETUP_COMPLETE, 0);
    }

    private boolean markAfterSetupWizard() {
        return Settings.Secure.putInt(mContext.getContentResolver(), USER_SETUP_COMPLETE, 1);
    }
}

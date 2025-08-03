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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_PROVISIONING_INITIATOR_PACKAGE;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_STATE;

import static com.android.managedprovisioning.TestUtils.assertIntentsEqual;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.google.android.setupcompat.util.WizardManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;


@SmallTest
@RunWith(JUnit4.class)
public class DeviceManagementRoleHolderHelperTest {
    private static final String ROLE_HOLDER_PACKAGE_NAME = "com.test.package";
    private static final String ROLE_HOLDER_EMPTY_PACKAGE_NAME = "";
    private static final String ROLE_HOLDER_NULL_PACKAGE_NAME = null;
    private static final PersistableBundle TEST_ROLE_HOLDER_STATE = new PersistableBundle();
    private static final String TEST_CALLING_PACKAGE = "test.calling.package";
    private static final Bundle TEST_ADDITIONAL_EXTRAS = new Bundle();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    public static final String TEST_EXTRA_KEY = "test_extra_key";
    public static final String TEST_EXTRA_VALUE = "test_extra_value";
    private static final Intent MANAGED_PROFILE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent MANAGED_PROFILE_ROLE_HOLDER_INTENT =
            createManagedProfileRoleHolderIntent();
    private static final Intent MANAGED_PROFILE_ROLE_HOLDER_INTENT_WITH_MINIMAL_EXTRAS =
            createManagedProfileRoleHolderIntentWithMinimalExtras();
    private static final Intent PROVISION_TRUSTED_SOURCE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent PROVISION_TRUSTED_SOURCE_ROLE_HOLDER_INTENT =
            createTrustedSourceRoleHolderIntent();
    private static final Intent PROVISION_TRUSTED_SOURCE_ROLE_HOLDER_INTENT_WITH_MINIMAL_EXTRAS =
            createTrustedSourceRoleHolderIntentWithMinimalExtras();
    private static final Intent FINANCED_DEVICE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent PROVISION_FINALIZATION_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_FINALIZATION);
    private static final Intent PROVISION_FINALIZATION_ROLE_HOLDER_INTENT =
            new Intent(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION)
                    .setPackage(ROLE_HOLDER_PACKAGE_NAME);
    private static final Intent MANAGED_PROVISIONING_INTENT = MANAGED_PROFILE_INTENT;
    private static final Intent INVALID_MANAGED_PROVISIONING_INTENT =
            new Intent("action.intent.test");
    private Set<String> mRoleHolderHandledIntents;
    private boolean mCanDelegateProvisioningToRoleHolder;
    private final FeatureFlagChecker mFeatureFlagChecker =
            () -> mCanDelegateProvisioningToRoleHolder;

    @Before
    public void setUp() {
        enableRoleHolderDelegation();
        mRoleHolderHandledIntents = createRoleHolderRequiredIntentActionsSet();
    }

    @After
    public void tearDown() {
        disableRoleHolderDelegation();
    }

    @Test
    public void roleHolderHelperConstructor_roleHolderPackageNameEmpty_noExceptionThrown() {
        createRoleHolderHelper(ROLE_HOLDER_EMPTY_PACKAGE_NAME);
    }

    @Test
    public void isRoleHolderReadyForProvisioning_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isTrue();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_emptyRoleHolderPackageName_isFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderDelegationDisabled_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();
        disableRoleHolderDelegation();

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderNotInstalled_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderNotInstalled();

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderStub_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithStubRoleHolder();

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderInvalid_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithInvalidRoleHolder();

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderResolvesRequiredIntents_returnsTrue() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isTrue();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderResolvesRequiredIntentsExceptManagedProfile_returnsFalse() {
        mRoleHolderHandledIntents.remove(
                DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE);
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderResolvesRequiredIntentsExceptTrustedSource_returnsFalse() {
        mRoleHolderHandledIntents.remove(
                ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_roleHolderResolvesRequiredIntentsExceptFinalization_returnsFalse() {
        mRoleHolderHandledIntents.remove(
                DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION);
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROVISIONING_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_provisioningStartedViaManagedProfileIntent_returnsTrue() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, MANAGED_PROFILE_INTENT)).isTrue();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_provisioningStartedViaTrustedSourceIntent_returnsTrue() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, PROVISION_TRUSTED_SOURCE_INTENT)).isTrue();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_provisioningStartedViaFinancedDeviceIntent_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, FINANCED_DEVICE_INTENT)).isFalse();
    }

    @Test
    public void isRoleHolderReadyForProvisioning_provisioningStartedViaFinalizationIntent_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
                        mRoleHolderHandledIntents);

        assertThat(roleHolderHelper.isRoleHolderReadyForProvisioning(
                mContext, PROVISION_FINALIZATION_INTENT)).isFalse();
    }

    @Test
    public void createRoleHolderProvisioningIntent_invalidProvisioningIntent_throwsException() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertThrows(IllegalArgumentException.class, () ->
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        INVALID_MANAGED_PROVISIONING_INTENT,
                        TEST_ADDITIONAL_EXTRAS, TEST_CALLING_PACKAGE, TEST_ROLE_HOLDER_STATE
                ));
    }

    @Test
    public void createRoleHolderProvisioningIntent_managedProfileProvisioningIntent_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertIntentsEqual(
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        MANAGED_PROFILE_INTENT,
                        TEST_ADDITIONAL_EXTRAS, TEST_CALLING_PACKAGE, TEST_ROLE_HOLDER_STATE
                ),
                MANAGED_PROFILE_ROLE_HOLDER_INTENT);
    }

    @Test
    public void
            createRoleHolderProvisioningIntent_managedProfileProvisioningIntentWithNoStateOrCallingPackage_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertIntentsEqual(
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        MANAGED_PROFILE_INTENT,
                        /* roleHolderState= */ TEST_ADDITIONAL_EXTRAS, null, null
                        /* callingPackage= */),
                MANAGED_PROFILE_ROLE_HOLDER_INTENT_WITH_MINIMAL_EXTRAS);
    }

    @Test
    public void createRoleHolderProvisioningIntent_emptyRoleHolderPackageName_throwsException() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThrows(IllegalStateException.class, () ->
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        MANAGED_PROFILE_INTENT,
                        TEST_ADDITIONAL_EXTRAS, TEST_CALLING_PACKAGE, TEST_ROLE_HOLDER_STATE
                ));
    }

    @Test
    public void createRoleHolderProvisioningIntent_trustedSourceProvisioningIntent_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertIntentsEqual(
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        TEST_ADDITIONAL_EXTRAS, TEST_CALLING_PACKAGE, TEST_ROLE_HOLDER_STATE
                ),
                PROVISION_TRUSTED_SOURCE_ROLE_HOLDER_INTENT);
    }

    @Test
    public void
            createRoleHolderProvisioningIntent_trustedSourceProvisioningIntentWithNoStateOrCallingPackage_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertIntentsEqual(
                roleHolderHelper.createRoleHolderProvisioningIntent(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        /* roleHolderState= */ TEST_ADDITIONAL_EXTRAS, null, null
                        /* callingPackage= */),
                PROVISION_TRUSTED_SOURCE_ROLE_HOLDER_INTENT_WITH_MINIMAL_EXTRAS);
    }

    @Test
    public void createRoleHolderProvisioningIntent_financedDeviceProvisioningIntent_throwsException() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertThrows(
                IllegalArgumentException.class,
                () -> roleHolderHelper.createRoleHolderProvisioningIntent(
                        FINANCED_DEVICE_INTENT,
                        TEST_ADDITIONAL_EXTRAS, TEST_CALLING_PACKAGE, TEST_ROLE_HOLDER_STATE
                ));
    }

    @Test
    public void createRoleHolderFinalizationIntent_works() {
        DeviceManagementRoleHolderHelper roleHolderHelper = createRoleHolderHelper();

        assertIntentsEqual(
                roleHolderHelper.createRoleHolderFinalizationIntent(
                        /* parentActivityIntent= */ null),
                PROVISION_FINALIZATION_ROLE_HOLDER_INTENT);
    }

    @Test
    public void createRoleHolderFinalizationIntent_emptyRoleHolderPackageName_throwsException() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThrows(IllegalStateException.class,
                () -> roleHolderHelper.createRoleHolderFinalizationIntent(
                        /* parentActivityIntent= */ null));
    }

    @Test
    public void isRoleHolderProvisioningEnabled_roleHolderConfigured_returnsTrue() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_PACKAGE_NAME);

        assertThat(roleHolderHelper.isRoleHolderProvisioningEnabled()).isTrue();
    }

    @Test
    public void isRoleHolderProvisioningEnabled_roleHolderNotConfigured_returnsFalse() {
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderHelper.isRoleHolderProvisioningEnabled()).isFalse();
    }

    @Test
    public void isRoleHolderProvisioningEnabled_featureFlagDisabled_returnsFalse() {
        mCanDelegateProvisioningToRoleHolder = false;
        DeviceManagementRoleHolderHelper roleHolderHelper =
                createRoleHolderHelper(ROLE_HOLDER_PACKAGE_NAME);

        assertThat(roleHolderHelper.isRoleHolderProvisioningEnabled()).isFalse();
    }

    private DeviceManagementRoleHolderHelper createRoleHolderHelper() {
        return new DeviceManagementRoleHolderHelper(
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* resolveIntentChecker= */ (intent, packageManager) -> true,
                /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                mFeatureFlagChecker);
    }

    private DeviceManagementRoleHolderHelper createRoleHolderHelper(
            String roleHolderPackageName) {
        return new DeviceManagementRoleHolderHelper(
                roleHolderPackageName,
                /* packageInstallChecker= */ (packageName) -> true,
                /* resolveIntentChecker= */ (intent, packageManager) -> true,
                /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                mFeatureFlagChecker);
    }

    private DeviceManagementRoleHolderHelper createRoleHolderHelperWithRoleHolderNotInstalled() {
        return new DeviceManagementRoleHolderHelper(
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> false,
                /* resolveIntentChecker= */ (intent, packageManager) -> true,
                /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                mFeatureFlagChecker);
    }

    private DeviceManagementRoleHolderHelper createRoleHolderHelperWithStubRoleHolder() {
        return new DeviceManagementRoleHolderHelper(
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* resolveIntentChecker= */ (intent, packageManager) -> true,
                /* roleHolderStubChecker= */ (packageName, packageManager) -> true,
                mFeatureFlagChecker);
    }

    private DeviceManagementRoleHolderHelper createRoleHolderHelperWithInvalidRoleHolder() {
        // A role holder is considered invalid if it is not able to resolve all the required intents
        return new DeviceManagementRoleHolderHelper(
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* resolveIntentChecker= */ (intent, packageManager) -> false,
                /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                mFeatureFlagChecker);
    }

    private DeviceManagementRoleHolderHelper
            createRoleHolderHelperWithRoleHolderResolvesRequiredIntents(
            Set<String> roleHolderHandledIntents) {
        // A role holder is considered invalid if it is not able to resolve all the required intents
        return new DeviceManagementRoleHolderHelper(
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* resolveIntentChecker= */ (intent, packageManager) ->
                        roleHolderHandledIntents.contains(intent.getAction()),
                /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                mFeatureFlagChecker);
    }

    private static Set<String> createRoleHolderRequiredIntentActionsSet() {
        Set<String> result = new HashSet<>();
        result.add(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE);
        result.add(ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        result.add(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION);
        return result;
    }

    private static Intent createManagedProfileRoleHolderIntent() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE)
                .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE)
                .setPackage(ROLE_HOLDER_PACKAGE_NAME)
                .putExtra(EXTRA_ROLE_HOLDER_PROVISIONING_INITIATOR_PACKAGE, TEST_CALLING_PACKAGE)
                .putExtra(EXTRA_ROLE_HOLDER_STATE, TEST_ROLE_HOLDER_STATE);
        WizardManagerHelper.copyWizardManagerExtras(MANAGED_PROFILE_INTENT, intent);
        return intent;
    }

    private static Intent createManagedProfileRoleHolderIntentWithMinimalExtras() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE)
                .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE)
                .setPackage(ROLE_HOLDER_PACKAGE_NAME);
        WizardManagerHelper.copyWizardManagerExtras(MANAGED_PROFILE_INTENT, intent);
        return intent;
    }

    private static Intent createTrustedSourceRoleHolderIntent() {
        Intent intent = new Intent(ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE)
                .setPackage(ROLE_HOLDER_PACKAGE_NAME)
                .putExtra(EXTRA_ROLE_HOLDER_PROVISIONING_INITIATOR_PACKAGE, TEST_CALLING_PACKAGE)
                .putExtra(EXTRA_ROLE_HOLDER_STATE, TEST_ROLE_HOLDER_STATE);
        WizardManagerHelper.copyWizardManagerExtras(PROVISION_TRUSTED_SOURCE_INTENT, intent);
        return intent;
    }

    private static Intent createTrustedSourceRoleHolderIntentWithMinimalExtras() {
        Intent intent = new Intent(ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE)
                .setPackage(ROLE_HOLDER_PACKAGE_NAME);
        WizardManagerHelper.copyWizardManagerExtras(PROVISION_TRUSTED_SOURCE_INTENT, intent);
        return intent;
    }

    private void enableRoleHolderDelegation() {
        mCanDelegateProvisioningToRoleHolder = true;
    }

    private void disableRoleHolderDelegation() {
        mCanDelegateProvisioningToRoleHolder = false;
    }
}

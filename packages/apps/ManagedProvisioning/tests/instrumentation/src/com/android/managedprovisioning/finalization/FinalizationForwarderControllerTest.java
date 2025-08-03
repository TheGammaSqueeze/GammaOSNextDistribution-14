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

package com.android.managedprovisioning.finalization;

import static com.android.managedprovisioning.ManagedProvisioningScreens.FINALIZATION_INSIDE_SUW;
import static com.android.managedprovisioning.TestUtils.assertIntentEquals;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.ScreenManager;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper;
import com.android.managedprovisioning.common.FeatureFlagChecker;
import com.android.managedprovisioning.common.PackageInstallChecker;
import com.android.managedprovisioning.common.SharedPreferences;
import com.android.managedprovisioning.testcommon.FakeSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@SmallTest
@RunWith(JUnit4.class)
public class FinalizationForwarderControllerTest {

    private static final String TEST_ROLE_HOLDER_PACKAGE = "test.roleholder.package";
    private static final Intent FINALIZATION_INTENT_ROLE_HOLDER =
            new Intent(DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION)
                    .setPackage(TEST_ROLE_HOLDER_PACKAGE);
    private static final Intent FINALIZATION_INTENT_PLATFORM_PROVIDED =
            new Intent(
                    ApplicationProvider.getApplicationContext(),
                    FinalizationInsideSuwActivity.class);
    private static final Intent FINALIZATION_INTENT_TEST_OVERRIDE =
            new Intent(
                    ApplicationProvider.getApplicationContext(),
                    Activity.class);
    private static final int PROVISIONING_FINALIZATION_UNDEFINED = 0;
    private static final int PROVISIONING_FINALIZATION_ROLE_HOLDER = 1;
    private static final int PROVISIONING_FINALIZATION_PLATFORM_PROVIDED = 2;

    private final FinalizationForwarderController.Ui mUi = createUi();
    private final SharedPreferences mSharedPreferences = new FakeSharedPreferences();
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private int mRoleHolderFinalizationType;
    private FinalizationForwarderController mController;
    private ScreenManager mScreenManager;

    @Before
    public void setUp() {
        mRoleHolderFinalizationType = PROVISIONING_FINALIZATION_UNDEFINED;
        ManagedProvisioningBaseApplication app = (ManagedProvisioningBaseApplication) mContext;
        mScreenManager = app.getScreenManager();
        mController = new FinalizationForwarderController(
                new DeviceManagementRoleHolderHelper(
                    TEST_ROLE_HOLDER_PACKAGE,
                    new TestPackageInstallChecker(),
                    new DeviceManagementRoleHolderHelper.DefaultResolveIntentChecker(),
                    new DeviceManagementRoleHolderHelper.DefaultRoleHolderStubChecker(),
                    (FeatureFlagChecker) () -> false),
                mUi,
                mSharedPreferences,
                mScreenManager);
    }

    @Test
    public void createRoleHolderFinalizationIntent_works() {
        Intent finalizationIntent = mController.createRoleHolderFinalizationIntent(
                mContext, /* parentActivityIntent= */ null);

        assertIntentEquals(finalizationIntent, FINALIZATION_INTENT_ROLE_HOLDER);
    }

    @Test
    public void createPlatformProvidedProvisioningFinalizationIntent_works() {
        Intent finalizationIntent =
                mController.createPlatformProvidedProvisioningFinalizationIntent(
                        mContext, /* parentActivityIntent= */ null);

        assertIntentEquals(finalizationIntent, FINALIZATION_INTENT_PLATFORM_PROVIDED);
    }

    @Test
    public void createPlatformProvidedProvisioningFinalizationIntent_intentOverridden_works() {
        Class<? extends Activity> originalActivity =
                mScreenManager.getActivityClassForScreen(FINALIZATION_INSIDE_SUW);
        mScreenManager.setOverrideActivity(FINALIZATION_INSIDE_SUW, Activity.class);
        try {
            Intent finalizationIntent =
                    mController.createPlatformProvidedProvisioningFinalizationIntent(
                            mContext, /* parentActivityIntent= */ null);

            assertIntentEquals(finalizationIntent, FINALIZATION_INTENT_TEST_OVERRIDE);
        } finally {
            mScreenManager.setOverrideActivity(FINALIZATION_INSIDE_SUW, originalActivity);
        }
    }

    @Test
    public void forwardFinalization_withRoleHolderDelegationPreferenceTrue_startsRoleHolderFinalization() {
        mSharedPreferences.setIsProvisioningFlowDelegatedToRoleHolder(true);

        mController.forwardFinalization(mContext);

        assertThat(mRoleHolderFinalizationType).isEqualTo(PROVISIONING_FINALIZATION_ROLE_HOLDER);
    }

    @Test
    public void forwardFinalization_withRoleHolderDelegationPreferenceFalse_startsPlatformProvidedFinalization() {
        mSharedPreferences.setIsProvisioningFlowDelegatedToRoleHolder(false);

        mController.forwardFinalization(mContext);

        assertThat(mRoleHolderFinalizationType).isEqualTo(
                PROVISIONING_FINALIZATION_PLATFORM_PROVIDED);
    }

    private class TestPackageInstallChecker implements PackageInstallChecker {
        @Override
        public boolean isPackageInstalled(String packageName) {
            return false;
        }
    }

    private FinalizationForwarderController.Ui createUi() {
        return new FinalizationForwarderController.Ui() {

            @Override
            public void startRoleHolderFinalization() {
                mRoleHolderFinalizationType = PROVISIONING_FINALIZATION_ROLE_HOLDER;
            }

            @Override
            public void startPlatformProvidedProvisioningFinalization() {
                mRoleHolderFinalizationType = PROVISIONING_FINALIZATION_PLATFORM_PROVIDED;
            }
        };
    }
}

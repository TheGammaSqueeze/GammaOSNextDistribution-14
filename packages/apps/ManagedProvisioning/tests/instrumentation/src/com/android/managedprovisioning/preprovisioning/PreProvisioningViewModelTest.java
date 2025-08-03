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

package com.android.managedprovisioning.preprovisioning;

import static com.android.managedprovisioning.TestUtils.assertBundlesEqual;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_GETTING_PROVISIONING_MODE;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_PREPROVISIONING_INITIALIZED;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_PREPROVISIONING_INITIALIZING;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_PROVISIONING_FINALIZED;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_PROVISIONING_STARTED;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_ROLE_HOLDER_PROVISIONING;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_SHOWING_USER_CONSENT;
import static com.android.managedprovisioning.preprovisioning.PreProvisioningViewModel.STATE_UPDATING_ROLE_HOLDER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.TestUtils;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.parser.MessageParser;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public final class PreProvisioningViewModelTest {

    public static final String TEST_KEY = "testKey";
    public static final String TEST_VALUE = "testValue";
    private final Instrumentation mInstrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    private PreProvisioningViewModel mViewModel;
    private TimeLogger mTimeLogger;
    private EncryptionController mEncryptionController;
    private final Context mContext = InstrumentationRegistry.getTargetContext();

    private static final PersistableBundle ROLE_HOLDER_STATE = createRoleHolderStateBundle();

    @Before
    public void setUp()  {
        mTimeLogger = new TimeLogger(mContext, /* category */ 0);
        MessageParser messageParser = new MessageParser(mContext, new Utils());
        mEncryptionController = TestUtils.createEncryptionController(mContext);
        mViewModel = new PreProvisioningViewModel(
                mTimeLogger,
                messageParser,
                mEncryptionController, new PreProvisioningViewModel.DefaultConfig());
    }

    @Test
    public void getState_defaultsToInitializing() {
        assertThat(mViewModel.getState().getValue())
                .isEqualTo(STATE_PREPROVISIONING_INITIALIZING);
    }

    @Test
    public void onReturnFromProvisioning_stateIsProvisioningFinalized() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onReturnFromProvisioning();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_PROVISIONING_FINALIZED);
                });
    }

    @Test
    public void onAdminIntegratedFlowInitiated_stateIsGettingProvisioningMode() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onAdminIntegratedFlowInitiated();
                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_GETTING_PROVISIONING_MODE);
                });
    }

    @Test
    public void onShowUserConsent_stateIsShowingUserConsent() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onShowUserConsent();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_SHOWING_USER_CONSENT);
                });
    }

    @Test
    public void onProvisioningStartedAfterUserConsent_stateIsProvisioningStarted() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onProvisioningStartedAfterUserConsent();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_PROVISIONING_STARTED);
                });
    }

    @Test
    public void onProvisioningInitiated_stateIsProvisioningInitialized() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onPlatformProvisioningInitiated();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_PREPROVISIONING_INITIALIZED);
                });
    }

    @Test
    public void getEncryptionController_valuesAreEqual() {
        assertThat(mViewModel.getEncryptionController())
                .isEqualTo(mEncryptionController);
    }

    @Test
    public void getTimeLogger_valuesAreEqual() {
        assertThat(mViewModel.getTimeLogger())
                .isEqualTo(mTimeLogger);
    }

    @Test
    public void loadParamsIfNecessary_invalidParams_throwsException() {
        Intent invalidIntent = new Intent();

        assertThrows(
                IllegalProvisioningArgumentException.class,
                () -> mViewModel.loadParamsIfNecessary(invalidIntent));
    }

    @Test
    public void canRetryRoleHolderUpdate_noTries_works() {
        mViewModel.incrementRoleHolderUpdateRetryCount();

        assertThat(mViewModel.canRetryRoleHolderUpdate()).isTrue();
    }

    @Test
    public void canRetryRoleHolderUpdate_threeTries_isFalse() {
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();

        assertThat(mViewModel.canRetryRoleHolderUpdate()).isFalse();
    }

    @Test
    public void canRetryRoleHolderUpdate_resetAfterMaxTries_isTrue() {
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.resetRoleHolderUpdateRetryCount();

        assertThat(mViewModel.canRetryRoleHolderUpdate()).isTrue();
    }

    @Test
    public void canRetryRoleHolderUpdate_resetAndDoMaxRetries_isFalse() {
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.resetRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();
        mViewModel.incrementRoleHolderUpdateRetryCount();

        assertThat(mViewModel.canRetryRoleHolderUpdate()).isFalse();
    }

    @Test
    public void onRoleHolderUpdateInitiated_works() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onRoleHolderUpdateInitiated();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_UPDATING_ROLE_HOLDER);
                });
    }

    @Test
    public void onRoleHolderProvisioningInitiated_works() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.onRoleHolderProvisioningInitiated();

                    assertThat(mViewModel.getState().getValue())
                            .isEqualTo(STATE_ROLE_HOLDER_PROVISIONING);
                });
    }

    @Test
    public void setRoleHolderState_works() {
        mViewModel.setRoleHolderState(ROLE_HOLDER_STATE);

        assertBundlesEqual(mViewModel.getRoleHolderState(), ROLE_HOLDER_STATE);
    }

    @Test
    public void getRoleHolderState_modifySetState_isImmutable() {
        PersistableBundle bundle = new PersistableBundle(ROLE_HOLDER_STATE);

        mViewModel.setRoleHolderState(bundle);
        bundle.putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(mViewModel.getRoleHolderState(), ROLE_HOLDER_STATE);
    }

    @Test
    public void getRoleHolderState_modifyGetState_isImmutable() {
        PersistableBundle bundle = new PersistableBundle(ROLE_HOLDER_STATE);

        mViewModel.setRoleHolderState(bundle);
        mViewModel.getRoleHolderState().putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(mViewModel.getRoleHolderState(), ROLE_HOLDER_STATE);
    }

    private static PersistableBundle createRoleHolderStateBundle() {
        PersistableBundle result = new PersistableBundle();
        result.putString("key1", "value1");
        result.putInt("key2", 2);
        result.putBoolean("key3", true);
        return result;
    }
}

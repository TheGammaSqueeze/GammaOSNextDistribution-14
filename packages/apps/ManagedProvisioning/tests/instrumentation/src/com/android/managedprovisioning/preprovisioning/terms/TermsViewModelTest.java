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

package com.android.managedprovisioning.preprovisioning.terms;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.util.DeviceHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

@SmallTest
public class TermsViewModelTest {

    private static final String HEADER_1 = "header1";
    private static final String HEADER_2 = "header2";
    private static final int TERM_POSITION = 1;
    private static final int TERM_POSITION_OUT_OF_BOUNDS = 1000;
    private static final DisclaimersParam DISCLAIMERS =
            new DisclaimersParam.Builder()
                    .setDisclaimers(new DisclaimersParam.Disclaimer[]{
                            new DisclaimersParam.Disclaimer(
                                    HEADER_1,
                                    "content://test1"),
                            new DisclaimersParam.Disclaimer(
                                    HEADER_2,
                                    "content://test2")
                    })
                    .build();
    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final String TEST_PACKAGE = "com.android.managedprovisioning.tests";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final ProvisioningParams PARAMS_MANAGED_DEVICE =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                    .setDeviceAdminComponentName(ADMIN)
                    .build();
    private static final ProvisioningParams PARAMS_MANAGED_PROFILE =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                    .setDeviceAdminComponentName(ADMIN)
                    .build();
    private static final ProvisioningParams PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                    .setDisclaimersParam(DISCLAIMERS)
                    .setDeviceAdminComponentName(ADMIN)
                    .build();

    private final StoreUtils.TextFileReader mTextFileReader = file -> "terms content";
    private final Utils mUtils = new Utils();

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final CharSequence TEST_DEVICE_NAME = DeviceHelper.getDeviceName(mContext);

    @Test
    public void getTerms_hasExpectedResults() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        final List<TermsDocument> terms = viewModel.getTerms();

        assertThat(terms.get(0).getHeading()).isEqualTo(HEADER_1);
        assertThat(terms.get(1).getHeading()).isEqualTo(HEADER_2);
        assertThat(terms).hasSize(2);
    }

    @Test
    public void getGeneralDisclaimer_deviceOwner_hasExpectedContent() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE);

        final TermsDocument generalDisclaimer = viewModel.getGeneralDisclaimer();

        assertThat(generalDisclaimer.getHeading())
                .isEqualTo(mContext.getString(R.string.managed_device_info));
        assertThat(generalDisclaimer.getContent())
                .isEqualTo(mContext.getString(
                        R.string.admin_has_ability_to_monitor_device, TEST_DEVICE_NAME));
    }

    @Test
    public void getGeneralDisclaimer_workProfile_hasExpectedContent() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_PROFILE);

        final TermsDocument generalDisclaimer = viewModel.getGeneralDisclaimer();

        assertThat(generalDisclaimer.getHeading())
                .isEqualTo(mContext.getString(R.string.work_profile_info));
        assertThat(generalDisclaimer.getContent())
                .isEqualTo(mContext.getString(R.string.admin_has_ability_to_monitor_profile));
    }

    @Test
    public void setTermExpanded_works() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        viewModel.setTermExpanded(TERM_POSITION, /* expanded= */ true);

        assertThat(viewModel.isTermExpanded(TERM_POSITION)).isTrue();
    }

    @Test
    public void setTermExpanded_invalidPosition_throwsException() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        assertThrows(IllegalArgumentException.class,
                () -> viewModel.setTermExpanded(TERM_POSITION_OUT_OF_BOUNDS, /* expanded= */ true));
    }

    @Test
    public void isTermExpanded_defaultsToFalse() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        assertThat(viewModel.isTermExpanded(TERM_POSITION)).isFalse();
    }

    @Test
    public void isTermExpanded_invalidPosition_throwsException() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        assertThrows(IllegalArgumentException.class,
                () -> viewModel.isTermExpanded(TERM_POSITION_OUT_OF_BOUNDS));
    }

    @Test
    public void getNumberOfReadTerms_works() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        viewModel.setTermExpanded(TERM_POSITION, /* expanded= */ true);

        assertThat(viewModel.getNumberOfReadTerms()).isEqualTo(1);
    }

    @Test
    public void getNumberOfReadTerms_defaultsToZero() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        assertThat(viewModel.getNumberOfReadTerms()).isEqualTo(0);
    }

    @Test
    public void getNumberOfReadTerms_toggleDisclaimer_retainsReadState() {
        TermsViewModel viewModel = createViewModel(PARAMS_MANAGED_DEVICE_WITH_DISCLAIMERS);

        viewModel.setTermExpanded(TERM_POSITION, /* expanded= */ true);
        viewModel.setTermExpanded(TERM_POSITION, /* expanded= */ false);

        assertThat(viewModel.getNumberOfReadTerms()).isEqualTo(1);
    }

    @NonNull
    private TermsViewModel createViewModel(ProvisioningParams params) {
        TermsProvider termsProvider = new TermsProvider(
                mContext, mTextFileReader, params, mUtils, ArrayList::new);
        return new TermsViewModel(termsProvider);
    }
}

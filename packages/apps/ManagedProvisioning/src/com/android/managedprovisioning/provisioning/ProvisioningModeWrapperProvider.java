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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;

import static java.util.Objects.requireNonNull;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;
import com.android.managedprovisioning.util.LazyStringResource;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Utility class to get the corresponding wrapper that contains provisioning transition content.
 */
public class ProvisioningModeWrapperProvider {
    private final ProvisioningParams mParams;

    public ProvisioningModeWrapperProvider(ProvisioningParams params) {
        this.mParams = requireNonNull(params);
    }

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_WRAPPER =
            new ProvisioningModeWrapper(
                    ImmutableList.of(
                            new TransitionScreenWrapper(
                                    R.string.work_profile_provisioning_step_1_header,
                                    R.raw.separate_work_and_personal_animation),
                            new TransitionScreenWrapper(
                                    R.string.work_profile_provisioning_step_2_header,
                                    R.raw.pause_work_apps_animation),
                            new TransitionScreenWrapper(
                                    R.string.work_profile_provisioning_step_3_header,
                                    R.raw.not_private_animation)),
                    R.string.work_profile_provisioning_summary);

    /**
     * Return default provisioning mode wrapper depending on provisioning parameter.
     */
    public ProvisioningModeWrapper getProvisioningModeWrapper(
            @ProvisioningMode int provisioningMode,
            CharSequence deviceName) {
        switch (provisioningMode) {
            case PROVISIONING_MODE_WORK_PROFILE:
                return WORK_PROFILE_WRAPPER;
            case PROVISIONING_MODE_FULLY_MANAGED_DEVICE:
                return getProvisioningModeWrapperForFullyManaged(deviceName);
            case PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE:
                return getProvisioningModeWrapperForCope(deviceName);
            case PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE:
                // fall through
        }
        throw new IllegalStateException("Unexpected provisioning mode " + provisioningMode);
    }

    @VisibleForTesting
    private ProvisioningModeWrapper getProvisioningModeWrapperForCope(CharSequence deviceName) {
        return new ProvisioningModeWrapper(
                ImmutableList.of(
                        new TransitionScreenWrapper(
                                R.string.cope_provisioning_step_1_header,
                                R.raw.separate_work_and_personal_animation),
                        new TransitionScreenWrapper(
                                R.string.cope_provisioning_step_2_header,
                                /* descriptionId= */ 0,
                                R.raw.personal_apps_separate_hidden_from_work_animation,
                                /* shouldLoop= */ false),
                        new TransitionScreenWrapper(
                                R.string.cope_provisioning_step_3_header,
                                R.raw.it_admin_control_device_block_apps_animation)),
                LazyStringResource.of(R.string.cope_provisioning_summary, deviceName));
    }

    /**
     * Return the provisioning mode wrapper for a fully-managed device.
     * The second screen, as well as the accessible summary, will be different, depending on whether
     * the admin can grant sensors-related permissions on this device or not.
     */
    @VisibleForTesting
    private ProvisioningModeWrapper getProvisioningModeWrapperForFullyManaged(
            CharSequence deviceName) {
        final LazyStringResource provisioningSummary;
        TransitionScreenWrapper.Builder secondScreenBuilder =
                new TransitionScreenWrapper.Builder()
                        .setHeader(
                                LazyStringResource.of(
                                        R.string.fully_managed_device_provisioning_step_2_header,
                                        deviceName));

        // Admin cannot grant sensors permissions
        if (!mParams.deviceOwnerPermissionGrantOptOut) {
            var subHeader = LazyStringResource.of(
                    R.string.fully_managed_device_provisioning_permissions_subheader,
                    deviceName);
            var secondarySubHeader = LazyStringResource.of(
                    R.string.fully_managed_device_provisioning_permissions_secondary_subheader,
                    deviceName);
            secondScreenBuilder
                    .setSubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_header)
                    .setSubHeader(subHeader)
                    .setSubHeaderIcon(R.drawable.ic_history)
                    .setSecondarySubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_secondary_header)
                    .setSecondarySubHeader(secondarySubHeader)
                    .setSecondarySubHeaderIcon(R.drawable.ic_perm_device_information)
                    .setShouldLoop(true);
            provisioningSummary = LazyStringResource.of(
                    R.string.fully_managed_device_with_permission_control_provisioning_summary,
                    deviceName);
        } else {
            provisioningSummary =
                    LazyStringResource.of(R.string.fully_managed_device_provisioning_summary,
                            deviceName);
            secondScreenBuilder
                    .setDescription(LazyStringResource.of(
                            R.string.fully_managed_device_provisioning_step_2_subheader,
                            deviceName))
                    .setAnimation(R.raw.not_private_animation);
        }

        TransitionScreenWrapper firstScreen =
                new TransitionScreenWrapper(
                        R.string.fully_managed_device_provisioning_step_1_header,
                        R.raw.connect_on_the_go_animation);
        return new ProvisioningModeWrapper(
                ImmutableList.of(firstScreen, secondScreenBuilder.build()), provisioningSummary);
    }

    static final class ProvisioningModeWrapper {
        final List<TransitionScreenWrapper> mTransitions;
        final LazyStringResource mSummary;

        ProvisioningModeWrapper(List<TransitionScreenWrapper> transitions,
                                LazyStringResource summary) {
            this.mTransitions = requireNonNull(transitions);
            this.mSummary = summary;
        }

        ProvisioningModeWrapper(List<TransitionScreenWrapper> transitions,
                                @StringRes int summaryId) {
            this(transitions, LazyStringResource.of(summaryId));
        }
    }
}

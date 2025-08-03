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

import static android.app.admin.DevicePolicyManager.ACTION_ESTABLISH_NETWORK_CONNECTION;
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
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class with methods related to parsing the provisioning extras
 */
public class ParserUtils {

    private static final ArraySet<Integer> ALLOWED_COMBINATIONS = new ArraySet<>();
    {
        ALLOWED_COMBINATIONS.add(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED);
        ALLOWED_COMBINATIONS.add(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
        ALLOWED_COMBINATIONS.add(FLAG_SUPPORTED_MODES_DEVICE_OWNER);
        ALLOWED_COMBINATIONS.add(
                FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED);
    }

    /**
     * Returns the provisioning trigger supplied in the provisioning extras only if it was supplied
     * alongside the {@link DevicePolicyManager#ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}
     * intent action. Otherwise it returns {@link
     * DevicePolicyManager#PROVISIONING_TRIGGER_UNSPECIFIED}.
     */
    int extractProvisioningTrigger(Intent intent) {
        if (!ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(intent.getAction())) {
            return PROVISIONING_TRIGGER_UNSPECIFIED;
        }
        return intent.getIntExtra(
                EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    /**
     * Translates a given managed provisioning intent to its corresponding provisioning flow, using
     * the action from the intent.
     *
     * <p>This is necessary because, unlike other provisioning actions which has 1:1 mapping, there
     * are multiple actions that can trigger the device owner provisioning flow. This includes
     * {@link ACTION_PROVISION_MANAGED_DEVICE}, {@link ACTION_NDEF_DISCOVERED} and
     * {@link ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}. These 3 actions are equivalent
     * except they are sent from a different source.
     *
     * @return the appropriate DevicePolicyManager declared action for the given incoming intent.
     * @throws IllegalProvisioningArgumentException if intent is malformed
     */
    String extractProvisioningAction(Intent intent,
            SettingsFacade settingsFacade, Context context)
            throws IllegalProvisioningArgumentException {
        if (intent == null || intent.getAction() == null) {
            throw new IllegalProvisioningArgumentException("Null intent action.");
        }

        // Map the incoming intent to a DevicePolicyManager.ACTION_*, as there is a N:1 mapping in
        // some cases.
        switch (intent.getAction()) {
            // Trivial cases.
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_PROFILE:
            case ACTION_PROVISION_FINANCED_DEVICE:
                return intent.getAction();

            // Silent device owner is same as device owner.
            case ACTION_PROVISION_MANAGED_DEVICE_SILENTLY:
                return ACTION_PROVISION_MANAGED_DEVICE;

            // NFC cases which need to take mime-type into account.
            case ACTION_NDEF_DISCOVERED:
                String mimeType = intent.getType();
                if (mimeType == null) {
                    throw new IllegalProvisioningArgumentException(
                            "Unknown NFC bump mime-type: " + mimeType);
                }
                switch (mimeType) {
                    case MIME_TYPE_PROVISIONING_NFC:
                        return ACTION_PROVISION_MANAGED_DEVICE;

                    default:
                        throw new IllegalProvisioningArgumentException(
                                "Unknown NFC bump mime-type: " + mimeType);
                }
            case ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE:
                return settingsFacade.isDuringSetupWizard(context)
                        ? ACTION_PROVISION_MANAGED_DEVICE
                        : ACTION_PROVISION_MANAGED_PROFILE;
            case ACTION_ESTABLISH_NETWORK_CONNECTION:
                return ACTION_ESTABLISH_NETWORK_CONNECTION;
            default:
                throw new IllegalProvisioningArgumentException("Unknown intent action "
                        + intent.getAction());
        }
    }

    /**
     * Returns an {@link ArrayList} containing the allowed provisioning modes that can be returned
     * by the DPC for the admin-integrated flow.
     *
     * <p>The array will be a subset of {{@link
     * DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE}, {@link
     * DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE}, {@link
     * DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE}}.
     *
     * {@code supportedModes} is a validated value passed by the provisioning
     * initiator via the {@link DevicePolicyManager#EXTRA_PROVISIONING_SUPPORTED_MODES} extra.
     * Its value can be a combination of {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED}, {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_PERSONALLY_OWNED}, {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_DEVICE_OWNER} or {@link
     * com.android.managedprovisioning.model.ProvisioningParams
     * #DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES} if the value is not passed
     * as part of {@link DevicePolicyManager#ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}.
     *
     * <p>If the intent action is not {@link
     * DevicePolicyManager#ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}, an empty array
     * is returned, since the result is only relevant to the admin-integrated flow.
     *
     * <p>If {@link DevicePolicyManager#EXTRA_PROVISIONING_SUPPORTED_MODES} is not provided,
     * {@link DevicePolicyManager#FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED} is used as default.
     *
     * <ul>
     *     <li>
     *         If only organization-owned provisioning is supported, allow
     *         {@link DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE} and
     *         {@link DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE} on an organization
     *         -owned device.
     *     </li>
     *     <li>
     *          If only personally-owned provisioning is supported, allow just
     *          {@link DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE} on a
     *          personally-owned device.
     *     </li>
     *     <li>
     *          If both are supported, allow
     *          {@link DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE},
     *          {@link DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE} on an
     *          organization-owned device, and {@link
     *          DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE} for a
     *          managed profile on a personally-owned device.
     *     </li>
     *     <li>
     *         If {@link DevicePolicyManager#FLAG_SUPPORTED_MODES_DEVICE_OWNER} is used, allow just
     *         {@link DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE}.
     *     </li>
     * </ul>
     *
     * <p>The device serial number and IMEI wil be sent to the DPC with the
     * {@link DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE} and
     * {@link DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE} extras
     * only in the first case when organization-owned provisioning is the only ownership model
     * supported.
     */
    /*
     The method's return type is ArrayList because the result is passed to a Bundle} object via its
     Bundle#putIntegerArrayList method. This is done to avoid using Bundle#putSerializable with
     HashSet which is not recommended.
     */
    public ArrayList<Integer> getAllowedProvisioningModes(Context context,
            int supportedModes, Utils utils) {
        if (supportedModes == DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES) {
            ProvisionLogger.logi("Not admin-integrated flow, "
                    + "no allowed provisioning modes necessary.");
            return new ArrayList<>();
        }
        validateSupportedModes(supportedModes);
        ArrayList<Integer> result = new ArrayList<>();
        if (utils.containsBinaryFlags(supportedModes, FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)) {
            result.addAll(List.of(
                    PROVISIONING_MODE_MANAGED_PROFILE,
                    PROVISIONING_MODE_FULLY_MANAGED_DEVICE));
            if (utils.containsBinaryFlags(supportedModes, FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)) {
                result.add(PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE);
            }
        } else if (utils.containsBinaryFlags(
                supportedModes, FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)) {
            result.addAll(List.of(
                    PROVISIONING_MODE_MANAGED_PROFILE));
        } else if (utils.containsBinaryFlags(supportedModes, FLAG_SUPPORTED_MODES_DEVICE_OWNER)) {
            result.addAll(List.of(
                    PROVISIONING_MODE_FULLY_MANAGED_DEVICE));
        }
        ProvisionLogger.logi("Allowed provisioning modes before checking for managed users "
                + "support: " + result);
        boolean supportsManagedUsers = supportsManagedUsers(context);
        if (!supportsManagedUsers) {
            result.removeAll(List.of(
                    PROVISIONING_MODE_MANAGED_PROFILE,
                    PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE));
        }
        ProvisionLogger.logi("Supports managed users: " + supportsManagedUsers);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No available supported provisioning modes. Requested support mode was "
                            + supportedModes);
        }
        ProvisionLogger.logi("Allowed provisioning modes: " + result);
        return result;
    }

    /**
     * Throws {@link IllegalArgumentException} if {@code supportedModes} contains an
     * unsupported binary flag combination.
     *
     * @see DevicePolicyManager#FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED
     * @see DevicePolicyManager#FLAG_SUPPORTED_MODES_PERSONALLY_OWNED
     * @see DevicePolicyManager#FLAG_SUPPORTED_MODES_DEVICE_OWNER
     */
    public void validateSupportedModes(int supportedModes) {
        if (!ALLOWED_COMBINATIONS.contains(supportedModes)) {
            throw new IllegalArgumentException(
                    "Supported modes flag combination not supported. Supported modes: "
                            + supportedModes);
        }
    }

    private boolean supportsManagedUsers(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
    }
}

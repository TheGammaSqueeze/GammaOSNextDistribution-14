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

package com.android.managedprovisioning;

import static com.android.managedprovisioning.ManagedProvisioningScreens.ADMIN_INTEGRATED_PREPARE;
import static com.android.managedprovisioning.ManagedProvisioningScreens.DOWNLOAD_ROLE_HOLDER;
import static com.android.managedprovisioning.ManagedProvisioningScreens.ENCRYPT;
import static com.android.managedprovisioning.ManagedProvisioningScreens.ESTABLISH_NETWORK_CONNECTION;
import static com.android.managedprovisioning.ManagedProvisioningScreens.FINALIZATION_INSIDE_SUW;
import static com.android.managedprovisioning.ManagedProvisioningScreens.FINANCED_DEVICE_LANDING;
import static com.android.managedprovisioning.ManagedProvisioningScreens.LANDING;
import static com.android.managedprovisioning.ManagedProvisioningScreens.POST_ENCRYPT;
import static com.android.managedprovisioning.ManagedProvisioningScreens.PRE_PROVISIONING;
import static com.android.managedprovisioning.ManagedProvisioningScreens.PRE_PROVISIONING_VIA_NFC;
import static com.android.managedprovisioning.ManagedProvisioningScreens.PROVISIONING;
import static com.android.managedprovisioning.ManagedProvisioningScreens.RESET_AND_RETURN_DEVICE;
import static com.android.managedprovisioning.ManagedProvisioningScreens.RESET_DEVICE;
import static com.android.managedprovisioning.ManagedProvisioningScreens.RETRY_LAUNCH;
import static com.android.managedprovisioning.ManagedProvisioningScreens.TERMS;
import static com.android.managedprovisioning.ManagedProvisioningScreens.WEB;

import android.app.Activity;

import com.android.managedprovisioning.common.RetryLaunchActivity;
import com.android.managedprovisioning.finalization.FinalizationInsideSuwActivity;
import com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionActivity;
import com.android.managedprovisioning.preprovisioning.DownloadRoleHolderActivity;
import com.android.managedprovisioning.preprovisioning.EncryptDeviceActivity;
import com.android.managedprovisioning.preprovisioning.PostEncryptionActivity;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivity;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivityViaNfc;
import com.android.managedprovisioning.preprovisioning.WebActivity;
import com.android.managedprovisioning.preprovisioning.terms.TermsActivity;
import com.android.managedprovisioning.provisioning.AdminIntegratedFlowPrepareActivity;
import com.android.managedprovisioning.provisioning.FinancedDeviceLandingActivity;
import com.android.managedprovisioning.provisioning.LandingActivity;
import com.android.managedprovisioning.provisioning.ProvisioningActivity;
import com.android.managedprovisioning.provisioning.ResetAndReturnDeviceActivity;
import com.android.managedprovisioning.provisioning.ResetDeviceActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * A manager for the mapping between {@link ManagedProvisioningScreens} and {@link Activity}.
 *
 * <p>By default, all {@link ManagedProvisioningScreens} map to the base {@code ManagedProvisioning}
 * screens. {@code ManagedProvisioning} inheritors can call {@link
 * #setOverrideActivity(ManagedProvisioningScreens, Class)} if they want the screens to map to
 * their own {@link Activity} implementations.
 */
public final class ScreenManager {
    static final Map<ManagedProvisioningScreens, Class<? extends Activity>>
            DEFAULT_SCREEN_TO_ACTIVITY_MAP = createBaseScreenMap();

    private final Map<ManagedProvisioningScreens, Class<? extends Activity>> mScreenToActivityMap;

    ScreenManager(Map<ManagedProvisioningScreens, Class<? extends Activity>> screenToActivityMap) {
        mScreenToActivityMap = new HashMap<>(screenToActivityMap);
        validateEachScreenHasMapping();
    }

    /**
     * Maps the provided {@code screen} to the provided {@code activityClass}.
     *
     * <p>When ManagedProvisioning wants to launch any of the screens in {@link
     * ManagedProvisioningScreens}, instead of its base {@link Activity} implementation, it will
     * launch the class provided here.
     */
    public void setOverrideActivity(ManagedProvisioningScreens screen,
            Class<? extends Activity> newClass) {
        mScreenToActivityMap.put(screen, newClass);
    }

    /**
     * Retrieves the {@link Activity} class associated with the provided {@code screen}.
     *
     * <p>If no screens were set via {@link #setOverrideActivity(ManagedProvisioningScreens,
     * Class)}, the base ManagedProvisioning {@link Activity} implementation will be returned.
     */
    public Class<? extends Activity> getActivityClassForScreen(ManagedProvisioningScreens screen) {
        return mScreenToActivityMap.get(screen);
    }

    private static Map<ManagedProvisioningScreens, Class<? extends Activity>>
            createBaseScreenMap() {
        Map<ManagedProvisioningScreens, Class<? extends Activity>> map =
                new HashMap<>(ManagedProvisioningScreens.values().length);
        map.put(LANDING, LandingActivity.class);
        map.put(PRE_PROVISIONING, PreProvisioningActivity.class);
        map.put(PRE_PROVISIONING_VIA_NFC, PreProvisioningActivityViaNfc.class);
        map.put(PROVISIONING, ProvisioningActivity.class);
        map.put(ADMIN_INTEGRATED_PREPARE, AdminIntegratedFlowPrepareActivity.class);
        map.put(RESET_AND_RETURN_DEVICE, ResetAndReturnDeviceActivity.class);
        map.put(RESET_DEVICE, ResetDeviceActivity.class);
        map.put(WEB, WebActivity.class);
        map.put(ENCRYPT, EncryptDeviceActivity.class);
        map.put(POST_ENCRYPT, PostEncryptionActivity.class);
        map.put(FINALIZATION_INSIDE_SUW, FinalizationInsideSuwActivity.class);
        map.put(TERMS, TermsActivity.class);
        map.put(FINANCED_DEVICE_LANDING, FinancedDeviceLandingActivity.class);
        map.put(RETRY_LAUNCH, RetryLaunchActivity.class);
        map.put(DOWNLOAD_ROLE_HOLDER, DownloadRoleHolderActivity.class);
        map.put(ESTABLISH_NETWORK_CONNECTION, EstablishNetworkConnectionActivity.class);
        return map;
    }

    /**
     * Makes sure that each entry in {@link ManagedProvisioningScreens} has a corresponding entry
     * in the map.
     */
    private void validateEachScreenHasMapping() {
        for (ManagedProvisioningScreens screen : ManagedProvisioningScreens.values()) {
            if (!mScreenToActivityMap.containsKey(screen)) {
                throw new IllegalStateException("Screen " + screen + " has no mapping.");
            }
        }
    }
}

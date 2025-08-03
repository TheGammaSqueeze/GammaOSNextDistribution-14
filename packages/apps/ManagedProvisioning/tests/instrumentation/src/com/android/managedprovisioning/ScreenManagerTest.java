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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.RetryLaunchActivity;
import com.android.managedprovisioning.finalization.FinalizationForwarderActivity;
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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SmallTest
public final class ScreenManagerTest {
    private static final int EXPECTED_NUMBER_OF_SCREENS = 16;
    private static final Map<ManagedProvisioningScreens, Class<? extends Activity>>
            TEST_SCREEN_TO_ACTIVITY_MAP = createTestScreenToActivityMap();
    private static final Map<ManagedProvisioningScreens, Class<? extends Activity>>
            TEST_INVALID_SCREEN_TO_ACTIVITY_MAP = createInvalidTestScreenToActivityMap();
    private static final Set<String> NON_OVERRIDABLE_ACTIVITIES = new HashSet<>(Arrays.asList(
            // The following activity aliases target PreProvisioningActivity
            "com.android.managedprovisioning.PreProvisioningActivityAfterEncryption",
            "com.android.managedprovisioning.PreProvisioningActivityViaTrustedApp",
            "com.android.managedprovisioning.PreProvisioningActivityViaNfc",
            FinalizationForwarderActivity.class.getName(),
            TrampolineActivity.class.getName()
    ));

    private final Context mContext = InstrumentationRegistry.getTargetContext();

    @Test
    public void getActivityClassForScreen_withDefaultMap_success() {
        ScreenManager screenManager =
                new ScreenManager(ScreenManager.DEFAULT_SCREEN_TO_ACTIVITY_MAP);

        assertThat(screenManager.getActivityClassForScreen(LANDING))
                .isEqualTo(LandingActivity.class);
        assertThat(screenManager.getActivityClassForScreen(PRE_PROVISIONING))
                .isEqualTo(PreProvisioningActivity.class);
        assertThat(screenManager.getActivityClassForScreen(PRE_PROVISIONING_VIA_NFC))
                .isEqualTo(PreProvisioningActivityViaNfc.class);
        assertThat(screenManager.getActivityClassForScreen(PROVISIONING))
                .isEqualTo(ProvisioningActivity.class);
        assertThat(screenManager.getActivityClassForScreen(ADMIN_INTEGRATED_PREPARE))
                .isEqualTo(AdminIntegratedFlowPrepareActivity.class);
        assertThat(screenManager.getActivityClassForScreen(RESET_AND_RETURN_DEVICE))
                .isEqualTo(ResetAndReturnDeviceActivity.class);
        assertThat(screenManager.getActivityClassForScreen(RESET_DEVICE))
                .isEqualTo(ResetDeviceActivity.class);
        assertThat(screenManager.getActivityClassForScreen(WEB))
                .isEqualTo(WebActivity.class);
        assertThat(screenManager.getActivityClassForScreen(ENCRYPT))
                .isEqualTo(EncryptDeviceActivity.class);
        assertThat(screenManager.getActivityClassForScreen(POST_ENCRYPT))
                .isEqualTo(PostEncryptionActivity.class);
        assertThat(screenManager.getActivityClassForScreen(FINALIZATION_INSIDE_SUW))
                .isEqualTo(FinalizationInsideSuwActivity.class);
        assertThat(screenManager.getActivityClassForScreen(TERMS))
                .isEqualTo(TermsActivity.class);
        assertThat(screenManager.getActivityClassForScreen(FINANCED_DEVICE_LANDING))
                .isEqualTo(FinancedDeviceLandingActivity.class);
        assertThat(screenManager.getActivityClassForScreen(RETRY_LAUNCH))
                .isEqualTo(RetryLaunchActivity.class);
        assertThat(screenManager.getActivityClassForScreen(DOWNLOAD_ROLE_HOLDER))
                .isEqualTo(DownloadRoleHolderActivity.class);
        assertThat(screenManager.getActivityClassForScreen(ESTABLISH_NETWORK_CONNECTION))
                .isEqualTo(EstablishNetworkConnectionActivity.class);
    }

    @Test
    public void defaultScreenToActivityMap_hasExpectedSize() {
        assertThat(ScreenManager.DEFAULT_SCREEN_TO_ACTIVITY_MAP.size())
                .isEqualTo(EXPECTED_NUMBER_OF_SCREENS);
    }

    @Test
    public void getActivityClassForScreen_withCustomMap_success() {
        ScreenManager screenManager = new ScreenManager(TEST_SCREEN_TO_ACTIVITY_MAP);

        assertThat(screenManager.getActivityClassForScreen(LANDING)).isEqualTo(Activity.class);
    }

    @Test
    public void setOverrideActivity_success() {
        ScreenManager screenManager =
                new ScreenManager(ScreenManager.DEFAULT_SCREEN_TO_ACTIVITY_MAP);

        screenManager.setOverrideActivity(
                ManagedProvisioningScreens.LANDING,
                Activity.class);

        assertThat(screenManager.getActivityClassForScreen(LANDING)).isEqualTo(Activity.class);
    }

    @Test
    public void constructObject_withInvalidMap_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> new ScreenManager(TEST_INVALID_SCREEN_TO_ACTIVITY_MAP));
    }

    @Test
    public void verifyEveryActivityIsMapped() throws PackageManager.NameNotFoundException {
        List<String> activities = getManagedProvisioningActivityNames();

        List<String> unmappedActivities = getUnmappedOverridableActivities(activities);

        assertThat(unmappedActivities).isEmpty();
    }

    private List<String> getUnmappedOverridableActivities(List<String> activities) {
        return activities
                .stream()
                .filter(activityName -> !isActivityMapped(activityName))
                .filter(this::isActivityOverridable)
                .collect(Collectors.toList());
    }

    private boolean isActivityOverridable(String activityName) {
        return !NON_OVERRIDABLE_ACTIVITIES.contains(activityName);
    }

    private boolean isActivityMapped(String activityName) {
        Collection<Class<? extends Activity>> mappedActivities =
                ScreenManager.DEFAULT_SCREEN_TO_ACTIVITY_MAP.values();
        return mappedActivities
                .stream()
                .anyMatch(activityClass -> activityClass.getName().equals(activityName));
    }

    private List<String> getManagedProvisioningActivityNames()
            throws PackageManager.NameNotFoundException {
        PackageManager packageManager = mContext.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(
                mContext.getPackageName(),
                PackageManager.GET_ACTIVITIES);
        return Arrays.stream(packageInfo.activities)
                .map(activityInfo -> activityInfo.name)
                .filter(name -> !Objects.equals(name, "com.android.onboarding.bedsteadonboarding.activities.TrampolineActivity"))
                .collect(Collectors.toList());
    }

    private static Map<ManagedProvisioningScreens, Class<? extends Activity>>
    createTestScreenToActivityMap() {
        Map<ManagedProvisioningScreens, Class<? extends Activity>> map = new HashMap<>();
        map.put(LANDING, Activity.class);
        map.put(PRE_PROVISIONING, Activity.class);
        map.put(PRE_PROVISIONING_VIA_NFC, Activity.class);
        map.put(PROVISIONING, Activity.class);
        map.put(ADMIN_INTEGRATED_PREPARE, Activity.class);
        map.put(RESET_AND_RETURN_DEVICE, Activity.class);
        map.put(RESET_DEVICE, Activity.class);
        map.put(WEB, Activity.class);
        map.put(ENCRYPT, Activity.class);
        map.put(POST_ENCRYPT, Activity.class);
        map.put(FINALIZATION_INSIDE_SUW, Activity.class);
        map.put(TERMS, Activity.class);
        map.put(FINANCED_DEVICE_LANDING, Activity.class);
        map.put(RETRY_LAUNCH, Activity.class);
        map.put(DOWNLOAD_ROLE_HOLDER, Activity.class);
        map.put(ESTABLISH_NETWORK_CONNECTION, Activity.class);
        return map;
    }

    private static Map<ManagedProvisioningScreens, Class<? extends Activity>>
    createInvalidTestScreenToActivityMap() {
        return Map.of(LANDING, LandingActivity.class);
    }
}

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

package com.android.car.settings.accessibility;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.view.accessibility.AccessibilityManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceCategory;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ScreenReaderCategoryPreferenceControllerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private PreferenceCategory mPreference;
    private PreferenceControllerTestHelper<ScreenReaderCategoryPreferenceController>
            mPreferenceControllerHelper;
    private ShadowAccessibilityManager mShadowAccessibilityManager;
    private ComponentName mScreenReaderComponent;

    @Before
    public void setUp() {
        mPreference = new PreferenceCategory(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        ScreenReaderCategoryPreferenceController.class, mPreference);
        mShadowAccessibilityManager = Shadows.shadowOf(
                mContext.getSystemService(AccessibilityManager.class));
        mScreenReaderComponent = ComponentName.unflattenFromString(
                mContext.getString(R.string.config_default_screen_reader));
    }

    @Test
    public void testGetAvailability_screenReaderInstalled_isAvailable() throws Exception {
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                List.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                AVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderInstalled_zoneWrite() throws Exception {
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                List.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("write");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                AVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderInstalled_zoneRead() throws Exception {
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                List.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("read");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailability_screenReaderInstalled_zoneHidden() throws Exception {
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                List.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("hidden");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderNotInstalled_isUnavailable() {
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(List.of());

        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderNotInstalled_isUnavailable_zoneWrite() {
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(List.of());

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("write");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderNotInstalled_isUnavailable_zoneRead() {
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(List.of());

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("read");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailability_screenReaderNotInstalled_isUnavailable_zoneHidden() {
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(List.of());

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("hidden");
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }
}

/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.view.accessibility.AccessibilityManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class ScreenReaderSettingsIntentPreferenceControllerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    ShadowAccessibilityManager mShadowAccessibilityManager;
    private ComponentName mScreenReaderComponent;
    private PreferenceControllerTestHelper<ScreenReaderSettingsIntentPreferenceController>
            mPreferenceControllerHelper;
    private Preference mPreference;

    @Before

    public void setUp() {
        mPreference = new Preference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        ScreenReaderSettingsIntentPreferenceController.class,
                        mPreference);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mScreenReaderComponent = ComponentName.unflattenFromString(
                mContext.getString(R.string.config_default_screen_reader));
        mShadowAccessibilityManager = Shadows.shadowOf(mContext.getSystemService(
                AccessibilityManager.class));
    }

    @Test
    public void testUpdateState_noSettingsActivity_hidesSettings()
            throws XmlPullParserException, IOException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.labelRes = R.string.fake_title;
        resolveInfo.nonLocalizedLabel = mContext.getString(R.string.fake_title);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.name = mScreenReaderComponent.getPackageName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                ImmutableList.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState_noSettingsActivity_hidesSettings_zoneWrite()
            throws XmlPullParserException, IOException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.labelRes = R.string.fake_title;
        resolveInfo.nonLocalizedLabel = mContext.getString(R.string.fake_title);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.name = mScreenReaderComponent.getPackageName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                ImmutableList.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("write");
        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState_noSettingsActivity_hidesSettings_zoneRead()
            throws XmlPullParserException, IOException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.labelRes = R.string.fake_title;
        resolveInfo.nonLocalizedLabel = mContext.getString(R.string.fake_title);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.name = mScreenReaderComponent.getPackageName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                ImmutableList.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("read");
        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState_noSettingsActivity_hidesSettings_zoneHidden()
            throws XmlPullParserException, IOException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.labelRes = R.string.fake_title;
        resolveInfo.nonLocalizedLabel = mContext.getString(R.string.fake_title);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = mScreenReaderComponent.getPackageName();
        serviceInfo.name = mScreenReaderComponent.getClassName();
        resolveInfo.serviceInfo = serviceInfo;
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.name = mScreenReaderComponent.getPackageName();
        AccessibilityServiceInfo accessibilityServiceInfo = new AccessibilityServiceInfo(
                resolveInfo, mContext);
        mShadowAccessibilityManager.setInstalledAccessibilityServiceList(
                ImmutableList.of(accessibilityServiceInfo));

        mPreferenceControllerHelper.getController().setAvailabilityStatusForZone("hidden");
        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreferenceControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }
}

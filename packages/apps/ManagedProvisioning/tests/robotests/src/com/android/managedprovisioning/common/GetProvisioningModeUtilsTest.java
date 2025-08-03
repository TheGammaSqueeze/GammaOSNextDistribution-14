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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_GET_PROVISIONING_MODE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for {@link GetProvisioningModeUtils}.
 */
@RunWith(RobolectricTestRunner.class)
public class GetProvisioningModeUtilsTest {

    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final String TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName TEST_MDM_ADMIN = new ComponentName(TEST_MDM_PACKAGE_NAME,
            TEST_MDM_ADMIN_RECEIVER);
    private static final ProvisioningParams PARAMS = ProvisioningParams.Builder.builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_MDM_ADMIN)
            .build();
    private static final String STRING_EXTRA_KEY = "myStringExtra";
    private static final String STRING_EXTRA_VALUE = "some string";
    private static final String INT_EXTRA_KEY = "myIntExtra";
    private static final int INT_EXTRA_VALUE = 2;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final GetProvisioningModeUtils mProvisioningModeUtils = new GetProvisioningModeUtils();
    private final TransitionHelper mTransitionHelper = new TransitionHelper();

    @Test
    public void isGetProvisioningModeActivityResolvable_resolvableActivity_returnsTrue() {
        Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(intentGetMode, info);

        assertThat(mProvisioningModeUtils
                .isGetProvisioningModeActivityResolvable(mContext, PARAMS)).isTrue();
    }

    @Test
    public void isGetProvisioningModeActivityResolvable_notResolvableActivity_returnsFalse() {
        Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);

        assertThat(mProvisioningModeUtils
                .isGetProvisioningModeActivityResolvable(mContext, PARAMS)).isFalse();
    }

    @Ignore("b/218480743")
    @Test
    public void startGetProvisioningModeActivityIfResolved_resolvableActivity_returnsTrue() {
        Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(intentGetMode, info);
        int requestCode = 1;
        Activity parentActivity = Robolectric.buildActivity(Activity.class).create().get();

        assertThat(mProvisioningModeUtils
                .startGetProvisioningModeActivityIfResolved(
                parentActivity, PARAMS, new Bundle(), requestCode, mTransitionHelper)).isTrue();
    }

    @Ignore("b/218480743")
    @Test
    public void startGetProvisioningModeActivityIfResolved_resolvableActivity_startsGetProvisioningModeIntent() {
        Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(intentGetMode, info);
        int requestCode = 1;
        Activity parentActivity = Robolectric.buildActivity(Activity.class).create().get();

        mProvisioningModeUtils.startGetProvisioningModeActivityIfResolved(
                parentActivity, PARAMS, new Bundle(), requestCode, mTransitionHelper);

        Intent startedIntent = shadowOf(parentActivity).peekNextStartedActivity();
        assertThat(startedIntent.getAction())
                .isEqualTo(ACTION_GET_PROVISIONING_MODE);
        assertThat(startedIntent.getPackage())
                .isEqualTo(TEST_MDM_PACKAGE_NAME);
    }

    @Ignore("b/218480743")
    @Test
    public void
    startGetProvisioningModeActivityIfResolved_resolvableActivity_includesAdditionalExtras() {
        Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(intentGetMode, info);
        int requestCode = 1;
        Activity parentActivity = Robolectric.buildActivity(Activity.class).create().get();
        Bundle additionalExtras = new Bundle();
        additionalExtras.putInt(INT_EXTRA_KEY, INT_EXTRA_VALUE);
        additionalExtras.putString(STRING_EXTRA_KEY, STRING_EXTRA_VALUE);

        mProvisioningModeUtils.startGetProvisioningModeActivityIfResolved(
                parentActivity, PARAMS, additionalExtras, requestCode, mTransitionHelper);

        Bundle extras = shadowOf(parentActivity).peekNextStartedActivity().getExtras();
        assertThat(extras.getInt(INT_EXTRA_KEY)).isEqualTo(INT_EXTRA_VALUE);
        assertThat(extras.getString(STRING_EXTRA_KEY)).isEqualTo(STRING_EXTRA_VALUE);
    }

    private ResolveInfo createFakeResolveInfo() {
        ResolveInfo info = new ResolveInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = TEST_MDM_PACKAGE_NAME;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.name = "SomeClassName";
        info.activityInfo = activityInfo;
        return info;
    }
}

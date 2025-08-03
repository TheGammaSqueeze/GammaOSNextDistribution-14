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
package com.android.car.settings.enterprise;

import static android.car.test.mocks.CarArgumentMatchers.intentFor;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;

public final class WorkPolicyInfoPreferenceControllerTest
        extends BaseEnterprisePreferenceControllerTestCase {

    @Mock
    private ResolveInfo mResolveInfo;

    @Mock
    private Preference mPreference;

    @Test
    public void testGetPreferenceType() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();

        assertWithMessage("preferenceType").that(controller.getPreferenceType())
                .isEqualTo(Preference.class);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureDisabled();

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneWrite() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneRead() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneHidden() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        // Don't need to mock anything else

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneWrite() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneRead() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneHidden() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithoutReceiver() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        // Don't need to mock anything else

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithoutReceiver_zoneWrite() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithoutReceiver_zoneRead() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithoutReceiver_zoneHidden() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        // Don't need to mock anything else
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithReceiver()  {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithReceiver_zoneWrite()  {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithReceiver_zoneRead()  {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_adminWithReceiver_zoneHidden()  {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateStatus_noFeature() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureDisabled();
        // Don't need to mock anything else

        controller.updateState(mPreference);

        verifyPreferenceIntentNotSet();
        verifyPreferenceTitleNotSet();
    }

    @Test
    public void testUpdateStatus_noAdmin() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        // Don't need to mock anything else

        controller.updateState(mPreference);

        verifyPreferenceIntentNotSet();
    }

    @Test
    public void testUpdateStatus_adminWithoutReceiver() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        // Don't need to mock anything else

        controller.updateState(mPreference);

        verifyPreferenceIntentNotSet();
        verifyPreferenceTitleNotSet();
    }

    @Test
    public void testUpdateStatus_adminWithReceiver() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();

        controller.updateState(mPreference);

        verifyPreferenceIntentSet();
        verifyPreferenceTitleSet();
    }

    @Test
    public void testUpdateStatus_adminWithReceiverButNoPackageInfo() throws Exception {
        WorkPolicyInfoPreferenceController controller = newControllerWithFeatureEnabled();
        mockProfileOwner();
        mockHasIntent();
        mockAppInfoMissing();

        controller.updateState(mPreference);

        verifyPreferenceIntentNotSet();
        verifyPreferenceTitleNotSet();
    }

    // Must create new instances on demand as the feature check is done on constructor
    private WorkPolicyInfoPreferenceController newControllerWithFeatureDisabled() {
        mockNoDeviceAdminFeature();
        return newController();
    }

    // Must create new instances on demand as the feature check is done on constructor
    private WorkPolicyInfoPreferenceController newControllerWithFeatureEnabled() {
        mockHasDeviceAdminFeature();
        return newController();
    }

    private WorkPolicyInfoPreferenceController newController() {
        return new WorkPolicyInfoPreferenceController(mSpiedContext, mPreferenceKey,
                mFragmentController, mUxRestrictions);
    }

    private void mockHasIntent() {
        // Must use doReturn() instead of when() because it's a spy
        doReturn(Arrays.asList(mResolveInfo)).when(mSpiedPm)
                .queryIntentActivities(showWorkPolicyInfoIntent(), eq(/* flags= */ 0));
    }

    private void mockAppInfoMissing() throws NameNotFoundException {
        // Must use doThrow() instead of when() because it's a spy
        doThrow(new NameNotFoundException("D'OH!")).when(mSpiedPm).getApplicationInfo(mPackageName,
                /* flags= */ 0);
    }

    private Intent showWorkPolicyInfoIntent() {
        return intentFor(Settings.ACTION_SHOW_WORK_POLICY_INFO, mDefaultAdmin.getPackageName());
    }

    private void verifyPreferenceIntentNotSet() {
        verify(mPreference, never()).setIntent(any());
    }

    private void verifyPreferenceIntentSet() {
        verify(mPreference).setIntent(showWorkPolicyInfoIntent());
    }

    private void verifyPreferenceTitleNotSet() {
        verify(mPreference, never()).setTitle(any());
    }

    private void verifyPreferenceTitleSet() {
        verify(mPreference).setTitle(mRealContext.getString(R.string.work_policy_privacy_settings,
                mAppName));
    }
}

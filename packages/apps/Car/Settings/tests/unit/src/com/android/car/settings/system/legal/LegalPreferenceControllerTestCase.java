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

package com.android.car.settings.system.legal;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public abstract class LegalPreferenceControllerTestCase {
    private static final String TEST_LABEL = "test_label";
    private static final String TEST_PACKAGE_NAME = "com.android.car.settings.testutils";
    private static final String TEST_ACTIVITY_NAME = "BaseTestActivity";

    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private LegalPreferenceController mPreferenceController;

    protected Context mContext = spy(ApplicationProvider.getApplicationContext());
    protected CarUxRestrictions mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */
            true, CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

    @Mock
    protected FragmentController mFragmentController;
    @Mock
    private PackageManager mMockPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getPackageManager()).thenReturn(mMockPm);

        mPreference = new Preference(mContext);
        mPreferenceController = getPreferenceController();
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    protected abstract LegalPreferenceController getPreferenceController();

    protected abstract String getIntentAction();

    @Test
    public void getIntent_shouldUseRightIntent() {
        Intent intent = mPreferenceController.getIntent();
        assertThat(intent.getAction()).isEqualTo(getIntentAction());
    }

    @Test
    public void getAvailabilityStatus_systemApp_shouldReturnAvailable() {
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_systemApp_shouldReturnAvailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_systemApp_shouldReturnAvailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_systemApp_shouldReturnAvailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_notSystemApp_shouldReturnUnsupported() {
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ false));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);
        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(UNSUPPORTED_ON_DEVICE);

        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(null);
        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSystemApp_shouldReturnUnsupported_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ false));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);

        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(null);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSystemApp_shouldReturnUnsupported_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ false));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);

        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(null);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSystemApp_shouldReturnUnsupported_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ false));
        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(list);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);

        when(mMockPm.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(null);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_intentResolvesToNull_shouldReturnUnsupported() {
        when(mMockPm.queryIntentActivities(eq(mPreferenceController.getIntent()), anyInt()))
                .thenReturn(Collections.emptyList());

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_intentResolvesToNull_shouldReturnUnsupported_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        when(mMockPm.queryIntentActivities(eq(mPreferenceController.getIntent()), anyInt()))
                .thenReturn(Collections.emptyList());

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_intentResolvesToNull_shouldReturnUnsupported_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        when(mMockPm.queryIntentActivities(eq(mPreferenceController.getIntent()), anyInt()))
                .thenReturn(Collections.emptyList());

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_intentResolvesToNull_shouldReturnUnsupported_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        when(mMockPm.queryIntentActivities(eq(mPreferenceController.getIntent()), anyInt()))
                .thenReturn(Collections.emptyList());

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void onCreate_intentResolvesToActivity_isVisible() {
        Intent intent = mPreferenceController.getIntent();

        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));

        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(list);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void onCreate_intentResolvesToActivity_updatesTitle() {
        Intent intent = mPreferenceController.getIntent();

        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));

        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(list);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getTitle()).isEqualTo(TEST_LABEL);
    }

    @Test
    @FlakyTest(bugId = 316067921)
    public void onCreate_intentResolvesToActivity_updatesIntentToSpecificActivity() {
        Intent intent = mPreferenceController.getIntent();

        List<ResolveInfo> list = new ArrayList<>();
        list.add(getTestResolveInfo(/* isSystemApp= */ true));

        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(list);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getIntent().getComponent().flattenToString()).isEqualTo(
                TEST_PACKAGE_NAME + "/" + TEST_ACTIVITY_NAME);
    }

    /**
     * Returns a ResolveInfo object for testing
     *
     * @param isSystemApp If true, the application is a system app.
     */
    private ResolveInfo getTestResolveInfo(boolean isSystemApp) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = TEST_PACKAGE_NAME;
        activityInfo.name = TEST_ACTIVITY_NAME;
        activityInfo.applicationInfo = new ApplicationInfo();
        if (isSystemApp) {
            activityInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }

        ResolveInfo resolveInfo = new ResolveInfo() {
            @Override
            public CharSequence loadLabel(PackageManager pm) {
                return TEST_LABEL;
            }
        };
        resolveInfo.activityInfo = activityInfo;
        return resolveInfo;
    }
}

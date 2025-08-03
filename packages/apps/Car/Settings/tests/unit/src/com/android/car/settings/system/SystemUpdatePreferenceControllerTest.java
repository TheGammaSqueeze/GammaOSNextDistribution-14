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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.telephony.CarrierConfigManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;


import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Collections;

/** Unit test for {@link SystemUpdatePreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class SystemUpdatePreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private SystemUpdatePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private PackageManager mMockPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(UserManager.class, withSettings().lenient())
                .startMocking();
        when(UserManager.get(mContext)).thenReturn(mMockUserManager);
        when(mContext.getPackageManager()).thenReturn(mMockPm);

        mPreference = new Preference(mContext);
        mPreferenceController = new SystemUpdatePreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_adminUser_available() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_available_zoneWrite() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_available_zoneRead() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    @FlakyTest(bugId = 316042666)
    public void getAvailabilityStatus_adminUser_available_zoneHidden() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser() {
        when(mMockUserManager.isAdminUser()).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneWrite() {
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneRead() {
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneHidden() {
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void onCreate_setsActivityLabelAsTitle() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.packageName = "some.test.package";
        activityInfo.name = "SomeActivity";

        String label = "Activity Label";
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.nonLocalizedLabel = label;
        resolveInfo.activityInfo = activityInfo;

        Intent intent = new Intent();
        when(mMockPm.queryIntentActivities(eq(intent), anyInt())).thenReturn(
                Collections.singletonList(resolveInfo));
        mPreference.setIntent(intent);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getTitle()).isEqualTo(label);
    }

    @Test
    public void refreshUi_activityNotFound_hidesPreference() {
        mPreference.setIntent(new Intent());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void preferenceClicked_triggersClientInitiatedAction() {
        // Arrange
        String action = "action";
        String key = "key";
        String value = "value";

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL, true);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING, action);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING, key);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING, value);

        CarrierConfigManager mockConfigManager = mock(CarrierConfigManager.class);
        when(mContext.getSystemService(CarrierConfigManager.class)).thenReturn(mockConfigManager);
        when(mockConfigManager.getConfig()).thenReturn(config);
        Context mockApplicationContext = mock(Context.class);
        when(mContext.getApplicationContext()).thenReturn(mockApplicationContext);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        // Act
        mPreference.performClick();

        // Assert
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mockApplicationContext).sendBroadcast(captor.capture());
        Intent broadcast = captor.getValue();
        assertThat(broadcast.getAction()).isEqualTo(action);
        assertThat(broadcast.getStringExtra(key)).isEqualTo(value);
    }

    @Test
    public void preferenceClicked_handledReturnsFalse() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        assertThat(mPreference.getOnPreferenceClickListener().onPreferenceClick(
                mPreference)).isFalse();
    }
}

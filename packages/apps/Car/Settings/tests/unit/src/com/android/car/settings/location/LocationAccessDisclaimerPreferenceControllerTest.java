/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.settings.location;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.location.LocationManager;
import android.os.PackageTagsList;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class LocationAccessDisclaimerPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LocationAccessDisclaimerPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private LocationManager mLocationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);

        mSession = mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager.class)
                .startMocking();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreferenceController = new LocationAccessDisclaimerPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void getDefaultAvailabilityStatus_driverWithAdas_conditionallyUnavailable() {
        int currentUser = UserHandle.myUserId();
        when(ActivityManager.getCurrentUser()).thenReturn(currentUser);
        PackageTagsList list = new PackageTagsList.Builder().add("testApp1").build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getDefaultAvailabilityStatus_driverNoAdas_available() {
        int currentUser = UserHandle.myUserId();
        when(ActivityManager.getCurrentUser()).thenReturn(currentUser);
        PackageTagsList list = new PackageTagsList.Builder().build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getDefaultAvailabilityStatus_passengerAdas_available() {
        int nonCurrentUser = UserHandle.myUserId() + 1;
        when(ActivityManager.getCurrentUser()).thenReturn(nonCurrentUser);
        PackageTagsList list = new PackageTagsList.Builder().add("testApp1").build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getDefaultAvailabilityStatus_passengerNoAdas_available() {
        int nonCurrentUser = UserHandle.myUserId() + 1;
        when(ActivityManager.getCurrentUser()).thenReturn(nonCurrentUser);
        PackageTagsList list = new PackageTagsList.Builder().build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }
}

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

package com.android.car.settings.profiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class ProfileDetailsBasePreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private TestProfileDetailsBasePreferenceController mPreferenceController;
    private Preference mPreference;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserInfo mUserInfo;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreferenceController = new TestProfileDetailsBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreference = new Preference(mContext);
    }

    @Test
    public void testCheckInitialized_missingUserInfo() {
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void testOnStart_registerListener() {
        mPreferenceController.setUserInfo(mUserInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mContext).registerReceiver(eq(mPreferenceController.mProfileUpdateReceiver), any());
    }

    @Test
    public void testOnStop_unregisterListener() {
        mPreferenceController.setUserInfo(mUserInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mContext).unregisterReceiver(mPreferenceController.mProfileUpdateReceiver);
    }

    private static class TestProfileDetailsBasePreferenceController extends
            ProfileDetailsBasePreferenceController<Preference> {

        TestProfileDetailsBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }
    }
}

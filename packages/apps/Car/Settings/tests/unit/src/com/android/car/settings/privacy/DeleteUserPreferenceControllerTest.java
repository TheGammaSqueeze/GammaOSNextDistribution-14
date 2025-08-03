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

package com.android.car.settings.privacy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.profiles.RemoveProfileHandler;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class DeleteUserPreferenceControllerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private Preference mPreference;
    private DeleteUserPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private RemoveProfileHandler mRemoveProfileHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new DeleteUserPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions,
                mRemoveProfileHandler);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_canRemoveProfile_preferenceEnabled() {
        when(mRemoveProfileHandler.canRemoveProfile(any())).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_cannotRemoveProfile_preferenceDisabled() {
        when(mRemoveProfileHandler.canRemoveProfile(any())).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onClick_preferenceDisabled_showsToast() {
        Toast mockToast = mock(Toast.class);
        MockitoSession session = ExtendedMockito.mockitoSession()
                .mockStatic(Toast.class, withSettings().lenient())
                .startMocking();
        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mockToast);
        when(mRemoveProfileHandler.canRemoveProfile(any())).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        ExtendedMockito.verify(
                () -> Toast.makeText(any(),
                        eq(mContext.getString(R.string.action_unavailable)),
                        anyInt()));
        verify(mockToast).show();
        session.finishMocking();
    }

    @Test
    public void onClick_preferenceEnabled_showsDialog() {
        when(mRemoveProfileHandler.canRemoveProfile(any())).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        verify(mRemoveProfileHandler).showConfirmRemoveProfileDialog();
    }
}

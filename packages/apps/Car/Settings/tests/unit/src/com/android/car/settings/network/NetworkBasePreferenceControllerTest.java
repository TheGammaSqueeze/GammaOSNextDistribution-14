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

package com.android.car.settings.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.NetworkTemplate;
import android.telephony.TelephonyManager;

import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.datausage.DataUsageUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class NetworkBasePreferenceControllerTest {

    private static final int SUB_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private TestNetworkBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private NetworkTemplate mMockNetworkTemplate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(TelephonyManager.class, withSettings().lenient())
                .mockStatic(DataUsageUtils.class, withSettings().lenient())
                .startMocking();

        ExtendedMockito.when(TelephonyManager.from(mContext)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManager);
        ExtendedMockito.when(DataUsageUtils.getMobileNetworkTemplate(mMockTelephonyManager, SUB_ID))
                .thenReturn(mMockNetworkTemplate);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new TestNetworkBasePreferenceController(mContext,
                "key", mMockFragmentController, mCarUxRestrictions);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onSubIdSet_setsFields() {
        mPreferenceController.setFields(SUB_ID);
        assertThat(mPreferenceController.getSubId()).isEqualTo(SUB_ID);
        assertThat(mPreferenceController.getTelephonyManager()).isEqualTo(mMockTelephonyManager);
        assertThat(mPreferenceController.getNetworkTemplate()).isEqualTo(mMockNetworkTemplate);
    }

    /** Concrete impl of {@link NetworkBasePreferenceController} for testing. */
    private static class TestNetworkBasePreferenceController extends
            NetworkBasePreferenceController<Preference> {

        TestNetworkBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }
    }
}

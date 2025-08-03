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

package com.android.car.settings.units;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictions;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class UnitsPressurePreferenceControllerTest {
    private static final Unit[] AVAILABLE_UNITS =
            {UnitsMap.PSI, UnitsMap.BAR};

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ListPreference mPreference;
    private UnitsPressurePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private ArgumentCaptor<Integer> mCaptor = ArgumentCaptor.forClass(Integer.class);

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CarUnitsManager mCarUnitsManager;
    @Mock
    private CarPropertyManager mCarPropertyManager;
    @Mock
    private CarPropertyValue mCarPropertyValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mCarUnitsManager.getUnitsSupportedByProperty(mCaptor.capture()))
                .thenReturn(AVAILABLE_UNITS);
        when(mCarUnitsManager.getUnitUsedByProperty(anyInt())).thenReturn(null);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new ListPreference(mContext);
        mPreferenceController = new TestUnitsPressurePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onCarServiceConnected_setsPreferenceEntriesAndValue() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        CharSequence[] expectedEntries = mPreferenceController.getEntriesOfSupportedUnits();

        assertThat(mCaptor.getValue()).isEqualTo(VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS);
        assertThat(mPreference.getEntries()).isEqualTo(expectedEntries);
        assertThat(mPreference.getValue()).isEqualTo(Integer.toString(AVAILABLE_UNITS[0].getId()));
    }

    @Test
    public void onPreferenceChanged_runsSetUnitUsedByPropertyWithNewUnit() {
        Unit newUnit = AVAILABLE_UNITS[1];
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        mPreferenceController.handlePreferenceChanged(mPreference,
                Integer.toString(newUnit.getId()));

        verify(mCarUnitsManager).setUnitUsedByProperty(
                VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                newUnit.getId());
    }

    @Test
    public void onPropertyChanged_propertyStatusIsAvailable_setsNewUnitIdAsValue() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        Unit newUnit = AVAILABLE_UNITS[1];;
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(newUnit.getId());
        mPreferenceController.mCarPropertyEventCallback.onChangeEvent(mCarPropertyValue);

        assertThat(mPreference.getValue()).isEqualTo(Integer.toString(newUnit.getId()));
        assertThat(mPreference.getSummary().toString())
                .isEqualTo(mContext.getString(newUnit.getAbbreviationResId()));
    }

    private class TestUnitsPressurePreferenceController extends
            UnitsPressurePreferenceController {

        public TestUnitsPressurePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        CarUnitsManager createCarUnitsManager() {
            return mCarUnitsManager;
        }
    }
}

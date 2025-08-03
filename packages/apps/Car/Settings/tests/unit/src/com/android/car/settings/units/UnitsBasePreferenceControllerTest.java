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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class UnitsBasePreferenceControllerTest {

    private static final int TEST_PROPERTY_ID = -1;
    private static final Unit[] AVAILABLE_UNITS =
            {UnitsMap.YEAR, UnitsMap.SECS, UnitsMap.NANO_SECS};
    private static final Unit DEFAULT_UNIT = UnitsMap.YEAR;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ListPreference mPreference;
    private UnitsBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CarPropertyManager mCarPropertyManager;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarUnitsManager mCarUnitsManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        doNothing().when(mCarUnitsManager).registerCarServiceListener(any());
        when(mCarUnitsManager.getUnitsSupportedByProperty(TEST_PROPERTY_ID))
                .thenReturn(AVAILABLE_UNITS);
        updateDefaultUnit(DEFAULT_UNIT);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new ListPreference(mContext);
        mPreferenceController = new TestUnitsBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onCreate_registersCarServiceListener() {
        verify(mCarUnitsManager)
                .registerCarServiceListener(mPreferenceController.mOnCarServiceListener);
    }

    @Test
    public void onCreate_preferenceIsConditionallyUnavailable() {
        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_preferenceIsConditionallyUnavailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_preferenceIsConditionallyUnavailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_preferenceIsConditionallyUnavailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_availableUnitsExist_preferenceIsAvailable() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.AVAILABLE);
    }

    @Test
    public void onCarServiceConnected_availableUnitsExist_preferenceIsAvailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void onCarServiceConnected_availableUnitsExist_preferenceIsAvailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onCarServiceConnected_availableUnitsExist_preferenceIsAvailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_noAvailableUnits_preferenceIsConditionallyUnavailable() {
        when(mCarUnitsManager.getUnitsSupportedByProperty(TEST_PROPERTY_ID))
                .thenReturn(null);
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_preferenceIsConditionallyUnavailable_zoneWrite() {
        when(mCarUnitsManager.getUnitsSupportedByProperty(TEST_PROPERTY_ID))
                .thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_preferenceIsConditionallyUnavailable_zoneRead() {
        when(mCarUnitsManager.getUnitsSupportedByProperty(TEST_PROPERTY_ID))
                .thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_preferenceIsConditionallyUnavailable_zoneHidden() {
        when(mCarUnitsManager.getUnitsSupportedByProperty(TEST_PROPERTY_ID))
                .thenReturn(null);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_setsEntriesOfSupportedUnits() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        CharSequence[] expectedEntries = mPreferenceController.getEntriesOfSupportedUnits();

        assertThat(mPreference.getEntries()).isEqualTo(expectedEntries);
    }

    @Test
    public void onCarServiceConnected_setsSupportedUnitsIdsAsEntryValues() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        CharSequence[] expectedEntryValues = mPreferenceController.getIdsOfSupportedUnits();

        assertThat(mPreference.getEntryValues()).isEqualTo(expectedEntryValues);
    }

    @Test
    public void onCarServiceConnected_setsUnitBeingUsedAsPreferenceValue() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        String expectedValue = Integer.toString(DEFAULT_UNIT.getId());

        assertThat(mPreference.getValue()).isEqualTo(expectedValue);
    }

    @Test
    public void onPreferenceChanged_runsSetUnitUsedByPropertyWithNewUnit() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        mPreferenceController.handlePreferenceChanged(mPreference,
                Integer.toString(UnitsMap.SECS.getId()));

        assertThat(mCarUnitsManager.getUnitUsedByProperty(TEST_PROPERTY_ID))
                .isEqualTo(UnitsMap.SECS);
    }

    @Test
    public void onPropertyChanged_propertyStatusIsAvailable_setsNewUnitIdAsValue() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        Unit newUnit = UnitsMap.SECS;
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(newUnit.getId());
        mPreferenceController.mCarPropertyEventCallback.onChangeEvent(mCarPropertyValue);

        assertThat(mPreference.getValue()).isEqualTo(Integer.toString(newUnit.getId()));
    }

    @Test
    public void onPropertyChanged_propertyStatusIsAvailable_setsNewUnitAbbreviationAsSummary() {
        mPreferenceController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        Unit newUnit = UnitsMap.SECS;
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(newUnit.getId());
        mPreferenceController.mCarPropertyEventCallback.onChangeEvent(mCarPropertyValue);

        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(newUnit.getAbbreviationResId()));
    }

    @Test
    public void onDestroy_disconnectsCarUnitsManager() {
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mCarUnitsManager).disconnect();
    }

    @Test
    public void onDestroy_unregistersCarServiceListener() {
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mCarUnitsManager).unregisterCarServiceListener();
    }

    private void updateDefaultUnit(Unit unit) {
        when(mCarUnitsManager.getUnitUsedByProperty(TEST_PROPERTY_ID))
                .thenReturn(unit);
    }

    private class TestUnitsBasePreferenceController extends UnitsBasePreferenceController {

        private TestUnitsBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected int getPropertyId() {
            return TEST_PROPERTY_ID;
        }


        @Override
        protected Class<ListPreference> getPreferenceType() {
            return ListPreference.class;
        }

        @Override
        public boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
            int unitId = Integer.parseInt((String) newValue);
            Unit newUnit = UnitsMap.MAP.get(unitId);
            if (newUnit != null) {
                updateDefaultUnit(newUnit);
            }
            return super.handlePreferenceChanged(preference, newValue);
        }

        @Override
        CarUnitsManager createCarUnitsManager() {
            return mCarUnitsManager;
        }
    }
}

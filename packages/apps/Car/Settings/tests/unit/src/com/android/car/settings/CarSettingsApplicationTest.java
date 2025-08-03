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

package com.android.car.settings;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.Car;
import android.car.Car.CarServiceLifecycleListener;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneConfigChangeListener;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.VehicleAreaSeat;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.hardware.display.DisplayManagerGlobal;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class CarSettingsApplicationTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private MockitoSession mSession;

    @Mock
    private Car mCar;
    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;
    @Mock
    private CarAudioManager mCarAudioManager;

    private final OccupantZoneInfo mZoneInfoDriver = new OccupantZoneInfo(0,
            CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER,
            VehicleAreaSeat.SEAT_ROW_1_LEFT);
    private final OccupantZoneInfo mZoneInfoPassenger = new OccupantZoneInfo(1,
            CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER,
            VehicleAreaSeat.SEAT_ROW_1_RIGHT);
    private final int mPrimaryAudioZoneId = 0;
    private final int mSecondaryAudioZoneId = 1;
    private final Display mDefaultDisplay = new Display(DisplayManagerGlobal.getInstance(),
            Display.DEFAULT_DISPLAY, new DisplayInfo(),
            DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS);
    private final Display mSecondaryDisplay = new Display(DisplayManagerGlobal.getInstance(),
            Display.DEFAULT_DISPLAY + 1, new DisplayInfo(),
            DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS);

    private CarSettingsApplication mCarSettingsApplication;
    private CarServiceLifecycleListener mCarServiceLifecycleListener;
    private OccupantZoneConfigChangeListener mConfigChangeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = mockitoSession().mockStatic(Car.class,
                withSettings().lenient()).startMocking();

        doAnswer(invocation -> {
            mCarServiceLifecycleListener = invocation.getArgument(3);
            return mCar;
        }).when(() -> Car.createCar(any(), any(), anyLong(), any()));

        doAnswer(invocation -> {
            mConfigChangeListener = (OccupantZoneConfigChangeListener) invocation.getArgument(0);
            return null;
        }).when(mCarOccupantZoneManager).registerOccupantZoneConfigChangeListener(any());

        mCarSettingsApplication = new CarSettingsApplication();
        mCarSettingsApplication.onCreate();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onLifecycleChanged_carServiceNotReady() {
        mCarServiceLifecycleListener.onLifecycleChanged(null, false);

        assertThat(mCarSettingsApplication.getCarAudioManager()).isEqualTo(
                null);
        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);
        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                CarAudioManager.INVALID_AUDIO_ZONE);
        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_INVALID);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_carAudioManager() {
        when(mCar.getCarManager(Car.AUDIO_SERVICE)).thenReturn(mCarAudioManager);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getCarAudioManager()).isEqualTo(
                mCarAudioManager);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_zoneInfoDriver() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_zoneInfoPassenger() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoPassenger);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_zoneInfoNull() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(null);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_INVALID);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_primaryAudioZone() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        when(mCarOccupantZoneManager.getAudioZoneIdForOccupant(
                mZoneInfoDriver)).thenReturn(mPrimaryAudioZoneId);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mPrimaryAudioZoneId);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_secondaryAudioZone() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoPassenger);
        when(mCarOccupantZoneManager.getAudioZoneIdForOccupant(
                mZoneInfoPassenger)).thenReturn(mSecondaryAudioZoneId);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mSecondaryAudioZoneId);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_defaultDisplayId() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        when(mCarOccupantZoneManager.getDisplayForOccupant(mZoneInfoDriver,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(mDefaultDisplay);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_secondaryDisplayId() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoPassenger);
        when(mCarOccupantZoneManager.getDisplayForOccupant(mZoneInfoPassenger,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(mSecondaryDisplay);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY + 1);
    }

    @Test
    public void onLifecycleChanged_carServiceReady_occupantZoneServiceIsNull() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(null);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_INVALID);
        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                CarAudioManager.INVALID_AUDIO_ZONE);
        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onLifecycleChanged_carServiceCrashed_usePreviousValues_exceptCarAudioManager() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCar.getCarManager(Car.AUDIO_SERVICE)).thenReturn(mCarAudioManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        when(mCarOccupantZoneManager.getAudioZoneIdForOccupant(
                mZoneInfoDriver)).thenReturn(mPrimaryAudioZoneId);
        when(mCarOccupantZoneManager.getDisplayForOccupant(mZoneInfoDriver,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(mDefaultDisplay);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getCarAudioManager()).isEqualTo(
                mCarAudioManager);
        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER);
        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mPrimaryAudioZoneId);
        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);

        mCarServiceLifecycleListener.onLifecycleChanged(null, false);

        assertThat(mCarSettingsApplication.getCarAudioManager()).isEqualTo(
                null);
        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER);
        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mPrimaryAudioZoneId);
        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);
    }

    @Test
    public void onOccupantZoneConfigChanged_flagDisplay_displayChanged() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        when(mCarOccupantZoneManager.getDisplayForOccupant(mZoneInfoDriver,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(mDefaultDisplay);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY);

        when(mCarOccupantZoneManager.getDisplayForOccupant(mZoneInfoDriver,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN)).thenReturn(mSecondaryDisplay);
        mConfigChangeListener.onOccupantZoneConfigChanged(
                CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY);

        assertThat(mCarSettingsApplication.getMyOccupantZoneDisplayId()).isEqualTo(
                Display.DEFAULT_DISPLAY + 1);
    }

    @Test
    public void onOccupantZoneConfigChanged_flagUser_zoneChanged() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER);

        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoPassenger);
        mConfigChangeListener.onOccupantZoneConfigChanged(
                CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        assertThat(mCarSettingsApplication.getMyOccupantZoneType()).isEqualTo(
                CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER);
    }

    @Test
    public void onOccupantZoneConfigChanged_flagAudio_audioChanged() {
        when(mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE)).thenReturn(mCarOccupantZoneManager);
        when(mCarOccupantZoneManager.getMyOccupantZone()).thenReturn(mZoneInfoDriver);
        when(mCarOccupantZoneManager.getAudioZoneIdForOccupant(
                mZoneInfoDriver)).thenReturn(mPrimaryAudioZoneId);
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mPrimaryAudioZoneId);

        when(mCarOccupantZoneManager.getAudioZoneIdForOccupant(
                mZoneInfoDriver)).thenReturn(mSecondaryAudioZoneId);
        mConfigChangeListener.onOccupantZoneConfigChanged(
                CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_AUDIO);

        assertThat(mCarSettingsApplication.getMyAudioZoneId()).isEqualTo(
                mSecondaryAudioZoneId);
    }
}

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

package com.android.car;

import static android.car.hardware.CarPropertyValue.STATUS_AVAILABLE;
import static android.car.settings.CarSettings.Global.FORCED_DAY_NIGHT_MODE;

import static com.android.car.CarNightService.FORCED_DAY_MODE;
import static com.android.car.CarNightService.FORCED_NIGHT_MODE;
import static com.android.car.CarNightService.FORCED_SENSOR_MODE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyValue;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.MockSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link CarNightService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CarNightServiceTest extends AbstractExtendedMockitoTestCase {
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_ENABLED =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 1000, true);
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_DISABLED =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 1000, false);
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_DISABLED_NO_TIMESTAMP =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 0, false);
    private static final int INVALID_NIGHT_MODE = 100;

    @Mock
    private Context mContext;
    @Mock
    private CarPropertyService mCarPropertyService;
    @Mock
    private UiModeManager mUiModeManager;
    @Mock
    private ContentResolver mContentResolver;

    private CarNightService mService;

    // Not used directly, but sets proper mockStatic() expectations on Settings
    @SuppressWarnings("UnusedVariable")
    private MockSettings mMockSettings;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        mMockSettings = new MockSettings(session);
    }

    @Before
    public void setUp() {
        when(mContext.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(mUiModeManager);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        Settings.Global.putInt(mContentResolver, CarSettings.Global.FORCED_DAY_NIGHT_MODE,
                FORCED_SENSOR_MODE);
        mService = new CarNightService(mContext, mCarPropertyService);
    }

    @Test
    public void onInit_setsNightModeFromProperty() {
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void onInit_propertyTimestampMissing_setsDefaultNightMode() {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(NIGHT_MODE_PROPERTY_DISABLED_NO_TIMESTAMP);
        mService.init();
        verify(mUiModeManager).setNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void onInit_propertyMissing_setsDefaultNightMode() {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(null);
        mService.init();
        verify(mUiModeManager).setNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void forceDayNightMode_setsDayMode() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        int expectedNewMode = UiModeManager.MODE_NIGHT_YES;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_NO);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        int updatedMode = mService.forceDayNightMode(FORCED_NIGHT_MODE);

        verify(mUiModeManager).setNightMode(expectedNewMode);
        assertThat(updatedMode).isEqualTo(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_setsNightMode() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        int expectedNewMode = UiModeManager.MODE_NIGHT_NO;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        int updatedMode = mService.forceDayNightMode(FORCED_DAY_MODE);

        verify(mUiModeManager).setNightMode(expectedNewMode);
        assertThat(updatedMode).isEqualTo(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_invalidMode() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        int updatedMode = mService.forceDayNightMode(INVALID_NIGHT_MODE);

        verifyNoMoreInteractions(mUiModeManager);
        assertThat(updatedMode).isEqualTo(-1);
    }

    @Test
    public void onInit_registersContentObserver() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        mService.init();
        verifyAndGetNightModeSettingContentObserver();
    }

    @Test
    public void forceDayNightMode_withSetting_setsDayMode() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        int expectedNewMode = UiModeManager.MODE_NIGHT_NO;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        Settings.Global.putInt(mContentResolver, FORCED_DAY_NIGHT_MODE,
                FORCED_DAY_MODE);
        ContentObserver observer = verifyAndGetNightModeSettingContentObserver();
        observer.onChange(false);

        verify(mUiModeManager).setNightMode(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_withSetting_setsNightMode() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        int expectedNewMode = UiModeManager.MODE_NIGHT_YES;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_NO);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        Settings.Global.putInt(mContentResolver, FORCED_DAY_NIGHT_MODE,
                FORCED_NIGHT_MODE);
        ContentObserver observer = verifyAndGetNightModeSettingContentObserver();
        observer.onChange(false);

        verify(mUiModeManager).setNightMode(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_withSetting_invalidMode() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING);
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        Settings.Global.putInt(mContentResolver, FORCED_DAY_NIGHT_MODE,
                INVALID_NIGHT_MODE);
        ContentObserver observer = verifyAndGetNightModeSettingContentObserver();
        observer.onChange(false);

        verifyNoMoreInteractions(mUiModeManager);
    }

    private void initServiceWithNightMode(int mode) {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(mode == UiModeManager.MODE_NIGHT_YES
                        ? NIGHT_MODE_PROPERTY_ENABLED
                        : NIGHT_MODE_PROPERTY_DISABLED);
        mService.init();
        verify(mUiModeManager).setNightMode(mode);
    }

    private ContentObserver verifyAndGetNightModeSettingContentObserver() {
        ArgumentCaptor<ContentObserver> captor = ArgumentCaptor.forClass(ContentObserver.class);
        verify(mContentResolver).registerContentObserver(
                eq(Settings.Global.getUriFor(FORCED_DAY_NIGHT_MODE)), eq(false), captor.capture());
        assertThat(captor.getValue()).isNotNull();
        return captor.getValue();
    }
}

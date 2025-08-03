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

package com.android.car.internal.property;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.VehicleAreaType;
import android.car.feature.FeatureFlags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class InputSanitizationUtilsUnitTest {
    private static final int PROPERTY_ID = 123;
    private static final float DEFAULT_UPDATE_RATE_HZ = 55.5f;
    private static final float MIN_UPDATE_RATE_HZ = 11.11f;
    private static final float MAX_UPDATE_RATE_HZ = 100.3f;
    private static final CarPropertyConfig.Builder<Integer> CAR_PROPERTY_CONFIG_BUILDER =
            CarPropertyConfig.newBuilder(
                    Integer.class, PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

    @Test
    public void sanitizeUpdateRateHz_returnsZeroForStaticProperties() {
        assertThat(
                        InputSanitizationUtils.sanitizeUpdateRateHz(
                                CAR_PROPERTY_CONFIG_BUILDER
                                        .setChangeMode(
                                                CarPropertyConfig
                                                        .VEHICLE_PROPERTY_CHANGE_MODE_STATIC)
                                        .build(),
                                DEFAULT_UPDATE_RATE_HZ))
                .isEqualTo(0);
    }

    @Test
    public void sanitizeUpdateRateHz_returnsZeroForOnChangeProperties() {
        assertThat(
                        InputSanitizationUtils.sanitizeUpdateRateHz(
                                CAR_PROPERTY_CONFIG_BUILDER
                                        .setChangeMode(
                                                CarPropertyConfig
                                                        .VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE)
                                        .build(),
                                DEFAULT_UPDATE_RATE_HZ))
                .isEqualTo(0);
    }

    @Test
    public void sanitizeUpdateRateHz_returnsMaxUpdateRateHzIfOver() {
        assertThat(
                        InputSanitizationUtils.sanitizeUpdateRateHz(
                                CAR_PROPERTY_CONFIG_BUILDER
                                        .setChangeMode(
                                                CarPropertyConfig
                                                        .VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                                        .setMinSampleRate(MIN_UPDATE_RATE_HZ)
                                        .setMaxSampleRate(MAX_UPDATE_RATE_HZ)
                                        .build(),
                                MAX_UPDATE_RATE_HZ + 1))
                .isEqualTo(MAX_UPDATE_RATE_HZ);
    }

    @Test
    public void sanitizeUpdateRateHz_returnsMinUpdateRateHzIfOver() {
        assertThat(
                        InputSanitizationUtils.sanitizeUpdateRateHz(
                                CAR_PROPERTY_CONFIG_BUILDER
                                        .setChangeMode(
                                                CarPropertyConfig
                                                        .VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                                        .setMinSampleRate(MIN_UPDATE_RATE_HZ)
                                        .setMaxSampleRate(MAX_UPDATE_RATE_HZ)
                                        .build(),
                                MIN_UPDATE_RATE_HZ - 1))
                .isEqualTo(MIN_UPDATE_RATE_HZ);
    }

    @Test
    public void sanitizeUpdateRateHz_returnsDefaultUpdateRateHz() {
        assertThat(
                        InputSanitizationUtils.sanitizeUpdateRateHz(
                                CAR_PROPERTY_CONFIG_BUILDER
                                        .setChangeMode(
                                                CarPropertyConfig
                                                        .VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                                        .setMinSampleRate(MIN_UPDATE_RATE_HZ)
                                        .setMaxSampleRate(MAX_UPDATE_RATE_HZ)
                                        .build(),
                                DEFAULT_UPDATE_RATE_HZ))
                .isEqualTo(DEFAULT_UPDATE_RATE_HZ);
    }

    @Test
    public void testIsVurAllowed() {
        CarPropertyConfig config = mock(CarPropertyConfig.class);
        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        FeatureFlags featureFlags = mock(FeatureFlags.class);
        when(featureFlags.variableUpdateRate()).thenReturn(true);

        assertThat(InputSanitizationUtils.isVurAllowed(featureFlags, config)).isTrue();
    }

    @Test
    public void testIsVurAllowed_featureDisabled() {
        CarPropertyConfig config = mock(CarPropertyConfig.class);
        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        FeatureFlags featureFlags = mock(FeatureFlags.class);
        when(featureFlags.variableUpdateRate()).thenReturn(false);

        assertThat(InputSanitizationUtils.isVurAllowed(featureFlags, config)).isFalse();
    }

    @Test
    public void testIsVurAllowed_propertyNotContinuous() {
        CarPropertyConfig config = mock(CarPropertyConfig.class);
        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE);
        FeatureFlags featureFlags = mock(FeatureFlags.class);
        when(featureFlags.variableUpdateRate()).thenReturn(true);

        assertThat(InputSanitizationUtils.isVurAllowed(featureFlags, config)).isFalse();
    }

    @Test
    public void testSanitizeEnableVariableUpdateRate() {
        CarSubscription inputOption = newCarSubscription(PROPERTY_ID, new int[]{1, 2, 3},
                DEFAULT_UPDATE_RATE_HZ, true);

        FeatureFlags featureFlags = mock(FeatureFlags.class);
        when(featureFlags.variableUpdateRate()).thenReturn(true);
        CarPropertyConfig config = mock(CarPropertyConfig.class);
        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        AreaIdConfig areaIdConfigEnabled = mock(AreaIdConfig.class);
        when(areaIdConfigEnabled.isVariableUpdateRateSupported()).thenReturn(true);
        AreaIdConfig areaIdConfigDisabled = mock(AreaIdConfig.class);
        when(areaIdConfigDisabled.isVariableUpdateRateSupported()).thenReturn(false);
        when(config.getAreaIdConfig(1)).thenReturn(areaIdConfigEnabled);
        when(config.getAreaIdConfig(2)).thenThrow(new IllegalArgumentException());
        when(config.getAreaIdConfig(3)).thenReturn(areaIdConfigDisabled);

        List<CarSubscription> sanitizedOptions =
                InputSanitizationUtils.sanitizeEnableVariableUpdateRate(featureFlags, config,
                inputOption);

        assertThat(sanitizedOptions).containsExactly(
                newCarSubscription(PROPERTY_ID, new int[]{1}, DEFAULT_UPDATE_RATE_HZ, true),
                newCarSubscription(PROPERTY_ID, new int[]{2, 3}, DEFAULT_UPDATE_RATE_HZ, false));
    }

    @Test
    public void testSanitizeResolution() {
        FeatureFlags featureFlags = mock(FeatureFlags.class);
        CarPropertyConfig config = mock(CarPropertyConfig.class);
        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE);
        when(featureFlags.subscriptionWithResolution()).thenReturn(false);

        assertThat(InputSanitizationUtils.sanitizeResolution(featureFlags,
                config, 123.456f)).isEqualTo(0.0f);

        when(featureFlags.subscriptionWithResolution()).thenReturn(true);
        assertThat(InputSanitizationUtils.sanitizeResolution(featureFlags,
                config, 123.456f)).isEqualTo(0.0f);

        when(config.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        assertThat(InputSanitizationUtils.sanitizeResolution(featureFlags,
                config, 0.0f)).isEqualTo(0.0f);
        assertThat(InputSanitizationUtils.sanitizeResolution(featureFlags,
                config, 1.0f)).isEqualTo(1.0f);
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizationUtils.sanitizeResolution(featureFlags,
                        config, 2.0f));
    }

    private static CarSubscription newCarSubscription(int propertyId, int[] areaIds,
            float updateRateHz, boolean enableVur) {
        CarSubscription option = new CarSubscription();
        option.propertyId = propertyId;
        option.areaIds = areaIds;
        option.updateRateHz = updateRateHz;
        option.enableVariableUpdateRate = enableVur;
        return option;
    }
}

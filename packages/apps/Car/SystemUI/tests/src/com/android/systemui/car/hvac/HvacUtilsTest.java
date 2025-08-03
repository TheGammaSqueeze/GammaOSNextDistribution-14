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

package com.android.systemui.car.hvac;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@CarSystemUiTest
@SmallTest
public class HvacUtilsTest extends SysuiTestCase {
    @Mock
    private CarPropertyConfig<Float> mFloatCarPropertyConfig;
    @Mock
    private CarPropertyConfig<Integer> mIntegerCarPropertyConfig;
    @Mock
    private AreaIdConfig<Integer> mAreaIdConfig1;
    @Mock
    private AreaIdConfig<Integer> mAreaIdConfig2;
    @Mock
    private AreaIdConfig<Integer> mAreaIdConfig3;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isFalse();
    }

    @Test
    public void getHighestMinValueForAllAreaIds_nonInteger_returnsNull() {
        when(mFloatCarPropertyConfig.getPropertyType()).thenReturn(Float.class);
        assertThat(HvacUtils.getHighestMinValueForAllAreaIds(mFloatCarPropertyConfig))
                .isEqualTo(null);
    }

    @Test
    public void getHighestMinValueForAllAreaIds_integer_returnsHighest() {
        when(mAreaIdConfig1.getMinValue()).thenReturn(1);
        when(mAreaIdConfig2.getMinValue()).thenReturn(2);
        when(mAreaIdConfig3.getMinValue()).thenReturn(3);
        when(mIntegerCarPropertyConfig.getPropertyType()).thenReturn(Integer.class);
        when(mIntegerCarPropertyConfig.getAreaIdConfigs())
                .thenReturn(List.of(mAreaIdConfig1, mAreaIdConfig2, mAreaIdConfig3));
        assertThat(HvacUtils.getHighestMinValueForAllAreaIds(mIntegerCarPropertyConfig))
                .isEqualTo(3);
    }

    @Test
    public void getHighestMinValueForAllAreaIds_integer_ignoresNullValues() {
        when(mAreaIdConfig1.getMinValue()).thenReturn(1);
        when(mAreaIdConfig2.getMinValue()).thenReturn(2);
        when(mAreaIdConfig3.getMinValue()).thenReturn(null);
        when(mIntegerCarPropertyConfig.getPropertyType()).thenReturn(Integer.class);
        when(mIntegerCarPropertyConfig.getAreaIdConfigs())
                .thenReturn(List.of(mAreaIdConfig1, mAreaIdConfig2, mAreaIdConfig3));
        assertThat(HvacUtils.getHighestMinValueForAllAreaIds(mIntegerCarPropertyConfig))
                .isEqualTo(2);
    }

    @Test
    public void getLowestMaxValueForAllAreaIds_nonInteger_returnsNull() {
        when(mFloatCarPropertyConfig.getPropertyType()).thenReturn(Float.class);
        assertThat(HvacUtils.getLowestMaxValueForAllAreaIds(mFloatCarPropertyConfig))
                .isEqualTo(null);
    }

    @Test
    public void getLowestMaxValueForAllAreaIds_integer_returnsLowest() {
        when(mAreaIdConfig1.getMaxValue()).thenReturn(1);
        when(mAreaIdConfig2.getMaxValue()).thenReturn(2);
        when(mAreaIdConfig3.getMaxValue()).thenReturn(3);
        when(mIntegerCarPropertyConfig.getPropertyType()).thenReturn(Integer.class);
        when(mIntegerCarPropertyConfig.getAreaIdConfigs())
                .thenReturn(List.of(mAreaIdConfig1, mAreaIdConfig2, mAreaIdConfig3));
        assertThat(HvacUtils.getLowestMaxValueForAllAreaIds(mIntegerCarPropertyConfig))
                .isEqualTo(1);
    }

    @Test
    public void getLowestMaxValueForAllAreaIds_integer_ignoresNullValues() {
        when(mAreaIdConfig1.getMaxValue()).thenReturn(0);
        when(mAreaIdConfig2.getMaxValue()).thenReturn(1);
        when(mAreaIdConfig3.getMaxValue()).thenReturn(null);
        when(mIntegerCarPropertyConfig.getPropertyType()).thenReturn(Integer.class);
        when(mIntegerCarPropertyConfig.getAreaIdConfigs())
                .thenReturn(List.of(mAreaIdConfig1, mAreaIdConfig2, mAreaIdConfig3));
        assertThat(HvacUtils.getLowestMaxValueForAllAreaIds(mIntegerCarPropertyConfig))
                .isEqualTo(0);
    }
}

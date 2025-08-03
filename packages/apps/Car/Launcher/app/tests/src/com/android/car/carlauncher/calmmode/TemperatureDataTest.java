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

package com.android.car.carlauncher.calmmode;

import static org.junit.Assert.assertEquals;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.icu.util.MeasureUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

public class TemperatureDataTest extends AbstractExtendedMockitoTestCase {

    private static final float TEMP_CONVERSION_DELTA = 0.005f;
    private static final Locale TEST_LOCALE_US = Locale.US;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBuilder_celsius_objectCreated() {
        float testValue = 25.12345f;

        TemperatureData temp = new TemperatureData.Builder().setValueCelsius(testValue).build();

        assertEquals(MeasureUnit.CELSIUS, temp.getUnit());
        assertEquals(testValue, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testBuilder_fahrenheit_objectCreated() {
        float testValue = 80.1234f;

        TemperatureData temp = new TemperatureData.Builder().setValueFahrenheit(testValue).build();

        assertEquals(testValue, TEMP_CONVERSION_DELTA, temp.getValue());
        assertEquals(MeasureUnit.FAHRENHEIT, temp.getUnit());
    }

    @Test
    public void testBuilder_noValue_defaultsToZeroCelsius() {
        float tempZero = 0f;

        TemperatureData temp = new TemperatureData.Builder().build();

        assertEquals(tempZero, temp.getValue(), TEMP_CONVERSION_DELTA);
        assertEquals(MeasureUnit.CELSIUS, temp.getUnit());
    }

    @Test
    public void testConversion_celsiusDecimalPositive_convertsToF() {
        float tempCVal = 25.12345f;
        float tempFExpected = 77.22f;

        TemperatureData temp = new TemperatureData.Builder().setValueCelsius(tempCVal).build();
        temp.convertToFahrenheit();

        assertEquals(tempFExpected, temp.getValue(), TEMP_CONVERSION_DELTA);
        assertEquals(MeasureUnit.FAHRENHEIT, temp.getUnit());
    }

    @Test
    public void testConversion_celsiusRoundPositive_convertsToF() {
        float tempCVal = 25f;
        float tempFExpected = 77f;

        TemperatureData temp = new TemperatureData.Builder().setValueCelsius(tempCVal).build();
        temp.convertToFahrenheit();

        assertEquals(tempFExpected, temp.getValue(), TEMP_CONVERSION_DELTA);
        assertEquals(temp.getUnit(), MeasureUnit.FAHRENHEIT);
    }


    @Test
    public void testConversion_celsiusNegative_convertsToF() {
        float tempCVal = -30f;
        float tempFExpected = -22f;

        TemperatureData temp =
                new TemperatureData.Builder().setValueCelsius(tempCVal).build();
        temp.convertToFahrenheit();

        assertEquals(MeasureUnit.FAHRENHEIT, temp.getUnit());
        assertEquals(tempFExpected, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConversion_celsiusConvertToC_NoOp() {
        float tempCVal = -10f;

        TemperatureData temp =
                new TemperatureData.Builder().setValueCelsius(tempCVal).build();
        temp.convertToCelsius();

        assertEquals(MeasureUnit.CELSIUS, temp.getUnit());
        assertEquals(tempCVal, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConversion_fahrenheitPositive_convertsToC() {
        float tempFVal = 77.2f;
        float tempCExpected = 25.11f;

        TemperatureData temp =
                new TemperatureData.Builder().setValueFahrenheit(tempFVal).build();
        temp.convertToCelsius();

        assertEquals(MeasureUnit.CELSIUS, temp.getUnit());
        assertEquals(tempCExpected, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testBuilder_fahrenheitNegative_convertsToC() {
        float tempF = -10f;
        float tempCExpected = -23.33f;

        TemperatureData temp =
                new TemperatureData.Builder().setValueFahrenheit(tempF).build();
        temp.convertToCelsius();

        assertEquals(MeasureUnit.CELSIUS, temp.getUnit());
        assertEquals(tempCExpected, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testBuilder_fahrenheit_convertsToFNoOp() {
        float tempF = -10f;

        TemperatureData temp =
                new TemperatureData.Builder().setValueFahrenheit(tempF).build();
        temp.convertToFahrenheit();

        assertEquals(MeasureUnit.FAHRENHEIT, temp.getUnit());
        assertEquals(tempF, temp.getValue(), TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testBuildTemperatureString_positiveCelsius_matchesForLocaleUS() {
        float tempCVal = 25.12345f;
        String tempCExpected = "25°C";
        TemperatureData tempC = new TemperatureData.Builder().setValueCelsius(tempCVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempC, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempCExpected, tempCActual);
    }

    @Test
    public void testBuildTemperatureString_positiveCelsiusDoNotShowUnit_matchesForLocaleUS() {
        float tempCVal = 25.12345f;
        String tempCExpected = "25°";
        TemperatureData tempC = new TemperatureData.Builder().setValueCelsius(tempCVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempC, TEST_LOCALE_US,
                /* showUnit = */ false);

        assertEquals(tempCExpected, tempCActual);
    }

    @Test
    public void testBuildTemperatureString_positiveFahrenheit_matchesForLocaleUS() {
        float tempFVal = 77.2212f;
        String tempFExpected = "77°F";
        TemperatureData tempF = new TemperatureData.Builder().setValueFahrenheit(tempFVal).build();

        String tempFActual = TemperatureData.buildTemperatureString(tempF, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempFExpected, tempFActual);
    }

    @Test
    public void testBuildTemperatureString_positiveFahrenheitDoNotShowUnit_matchesForLocaleUS() {
        float tempFVal = 77.2212f;
        String tempFExpected = "77°";
        TemperatureData tempF = new TemperatureData.Builder().setValueFahrenheit(tempFVal).build();

        String tempFActual = TemperatureData.buildTemperatureString(tempF, TEST_LOCALE_US,
                /* showUnit = */ false);

        assertEquals(tempFExpected, tempFActual);
    }

    @Test
    public void testBuildTemperatureString_negativeCelsius_matchesForLocaleUS() {
        float tempCVal = -20f;
        String tempCExpected = "-20°C";
        TemperatureData tempC = new TemperatureData.Builder().setValueCelsius(tempCVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempC, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempCExpected, tempCActual);
    }

    @Test
    public void testBuildTemperatureString_negativeCelsiusDoNotShowUnit_matchesForLocaleUS() {
        float tempCVal = -20f;
        String tempCExpected = "-20°";
        TemperatureData tempC = new TemperatureData.Builder().setValueCelsius(tempCVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempC, TEST_LOCALE_US,
                /* showUnit = */ false);

        assertEquals(tempCExpected, tempCActual);
    }

    @Test
    public void testBuildTemperatureString_negativeFahrenheit_matchesForLocaleUS() {
        float tempFVal = -4f;
        String tempFExpected = "-4°F";
        TemperatureData tempF = new TemperatureData.Builder().setValueFahrenheit(tempFVal).build();

        String tempFActual = TemperatureData.buildTemperatureString(tempF, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempFExpected, tempFActual);
    }

    @Test
    public void testBuildTemperatureString_negativeFahrenheitDoNotShowUnit_matchesForLocaleUS() {
        float tempFVal = -4f;
        String tempFExpected = "-4°";
        TemperatureData tempF = new TemperatureData.Builder().setValueFahrenheit(tempFVal).build();

        String tempFActual = TemperatureData.buildTemperatureString(tempF, TEST_LOCALE_US,
                /* showUnit = */ false);

        assertEquals(tempFExpected, tempFActual);
    }

    @Test
    public void testBuildTemperatureString_zeroCelsius_matchesForLocaleUS() {
        float tempCVal = 0f;
        String tempCExpected = "0°C";
        TemperatureData tempC = new TemperatureData.Builder().setValueCelsius(tempCVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempC, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempCExpected, tempCActual);
    }

    @Test
    public void testBuildTemperatureString_zeroFahrenheit_matchesForLocaleUS() {
        float tempFVal = 0f;
        String tempFExpected = "0°C";
        TemperatureData tempF = new TemperatureData.Builder().setValueCelsius(tempFVal).build();

        String tempCActual = TemperatureData.buildTemperatureString(tempF, TEST_LOCALE_US,
                /* showUnit = */ true);

        assertEquals(tempFExpected, tempCActual);
    }

    @Test
    public void testConvertCelsiusToFahrenheit_positiveRoundC_convertsToF() {
        float tempCVal = 25f;
        float tempFExpected = 77f;

        float tempFActual = TemperatureData.convertCelsiusToFahrenheit(tempCVal);

        assertEquals(tempFExpected, tempFActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertCelsiusToFahrenheit_positiveDecimalC_convertsToF() {
        float tempCVal = 25.1234f;
        float tempFExpected = 77.22f;

        float tempFActual = TemperatureData.convertCelsiusToFahrenheit(tempCVal);

        assertEquals(tempFExpected, tempFActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertCelsiusToFahrenheit_negativeRoundC_convertsToF() {
        float tempCVal = -20f;
        float tempFExpected = -4f;

        float tempFActual = TemperatureData.convertCelsiusToFahrenheit(tempCVal);

        assertEquals(tempFExpected, tempFActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertCelsiusToFahrenheit_zeroC_convertsToF() {
        float tempCVal = 0f;
        float tempFExpected = 32f;

        float tempFActual = TemperatureData.convertCelsiusToFahrenheit(tempCVal);

        assertEquals(tempFExpected, tempFActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertCelsiusToFahrenheit_celsiusIsZeroF_convertsToF() {
        float tempCVal = -17.778f;
        float tempFExpected = 0f;

        float tempFActual = TemperatureData.convertCelsiusToFahrenheit(tempCVal);

        assertEquals(tempFExpected, tempFActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertFahrenheitToCelsius_positiveRoundF_convertsToC() {
        float tempFVal = 77f;
        float tempCExpected = 25f;

        float tempCActual = TemperatureData.convertFahrenheitToCelsius(tempFVal);

        assertEquals(tempCExpected, tempCActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertFahrenheitToCelsius_positiveDecimalF_convertsToC() {
        float tempFVal = 77.22f;
        float tempCExpected = 25.1234f;

        float tempCActual = TemperatureData.convertFahrenheitToCelsius(tempFVal);

        assertEquals(tempCExpected, tempCActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertFahrenheitToCelsius_negativeRoundF_convertsToC() {
        float tempFVal = -4f;
        float tempCExpected = -20f;

        float tempCActual = TemperatureData.convertFahrenheitToCelsius(tempFVal);

        assertEquals(tempCExpected, tempCActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertFahrenheitToCelsius_zeroF_convertsToC() {
        float tempFVal = 0f;
        float tempCExpected = -17.778f;

        float tempCActual = TemperatureData.convertFahrenheitToCelsius(tempFVal);

        assertEquals(tempCExpected, tempCActual, TEMP_CONVERSION_DELTA);
    }

    @Test
    public void testConvertFahrenheitToCelsius_fahrenheitIsZeroC_convertsToC() {
        float tempFVal = 32f;
        float tempCExpected = 0f;

        float tempCActual = TemperatureData.convertFahrenheitToCelsius(tempFVal);

        assertEquals(tempCExpected, tempCActual, TEMP_CONVERSION_DELTA);
    }
}

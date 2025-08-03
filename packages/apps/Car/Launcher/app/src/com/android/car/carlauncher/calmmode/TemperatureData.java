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

import android.icu.number.Notation;
import android.icu.number.NumberFormatter;
import android.icu.number.Precision;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Class used to represent temperature
 * <p>Contains a {@code float value}  and {@link MeasureUnit unit}
 * <p>Use {@link Builder} to create an instance of {@link TemperatureData}
 */
public class TemperatureData {

    private static final String TAG = TemperatureData.class.getSimpleName();
    private static final boolean DEBUG = Build.isDebuggable();
    private float mValue;
    @NonNull
    private MeasureUnit mUnit;

    private TemperatureData(float value, @NonNull MeasureUnit unit) {
        this.mValue = value;
        this.mUnit = unit;
    }

    /**
     * @param temperatureData temperature data
     * @param locale locale to use for creating string
     * @return compact string representation of the value based on the locale
     */
    @NonNull
    public static String buildTemperatureString(
            @NonNull TemperatureData temperatureData, @NonNull Locale locale, boolean showUnit) {
        return NumberFormatter.withLocale(locale)
                .notation(Notation.compactShort())
                .precision(Precision.integer())
                .unit(showUnit ? temperatureData.getUnit() : MeasureUnit.GENERIC_TEMPERATURE)
                .format(temperatureData.getValue())
                .toString();
    }

    /**
     * Converts temperature value from Celsius to Fahrenheit
     *
     * @param temperatureInCelsius temperature value in Celsius
     * @return temperature value in Fahrenheit
     */
    public static float convertCelsiusToFahrenheit(float temperatureInCelsius) {
        return (temperatureInCelsius * 9f / 5f) + 32;
    }

    /**
     * Converts temperature value from Fahrenheit to Celsius
     *
     * @param temperatureInFahrenheit temperature value in Fahrenheit
     * @return temperature value in Celsius
     */
    public static float convertFahrenheitToCelsius(float temperatureInFahrenheit) {
        return (temperatureInFahrenheit - 32) * 5f / 9f;
    }

    public float getValue() {
        return mValue;
    }

    @NonNull
    public MeasureUnit getUnit() {
        return mUnit;
    }

    @Override
    public String toString() {
        return "TemperatureData{" + "value=" + mValue + ", unit=" + mUnit + '}';
    }


    /** Converts the value and changes unit from Fahrenheit to Celsius,
     * does nothing if unit is already Celsius
     */
    public void convertToFahrenheit() {
        if (mUnit == MeasureUnit.CELSIUS) {
            mValue = convertCelsiusToFahrenheit(mValue);
            mUnit = MeasureUnit.FAHRENHEIT;
            return;
        }
        // no-op if unit is already FAHRENHEIT
        if (DEBUG) {
            Log.v(TAG, "Unit is already FAHRENHEIT. No conversion performed.");
        }
    }

    /** Converts the value and changes unit to Fahrenheit,
     * does nothing if unit is already Fahrenheit
     */
    public void convertToCelsius() {
        if (mUnit == MeasureUnit.FAHRENHEIT) {
            mValue = convertFahrenheitToCelsius(mValue);
            mUnit = MeasureUnit.CELSIUS;
        }
        // no-op if unit is already CELSIUS
        if (DEBUG) {
            Log.v(TAG, "Unit is already CELSIUS. No conversion performed.");
        }
    }

    public static final class Builder {

        private float mValue;
        @NonNull private MeasureUnit mUnit = MeasureUnit.CELSIUS;

        private Builder(float mValue, @NonNull MeasureUnit mUnit) {
            this.mValue = mValue;
            this.mUnit = mUnit;
        }

        public Builder() {}

        /**
         * @param data TemperatureData object used to create Builder
         * @return TemperatureData.Builder object
         */
        @NonNull
        public static Builder from(TemperatureData data) {
            return new Builder(data.getValue(), data.getUnit());
        }

        /**
         * @param temperatureCelsius temperature value in degrees Celsius
         * @return {@link Builder builder}
         */
        Builder setValueCelsius(float temperatureCelsius) {
            mValue = temperatureCelsius;
            mUnit = MeasureUnit.CELSIUS;
            return this;
        }

        Builder setValueFahrenheit(float temperatureFahrenheit) {
            mValue = temperatureFahrenheit;
            mUnit = MeasureUnit.FAHRENHEIT;
            return this;
        }

        TemperatureData build() {
            return new TemperatureData(mValue, mUnit);
        }
    }
}

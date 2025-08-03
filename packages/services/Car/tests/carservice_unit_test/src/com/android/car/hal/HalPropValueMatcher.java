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

package com.android.car.hal;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static org.mockito.ArgumentMatchers.argThat;

import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import org.mockito.ArgumentMatcher;

import java.util.Arrays;

public final class HalPropValueMatcher {

    private static final String TAG = HalPropValueMatcher.class.getSimpleName();

    /**
     * Matches if a {@link HalPropValue} has the given {@code prop}.
     */
    public static HalPropValue isProperty(int prop) {
        return argThat(new PropertyIdMatcher(prop));
    }

    /**
     * Matches if a {@link HalPropValue} has the given {@code prop} and {@code int32} values.
     */
    public static HalPropValue isPropertyWithValues(int prop, int...values) {
        return argThat(new PropertyIdMatcher(prop, values));
    }

    private static class PropertyIdMatcher implements ArgumentMatcher<HalPropValue> {

        final int mProp;
        private final int[] mValues;

        private PropertyIdMatcher(int prop) {
            this(prop, null);
        }

        private PropertyIdMatcher(int prop, int[] values) {
            mProp = prop;
            mValues = values;
        }

        @Override
        public boolean matches(HalPropValue argument) {
            Log.v(TAG, "PropertyIdMatcher: argument=" + argument);
            if (argument.getPropId() != mProp) {
                Log.w(TAG, "PropertyIdMatcher: Invalid prop on " + argument);
                return false;
            }
            if (mValues == null) return true;
            // Make sure values match
            if (mValues.length != argument.getInt32ValuesSize()) {
                Log.w(TAG, "PropertyIdMatcher: number of values (expected " + mValues.length
                        + ") mismatch on " + argument);
                return false;
            }

            for (int i = 0; i < mValues.length; i++) {
                if (mValues[i] != argument.getInt32Value(i)) {
                    Log.w(TAG, "PropertyIdMatcher: value mismatch at index " + i + " on " + argument
                            + ": expected " + Arrays.toString(mValues));
                    return false;
                }
            }
            return true;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
        public String toString() {
            return "prop: " + mProp + " values: " + Arrays.toString(mValues);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private HalPropValueMatcher() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}

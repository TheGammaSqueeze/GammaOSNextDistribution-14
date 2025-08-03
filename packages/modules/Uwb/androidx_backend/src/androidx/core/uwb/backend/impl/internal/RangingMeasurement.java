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

package androidx.core.uwb.backend.impl.internal;

import androidx.annotation.IntDef;

/** Measurement providing the value and confidence of the ranging. */
public class RangingMeasurement {

    @Confidence private final int mConfidence;
    private final float mValue;
    private final boolean mValid;

    public RangingMeasurement(@Confidence int confidence, float value, boolean valid) {
        this.mConfidence = confidence;
        this.mValue = value;
        mValid = valid;
    }

    /** Gets Confidence of this measurement. */
    @Confidence
    public int getConfidence() {
        return mConfidence;
    }

    /** Gets value of this measurement. */
    public float getValue() {
        return mValue;
    }

    /** Gets validity of this measurement. */
    public boolean isValid() {
        return mValid;
    }

    /** Possible confidence values for a {@link RangingMeasurement}. */
    @IntDef({CONFIDENCE_LOW, CONFIDENCE_MEDIUM, CONFIDENCE_HIGH})
    public @interface Confidence {}

    public static final int CONFIDENCE_LOW = 0;
    public static final int CONFIDENCE_MEDIUM = 1;
    public static final int CONFIDENCE_HIGH = 2;
}

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
package android.car;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used by {@link VehiclePropertyIds#GENERAL_SAFETY_REGULATION_COMPLIANCE} to
 * indicate what kind of general safety regulation compliance requirement is
 * enforced.
 */
public final class GsrComplianceType {
    /**
     * GSR compliance is not required.
     */
    public static final int GSR_COMPLIANCE_TYPE_NOT_REQUIRED = 0;
    /**
     * GSR compliance is required and the requirement solution version is 1.
     */
    public static final int GSR_COMPLIANCE_TYPE_REQUIRED_V1 = 1;

    /** @hide */
    @IntDef({
        GSR_COMPLIANCE_TYPE_NOT_REQUIRED,
        GSR_COMPLIANCE_TYPE_REQUIRED_V1
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {}

    private GsrComplianceType() {}
}

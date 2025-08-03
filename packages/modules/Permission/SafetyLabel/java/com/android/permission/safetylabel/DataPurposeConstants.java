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

package com.android.permission.safetylabel;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Constants for determining valid {@link Integer} data usage purposes for usage within
 * {@link DataType}
 */
public class DataPurposeConstants {

    /** List of valid data usage purposes */
    @Retention(SOURCE)
    @IntDef(
            prefix = "PURPOSE_",
            value = {
                PURPOSE_APP_FUNCTIONALITY,
                PURPOSE_ANALYTICS,
                PURPOSE_DEVELOPER_COMMUNICATIONS,
                PURPOSE_FRAUD_PREVENTION_SECURITY,
                PURPOSE_ADVERTISING,
                PURPOSE_PERSONALIZATION,
                PURPOSE_ACCOUNT_MANAGEMENT,
            })
    public @interface Purpose {}

    public static final int PURPOSE_APP_FUNCTIONALITY = 1;
    public static final int PURPOSE_ANALYTICS = 2;
    public static final int PURPOSE_DEVELOPER_COMMUNICATIONS = 3;
    public static final int PURPOSE_FRAUD_PREVENTION_SECURITY = 4;
    public static final int PURPOSE_ADVERTISING = 5;
    public static final int PURPOSE_PERSONALIZATION = 6;
    public static final int PURPOSE_ACCOUNT_MANAGEMENT = 7;
    // RESERVED/DEPRECATED  = 8
    // RESERVED/DEPRECATED  = 9

    /** {@link Set} of valid {@link Integer} purposes */
    @Purpose
    public  static final Set<Integer> VALID_PURPOSES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    PURPOSE_APP_FUNCTIONALITY,
                                    PURPOSE_ANALYTICS,
                                    PURPOSE_DEVELOPER_COMMUNICATIONS,
                                    PURPOSE_FRAUD_PREVENTION_SECURITY,
                                    PURPOSE_ADVERTISING,
                                    PURPOSE_PERSONALIZATION,
                                    PURPOSE_ACCOUNT_MANAGEMENT)));

    /** Returns {@link Set} of valid {@link Integer} purpose */
    @Purpose
    public static Set<Integer> getValidPurposes() {
        return VALID_PURPOSES;
    }
}

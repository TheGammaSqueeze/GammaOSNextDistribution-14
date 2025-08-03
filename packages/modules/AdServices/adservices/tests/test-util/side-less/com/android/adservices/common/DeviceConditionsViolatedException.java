/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.junit.AssumptionViolatedException;

import java.util.List;

/** Utility Exception class to surface device conditions being violated. */
final class DeviceConditionsViolatedException extends AssumptionViolatedException {

    private final ImmutableList<String> mReasons;

    DeviceConditionsViolatedException(List<String> reasons) {
        super(
                String.format(
                        "Assumptions violated: %d\nReasons: %s",
                        reasons.size(), String.join("\n", reasons)));
        this.mReasons = ImmutableList.copyOf(reasons);
    }

    @VisibleForTesting
    ImmutableList<String> getConditionsViolatedReasons() {
        return mReasons;
    }
}

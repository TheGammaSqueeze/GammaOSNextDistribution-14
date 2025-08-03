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

package com.android.adservices.service.signals.evict;

import android.annotation.NonNull;

import com.android.adservices.data.signals.DBProtectedSignal;

import java.util.List;

/** Signal size calculator for signal eviction. */
public class SignalSizeCalculator {

    /** Calculate the signal size for a list of signals in context of signal eviction. */
    public static int calculate(@NonNull List<DBProtectedSignal> signalList) {
        return signalList.stream().mapToInt(SignalSizeCalculator::calculate).sum();
    }

    /** Calculate the signal size for a signal in context of signal eviction. */
    public static int calculate(@NonNull DBProtectedSignal signal) {
        return signal.getKey().length + signal.getValue().length;
    }
}

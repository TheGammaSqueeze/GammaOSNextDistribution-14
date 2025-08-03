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

package com.android.server.art.model;

import android.annotation.NonNull;
import android.annotation.SystemApi;

import com.android.internal.annotations.Immutable;

import com.google.auto.value.AutoValue;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
@AutoValue
public abstract class OperationProgress {
    /** @hide */
    protected OperationProgress() {}

    /** @hide */
    public static @NonNull OperationProgress create(int current, int total) {
        return new AutoValue_OperationProgress(current, total);
    }

    /** The overall progress, in the range of [0, 100]. */
    public int getPercentage() {
        return getTotal() == 0 ? 100 : 100 * getCurrent() / getTotal();
    }

    /**
     * The number of processed items. Can be 0, which means the operation was just started.
     *
     * Currently, this is the number of packages, for which dexopt has been done, regardless
     * of the results (performed, failed, skipped, etc.).
     *
     * @hide
     */
    public abstract int getCurrent();

    /**
     * The total number of items. Stays constant during the operation.
     *
     * Currently, this is the total number of packages to dexopt.
     *
     * @hide
     */
    public abstract int getTotal();
}

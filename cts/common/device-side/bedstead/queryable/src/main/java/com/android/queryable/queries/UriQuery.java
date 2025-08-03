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

package com.android.queryable.queries;

import android.net.Uri;

import androidx.annotation.CheckResult;

import com.android.queryable.Queryable;

/** Query for a {@link Uri}. */
public interface UriQuery<E extends Queryable> extends Query<Uri> {

    /** Queries a {@link Uri}. */
    static UriQueryHelper.UriQueryBase uri() {
        return new UriQueryHelper.UriQueryBase();
    }

    /** Gets the {@link String} value of the {@link Uri}. */
    @CheckResult
    StringQuery<E> stringValue();

    /** Requires the {@link Uri} is equal to {@code uri}. */
    E isEqualTo(Uri uri);
}

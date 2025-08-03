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

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.CheckResult;

import com.android.queryable.Queryable;

import java.io.Serializable;

/** Query for a single key in a {@link Bundle}. */
public interface BundleKeyQuery<E extends Queryable> extends Queryable, Serializable, Parcelable {

    /** Require that the key exists. */
    E exists();

    /** Require that the key does not exist. */
    E doesNotExist();

    @CheckResult
    StringQuery<E> stringValue();

    @CheckResult
    SerializableQuery<E> serializableValue();

    @CheckResult
    BundleQuery<E> bundleValue();

    /**
     * The integer value of the key/
     */
    @CheckResult
    IntegerQuery<E> integerValue();

    /**
     * The long value of the key/
     */
    @CheckResult
    LongQuery<E> longValue();

    /**
     * The boolean value of the key/
     */
    @CheckResult
    BooleanQuery<E> booleanValue();

    /**
     * The string list value of the key/
     */
    @CheckResult
    ListQuery<E, String> stringListValue();

    /**
     * The integer list value of the key/
     */
    @CheckResult
    ListQuery<E, Integer> integerListValue();
}

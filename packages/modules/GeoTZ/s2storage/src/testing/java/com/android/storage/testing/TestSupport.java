/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.storage.testing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Methods to assist in writing readable tests. */
public final class TestSupport {

    private TestSupport() {
    }

    /** Returns a list from a varargs param. */
    @SafeVarargs
    public static <E> List<E> listOf(E... values) {
        return Arrays.asList(values);
    }

    /** Returns a set from a varargs param. */
    @SafeVarargs
    public static <E> Set<E> setOf(E... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}

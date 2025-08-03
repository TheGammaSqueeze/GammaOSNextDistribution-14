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

package com.android.csuite.testing;

import com.google.common.truth.Correspondence;

/**
 * Useful implementations of {@link Correspondence}.
 *
 * <p>Correspondences are used with {@code IterableSubject.comparingElementsUsing(Correspondence)}
 * and allow for making assertions on elements besides simple equality. See {@link Correspondence}
 * for more information.
 */
// TODO(hzalek): Move this into a dedicated testing library.
public final class Correspondences {

    private static final Correspondence<Object, Class<?>> INSTANCE_OF =
            Correspondence.from(
                    (Object obj, Class<?> clazz) -> {
                        return clazz.isInstance(obj);
                    },
                    "is an instance of");

    /**
     * Returns a {@link Correspondence} that determines whether elements are instances of the class
     * they are compared to.
     */
    public static Correspondence<Object, Class<?>> instanceOf() {
        return INSTANCE_OF;
    }

    private Correspondences() {}
}

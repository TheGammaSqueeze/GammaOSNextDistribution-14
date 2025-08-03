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

import com.android.queryable.Queryable;

import java.io.Serializable;

/** Query for a {@link Serializable}. */
public interface SerializableQuery<E extends Queryable> extends Query<Serializable> {

    /** Queries a {@link Serializable}. */
    static SerializableQueryHelper.SerializableQueryBase serializable() {
        return new SerializableQueryHelper.SerializableQueryBase();
    }

    /** Require that the {@link Serializable} is equal to {@code serializable}. */
    E isEqualTo(Serializable serializable);
}

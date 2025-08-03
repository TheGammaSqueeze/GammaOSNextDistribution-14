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

package com.android.adservices.service.common.bhttp;

import android.annotation.NonNull;

/**
 * Define the ability to convert a binary http related object to bytes.
 *
 * <p>Methods in this interface should be package private by default.
 */
abstract class BinaryHttpSerializableComponent {
    /**
     * Returns the serialized representation with a 2D byte array.
     *
     * <p>Structure of the 2D array depends on the extending class.
     *
     * <p>General contract is the flattened 1D array is the binary representation of the object.
     *
     * <p>This is help to reduce array coping during serializing each component, so we can do actual
     * data coping only when we actually construct the final byte array.
     */
    @NonNull
    abstract byte[][] knownLengthSerialize();

    /** Returns the number of byte arrays returned by {@link #knownLengthSerialize()}. */
    abstract int getKnownLengthSerializedSectionsCount();
}

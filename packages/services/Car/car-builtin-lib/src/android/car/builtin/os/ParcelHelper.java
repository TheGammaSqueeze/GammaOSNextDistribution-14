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

package android.car.builtin.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.util.ArraySet;

/**
 * Helper for {@link Parcel}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ParcelHelper {
    private ParcelHelper() {
        throw new UnsupportedOperationException();
    }

    /** Reads array of string from the passed parcel */
    @Nullable
    public static String[] readStringArray(@NonNull Parcel parcel) {
        return parcel.readStringArray();
    }

    /** Reads a Blob */
    @Nullable
    public static byte[] readBlob(@NonNull Parcel parcel) {
        return parcel.readBlob();
    }

    /** Writes the byte array to the Parcel */
    public static void writeBlob(@NonNull Parcel parcel, @Nullable byte[] b) {
        parcel.writeBlob(b);
    }

    /** Reads ArraySet */
    @Nullable
    public static ArraySet<? extends Object> readArraySet(@NonNull Parcel parcel,
            @Nullable ClassLoader loader) {
        return parcel.readArraySet(loader);
    }

    /** Writes ArraySet */
    public static void writeArraySet(@NonNull Parcel parcel,
            @Nullable ArraySet<? extends Object> val) {
        parcel.writeArraySet(val);
    }
}

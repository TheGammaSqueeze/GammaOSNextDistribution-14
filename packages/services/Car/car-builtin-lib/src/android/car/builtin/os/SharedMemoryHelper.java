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
import android.annotation.SystemApi;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;

import java.io.IOException;

/**
 * Helper for {@link SharedMemory}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class SharedMemoryHelper {
    private SharedMemoryHelper() {
        throw new UnsupportedOperationException();
    }

    /** Returns the backing file for the shared memory wrapped in {@code ParcelFileDescriptor}*/
    @NonNull
    public static ParcelFileDescriptor createParcelFileDescriptor(
            @NonNull SharedMemory memory) throws IOException {
        // Must duplicate the file descriptor because it is currently owned by memory, and we also
        // want the returned ParcelFileDescriptor to own it.
        return memory.getFdDup();
    }
}

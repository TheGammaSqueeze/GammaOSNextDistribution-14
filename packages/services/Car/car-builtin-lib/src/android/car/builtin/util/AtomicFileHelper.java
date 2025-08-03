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

package android.car.builtin.util;

import android.annotation.SystemApi;
import android.util.AtomicFile;

/**
 * Helper for {@link AtomicFile}
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AtomicFileHelper {
    private AtomicFileHelper()   {
        throw new UnsupportedOperationException();
    }

    /** Check {@link AtomicFile#exists()} */
    public static boolean exists(AtomicFile file) {
        return file.exists();
    }
}

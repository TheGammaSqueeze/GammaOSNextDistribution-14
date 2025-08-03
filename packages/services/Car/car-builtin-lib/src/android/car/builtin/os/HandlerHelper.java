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

package android.car.builtin.os;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Handler;

/**
 * Helper for {@link android.os.Handler}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class HandlerHelper {
    private HandlerHelper() {
        throw new UnsupportedOperationException();
    }

    /** Remove pending messages using equal. */
    public static void removeEqualMessages(Handler handler, int what, @Nullable Object object) {
        handler.removeEqualMessages(what, object);
    }
}

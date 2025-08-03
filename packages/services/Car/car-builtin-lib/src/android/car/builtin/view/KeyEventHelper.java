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

package android.car.builtin.view;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.view.KeyEvent;

/**
 * Provides access to {@code android.view.KeyEvent} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class KeyEventHelper {

    private KeyEventHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the display id for the key event passed as argument.
     * @deprecated Use {@link InputEventHelper} instead of this class.
     */
    @Deprecated
    public static void setDisplayId(@NonNull KeyEvent keyEvent, int newDisplayId) {
        keyEvent.setDisplayId(newDisplayId);
    }
}

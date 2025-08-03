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

package android.car.builtin.input;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.hardware.input.InputManager;
import android.os.IBinder;
import android.view.View;

/**
 * Helper for {@link InputManager}
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class InputManagerHelper {

    private InputManagerHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Injects the event passed as parameter in async mode.
     *
     * @param inputManager the Android input manager used to inject the event
     * @param event        the event to inject
     * @return {@code true} if injection succeeds
     */
    public static boolean injectInputEvent(@NonNull InputManager inputManager,
            @NonNull android.view.InputEvent event) {
        return inputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    /**
     * See {@link InputManager#pilferPointers(IBinder)}.
     */
    public static void pilferPointers(@NonNull InputManager inputManager, @NonNull View v) {
        inputManager.pilferPointers(v.getViewRootImpl().getInputToken());
    }
}

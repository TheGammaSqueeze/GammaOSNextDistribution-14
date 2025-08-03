/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.hardware.input.InputManager;
import android.os.InputEventInjectionSync;
import android.view.InputEvent;

public class InputManagerCompat {
    private final InputManager mInputManager;

    public InputManagerCompat(InputManager inputManager) {
        mInputManager = inputManager;
    }

    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = InputEventInjectionSync.NONE;

    public boolean injectInputEvent(InputEvent event, int mode) {
        return mInputManager.injectInputEvent(event, mode);
    }
}

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

import static com.google.common.truth.Truth.assertThat;

import android.view.Display;
import android.view.KeyEvent;

import org.junit.Test;

public final class KeyEventHelperTest {

    @Test
    public void testAssignDisplayId() {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);

        KeyEventHelper.setDisplayId(keyEvent, Display.INVALID_DISPLAY);
        assertThat(keyEvent.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);

        KeyEventHelper.setDisplayId(keyEvent, Display.DEFAULT_DISPLAY);
        assertThat(keyEvent.getDisplayId()).isEqualTo(Display.DEFAULT_DISPLAY);
    }
}

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

package android.car.builtin.view;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.junit.Test;

public final class InputEventHelperTest {

    @Test
    public void testAssignDisplayId_toKeyEvent() {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);

        InputEventHelper.setDisplayId(keyEvent, Display.INVALID_DISPLAY);
        assertThat(keyEvent.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);

        InputEventHelper.setDisplayId(keyEvent, Display.DEFAULT_DISPLAY);
        assertThat(keyEvent.getDisplayId()).isEqualTo(Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testAssignDisplayId_toMotionEvent() {
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent motionEvent = MotionEvent.obtain(
                    currentTime /* downTime */,
                    currentTime /* eventTime */,
                    MotionEvent.ACTION_DOWN,
                    1.0f /* x */,
                    1.0f /* y */,
                    0 /* metaState */
                );

        InputEventHelper.setDisplayId(motionEvent, Display.INVALID_DISPLAY);
        assertThat(motionEvent.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);

        InputEventHelper.setDisplayId(motionEvent, Display.DEFAULT_DISPLAY);
        assertThat(motionEvent.getDisplayId()).isEqualTo(Display.DEFAULT_DISPLAY);
    }
}

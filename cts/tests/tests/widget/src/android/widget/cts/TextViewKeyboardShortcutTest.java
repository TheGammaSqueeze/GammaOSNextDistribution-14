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

package android.widget.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextViewKeyboardShortcutTest {

    private static final int META_CTRL = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
    private static final int META_SHIFT = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;

    private static void onSimpleKey(View view, int keyCode) {
        view.onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        view.onKeyUp(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    @UiThreadTest
    @Test
    public void testUndoInvoke() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);

        onSimpleKey(et, KeyEvent.KEYCODE_H);
        onSimpleKey(et, KeyEvent.KEYCODE_E);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_O);

        assertThat(et.getText().toString()).isEqualTo("hello");

        et.onKeyShortcut(KeyEvent.KEYCODE_Z, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Z,
                0,  // key repeat
                META_CTRL));

        // We don't expect how UNDO reverts the change. Just expect something happened.
        assertThat(et.getText().toString()).isNotEqualTo("hello");
    }

    @UiThreadTest
    @Test
    public void testRedoInvoke_byCtrlShiftZ() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);

        onSimpleKey(et, KeyEvent.KEYCODE_H);
        onSimpleKey(et, KeyEvent.KEYCODE_E);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_O);

        assertThat(et.getText().toString()).isEqualTo("hello");

        et.onKeyShortcut(KeyEvent.KEYCODE_Z, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Z,
                0,  // key repeat
                META_CTRL));

        // We don't expect how UNDO reverts the change. Just expect something happened.
        assertThat(et.getText().toString()).isNotEqualTo("hello");

        et.onKeyShortcut(KeyEvent.KEYCODE_Z, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Z,
                0,  // key repeat
                META_CTRL | META_SHIFT));

        assertThat(et.getText().toString()).isEqualTo("hello");
    }

    @UiThreadTest
    @Test
    public void testRedoInvoke_byCtrlY() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditText et = new EditText(ctx);

        onSimpleKey(et, KeyEvent.KEYCODE_H);
        onSimpleKey(et, KeyEvent.KEYCODE_E);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_L);
        onSimpleKey(et, KeyEvent.KEYCODE_O);

        assertThat(et.getText().toString()).isEqualTo("hello");

        et.onKeyShortcut(KeyEvent.KEYCODE_Z, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Z,
                0,  // key repeat
                META_CTRL));

        // We don't expect how UNDO reverts the change. Just expect something happened.
        assertThat(et.getText().toString()).isNotEqualTo("hello");

        et.onKeyShortcut(KeyEvent.KEYCODE_Y, new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Y,
                0,  // key repeat
                META_CTRL));

        assertThat(et.getText().toString()).isEqualTo("hello");
    }
}

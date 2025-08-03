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

package com.android.layoutlib.bridge.util;

import android.util.TimeUtils;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.awt.Toolkit;

import static android.view.KeyEvent.KEYCODE_0;
import static android.view.KeyEvent.KEYCODE_1;
import static android.view.KeyEvent.KEYCODE_2;
import static android.view.KeyEvent.KEYCODE_3;
import static android.view.KeyEvent.KEYCODE_4;
import static android.view.KeyEvent.KEYCODE_5;
import static android.view.KeyEvent.KEYCODE_6;
import static android.view.KeyEvent.KEYCODE_7;
import static android.view.KeyEvent.KEYCODE_8;
import static android.view.KeyEvent.KEYCODE_9;
import static android.view.KeyEvent.KEYCODE_A;
import static android.view.KeyEvent.KEYCODE_ALT_LEFT;
import static android.view.KeyEvent.KEYCODE_ALT_RIGHT;
import static android.view.KeyEvent.KEYCODE_APOSTROPHE;
import static android.view.KeyEvent.KEYCODE_B;
import static android.view.KeyEvent.KEYCODE_BACKSLASH;
import static android.view.KeyEvent.KEYCODE_BREAK;
import static android.view.KeyEvent.KEYCODE_C;
import static android.view.KeyEvent.KEYCODE_COMMA;
import static android.view.KeyEvent.KEYCODE_CTRL_LEFT;
import static android.view.KeyEvent.KEYCODE_CTRL_RIGHT;
import static android.view.KeyEvent.KEYCODE_D;
import static android.view.KeyEvent.KEYCODE_DEL;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_E;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_EQUALS;
import static android.view.KeyEvent.KEYCODE_ESCAPE;
import static android.view.KeyEvent.KEYCODE_F;
import static android.view.KeyEvent.KEYCODE_F1;
import static android.view.KeyEvent.KEYCODE_F10;
import static android.view.KeyEvent.KEYCODE_F11;
import static android.view.KeyEvent.KEYCODE_F12;
import static android.view.KeyEvent.KEYCODE_F2;
import static android.view.KeyEvent.KEYCODE_F3;
import static android.view.KeyEvent.KEYCODE_F4;
import static android.view.KeyEvent.KEYCODE_F5;
import static android.view.KeyEvent.KEYCODE_F6;
import static android.view.KeyEvent.KEYCODE_F7;
import static android.view.KeyEvent.KEYCODE_F8;
import static android.view.KeyEvent.KEYCODE_F9;
import static android.view.KeyEvent.KEYCODE_FORWARD_DEL;
import static android.view.KeyEvent.KEYCODE_G;
import static android.view.KeyEvent.KEYCODE_GRAVE;
import static android.view.KeyEvent.KEYCODE_H;
import static android.view.KeyEvent.KEYCODE_I;
import static android.view.KeyEvent.KEYCODE_INSERT;
import static android.view.KeyEvent.KEYCODE_J;
import static android.view.KeyEvent.KEYCODE_K;
import static android.view.KeyEvent.KEYCODE_L;
import static android.view.KeyEvent.KEYCODE_LEFT_BRACKET;
import static android.view.KeyEvent.KEYCODE_M;
import static android.view.KeyEvent.KEYCODE_META_LEFT;
import static android.view.KeyEvent.KEYCODE_META_RIGHT;
import static android.view.KeyEvent.KEYCODE_MINUS;
import static android.view.KeyEvent.KEYCODE_MOVE_END;
import static android.view.KeyEvent.KEYCODE_MOVE_HOME;
import static android.view.KeyEvent.KEYCODE_N;
import static android.view.KeyEvent.KEYCODE_NUMPAD_0;
import static android.view.KeyEvent.KEYCODE_NUMPAD_1;
import static android.view.KeyEvent.KEYCODE_NUMPAD_2;
import static android.view.KeyEvent.KEYCODE_NUMPAD_3;
import static android.view.KeyEvent.KEYCODE_NUMPAD_4;
import static android.view.KeyEvent.KEYCODE_NUMPAD_5;
import static android.view.KeyEvent.KEYCODE_NUMPAD_6;
import static android.view.KeyEvent.KEYCODE_NUMPAD_7;
import static android.view.KeyEvent.KEYCODE_NUMPAD_8;
import static android.view.KeyEvent.KEYCODE_NUMPAD_9;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ADD;
import static android.view.KeyEvent.KEYCODE_NUMPAD_COMMA;
import static android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE;
import static android.view.KeyEvent.KEYCODE_NUMPAD_DOT;
import static android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
import static android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
import static android.view.KeyEvent.KEYCODE_O;
import static android.view.KeyEvent.KEYCODE_P;
import static android.view.KeyEvent.KEYCODE_PAGE_DOWN;
import static android.view.KeyEvent.KEYCODE_PAGE_UP;
import static android.view.KeyEvent.KEYCODE_PERIOD;
import static android.view.KeyEvent.KEYCODE_Q;
import static android.view.KeyEvent.KEYCODE_R;
import static android.view.KeyEvent.KEYCODE_RIGHT_BRACKET;
import static android.view.KeyEvent.KEYCODE_S;
import static android.view.KeyEvent.KEYCODE_SEMICOLON;
import static android.view.KeyEvent.KEYCODE_SHIFT_LEFT;
import static android.view.KeyEvent.KEYCODE_SHIFT_RIGHT;
import static android.view.KeyEvent.KEYCODE_SLASH;
import static android.view.KeyEvent.KEYCODE_SPACE;
import static android.view.KeyEvent.KEYCODE_T;
import static android.view.KeyEvent.KEYCODE_TAB;
import static android.view.KeyEvent.KEYCODE_U;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;
import static android.view.KeyEvent.KEYCODE_V;
import static android.view.KeyEvent.KEYCODE_W;
import static android.view.KeyEvent.KEYCODE_X;
import static android.view.KeyEvent.KEYCODE_Y;
import static android.view.KeyEvent.KEYCODE_Z;
import static java.awt.event.KeyEvent.KEY_LOCATION_RIGHT;
import static java.awt.event.KeyEvent.VK_0;
import static java.awt.event.KeyEvent.VK_1;
import static java.awt.event.KeyEvent.VK_2;
import static java.awt.event.KeyEvent.VK_3;
import static java.awt.event.KeyEvent.VK_4;
import static java.awt.event.KeyEvent.VK_5;
import static java.awt.event.KeyEvent.VK_6;
import static java.awt.event.KeyEvent.VK_7;
import static java.awt.event.KeyEvent.VK_8;
import static java.awt.event.KeyEvent.VK_9;
import static java.awt.event.KeyEvent.VK_A;
import static java.awt.event.KeyEvent.VK_ADD;
import static java.awt.event.KeyEvent.VK_ALT;
import static java.awt.event.KeyEvent.VK_ALT_GRAPH;
import static java.awt.event.KeyEvent.VK_B;
import static java.awt.event.KeyEvent.VK_BACK_QUOTE;
import static java.awt.event.KeyEvent.VK_BACK_SLASH;
import static java.awt.event.KeyEvent.VK_BACK_SPACE;
import static java.awt.event.KeyEvent.VK_C;
import static java.awt.event.KeyEvent.VK_CAPS_LOCK;
import static java.awt.event.KeyEvent.VK_CLOSE_BRACKET;
import static java.awt.event.KeyEvent.VK_COMMA;
import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_D;
import static java.awt.event.KeyEvent.VK_DECIMAL;
import static java.awt.event.KeyEvent.VK_DELETE;
import static java.awt.event.KeyEvent.VK_DIVIDE;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_E;
import static java.awt.event.KeyEvent.VK_END;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_EQUALS;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.awt.event.KeyEvent.VK_F;
import static java.awt.event.KeyEvent.VK_F1;
import static java.awt.event.KeyEvent.VK_F10;
import static java.awt.event.KeyEvent.VK_F11;
import static java.awt.event.KeyEvent.VK_F12;
import static java.awt.event.KeyEvent.VK_F2;
import static java.awt.event.KeyEvent.VK_F3;
import static java.awt.event.KeyEvent.VK_F4;
import static java.awt.event.KeyEvent.VK_F5;
import static java.awt.event.KeyEvent.VK_F6;
import static java.awt.event.KeyEvent.VK_F7;
import static java.awt.event.KeyEvent.VK_F8;
import static java.awt.event.KeyEvent.VK_F9;
import static java.awt.event.KeyEvent.VK_G;
import static java.awt.event.KeyEvent.VK_H;
import static java.awt.event.KeyEvent.VK_HOME;
import static java.awt.event.KeyEvent.VK_I;
import static java.awt.event.KeyEvent.VK_INSERT;
import static java.awt.event.KeyEvent.VK_J;
import static java.awt.event.KeyEvent.VK_K;
import static java.awt.event.KeyEvent.VK_L;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_M;
import static java.awt.event.KeyEvent.VK_META;
import static java.awt.event.KeyEvent.VK_MINUS;
import static java.awt.event.KeyEvent.VK_MULTIPLY;
import static java.awt.event.KeyEvent.VK_N;
import static java.awt.event.KeyEvent.VK_NUMPAD0;
import static java.awt.event.KeyEvent.VK_NUMPAD1;
import static java.awt.event.KeyEvent.VK_NUMPAD2;
import static java.awt.event.KeyEvent.VK_NUMPAD3;
import static java.awt.event.KeyEvent.VK_NUMPAD4;
import static java.awt.event.KeyEvent.VK_NUMPAD5;
import static java.awt.event.KeyEvent.VK_NUMPAD6;
import static java.awt.event.KeyEvent.VK_NUMPAD7;
import static java.awt.event.KeyEvent.VK_NUMPAD8;
import static java.awt.event.KeyEvent.VK_NUMPAD9;
import static java.awt.event.KeyEvent.VK_NUM_LOCK;
import static java.awt.event.KeyEvent.VK_O;
import static java.awt.event.KeyEvent.VK_OPEN_BRACKET;
import static java.awt.event.KeyEvent.VK_P;
import static java.awt.event.KeyEvent.VK_PAGE_DOWN;
import static java.awt.event.KeyEvent.VK_PAGE_UP;
import static java.awt.event.KeyEvent.VK_PAUSE;
import static java.awt.event.KeyEvent.VK_PERIOD;
import static java.awt.event.KeyEvent.VK_Q;
import static java.awt.event.KeyEvent.VK_QUOTE;
import static java.awt.event.KeyEvent.VK_R;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_S;
import static java.awt.event.KeyEvent.VK_SEMICOLON;
import static java.awt.event.KeyEvent.VK_SEPARATOR;
import static java.awt.event.KeyEvent.VK_SHIFT;
import static java.awt.event.KeyEvent.VK_SLASH;
import static java.awt.event.KeyEvent.VK_SPACE;
import static java.awt.event.KeyEvent.VK_SUBTRACT;
import static java.awt.event.KeyEvent.VK_T;
import static java.awt.event.KeyEvent.VK_TAB;
import static java.awt.event.KeyEvent.VK_U;
import static java.awt.event.KeyEvent.VK_UP;
import static java.awt.event.KeyEvent.VK_V;
import static java.awt.event.KeyEvent.VK_W;
import static java.awt.event.KeyEvent.VK_X;
import static java.awt.event.KeyEvent.VK_Y;
import static java.awt.event.KeyEvent.VK_Z;

public class KeyEventHandling {

    /**
     * Creates an Android KeyEvent from a Java KeyEvent
     */
    public static KeyEvent javaToAndroidKeyEvent(java.awt.event.KeyEvent event, long downTimeNanos,
            long currentTimeNanos) {
        int androidKeyCode = javaToAndroidKeyCode(event.getKeyCode(), event.getKeyLocation());
        int metaState = 0;
        if (event.isAltDown() | event.isAltGraphDown()) {
            metaState |= KeyEvent.META_ALT_ON;
        }
        if (event.isMetaDown()) {
            metaState |= KeyEvent.META_META_ON;
        }
        if (event.isControlDown()) {
            metaState |= KeyEvent.META_CTRL_ON;
        }
        if (event.isShiftDown()) {
            metaState |= KeyEvent.META_SHIFT_ON;
        }
        try {
            if (Toolkit.getDefaultToolkit().getLockingKeyState(VK_CAPS_LOCK)) {
                metaState |= KeyEvent.META_CAPS_LOCK_ON;
            }
            if (Toolkit.getDefaultToolkit().getLockingKeyState(VK_NUM_LOCK)) {
                metaState |= KeyEvent.META_NUM_LOCK_ON;
            }
        } catch (UnsupportedOperationException ignore) {
        }
        int keyEventType;
        if (event.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
            keyEventType = KeyEvent.ACTION_DOWN;
        } else {
            keyEventType = KeyEvent.ACTION_UP;
        }
        // Use deviceId 1 as that corresponds to the Generic keyboard.
        return KeyEvent.obtain(downTimeNanos / TimeUtils.NANOS_PER_MS,
                currentTimeNanos / TimeUtils.NANOS_PER_MS, keyEventType, androidKeyCode, 0,
                metaState, 1, 0, 0, InputDevice.SOURCE_KEYBOARD, null);
    }

    private static int javaToAndroidKeyCode(int keyCode, int location) {
        switch(keyCode) {
            case VK_0: return KEYCODE_0;
            case VK_1: return KEYCODE_1;
            case VK_2: return KEYCODE_2;
            case VK_3: return KEYCODE_3;
            case VK_4: return KEYCODE_4;
            case VK_5: return KEYCODE_5;
            case VK_6: return KEYCODE_6;
            case VK_7: return KEYCODE_7;
            case VK_8: return KEYCODE_8;
            case VK_9: return KEYCODE_9;

            case VK_A: return KEYCODE_A;
            case VK_B: return KEYCODE_B;
            case VK_C: return KEYCODE_C;
            case VK_D: return KEYCODE_D;
            case VK_E: return KEYCODE_E;
            case VK_F: return KEYCODE_F;
            case VK_G: return KEYCODE_G;
            case VK_H: return KEYCODE_H;
            case VK_I: return KEYCODE_I;
            case VK_J: return KEYCODE_J;
            case VK_K: return KEYCODE_K;
            case VK_L: return KEYCODE_L;
            case VK_M: return KEYCODE_M;
            case VK_N: return KEYCODE_N;
            case VK_O: return KEYCODE_O;
            case VK_P: return KEYCODE_P;
            case VK_Q: return KEYCODE_Q;
            case VK_R: return KEYCODE_R;
            case VK_S: return KEYCODE_S;
            case VK_T: return KEYCODE_T;
            case VK_U: return KEYCODE_U;
            case VK_V: return KEYCODE_V;
            case VK_W: return KEYCODE_W;
            case VK_X: return KEYCODE_X;
            case VK_Y: return KEYCODE_Y;
            case VK_Z: return KEYCODE_Z;

            case VK_SPACE: return KEYCODE_SPACE;
            case VK_ENTER: return KEYCODE_ENTER;
            case VK_TAB: return KEYCODE_TAB;
            case VK_COMMA: return KEYCODE_COMMA;
            case VK_PERIOD: return KEYCODE_PERIOD;
            case VK_SLASH: return KEYCODE_SLASH;
            case VK_BACK_QUOTE: return KEYCODE_GRAVE;
            case VK_MINUS: return KEYCODE_MINUS;
            case VK_EQUALS: return KEYCODE_EQUALS;
            case VK_OPEN_BRACKET: return KEYCODE_LEFT_BRACKET;
            case VK_CLOSE_BRACKET: return KEYCODE_RIGHT_BRACKET;
            case VK_BACK_SLASH: return KEYCODE_BACKSLASH;
            case VK_SEMICOLON: return KEYCODE_SEMICOLON;
            case VK_QUOTE: return KEYCODE_APOSTROPHE;

            case VK_ESCAPE: return KEYCODE_ESCAPE;
            case VK_BACK_SPACE: return KEYCODE_DEL;

            case VK_NUMPAD0: return KEYCODE_NUMPAD_0;
            case VK_NUMPAD1: return KEYCODE_NUMPAD_1;
            case VK_NUMPAD2: return KEYCODE_NUMPAD_2;
            case VK_NUMPAD3: return KEYCODE_NUMPAD_3;
            case VK_NUMPAD4: return KEYCODE_NUMPAD_4;
            case VK_NUMPAD5: return KEYCODE_NUMPAD_5;
            case VK_NUMPAD6: return KEYCODE_NUMPAD_6;
            case VK_NUMPAD7: return KEYCODE_NUMPAD_7;
            case VK_NUMPAD8: return KEYCODE_NUMPAD_8;
            case VK_NUMPAD9: return KEYCODE_NUMPAD_9;
            case VK_MULTIPLY: return KEYCODE_NUMPAD_MULTIPLY;
            case VK_ADD: return KEYCODE_NUMPAD_ADD;
            case VK_SEPARATOR: return KEYCODE_NUMPAD_COMMA;
            case VK_SUBTRACT: return KEYCODE_NUMPAD_SUBTRACT;
            case VK_DECIMAL: return KEYCODE_NUMPAD_DOT;
            case VK_DIVIDE: return KEYCODE_NUMPAD_DIVIDE;

            case VK_DOWN: return KEYCODE_DPAD_DOWN;
            case VK_LEFT: return KEYCODE_DPAD_LEFT;
            case VK_RIGHT: return KEYCODE_DPAD_RIGHT;
            case VK_UP: return KEYCODE_DPAD_UP;

            case VK_ALT: return KEYCODE_ALT_LEFT;
            case VK_ALT_GRAPH: return KEYCODE_ALT_RIGHT;
            case VK_SHIFT: {
                if (location == KEY_LOCATION_RIGHT) {
                    return KEYCODE_SHIFT_RIGHT;
                } else {
                    return KEYCODE_SHIFT_LEFT;
                }
            }
            case VK_CONTROL: {
                if (location == KEY_LOCATION_RIGHT) {
                    return KEYCODE_CTRL_RIGHT;
                } else {
                    return KEYCODE_CTRL_LEFT;
                }
            }
            case VK_META: {
                if (location == KEY_LOCATION_RIGHT) {
                    return KEYCODE_META_RIGHT;
                } else {
                    return KEYCODE_META_LEFT;
                }
            }

            case VK_PAGE_DOWN: return KEYCODE_PAGE_DOWN;
            case VK_PAGE_UP: return KEYCODE_PAGE_UP;
            case VK_INSERT: return KEYCODE_INSERT;
            case VK_DELETE: return KEYCODE_FORWARD_DEL;
            case VK_HOME: return KEYCODE_MOVE_HOME;
            case VK_END: return KEYCODE_MOVE_END;
            case VK_PAUSE: return KEYCODE_BREAK;

            case VK_F1: return KEYCODE_F1;
            case VK_F2: return KEYCODE_F2;
            case VK_F3: return KEYCODE_F3;
            case VK_F4: return KEYCODE_F4;
            case VK_F5: return KEYCODE_F5;
            case VK_F6: return KEYCODE_F6;
            case VK_F7: return KEYCODE_F7;
            case VK_F8: return KEYCODE_F8;
            case VK_F9: return KEYCODE_F9;
            case VK_F10: return KEYCODE_F10;
            case VK_F11: return KEYCODE_F11;
            case VK_F12: return KEYCODE_F12;
        }

        return KEYCODE_UNKNOWN;
    }
}

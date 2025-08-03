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

package com.android.car.rotary;

import android.content.ComponentName;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.dump.DualDumpOutputStream;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/** Utility methods for dumpsys. */
final class DumpUtils {
    private DumpUtils() {}

    /** Writes {@code focusDirection} to a dump in text or proto format. */
    static void writeFocusDirection(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @View.FocusRealDirection int focusDirection) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, Navigator.directionToString(focusDirection));
            return;
        }
        int val;
        switch (focusDirection) {
            case View.FOCUS_LEFT:
                val = RotaryProtos.FOCUS_LEFT;
                break;
            case View.FOCUS_UP:
                val = RotaryProtos.FOCUS_UP;
                break;
            case View.FOCUS_RIGHT:
                val = RotaryProtos.FOCUS_RIGHT;
                break;
            case View.FOCUS_DOWN:
                val = RotaryProtos.FOCUS_DOWN;
                break;
            default:
                throw new IllegalArgumentException("Invalid direction: " + focusDirection);
        }
        dumpOutputStream.write(fieldName, fieldId, val);
    }

    /** Writes {@code rect} to a dump in text or proto format. */
    static void writeRect(@NonNull DualDumpOutputStream dumpOutputStream, @NonNull Rect rect,
            @NonNull String fieldName, long fieldId) {
        long fieldToken = dumpOutputStream.start(fieldName, fieldId);
        dumpOutputStream.write("left", RotaryProtos.Rect.LEFT, rect.left);
        dumpOutputStream.write("top", RotaryProtos.Rect.TOP, rect.top);
        dumpOutputStream.write("right", RotaryProtos.Rect.RIGHT, rect.right);
        dumpOutputStream.write("bottom", RotaryProtos.Rect.BOTTOM, rect.bottom);
        dumpOutputStream.end(fieldToken);
    }

    /** Writes {@code afterScrollAction} to a dump in text or proto format. */
    static void writeAfterScrollAction(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @RotaryService.AfterScrollAction int afterScrollAction) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, afterScrollAction);
            return;
        }
        int val;
        switch (afterScrollAction) {
            case RotaryService.NONE:
                val = RotaryProtos.AFTER_SCROLL_DO_NOTHING;
                break;
            case RotaryService.FOCUS_PREVIOUS:
                val = RotaryProtos.AFTER_SCROLL_FOCUS_PREVIOUS;
                break;
            case RotaryService.FOCUS_NEXT:
                val = RotaryProtos.AFTER_SCROLL_FOCUS_NEXT;
                break;
            case RotaryService.FOCUS_FIRST:
                val = RotaryProtos.AFTER_SCROLL_FOCUS_FIRST;
                break;
            case RotaryService.FOCUS_LAST:
                val = RotaryProtos.AFTER_SCROLL_FOCUS_LAST;
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid after scroll action: " + afterScrollAction);
        }
        dumpOutputStream.write(fieldName, fieldId, val);
    }

    /** Writes {@code componentName} to a dump in text or proto format. */
    static void writeComponentNameToString(@NonNull DualDumpOutputStream dumpOutputStream,
            @NonNull String fieldName, long fieldId, @Nullable ComponentName componentName) {
        dumpOutputStream.write(fieldName, fieldId,
                componentName == null ? null : componentName.flattenToShortString());
    }

    /** Writes {@code object.toString()} to a dump in text or proto format. */
    static void writeObject(@NonNull DualDumpOutputStream dumpOutputStream,
            @NonNull String fieldName, long fieldId, @Nullable Object object) {
        dumpOutputStream.write(fieldName, fieldId, object == null ? null : object.toString());
    }

    /**
     * Writes the result of {@link Object#toString} on each of {@code objects}' elements to a dump
     * in text or proto format. In the latter case, the field must be {@code repeated}.
     */
    static void writeObjects(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @NonNull Object[] objects) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, Arrays.toString(objects));
            return;
        }
        for (Object object : objects) {
            writeObject(dumpOutputStream, fieldName, fieldId, object);
        }
    }

    /**
     * Writes the given integers to a dump in text or proto format. In the latter case, the field
     * must be {@code repeated}.
     */
    static void writeInts(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId, @NonNull int[] vals) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, Arrays.toString(vals));
            return;
        }
        for (int val : vals) {
            dumpOutputStream.write(fieldName, fieldId, val);
        }
    }

    /**
     * Writes the given keycodes to a dump in text or proto format. In the former case, the keycodes
     * are written as {@link KeyCode} constants. In the latter case, the field must be {@code
     * repeated}.
     */
    static void writeKeyCodes(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId, @NonNull int[] vals) {
        if (!dumpAsProto) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < vals.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(KeyEvent.keyCodeToString(vals[i]));
            }
            sb.append(']');
            dumpOutputStream.write(fieldName, fieldId, sb.toString());
            return;
        }
        for (int val : vals) {
            dumpOutputStream.write(fieldName, fieldId, val);
        }
    }

    /**
     * Writes the given CharSequences to a dump in text or proto format, converting them to strings.
     * In the latter case, the field must be {@code repeated}.
     */
    static void writeCharSequences(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @NonNull Collection<CharSequence> vals) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, vals.toString());
            return;
        }
        for (CharSequence val : vals) {
            dumpOutputStream.write(fieldName, fieldId, val.toString());
        }
    }

    /**
     * Writes the given integers to a dump in text or proto format. In the latter case, the field
     * must be {@code repeated}.
     */
    static void writeIntegers(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @NonNull Collection<Integer> vals) {
        if (!dumpAsProto) {
            dumpOutputStream.write(fieldName, fieldId, vals.toString());
            return;
        }
        for (Integer val : vals) {
            dumpOutputStream.write(fieldName, fieldId, val);
        }
    }

    /**
     * Writes the given map from window ID to window type to a dump in text or proto format. In the
     * former case, the window types are written as {@link AccessibilityWindowInfo} constants. In
     * the latter case, the field must be a {@code map}.
     */
    static void writeWindowTypes(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @NonNull Map<Integer, Integer> map) {
        if (!dumpAsProto) {
            long fieldToken = dumpOutputStream.start(fieldName, fieldId);
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                dumpOutputStream.write(/* fieldName= */ entry.getKey().toString(), /* fieldId= */ 0,
                        AccessibilityWindowInfo.typeToString(entry.getValue()));
            }
            dumpOutputStream.end(fieldToken);
            return;
        }
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            long fieldToken = dumpOutputStream.start(fieldName, fieldId);
            dumpOutputStream.write("key", /* fieldId= */ 1, entry.getKey());
            dumpOutputStream.write("value", /* fieldId= */ 2, entry.getValue());
            dumpOutputStream.end(fieldToken);
        }
    }

    /**
     * Writes the given map from window ID to node to a dump in text or proto format. In both cases,
     * the nodes are written as {@code toString}s. In the latter case, the field must be a {@code
     * map}.
     */
    static void writeFocusedNodes(@NonNull DualDumpOutputStream dumpOutputStream,
            boolean dumpAsProto, @NonNull String fieldName, long fieldId,
            @NonNull Map<Integer, AccessibilityNodeInfo> map) {
        if (!dumpAsProto) {
            long fieldToken = dumpOutputStream.start(fieldName, fieldId);
            for (Map.Entry<Integer, AccessibilityNodeInfo> entry : map.entrySet()) {
                dumpOutputStream.write(/* fieldName= */ entry.getKey().toString(), /* fieldId= */ 0,
                        entry.getValue().toString());
            }
            dumpOutputStream.end(fieldToken);
            return;
        }
        for (Map.Entry<Integer, AccessibilityNodeInfo> entry : map.entrySet()) {
            long fieldToken = dumpOutputStream.start(fieldName, fieldId);
            dumpOutputStream.write("key", /* fieldId= */ 1, entry.getKey());
            dumpOutputStream.write("value", /* fieldId= */ 2, entry.getValue().toString());
            dumpOutputStream.end(fieldToken);
        }
    }
}

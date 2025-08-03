/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.util;

import android.database.Cursor;

import androidx.annotation.NonNull;

public class CursorUtils {

    private CursorUtils() {
        // Do not instantiate
    }

    public static Object[] getObjectFromCursor(@NonNull Cursor cursor) {
        Object[] values = new Object[cursor.getColumnCount()];
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            int fieldType = cursor.getType(i);
            switch (fieldType) {
                case Cursor.FIELD_TYPE_BLOB:
                    values[i] = cursor.getBlob(i);
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    values[i] = cursor.getDouble(i);
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    values[i] = cursor.getLong(i);
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    values[i] = cursor.getString(i);
                    break;
                case Cursor.FIELD_TYPE_NULL:
                    values[i] = null;
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown fieldType (" + fieldType + ") for column: " + i);
            }
        }
        return values;
    }
}

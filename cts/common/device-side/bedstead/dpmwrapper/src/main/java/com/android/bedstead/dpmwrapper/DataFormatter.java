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
package com.android.bedstead.dpmwrapper;

import static com.android.bedstead.dpmwrapper.Utils.EXTRA_ARG_PREFIX;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;

import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.os.CpuUsageInfo;
import android.os.Parcelable;
import android.util.ArraySet;
import android.util.Log;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class DataFormatter {

    private static final String TAG = DataFormatter.class.getSimpleName();

    // NOTE: Bundle has a putObject() method that would make it much easier to marshal the args,
    // but unfortunately there is no Intent.putObjectExtra() method (and intent.getBundle() returns
    // a copy, so we need to explicitly marshal any supported type).
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_BYTE_ARRAY = "byte_array";
    private static final String TYPE_FLOAT_ARRAY = "float_array";
    private static final String TYPE_STRING_OR_CHAR_SEQUENCE = "string";
    private static final String TYPE_PARCELABLE = "parcelable";
    private static final String TYPE_SERIALIZABLE = "serializable";
    private static final String TYPE_ARRAY_LIST_STRING = "array_list_string";
    private static final String TYPE_ARRAY_LIST_PARCELABLE = "array_list_parcelable";
    // NOTE: the value of a TYPE_ARRAY_LIST_BYTE_ARRAY is its length - the individual elements
    // are contained on separate extras, one per index, whose name is defined by
    // getExtraNameForArrayListElement()
    private static final String TYPE_ARRAY_LIST_BYTE_ARRAY = "array_list_byte_array";
    private static final String TYPE_SET_STRING = "set_string";
    // Must handle each array of parcelable subclass , as they need to be explicitly converted
    private static final String TYPE_CPU_USAFE_INFO_ARRAY = "cpu_usage_info_array";
    private static final String TYPE_CERTIFICATE = "certificate";
    private static final String TYPE_PRIVATE_KEY = "private_key";

    // Used when a method is called passing a null argument - the proper method will have to be
    // infered using findMethod()
    private static final String TYPE_NULL = "null";

    static void addArg(Intent intent, Object[] args, int index) {
        Object value = args[index];
        String extraTypeName = getArgExtraTypeName(index);
        String extraValueName = getArgExtraValueName(index);
        if (VERBOSE) {
            Log.v(TAG, "addArg(" + index + "): typeName= " + extraTypeName
                    + ", valueName= " + extraValueName);
        }
        if (value == null) {
            logMarshalling("Adding Null", index, extraTypeName, TYPE_NULL, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_NULL);
            return;

        }
        if ((value instanceof Boolean)) {
            logMarshalling("Adding Boolean", index, extraTypeName, TYPE_BOOLEAN, extraValueName,
                    value);
            intent.putExtra(extraTypeName, TYPE_BOOLEAN);
            intent.putExtra(extraValueName, ((Boolean) value).booleanValue());
            return;
        }
        if ((value instanceof Integer)) {
            logMarshalling("Adding Integer", index, extraTypeName, TYPE_INT, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_INT);
            intent.putExtra(extraValueName, ((Integer) value).intValue());
            return;
        }
        if ((value instanceof Long)) {
            logMarshalling("Adding Long", index, extraTypeName, TYPE_LONG, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_LONG);
            intent.putExtra(extraValueName, ((Long) value).longValue());
            return;
        }
        if ((value instanceof byte[])) {
            logMarshalling("Adding byte[]", index, extraTypeName, TYPE_BYTE_ARRAY, extraValueName,
                    value);
            intent.putExtra(extraTypeName, TYPE_BYTE_ARRAY);
            intent.putExtra(extraValueName, (byte[]) value);
            return;
        }
        if ((value instanceof float[])) {
            logMarshalling("Adding float[]", index, extraTypeName, TYPE_FLOAT_ARRAY, extraValueName,
                    value);
            intent.putExtra(extraTypeName, TYPE_FLOAT_ARRAY);
            intent.putExtra(extraValueName, (float[]) value);
            return;
        }
        if ((value instanceof CpuUsageInfo[])) {
            logMarshalling("Adding CpuUsageInfo[]", index, extraTypeName,
                    TYPE_CPU_USAFE_INFO_ARRAY, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_CPU_USAFE_INFO_ARRAY);
            intent.putExtra(extraValueName, (CpuUsageInfo[]) value);
            return;
        }
        if ((value instanceof CharSequence)) {
            logMarshalling("Adding CharSequence", index, extraTypeName,
                    TYPE_STRING_OR_CHAR_SEQUENCE, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_STRING_OR_CHAR_SEQUENCE);
            intent.putExtra(extraValueName, (CharSequence) value);
            return;
        }
        if (value instanceof PrivateKey) {
            if (!(value instanceof Serializable)) {
                throw new IllegalArgumentException("PrivateKey is not Serializable: "  + value);
            }
            logMarshalling("Adding PrivateKey", index, extraTypeName, TYPE_PRIVATE_KEY,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_PRIVATE_KEY);
            intent.putExtra(extraValueName, (Serializable) value);
            return;
        }
        if ((value instanceof Parcelable)) {
            logMarshalling("Adding Parcelable", index, extraTypeName, TYPE_PARCELABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_PARCELABLE);
            intent.putExtra(extraValueName, (Parcelable) value);
            return;
        }

        if (value instanceof Certificate) {
            if (!(value instanceof Serializable)) {
                throw new IllegalArgumentException("Certificate is not Serializable: "  + value);
            }
            logMarshalling("Adding Certificate", index, extraTypeName, TYPE_CERTIFICATE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_CERTIFICATE);
            intent.putExtra(extraValueName, (Serializable) value);
            return;
        }

        if ((value instanceof List<?>)) {
            List<?> list = (List<?>) value;

            String type = null;
            if (list.isEmpty()) {
                Log.w(TAG, "Empty list at index " + index + "; assuming it's List<String>");
                type = TYPE_ARRAY_LIST_STRING;
            } else {
                Object firstItem = list.get(0);
                if (firstItem instanceof String) {
                    type = TYPE_ARRAY_LIST_STRING;
                } else if (firstItem instanceof Parcelable) {
                    type = TYPE_ARRAY_LIST_PARCELABLE;
                } else if (firstItem instanceof byte[]) {
                    type = TYPE_ARRAY_LIST_BYTE_ARRAY;
                } else {
                    throw new IllegalArgumentException("Unsupported List type at index " + index
                            + ": " + firstItem);
                }
            }

            logMarshalling("Adding " + type, index, extraTypeName, type, extraValueName, value);
            intent.putExtra(extraTypeName, type);
            switch (type) {
                case TYPE_ARRAY_LIST_STRING:
                    @SuppressWarnings("unchecked")
                    ArrayList<String> arrayListString = (value instanceof ArrayList)
                            ? (ArrayList<String>) list
                            : new ArrayList<>((List<String>) list);
                    intent.putStringArrayListExtra(extraValueName, arrayListString);
                    break;
                case TYPE_ARRAY_LIST_PARCELABLE:
                    @SuppressWarnings("unchecked")
                    ArrayList<Parcelable> arrayListParcelable = (value instanceof ArrayList)
                            ? (ArrayList<Parcelable>) list
                            : new ArrayList<>((List<Parcelable>) list);
                    intent.putParcelableArrayListExtra(extraValueName, arrayListParcelable);
                    break;
                case TYPE_ARRAY_LIST_BYTE_ARRAY:
                    @SuppressWarnings("unchecked")
                    ArrayList<byte[]> arrayListByteArray = (value instanceof ArrayList)
                            ? (ArrayList<byte[]>) list
                            : new ArrayList<>((List<byte[]>) list);
                    int listSize = arrayListByteArray.size();
                    intent.putExtra(extraValueName, listSize);
                    for (int i = 0; i < listSize; i++) {
                        intent.putExtra(getExtraNameForArrayListElement(extraValueName, i),
                                arrayListByteArray.get(i));
                    }
                    break;
                default:
                    // should never happen because type is checked above
                    throw new AssertionError("invalid type conversion: " + type);
            }
            return;
        }

        // TODO(b/176993670): ArraySet<> is encapsulate as ArrayList<>, so most of the code below
        // could be reused (right now it was copy-and-paste from ArrayList<>, minus the Parcelable
        // part.
        if ((value instanceof Set<?>)) {
            Set<?> set = (Set<?>) value;

            String type = null;
            if (set.isEmpty()) {
                Log.w(TAG, "Empty set at index " + index + "; assuming it's Set<String>");
                type = TYPE_SET_STRING;
            } else {
                Object firstItem = set.iterator().next();
                if (firstItem instanceof String) {
                    type = TYPE_SET_STRING;
                } else {
                    throw new IllegalArgumentException("Unsupported Set type at index "
                            + index + ": " + firstItem);
                }
            }

            logMarshalling("Adding " + type, index, extraTypeName, type, extraValueName, value);
            intent.putExtra(extraTypeName, type);
            switch (type) {
                case TYPE_SET_STRING:
                    @SuppressWarnings("unchecked")
                    Set<String> stringSet = (Set<String>) value;
                    intent.putStringArrayListExtra(extraValueName, new ArrayList<>(stringSet));
                    break;
                default:
                    // should never happen because type is checked above
                    throw new AssertionError("invalid type conversion: " + type);
            }
            return;
        }

        if ((value instanceof Serializable)) {
            logMarshalling("Adding Serializable", index, extraTypeName, TYPE_SERIALIZABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_SERIALIZABLE);
            intent.putExtra(extraValueName, (Serializable) value);
            return;
        }

        throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                + (value == null ? "null" : value.getClass()));
    }

    private static String getExtraNameForArrayListElement(String baseExtraName, int index) {
        return baseExtraName + "_" + index;
    }

    static void getArg(Bundle extras, Object[] args, @Nullable Class<?>[] parameterTypes,
            int index) {
        String extraTypeName = getArgExtraTypeName(index);
        String extraValueName = getArgExtraValueName(index);
        String type = extras.getString(extraTypeName);
        if (VERBOSE) {
            Log.v(TAG, "getArg(" + index + "): typeName= " + extraTypeName + ", type=" + type
                    + ", valueName= " + extraValueName);
        }
        Object value = null;
        switch (type) {
            case TYPE_NULL:
                logMarshalling("Got null", index, extraTypeName, type, extraValueName, value);
                break;
            case TYPE_SET_STRING:
                @SuppressWarnings("unchecked")
                ArrayList<String> list = (ArrayList<String>) extras.get(extraValueName);
                value = new ArraySet<String>(list);
                logMarshalling("Got ArraySet<String>", index, extraTypeName, type, extraValueName,
                        value);
                break;
            case TYPE_CPU_USAFE_INFO_ARRAY:
                Parcelable[] raw = (Parcelable[]) extras.get(extraValueName);
                CpuUsageInfo[] cast = new CpuUsageInfo[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    cast[i] = (CpuUsageInfo) raw[i];
                }
                value = cast;
                logMarshalling("Got CpuUsageInfo[]", index, extraTypeName, type, extraValueName,
                        value);
                break;
            case TYPE_ARRAY_LIST_BYTE_ARRAY:
                int size = extras.getInt(extraValueName);
                ArrayList<byte[]> array = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    String extraName = getExtraNameForArrayListElement(extraValueName, i);
                    array.add(extras.getByteArray(extraName));
                }
                value = array;
                break;
            case TYPE_ARRAY_LIST_STRING:
            case TYPE_ARRAY_LIST_PARCELABLE:
            case TYPE_BYTE_ARRAY:
            case TYPE_FLOAT_ARRAY:
            case TYPE_BOOLEAN:
            case TYPE_INT:
            case TYPE_LONG:
            case TYPE_STRING_OR_CHAR_SEQUENCE:
            case TYPE_PARCELABLE:
            case TYPE_SERIALIZABLE:
            case TYPE_CERTIFICATE:
            case TYPE_PRIVATE_KEY:
                value = extras.get(extraValueName);
                logMarshalling("Got generic", index, extraTypeName, type, extraValueName, value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                        + extraTypeName);
        }
        if (parameterTypes != null) {
            Class<?> parameterType = null;
            // Must convert special types (like primitive to Object, generic list to list, etc...),
            // but not those that can be inferred from getClass() (like String or array)
            switch (type) {
                case TYPE_NULL:
                    break;
                case TYPE_BOOLEAN:
                    parameterType = boolean.class;
                    break;
                case TYPE_INT:
                    parameterType = int.class;
                    break;
                case TYPE_LONG:
                    parameterType = long.class;
                    break;
                case TYPE_STRING_OR_CHAR_SEQUENCE:
                    // A String is a CharSequence, but most methods take String, so we're assuming
                    // a string and handle the exceptional cases on findMethod()
                    parameterType = String.class;
                    break;
                case TYPE_ARRAY_LIST_STRING:
                    parameterType = List.class;
                    break;
                case TYPE_SET_STRING:
                    parameterType = Set.class;
                    break;
                case TYPE_PRIVATE_KEY:
                    parameterType = PrivateKey.class;
                    break;
                case TYPE_CERTIFICATE:
                    parameterType = Certificate.class;
                    break;
                default:
                    parameterType = value.getClass();
            }
            parameterTypes[index] = parameterType;
        }
        args[index] = value;
    }

    static String getArgExtraTypeName(int index) {
        return EXTRA_ARG_PREFIX + index + "_type";
    }

    static String getArgExtraValueName(int index) {
        return EXTRA_ARG_PREFIX + index + "_value";
    }

    private static void logMarshalling(String operation, int index, String typeName,
            String type, String valueName, Object value) {
        if (VERBOSE) {
            Log.v(TAG, operation + " on " + index + ": typeName=" + typeName + ", type=" + type
                    + ", valueName=" + valueName + ", value=" + value);
        }
    }

    private DataFormatter() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.util;

import android.content.ContentValues;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.fbs.EventFields;
import com.android.ondevicepersonalization.services.fbs.KeyValue;
import com.android.ondevicepersonalization.services.fbs.KeyValueList;
import com.android.ondevicepersonalization.services.fbs.Owner;
import com.android.ondevicepersonalization.services.fbs.QueryData;
import com.android.ondevicepersonalization.services.fbs.QueryFields;

import com.google.common.primitives.Ints;
import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Util class to support creation of OnDevicePersonalization flatbuffers
 */
public class OnDevicePersonalizationFlatbufferUtils {
    public static final byte DATA_TYPE_BYTE = 1;
    public static final byte DATA_TYPE_SHORT = 2;
    public static final byte DATA_TYPE_INT = 3;
    public static final byte DATA_TYPE_LONG = 4;
    public static final byte DATA_TYPE_FLOAT = 5;
    public static final byte DATA_TYPE_DOUBLE = 6;
    public static final byte DATA_TYPE_STRING = 7;
    public static final byte DATA_TYPE_BLOB = 8;
    public static final byte DATA_TYPE_BOOL = 9;
    private static final String TAG = "OnDevicePersonalizationFlatbufferUtils";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private OnDevicePersonalizationFlatbufferUtils() {
    }

    /**
     * Creates a byte array representing the QueryData as a flatbuffer
     */
    public static byte[] createQueryData(
            String servicePackageName, String certDigest, List<ContentValues> rows) {
        try {
            sLogger.d(TAG + ": createQueryData started.");
            FlatBufferBuilder builder = new FlatBufferBuilder();
            int ownerOffset = createOwner(builder, servicePackageName, certDigest);
            int rowsOffset = 0;
            if (rows != null && !rows.isEmpty()) {
                int[] rowOffsets = new int[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    ContentValues row = rows.get(i);
                    rowOffsets[i] = createKeyValueList(builder, row);
                }
                rowsOffset = QueryFields.createRowsVector(builder, rowOffsets);
            }
            QueryFields.startQueryFields(builder);
            QueryFields.addOwner(builder, ownerOffset);
            QueryFields.addRows(builder, rowsOffset);
            int[] queryFieldsOffset = new int[1];
            queryFieldsOffset[0] = QueryFields.endQueryFields(builder);
            int queryFieldsListOffset = QueryData.createQueryFieldsVector(
                    builder, queryFieldsOffset);
            QueryData.startQueryData(builder);
            QueryData.addQueryFields(builder, queryFieldsListOffset);
            int queryDataOffset = QueryData.endQueryData(builder);
            builder.finish(queryDataOffset);
            return builder.sizedByteArray();
        } catch (Exception e) {
            sLogger.e(e, TAG + ": createQueryData failed.");
            return new byte[0];
        }
    }

    /**
     * Creates a byte array representing the EventData as a flatbuffer
     */
    public static byte[] createEventData(ContentValues data) {
        try {
            sLogger.d(TAG + ": createEventData started.");
            FlatBufferBuilder builder = new FlatBufferBuilder();
            int dataOffset = createKeyValueList(builder, data);
            EventFields.startEventFields(builder);
            EventFields.addData(builder, dataOffset);
            int eventFieldsOffset = EventFields.endEventFields(builder);
            builder.finish(eventFieldsOffset);
            return builder.sizedByteArray();
        } catch (Exception e) {
            sLogger.e(e, TAG + ": createEventData failed.");
            return new byte[0];
        }
    }

    /**
     * Retrieves all KeyValueLists in a QueryField flatbuffer as a List of ContentValues objects.
     */
    public static List<ContentValues> getContentValuesFromQueryData(byte[] queryData) {
        List<ContentValues> contentValuesList = new ArrayList<>();
        QueryFields queryFields = QueryData.getRootAsQueryData(
                ByteBuffer.wrap(queryData)).queryFields(0);
        for (int i = 0; i < queryFields.rowsLength(); i++) {
            contentValuesList.add(getContentValuesFromKeyValueList(queryFields.rows(i)));
        }
        return contentValuesList;
    }


    /**
     * Retrieves the KeyValueList in a QueryField flatbuffer at the specified index as a
     * ContentValues object.
     */
    public static ContentValues getContentValuesRowFromQueryData(byte[] queryData, int rowIndex) {
        QueryFields queryFields = QueryData.getRootAsQueryData(
                ByteBuffer.wrap(queryData)).queryFields(0);
        return getContentValuesFromKeyValueList(queryFields.rows(rowIndex));
    }

    /**
     * Retrieves the length of the rows in a QueryField flatbuffer.
     */
    public static int getContentValuesLengthFromQueryData(byte[] queryData) {
        QueryFields queryFields = QueryData.getRootAsQueryData(
                ByteBuffer.wrap(queryData)).queryFields(0);
        return queryFields.rowsLength();
    }

    /**
     * Retrieves the KeyValueList in an EventData flatbuffer as a ContentValues object.
     */
    public static ContentValues getContentValuesFromEventData(byte[] eventData) {
        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(eventData));
        return getContentValuesFromKeyValueList(eventFields.data());
    }

    private static ContentValues getContentValuesFromKeyValueList(KeyValueList list) {
        ContentValues data = new ContentValues();
        for (int i = 0; i < list.entriesLength(); i++) {
            KeyValue kv = list.entries(i);
            switch (kv.type()) {
                case DATA_TYPE_BYTE:
                    data.put(kv.key(), kv.byteValue());
                    break;
                case DATA_TYPE_SHORT:
                    data.put(kv.key(), kv.shortValue());
                    break;
                case DATA_TYPE_INT:
                    data.put(kv.key(), kv.intValue());
                    break;
                case DATA_TYPE_LONG:
                    data.put(kv.key(), kv.longValue());
                    break;
                case DATA_TYPE_FLOAT:
                    data.put(kv.key(), kv.floatValue());
                    break;
                case DATA_TYPE_DOUBLE:
                    data.put(kv.key(), kv.doubleValue());
                    break;
                case DATA_TYPE_STRING:
                    data.put(kv.key(), kv.stringValue());
                    break;
                case DATA_TYPE_BLOB:
                    ByteBuffer buf = kv.blobValueAsByteBuffer();
                    byte[] arr = new byte[buf.remaining()];
                    buf.get(arr);
                    data.put(kv.key(), arr);
                    break;
                case DATA_TYPE_BOOL:
                    data.put(kv.key(), kv.boolValue());
                    break;
            }
        }
        return data;
    }

    private static int createKeyValueList(
            FlatBufferBuilder builder, ContentValues data) {
        int entriesOffset = 0;
        if (data != null) {
            ArrayList<Integer> entryOffsets = new ArrayList<>();
            for (var entry : data.valueSet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    entryOffsets.add(
                            createKeyValueEntry(builder, entry.getKey(), entry.getValue()));
                }
            }
            entriesOffset = KeyValueList.createEntriesVector(builder, Ints.toArray(entryOffsets));
        }
        KeyValueList.startKeyValueList(builder);
        KeyValueList.addEntries(builder, entriesOffset);
        return KeyValueList.endKeyValueList(builder);
    }

    private static int createKeyValueEntry(FlatBufferBuilder builder, String key, Object value) {
        int valueOffset = 0;
        if (value instanceof String) {
            valueOffset = builder.createString((String) value);
        } else if (value instanceof byte[]) {
            valueOffset = builder.createByteVector((byte[]) value);
        }
        int keyOffset = builder.createString(key);
        KeyValue.startKeyValue(builder);
        KeyValue.addKey(builder, keyOffset);
        if (value instanceof Byte) {
            KeyValue.addType(builder, DATA_TYPE_BYTE);
            KeyValue.addByteValue(builder, ((Byte) value).byteValue());
        } else if (value instanceof Short) {
            KeyValue.addType(builder, DATA_TYPE_SHORT);
            KeyValue.addShortValue(builder, ((Short) value).shortValue());
        } else if (value instanceof Integer) {
            KeyValue.addType(builder, DATA_TYPE_INT);
            KeyValue.addIntValue(builder, ((Integer) value).intValue());
        } else if (value instanceof Long) {
            KeyValue.addType(builder, DATA_TYPE_LONG);
            KeyValue.addLongValue(builder, ((Long) value).longValue());
        } else if (value instanceof Float) {
            KeyValue.addType(builder, DATA_TYPE_FLOAT);
            KeyValue.addFloatValue(builder, ((Float) value).floatValue());
        } else if (value instanceof Double) {
            KeyValue.addType(builder, DATA_TYPE_DOUBLE);
            KeyValue.addDoubleValue(builder, ((Double) value).doubleValue());
        } else if (value instanceof String) {
            KeyValue.addType(builder, DATA_TYPE_STRING);
            KeyValue.addStringValue(builder, valueOffset);
        } else if (value instanceof byte[]) {
            KeyValue.addType(builder, DATA_TYPE_BLOB);
            KeyValue.addBlobValue(builder, valueOffset);
        } else if (value instanceof Boolean) {
            KeyValue.addType(builder, DATA_TYPE_BOOL);
            KeyValue.addBoolValue(builder, ((Boolean) value).booleanValue());
        }
        return KeyValue.endKeyValue(builder);
    }

    private static int createOwner(
            FlatBufferBuilder builder,
            String packageName,
            String certDigest) {
        int packageNameOffset = 0;
        if (packageName != null) {
            packageNameOffset = builder.createString(packageName);
        }
        int certDigestOffset = 0;
        if (certDigest != null) {
            certDigestOffset = builder.createString(certDigest);
        }
        Owner.startOwner(builder);
        Owner.addPackageName(builder, packageNameOffset);
        Owner.addCertDigest(builder, certDigestOffset);
        return Owner.endOwner(builder);
    }
}


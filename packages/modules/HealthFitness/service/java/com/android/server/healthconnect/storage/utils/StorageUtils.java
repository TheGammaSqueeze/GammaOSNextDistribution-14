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

package com.android.server.healthconnect.storage.utils;

import static android.health.connect.HealthDataCategory.ACTIVITY;
import static android.health.connect.HealthDataCategory.SLEEP;
import static android.health.connect.datatypes.AggregationType.SUM;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HYDRATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_NUTRITION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED;
import static android.text.TextUtils.isEmpty;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.CLIENT_RECORD_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.RecordTypeForUuidMappings.getRecordTypeIdForUuid;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.HealthDataCategory;
import android.health.connect.RecordIdFilter;
import android.health.connect.internal.datatypes.InstantRecordInternal;
import android.health.connect.internal.datatypes.IntervalRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.health.connect.internal.datatypes.utils.RecordTypeRecordCategoryMapper;
import android.util.Slog;

import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An util class for HC storage
 *
 * @hide
 */
public final class StorageUtils {
    public static final String TEXT_NOT_NULL = "TEXT NOT NULL";
    public static final String TEXT_NOT_NULL_UNIQUE = "TEXT NOT NULL UNIQUE";
    public static final String TEXT_NULL = "TEXT";
    public static final String INTEGER = "INTEGER";
    public static final String INTEGER_UNIQUE = "INTEGER UNIQUE";
    public static final String INTEGER_NOT_NULL_UNIQUE = "INTEGER NOT NULL UNIQUE";
    public static final String INTEGER_NOT_NULL = "INTEGER NOT NULL";
    public static final String REAL = "REAL";
    public static final String REAL_NOT_NULL = "REAL NOT NULL";
    public static final String PRIMARY_AUTOINCREMENT = "INTEGER PRIMARY KEY AUTOINCREMENT";
    public static final String PRIMARY = "INTEGER PRIMARY KEY";
    public static final String DELIMITER = ",";
    public static final String BLOB = "BLOB";
    public static final String BLOB_UNIQUE_NULL = "BLOB UNIQUE";
    public static final String BLOB_UNIQUE_NON_NULL = "BLOB NOT NULL UNIQUE";
    public static final String BLOB_NON_NULL = "BLOB NOT NULL";
    public static final String SELECT_ALL = "SELECT * FROM ";
    public static final String LIMIT_SIZE = " LIMIT ";
    public static final int BOOLEAN_FALSE_VALUE = 0;
    public static final int BOOLEAN_TRUE_VALUE = 1;
    public static final int UUID_BYTE_SIZE = 16;
    private static final String TAG = "HealthConnectUtils";

    // Returns null if fetching any of the fields resulted in an error
    @Nullable
    public static String getConflictErrorMessageForRecord(
            Cursor cursor, ContentValues contentValues) {
        try {
            return "Updating record with uuid: "
                    + convertBytesToUUID(contentValues.getAsByteArray(UUID_COLUMN_NAME))
                    + " and client record id: "
                    + contentValues.getAsString(CLIENT_RECORD_ID_COLUMN_NAME)
                    + " conflicts with an existing record with uuid: "
                    + getCursorUUID(cursor, UUID_COLUMN_NAME)
                    + "  and client record id: "
                    + getCursorString(cursor, CLIENT_RECORD_ID_COLUMN_NAME);
        } catch (Exception exception) {
            Slog.e(TAG, "", exception);
            return null;
        }
    }

    /**
     * Sets UUID for the given record. If {@link RecordInternal#getClientRecordId()} is null or
     * empty, then the UUID is randomly generated. Otherwise, the UUID is generated as a combination
     * of {@link RecordInternal#getPackageName()}, {@link RecordInternal#getClientRecordId()} and
     * {@link RecordInternal#getRecordType()}.
     */
    public static void addNameBasedUUIDTo(@NonNull RecordInternal<?> recordInternal) {
        final String clientRecordId = recordInternal.getClientRecordId();
        if (isEmpty(clientRecordId)) {
            recordInternal.setUuid(UUID.randomUUID());
            return;
        }

        final UUID uuid =
                getUUID(
                        requireNonNull(recordInternal.getPackageName()),
                        clientRecordId,
                        recordInternal.getRecordType());
        recordInternal.setUuid(uuid);
    }

    /** Updates the uuid using the clientRecordID if the clientRecordId is present. */
    public static void updateNameBasedUUIDIfRequired(@NonNull RecordInternal<?> recordInternal) {
        final String clientRecordId = recordInternal.getClientRecordId();
        if (isEmpty(clientRecordId)) {
            // If clientRecordID is absent, use the uuid already set in the input record and
            // hence no need to modify it.
            return;
        }

        final UUID uuid =
                getUUID(
                        requireNonNull(recordInternal.getPackageName()),
                        clientRecordId,
                        recordInternal.getRecordType());
        recordInternal.setUuid(uuid);
    }

    /**
     * Returns a UUID for the given {@link RecordIdFilter} and package name. If {@link
     * RecordIdFilter#getClientRecordId()} is null or empty, then the UUID corresponds to {@link
     * RecordIdFilter#getId()}. Otherwise, the UUID is generated as a combination of the package
     * name, {@link RecordIdFilter#getClientRecordId()} and {@link RecordIdFilter#getRecordType()}.
     */
    public static UUID getUUIDFor(
            @NonNull RecordIdFilter recordIdFilter, @NonNull String packageName) {
        final String clientRecordId = recordIdFilter.getClientRecordId();
        if (isEmpty(clientRecordId)) {
            return UUID.fromString(recordIdFilter.getId());
        }

        return getUUID(
                packageName,
                clientRecordId,
                RecordMapper.getInstance().getRecordType(recordIdFilter.getRecordType()));
    }

    public static void addPackageNameTo(
            @NonNull RecordInternal<?> recordInternal, @NonNull String packageName) {
        recordInternal.setPackageName(packageName);
    }

    /** Checks if the value of given column is null */
    public static boolean isNullValue(Cursor cursor, String columnName) {
        return cursor.isNull(cursor.getColumnIndex(columnName));
    }

    public static String getCursorString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndex(columnName));
    }

    public static UUID getCursorUUID(Cursor cursor, String columnName) {
        return convertBytesToUUID(cursor.getBlob(cursor.getColumnIndex(columnName)));
    }

    public static int getCursorInt(Cursor cursor, String columnName) {
        return cursor.getInt(cursor.getColumnIndex(columnName));
    }

    /** Reads integer and converts to false anything apart from 1. */
    public static boolean getIntegerAndConvertToBoolean(Cursor cursor, String columnName) {
        String value = cursor.getString(cursor.getColumnIndex(columnName));
        if (value == null || value.isEmpty()) {
            return false;
        }
        return Integer.parseInt(value) == BOOLEAN_TRUE_VALUE;
    }

    public static long getCursorLong(Cursor cursor, String columnName) {
        return cursor.getLong(cursor.getColumnIndex(columnName));
    }

    public static double getCursorDouble(Cursor cursor, String columnName) {
        return cursor.getDouble(cursor.getColumnIndex(columnName));
    }

    public static byte[] getCursorBlob(Cursor cursor, String columnName) {
        return cursor.getBlob(cursor.getColumnIndex(columnName));
    }

    public static List<String> getCursorStringList(
            Cursor cursor, String columnName, String delimiter) {
        final String values = cursor.getString(cursor.getColumnIndex(columnName));
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.asList(values.split(delimiter));
    }

    public static List<Integer> getCursorIntegerList(
            Cursor cursor, String columnName, String delimiter) {
        final String stringList = cursor.getString(cursor.getColumnIndex(columnName));
        if (stringList == null || stringList.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(stringList.split(delimiter))
                .mapToInt(Integer::valueOf)
                .boxed()
                .toList();
    }

    public static List<Long> getCursorLongList(Cursor cursor, String columnName, String delimiter) {
        final String stringList = cursor.getString(cursor.getColumnIndex(columnName));
        if (stringList == null || stringList.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(stringList.split(delimiter)).mapToLong(Long::valueOf).boxed().toList();
    }

    public static String flattenIntList(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(DELIMITER));
    }

    public static String flattenLongList(List<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(DELIMITER));
    }

    public static String flattenIntArray(int[] values) {
        return Arrays.stream(values)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(DELIMITER));
    }

    @Nullable
    public static String getMaxPrimaryKeyQuery(@NonNull String tableName) {
        return "SELECT MAX("
                + PRIMARY_COLUMN_NAME
                + ") as "
                + PRIMARY_COLUMN_NAME
                + " FROM "
                + tableName;
    }

    /**
     * Reads ZoneOffset using given cursor. Returns null of column name is not present in the table.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public static ZoneOffset getZoneOffset(Cursor cursor, String startZoneOffsetColumnName) {
        ZoneOffset zoneOffset = null;
        if (cursor.getColumnIndex(startZoneOffsetColumnName) != -1) {
            zoneOffset =
                    ZoneOffset.ofTotalSeconds(
                            StorageUtils.getCursorInt(cursor, startZoneOffsetColumnName));
        }

        return zoneOffset;
    }

    /** Encodes record properties participating in deduplication into a byte array. */
    @Nullable
    public static byte[] getDedupeByteBuffer(@NonNull RecordInternal<?> record) {
        if (!isEmpty(record.getClientRecordId())) {
            return null; // If dedupe by clientRecordId then don't dedupe by hash
        }

        if (record instanceof InstantRecordInternal<?>) {
            return getDedupeByteBuffer((InstantRecordInternal<?>) record);
        }

        if (record instanceof IntervalRecordInternal<?>) {
            return getDedupeByteBuffer((IntervalRecordInternal<?>) record);
        }

        throw new IllegalArgumentException("Unexpected record type: " + record);
    }

    @NonNull
    private static byte[] getDedupeByteBuffer(@NonNull InstantRecordInternal<?> record) {
        return ByteBuffer.allocate(Long.BYTES * 3)
                .putLong(record.getAppInfoId())
                .putLong(record.getDeviceInfoId())
                .putLong(record.getTimeInMillis())
                .array();
    }

    @Nullable
    private static byte[] getDedupeByteBuffer(@NonNull IntervalRecordInternal<?> record) {
        final int type = record.getRecordType();
        if ((type == RECORD_TYPE_HYDRATION) || (type == RECORD_TYPE_NUTRITION)) {
            return null; // Some records are exempt from deduplication
        }

        return ByteBuffer.allocate(Long.BYTES * 4)
                .putLong(record.getAppInfoId())
                .putLong(record.getDeviceInfoId())
                .putLong(record.getStartTimeInMillis())
                .putLong(record.getEndTimeInMillis())
                .array();
    }

    /** Returns a UUID for the given package name, client record id and record type id. */
    private static UUID getUUID(
            @NonNull String packageName, @NonNull String clientRecordId, int recordTypeId) {
        final byte[] packageNameBytes = packageName.getBytes();
        final byte[] clientRecordIdBytes = clientRecordId.getBytes();

        byte[] bytes =
                ByteBuffer.allocate(
                                packageNameBytes.length
                                        + Integer.BYTES
                                        + clientRecordIdBytes.length)
                        .put(packageNameBytes)
                        .putInt(getRecordTypeIdForUuid(recordTypeId))
                        .put(clientRecordIdBytes)
                        .array();
        return UUID.nameUUIDFromBytes(bytes);
    }

    /**
     * Returns if priority of apps needs to be considered to compute the aggregate request for the
     * record type. Priority to be considered only for sleep and Activity categories.
     */
    public static boolean supportsPriority(int recordType, int operationType) {
        if (operationType != SUM) {
            return false;
        }

        @HealthDataCategory.Type
        int recordCategory =
                RecordTypeRecordCategoryMapper.getRecordCategoryForRecordType(recordType);
        return recordCategory == ACTIVITY || recordCategory == SLEEP;
    }

    /** Returns list of app Ids of contributing apps for the record type in the priority order */
    public static List<Long> getAppIdPriorityList(int recordType) {
        return HealthDataCategoryPriorityHelper.getInstance()
                .getAppIdPriorityOrder(
                        RecordTypeRecordCategoryMapper.getRecordCategoryForRecordType(recordType));
    }

    /** Returns if derivation needs to be done to calculate aggregate */
    public static boolean isDerivedType(int recordType) {
        return recordType == RECORD_TYPE_BASAL_METABOLIC_RATE
                || recordType == RECORD_TYPE_TOTAL_CALORIES_BURNED;
    }

    public static UUID convertBytesToUUID(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long high = byteBuffer.getLong();
        long low = byteBuffer.getLong();
        return new UUID(high, low);
    }

    public static byte[] convertUUIDToBytes(UUID uuid) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return byteBuffer.array();
    }

    public static String getHexString(byte[] value) {
        if (value == null) {
            return "";
        }

        final StringBuilder builder = new StringBuilder("x'");
        for (byte b : value) {
            builder.append(String.format("%02x", b));
        }
        builder.append("'");

        return builder.toString();
    }

    public static String getHexString(UUID uuid) {
        return getHexString(convertUUIDToBytes(uuid));
    }

    public static List<String> getListOfHexString(List<UUID> uuids) {
        List<String> hexStrings = new ArrayList<>();
        for (UUID uuid : uuids) {
            hexStrings.add(getHexString(convertUUIDToBytes(uuid)));
        }

        return hexStrings;
    }

    public static byte[] getSingleByteArray(List<UUID> uuids) {
        byte[] allByteArray = new byte[UUID_BYTE_SIZE * uuids.size()];

        ByteBuffer byteBuffer = ByteBuffer.wrap(allByteArray);
        for (UUID uuid : uuids) {
            byteBuffer.put(convertUUIDToBytes(uuid));
        }

        return byteBuffer.array();
    }

    public static List<UUID> getCursorUUIDList(Cursor cursor, String columnName) {
        byte[] bytes = cursor.getBlob(cursor.getColumnIndex(columnName));
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        List<UUID> uuidList = new ArrayList<>();
        while (byteBuffer.hasRemaining()) {
            long high = byteBuffer.getLong();
            long low = byteBuffer.getLong();
            uuidList.add(new UUID(high, low));
        }

        return uuidList;
    }

    /**
     * Returns a quoted id if {@code id} is not quoted. Following examples show the expected return
     * values,
     *
     * <p>getNormalisedId("id") -> "'id'"
     *
     * <p>getNormalisedId("'id'") -> "'id'"
     *
     * <p>getNormalisedId("x'id'") -> "x'id'"
     */
    public static String getNormalisedString(String id) {
        if (!id.startsWith("'") && !id.startsWith("x'")) {
            return "'" + id + "'";
        }

        return id;
    }

    /** Extracts and holds data from {@link ContentValues}. */
    public static class RecordIdentifierData {
        private final String mClientRecordId;
        private final UUID mUuid;

        public RecordIdentifierData(ContentValues contentValues) {
            mClientRecordId = contentValues.getAsString(CLIENT_RECORD_ID_COLUMN_NAME);
            mUuid = StorageUtils.convertBytesToUUID(contentValues.getAsByteArray(UUID_COLUMN_NAME));
        }

        @Nullable
        public String getClientRecordId() {
            return mClientRecordId;
        }

        @Nullable
        public UUID getUuid() {
            return mUuid;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            if (mClientRecordId != null && !mClientRecordId.isEmpty()) {
                builder.append("clientRecordID : ").append(mClientRecordId).append(" , ");
            }

            if (mUuid != null) {
                builder.append("uuid : ").append(mUuid).append(" , ");
            }
            return builder.toString();
        }
    }
}

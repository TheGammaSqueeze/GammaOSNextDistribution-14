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

package com.android.adservices.data.encryptionkey;

import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.service.encryptionkey.EncryptionKey;

import java.util.function.Function;

/** Helper class for EncryptionKey table SQLite operations. */
public class SqliteObjectMapper {

    /** Create {@link EncryptionKey} object from SQLite datastore. */
    public static EncryptionKey constructEncryptionKeyFromCursor(Cursor cursor) {
        EncryptionKey.Builder builder = new EncryptionKey.Builder();
        setTextColumn(cursor, EncryptionKeyTables.EncryptionKeyContract.ID, builder::setId);
        setTextColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE,
                (enumValue) -> builder.setKeyType(EncryptionKey.KeyType.valueOf(enumValue)));
        setTextColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setUriColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN,
                builder::setReportingOrigin);
        setTextColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.ENCRYPTION_KEY_URL,
                builder::setEncryptionKeyUrl);
        setTextColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.PROTOCOL_TYPE,
                (enumValue) ->
                        builder.setProtocolType(EncryptionKey.ProtocolType.valueOf(enumValue)));
        setIntColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID,
                builder::setKeyCommitmentId);
        setTextColumn(cursor, EncryptionKeyTables.EncryptionKeyContract.BODY, builder::setBody);
        setLongColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.EXPIRATION,
                builder::setExpiration);
        setLongColumn(
                cursor,
                EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME,
                builder::setLastFetchTime);

        return builder.build();
    }

    private static <BuilderType> void setTextColumn(
            Cursor cursor, String column, Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    private static <BuilderType> void setIntColumn(
            Cursor cursor, String column, Function<Integer, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getInt, setter);
    }

    private static <BuilderType> void setLongColumn(
            Cursor cursor, String column, Function<Long, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, setter);
    }

    private static <BuilderType> void setUriColumn(
            Cursor cursor, String column, Function<Uri, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, (x) -> setter.apply(Uri.parse(x)));
    }

    @SuppressWarnings("ReturnValueIgnored")
    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor,
            String column,
            Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }
}

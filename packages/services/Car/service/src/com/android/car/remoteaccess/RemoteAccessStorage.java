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

package com.android.car.remoteaccess;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.car.CarServiceUtils;
import com.android.car.CarServiceUtils.EncryptedData;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class RemoteAccessStorage {

    private static final String TAG = RemoteAccessStorage.class.getSimpleName();
    private static final String REMOTE_ACCESS_KEY_ALIAS = "REMOTE_ACCESS_KEY_ALIAS";

    private static String sKeyAlias = REMOTE_ACCESS_KEY_ALIAS;

    private final RemoteAccessDbHelper mDbHelper;

    RemoteAccessStorage(Context context, SystemInterface systemInterface, boolean inMemoryStorage) {
        mDbHelper = new RemoteAccessDbHelper(context,
                systemInterface.getSystemCarDir().getAbsolutePath(), inMemoryStorage);
    }

    void release() {
        mDbHelper.close();
    }

    @Nullable
    ClientIdEntry getClientIdEntry(String packageName) {
        return ClientIdTable.queryClientIdEntry(mDbHelper.getReadableDatabase(), packageName);
    }

    @Nullable
    List<ClientIdEntry> getClientIdEntries() {
        return ClientIdTable.queryClientIdEntries(mDbHelper.getReadableDatabase());
    }

    boolean updateClientId(ClientIdEntry entry) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransaction();
            if (!ClientIdTable.replaceEntry(db, entry)) {
                return false;
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    boolean deleteClientId(String clientId) {
        // TODO(b/260523566): Implement the logic.
        return false;
    }

    @VisibleForTesting
    static void setKeyAlias(String keyAlias) {
        sKeyAlias = keyAlias;
    }

    // Defines the client token entry stored in the ClientIdTable.
    static final class ClientIdEntry {
        public final String clientId;
        public final long idCreationTime;
        public final String uidName;

        ClientIdEntry(String clientId, long idCreationTime, String uidName) {
            Preconditions.checkArgument(uidName != null, "uidName cannot be null");
            this.clientId = clientId;
            this.idCreationTime = idCreationTime;
            this.uidName = uidName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ClientIdEntry)) {
                return false;
            }
            ClientIdEntry other = (ClientIdEntry) obj;
            return clientId.equals(other.clientId) && idCreationTime == other.idCreationTime
                    && uidName.equals(other.uidName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientId, idCreationTime, uidName);
        }

        @Override
        public String toString() {
            return new StringBuilder().append("ClientIdEntry{clientId: ").append(clientId)
                    .append(", idCreationTime: ").append(idCreationTime).append(", uidName: ")
                    .append(uidName).append('}').toString();
        }
    }

    /**
     * Defines the contents and queries for the client ID table.
     */
    static final class ClientIdTable {
        // TODO(b/266371728): Add columns to handle client ID expiration.
        public static final String TABLE_NAME = "client_token_table";
        public static final String INDEX_NAME = "index_package_name";
        public static final String COLUMN_CLIENT_ID = "client_id";
        public static final String COLUMN_CLIENT_ID_CREATION_TIME = "id_creation_time";
        public static final String COLUMN_UID_NAME = "uid_name";
        public static final String COLUMN_SECRET_KEY_IV = "secret_key_iv";

        private static final String STRING_ENCODING = "UTF-8";

        public static void createDb(SQLiteDatabase db) {
            StringBuilder createCommand = new StringBuilder();
            createCommand.append("CREATE TABLE ").append(TABLE_NAME).append(" (")
                    .append(COLUMN_UID_NAME).append(" TEXT NOT NULL PRIMARY KEY, ")
                    .append(COLUMN_CLIENT_ID).append(" BLOB NOT NULL, ")
                    .append(COLUMN_CLIENT_ID_CREATION_TIME).append(" BIGINT NOT NULL, ")
                    .append(COLUMN_SECRET_KEY_IV).append(" BLOB NOT NULL")
                    .append(")");
            db.execSQL(createCommand.toString());
            Slogf.i(TAG, "%s table is successfully created in the %s database (version %d)",
                    TABLE_NAME, RemoteAccessDbHelper.DATABASE_NAME,
                    RemoteAccessDbHelper.DATABASE_VERSION);
        }

        // Returns ClientIdEntry for the given package name.
        @SuppressWarnings("FormatString")
        @Nullable
        public static ClientIdEntry queryClientIdEntry(SQLiteDatabase db, String packageName) {
            String queryCommand = String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s = ?",
                    COLUMN_UID_NAME, COLUMN_CLIENT_ID, COLUMN_CLIENT_ID_CREATION_TIME,
                    COLUMN_SECRET_KEY_IV, TABLE_NAME, COLUMN_UID_NAME);
            String[] selectionArgs = new String[]{packageName};

            try (Cursor cursor = db.rawQuery(queryCommand, selectionArgs)) {
                while (cursor.moveToNext()) {
                    // Query by a primary key must return one result.
                    byte[] data = CarServiceUtils.decryptData(new EncryptedData(cursor.getBlob(1),
                            cursor.getBlob(3)), sKeyAlias);
                    if (data == null) {
                        Slogf.e(TAG, "Failed to query package name(%s): cannot decrypt data",
                                packageName);
                        return null;
                    }
                    try {
                        return new ClientIdEntry(new String(data, STRING_ENCODING),
                                cursor.getLong(2), cursor.getString(0));
                    } catch (UnsupportedEncodingException e) {
                        Slogf.e(TAG, e, "Failed to query package name(%s)", packageName);
                        return null;
                    }
                }
            }
            Slogf.w(TAG, "No entry for package name(%s) is found", packageName);
            return null;
        }

        // Returns all client ID entries.
        @SuppressWarnings("FormatString")
        @Nullable
        public static List<ClientIdEntry> queryClientIdEntries(SQLiteDatabase db) {
            String queryCommand = String.format("SELECT %s, %s, %s, %s FROM %s",
                    COLUMN_UID_NAME, COLUMN_CLIENT_ID, COLUMN_CLIENT_ID_CREATION_TIME,
                    COLUMN_SECRET_KEY_IV, TABLE_NAME);

            try (Cursor cursor = db.rawQuery(queryCommand, new String[]{})) {
                int entryCount = cursor.getCount();
                if (entryCount == 0) return null;
                List<ClientIdEntry> entries = new ArrayList<>(entryCount);
                while (cursor.moveToNext()) {
                    byte[] data = CarServiceUtils.decryptData(new EncryptedData(cursor.getBlob(1),
                            cursor.getBlob(3)), sKeyAlias);
                    if (data == null) {
                        Slogf.e(TAG, "Failed to query all client IDs: cannot decrypt data");
                        return null;
                    }
                    try {
                        entries.add(new ClientIdEntry(new String(data, STRING_ENCODING),
                                cursor.getLong(2), cursor.getString(0)));
                    } catch (UnsupportedEncodingException e) {
                        Slogf.e(TAG, "Failed to query all client IDs", e);
                        return null;
                    }
                }
                return entries;
            }
        }

        public static boolean replaceEntry(SQLiteDatabase db, ClientIdEntry entry) {
            EncryptedData data;
            try {
                data = CarServiceUtils.encryptData(entry.clientId.getBytes(STRING_ENCODING),
                        sKeyAlias);
            } catch (UnsupportedEncodingException e) {
                Slogf.e(TAG, e, "Failed to replace %s entry[%s]", TABLE_NAME, entry);
                return false;
            }
            if (data == null) {
                Slogf.e(TAG, "Failed to replace %s entry[%s]: cannot encrypt client ID",
                        TABLE_NAME, entry);
                return false;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_UID_NAME, entry.uidName);
            values.put(COLUMN_CLIENT_ID, data.getEncryptedData());
            values.put(COLUMN_CLIENT_ID_CREATION_TIME, entry.idCreationTime);
            values.put(COLUMN_SECRET_KEY_IV, data.getIv());

            try {
                if (db.replaceOrThrow(ClientIdTable.TABLE_NAME, /* nullColumnHack= */ null,
                        values) == -1) {
                    Slogf.e(TAG, "Failed to replace %s entry [%s]", TABLE_NAME, entry);
                    return false;
                }
            } catch (SQLException e) {
                Slogf.e(TAG, e, "Failed to replace %s entry [%s]", TABLE_NAME, entry);
                return false;
            }
            return true;
        }
    }

    static final class RemoteAccessDbHelper extends SQLiteOpenHelper {
        public static final String DATABASE_NAME = "car_remoteaccess.db";

        private static final int DATABASE_VERSION = 1;

        private static String getName(String systemCarDirPath, boolean inMemoryStorage) {
            return inMemoryStorage ? null : new File(systemCarDirPath, DATABASE_NAME)
                    .getAbsolutePath();
        }

        RemoteAccessDbHelper(Context context, String systemCarDirPath, boolean inMemoryStorage) {
            super(context.createDeviceProtectedStorageContext(),
                    getName(systemCarDirPath, inMemoryStorage), /* factory= */ null,
                    DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            ClientIdTable.createDb(db);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            // Do nothing. We have only one version.
        }
    }
}

/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.bluetooth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Define an implementation of ContentProvider for the Bluetooth migration
 */
class BluetoothLegacyMigration: ContentProvider() {
    companion object {
        private const val TAG = "BluetoothLegacyMigration"

        private const val AUTHORITY = "bluetooth_legacy.provider"

        private const val START_LEGACY_MIGRATION_CALL = "start_legacy_migration"
        private const val FINISH_LEGACY_MIGRATION_CALL = "finish_legacy_migration"

        private const val PHONEBOOK_ACCESS_PERMISSION = "phonebook_access_permission"
        private const val MESSAGE_ACCESS_PERMISSION = "message_access_permission"
        private const val SIM_ACCESS_PERMISSION = "sim_access_permission"

        private const val VOLUME_MAP = "bluetooth_volume_map"

        private const val OPP = "OPPMGR"
        private const val BLUETOOTH_OPP_CHANNEL = "btopp_channels"
        private const val BLUETOOTH_OPP_NAME = "btopp_names"

        private const val BLUETOOTH_SIGNED_DEFAULT = "com.google.android.bluetooth_preferences"

        private const val KEY_LIST = "key_list"

        private enum class UriId(
            val fileName: String,
            val handler: (ctx: Context) -> DatabaseHandler
        ) {
            BLUETOOTH(BluetoothDatabase.DATABASE_NAME, ::BluetoothDatabase),
            OPP(OppDatabase.DATABASE_NAME, ::OppDatabase),
        }

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            UriId.values().map { addURI(AUTHORITY, it.fileName, it.ordinal) }
        }

        private fun putObjectInBundle(bundle: Bundle, key: String, obj: Any?) {
            when (obj) {
                is Boolean -> bundle.putBoolean(key, obj)
                is Int -> bundle.putInt(key, obj)
                is Long -> bundle.putLong(key, obj)
                is String -> bundle.putString(key, obj)
                null -> throw UnsupportedOperationException("null type is not handled")
                else -> throw UnsupportedOperationException("${obj.javaClass.simpleName}: type is not handled")
            }
        }
    }

    private lateinit var mContext: Context

    /**
     * Always return true, indicating that the
     * provider loaded correctly.
     */
    override fun onCreate(): Boolean {
        mContext = context!!.createDeviceProtectedStorageContext()
        return true
    }

    /**
     * Use a content URI to get database name associated
     *
     * @param uri Content uri
     * @return A {@link Cursor} containing the results of the query.
     */
    override fun getType(uri: Uri): String {
        val database = UriId.values().firstOrNull { it.ordinal == URI_MATCHER.match(uri) }
            ?: throw UnsupportedOperationException("This Uri is not supported: $uri")
        return database.fileName
    }

    /**
     * Use a content URI to get information about a database
     *
     * @param uri Content uri
     * @param projection unused
     * @param selection unused
     * @param selectionArgs unused
     * @param sortOrder unused
     * @return A {@link Cursor} containing the results of the query.
     *
     */
    @Override
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val database = UriId.values().firstOrNull { it.ordinal == URI_MATCHER.match(uri) }
            ?: throw UnsupportedOperationException("This Uri is not supported: $uri")
        return database.handler(mContext).toCursor()
    }

    /**
     * insert() is not supported
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    /**
     * delete() is not supported
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException()
    }

    /**
     * update() is not supported
     */
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException()
    }

    abstract class MigrationHandler {
        abstract fun toBundle(): Bundle?
        abstract fun delete()
    }

    private class SharedPreferencesHandler(private val ctx: Context, private val key: String) :
        MigrationHandler() {

        override fun toBundle(): Bundle? {
            val pref = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            if (pref.all.isEmpty()) {
                Log.d(TAG, "No migration needed for shared preference: $key")
                return null
            }
            val bundle = Bundle()
            val keys = arrayListOf<String>()
            for (e in pref.all) {
                keys += e.key
                putObjectInBundle(bundle, e.key, e.value)
            }
            bundle.putStringArrayList(KEY_LIST, keys)
            Log.d(TAG, "SharedPreferences migrating ${keys.size} key(s) from $key")
            return bundle
        }

        override fun delete() {
            ctx.deleteSharedPreferences(key)
            Log.d(TAG, "$key: SharedPreferences deleted")
        }
    }

    abstract class DatabaseHandler(private val ctx: Context, private val dbName: String) :
        MigrationHandler() {

        abstract val sql: String

        fun toCursor(): Cursor? {
            val databasePath = ctx.getDatabasePath(dbName)
            if (!databasePath.exists()) {
                Log.d(TAG, "No migration needed for database: $dbName")
                return null
            }
            val db = SQLiteDatabase.openDatabase(
                databasePath,
                SQLiteDatabase.OpenParams.Builder().addOpenFlags(SQLiteDatabase.OPEN_READONLY)
                    .build()
            )
            return db.rawQuery(sql, null)
        }

        override fun toBundle(): Bundle? {
            throw UnsupportedOperationException()
        }

        override fun delete() {
            val databasePath = ctx.getDatabasePath(dbName)
            databasePath.delete()
            Log.d(TAG, "$dbName: database deleted")
        }
    }

    private class BluetoothDatabase(ctx: Context) : DatabaseHandler(ctx, DATABASE_NAME) {
        companion object {
            const val DATABASE_NAME = "bluetooth_db"
        }
        private val dbTable = "metadata"
        override val sql = "select * from $dbTable"
    }

    private class OppDatabase(ctx: Context) : DatabaseHandler(ctx, DATABASE_NAME) {
        companion object {
            const val DATABASE_NAME = "btopp.db"
        }
        private val dbTable = "btopp"
        override val sql = "select * from $dbTable"
    }

    /**
     * Fetch legacy data describe by {@code arg} and perform {@code method} action on it
     *
     * @param method Action to perform. One of START_LEGACY_MIGRATION_CALL|FINISH_LEGACY_MIGRATION_CALL
     * @param arg item on witch to perform the action specified by {@code method}
     * @param extras unused
     * @return A {@link Bundle} containing the results of the query.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val migrationHandler = when (arg) {
            OPP,
            VOLUME_MAP,
            BLUETOOTH_OPP_NAME,
            BLUETOOTH_OPP_CHANNEL,
            SIM_ACCESS_PERMISSION,
            MESSAGE_ACCESS_PERMISSION,
            PHONEBOOK_ACCESS_PERMISSION -> SharedPreferencesHandler(mContext, arg)
            BLUETOOTH_SIGNED_DEFAULT -> {
                val key = mContext.packageName + "_preferences"
                SharedPreferencesHandler(mContext, key)
            }
            BluetoothDatabase.DATABASE_NAME -> BluetoothDatabase(mContext)
            OppDatabase.DATABASE_NAME -> OppDatabase(mContext)
            else -> throw UnsupportedOperationException()
        }
        return when (method) {
            START_LEGACY_MIGRATION_CALL -> migrationHandler.toBundle()
            FINISH_LEGACY_MIGRATION_CALL -> {
                migrationHandler.delete()
                return null
            }
            else -> throw UnsupportedOperationException()
        }
    }
}

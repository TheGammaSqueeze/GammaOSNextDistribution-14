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

package com.android.adservices.data;

import android.annotation.NonNull;
import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.bundle.EntityBundle;
import androidx.room.migration.bundle.FieldBundle;
import androidx.room.migration.bundle.SchemaBundle;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.common.cache.CacheDatabase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** This UT is a guardrail to schema migration managed by Room. */
public class RoomSchemaMigrationGuardrailTest {
    // Note that this is not the context of this test, but the different context whose assets folder
    // is adservices/service-core/schemas
    private static final Context TARGET_CONTEXT =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static final List<Class<? extends RoomDatabase>> DATABASE_CLASSES =
            Arrays.stream(RoomDatabaseRegistration.class.getDeclaredFields())
                    .map(Field::getType)
                    .filter(aClass -> aClass.getSuperclass() == RoomDatabase.class)
                    .map(c -> (Class<? extends RoomDatabase>) c)
                    .collect(Collectors.toList());

    private static final Map<Class<? extends RoomDatabase>, List<Object>> ADDITIONAL_CONVERTOR =
            ImmutableMap.of(
                    CustomAudienceDatabase.class,
                    ImmutableList.of(new DBCustomAudience.Converters(true, true)));

    private static final List<DatabaseWithVersion> BYPASS_DATABASE_VERSIONS_NEW_FIELD_ONLY =
            ImmutableList.of(new DatabaseWithVersion(CustomAudienceDatabase.class, 2));
    private static final List<Class<? extends RoomDatabase>>
            BYPASS_DATABASE_CLASS_MIGRATION_TEST_ENFORCEMENT =
                    ImmutableList.of(CacheDatabase.class);
    // TODO(b/318501421): Implement missing tests.
    private static final List<DatabaseWithVersion> BYPASS_DATABASE_MIGRATION_TEST_FOR_VERSION =
            ImmutableList.of(
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 1),
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 3),
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 4),
                    new DatabaseWithVersion(AdSelectionDatabase.class, 4),
                    new DatabaseWithVersion(AdSelectionDatabase.class, 5),
                    new DatabaseWithVersion(SharedStorageDatabase.class, 2));

    private List<String> mErrors;

    @Before
    public void setup() {
        mErrors = new ArrayList<>();
    }

    @After
    public void teardown() {
        if (!mErrors.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Finish validating room databases with error \n%s",
                            String.join("\n", mErrors)));
        }
    }

    @Test
    public void validateDatabaseMigrationAllowedChanges() throws IOException {
        List<DatabaseWithVersion> databaseClassesWithNewestVersion =
                validateAndGetDatabaseClassesWithNewestVersionNumber();
        for (DatabaseWithVersion databaseWithVersion : databaseClassesWithNewestVersion) {
            validateClassMatchAndNewFieldOnly(databaseWithVersion);
            validateDatabaseMigrationTestExists(databaseWithVersion);
        }
    }

    private void validateDatabaseMigrationTestExists(DatabaseWithVersion databaseWithVersion) {
        if (databaseWithVersion.mVersion == 1) {
            return;
        }
        if (BYPASS_DATABASE_CLASS_MIGRATION_TEST_ENFORCEMENT.contains(
                databaseWithVersion.mRoomDatabaseClass)) {
            return;
        }

        String databaseClassName = databaseWithVersion.mRoomDatabaseClass.getCanonicalName();
        String testClassName = String.format("%sMigrationTest", databaseClassName);
        try {
            Class<?> testClass = Class.forName(testClassName);
            for (int i = 1; i < databaseWithVersion.mVersion; i++) {
                if (BYPASS_DATABASE_MIGRATION_TEST_FOR_VERSION.contains(
                        new DatabaseWithVersion(databaseWithVersion.mRoomDatabaseClass, i))) {
                    return;
                }
                String migrationTestMethodPrefix = String.format("testMigration%dTo%d", i, i + 1);
                if (!Arrays.stream(testClass.getMethods())
                        .anyMatch(method -> method.getName().contains(migrationTestMethodPrefix))) {
                    mErrors.add(
                            String.format(
                                    "Migration test %s* missing for database %s",
                                    migrationTestMethodPrefix, databaseClassName));
                }
            }
        } catch (ClassNotFoundException classNotFoundException) {
            mErrors.add(
                    String.format("Database migration test class %s is missing.", testClassName));
        }
    }

    private List<DatabaseWithVersion> validateAndGetDatabaseClassesWithNewestVersionNumber() {
        ImmutableList.Builder<DatabaseWithVersion> result = new ImmutableList.Builder<>();
        for (Class<? extends RoomDatabase> clazz : DATABASE_CLASSES) {
            try {
                final int newestDatabaseVersion = getNewestDatabaseVersion(clazz);
                result.add(new DatabaseWithVersion(clazz, newestDatabaseVersion));
            } catch (Exception e) {
                mErrors.add(
                        String.format(
                                "Fail to get database version for %s, with error %s.",
                                clazz.getCanonicalName(), e.getMessage()));
            }
        }
        return result.build();
    }

    private int getNewestDatabaseVersion(Class<? extends RoomDatabase> database)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        return database.getField("DATABASE_VERSION").getInt(null);
    }

    private void validateClassMatchAndNewFieldOnly(DatabaseWithVersion databaseWithVersion) {
        // Custom audience table v1 to v2 is violating the policy. Skip it.
        if (BYPASS_DATABASE_VERSIONS_NEW_FIELD_ONLY.contains(databaseWithVersion)) {
            return;
        }
        int newestDatabaseVersion = databaseWithVersion.mVersion;
        Class<? extends RoomDatabase> roomDatabaseClass = databaseWithVersion.mRoomDatabaseClass;
        if (databaseWithVersion.mVersion == 1) {
            return;
        }

        SchemaBundle oldSchemaBundle;
        SchemaBundle newSchemaBundle;
        try {
            oldSchemaBundle = loadSchema(roomDatabaseClass, newestDatabaseVersion - 1);
            newSchemaBundle = loadSchema(roomDatabaseClass, newestDatabaseVersion);
        } catch (IOException e) {
            mErrors.add(
                    String.format(
                            "Database %s schema not exported or exported with error.",
                            roomDatabaseClass.getName()));
            return;
        }

        Map<String, EntityBundle> oldTables =
                oldSchemaBundle.getDatabase().getEntitiesByTableName();
        Map<String, EntityBundle> newTables =
                newSchemaBundle.getDatabase().getEntitiesByTableName();
        validateSchemaBundleMatchesSchema(databaseWithVersion.mRoomDatabaseClass, newTables);

        // We don't care new table in a new DB version. So iterate through the old version.
        for (Map.Entry<String, EntityBundle> e : oldTables.entrySet()) {
            String tableName = e.getKey();

            // table in old version must show in new.
            if (!newTables.containsKey(tableName)) {
                mErrors.add(
                        String.format(
                                "New version DB is missing table %s present in old version",
                                tableName));
                continue;
            }

            EntityBundle oldEntityBundle = e.getValue();
            EntityBundle newEntityBundle = newTables.get(tableName);

            for (FieldBundle oldFieldBundle : oldEntityBundle.getFields()) {
                if (newEntityBundle.getFields().stream().noneMatch(oldFieldBundle::isSchemaEqual)) {
                    mErrors.add(
                            String.format(
                                    "Table %s and field %s: Missing field in new version or"
                                            + " mismatch field in new and old version.",
                                    tableName, oldEntityBundle));
                }
            }
        }
    }

    private void validateSchemaBundleMatchesSchema(
            Class<? extends RoomDatabase> database, Map<String, EntityBundle> entityBundleByName) {
        RoomDatabase inMemoryDatabase = getInMemoryDatabase(database);
        Map<String, String> tables =
                getTablesWithCreateSql(inMemoryDatabase.getOpenHelper().getReadableDatabase());
        for (Map.Entry<String, String> table : tables.entrySet()) {
            String name = table.getKey();
            String createSqlByClass = table.getValue();
            EntityBundle entityBundle = entityBundleByName.getOrDefault(name, null);
            String createSqlFromBundle =
                    Optional.ofNullable(entityBundle)
                            .map(EntityBundle::getCreateSql)
                            .map(
                                    sql ->
                                            sql.replace(
                                                    "IF NOT EXISTS `${TABLE_NAME}`",
                                                    "`" + name + "`"))
                            .orElse(null);
            if (!createSqlByClass.equals(createSqlFromBundle)) {
                mErrors.add(
                        String.format(
                                "Database %s, table %s class definition mismatches the exported"
                                        + " schema, did you forget to build package locally?\n"
                                        + "\tCreate sql by class: %s\n"
                                        + "\tCreate sql by bundle: %s",
                                database.getCanonicalName(),
                                name,
                                createSqlByClass,
                                createSqlFromBundle));
            }
        }
        if (!tables.keySet().containsAll(entityBundleByName.keySet())) {
            mErrors.add(
                    String.format(
                            "Database %s, table is deleted and schema json is not updated, did you"
                                    + " forget to build package locally?\n"
                                    + "\tTables in class are [%s]\n"
                                    + "\ttables in json are [%s]. ",
                            database.getCanonicalName(),
                            String.join(",", tables.keySet()),
                            String.join(",", entityBundleByName.keySet())));
        }
    }

    @NotNull
    private static RoomDatabase getInMemoryDatabase(Class<? extends RoomDatabase> database) {
        RoomDatabase.Builder<? extends RoomDatabase> builder =
                Room.inMemoryDatabaseBuilder(TARGET_CONTEXT, database);
        if (ADDITIONAL_CONVERTOR.containsKey(database)) {
            for (Object convertor : ADDITIONAL_CONVERTOR.get(database)) {
                builder.addTypeConverter(convertor);
            }
        }
        return builder.build();
    }

    private static Map<String, String> getTablesWithCreateSql(SupportSQLiteDatabase db) {
        Cursor c =
                db.query(
                        "SELECT name,sql FROM sqlite_master WHERE type='table' AND name not in"
                                + " ('room_master_table', 'android_metadata', 'sqlite_sequence')");
        c.moveToFirst();
        ImmutableMap.Builder<String, String> tables = new ImmutableMap.Builder<>();
        do {
            String name = c.getString(0);
            String createSql = c.getString(1);
            tables.put(name, createSql);
        } while (c.moveToNext());
        return tables.build();
    }

    private SchemaBundle loadSchema(Class<? extends RoomDatabase> database, int version)
            throws IOException {
        InputStream input =
                TARGET_CONTEXT
                        .getAssets()
                        .open(database.getCanonicalName() + "/" + version + ".json");
        return SchemaBundle.deserialize(input);
    }

    private static class DatabaseWithVersion {
        @NonNull private final Class<? extends RoomDatabase> mRoomDatabaseClass;
        private final int mVersion;

        DatabaseWithVersion(@NonNull Class<? extends RoomDatabase> roomDatabaseClass, int version) {
            mRoomDatabaseClass = roomDatabaseClass;
            mVersion = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DatabaseWithVersion)) return false;
            DatabaseWithVersion that = (DatabaseWithVersion) o;
            return mVersion == that.mVersion && mRoomDatabaseClass.equals(that.mRoomDatabaseClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mRoomDatabaseClass, mVersion);
        }

        @Override
        public String toString() {
            return "DatabaseWithVersion{"
                    + "mRoomDatabaseClass="
                    + mRoomDatabaseClass
                    + ", mVersion="
                    + mVersion
                    + '}';
        }
    }
}

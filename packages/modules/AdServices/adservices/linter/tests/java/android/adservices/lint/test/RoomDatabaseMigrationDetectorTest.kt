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

package android.adservices.lint.test

import android.adservices.lint.RoomDatabaseMigrationDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RoomDatabaseMigrationDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = RoomDatabaseMigrationDetector()

    override fun getIssues(): List<Issue> = listOf(RoomDatabaseMigrationDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testMigrationPath_happyCase() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testMigrationPath_missingDatabaseAnnotationError() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

public class FakeDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.MISSING_DATABASE_ANNOTATION_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_missingMigrationPathAttribute() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    version = FakeDatabase.DATABASE_VERSION)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.MISSING_AUTO_MIGRATION_ATTRIBUTE_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }
    @Test
    fun testMigrationPath_incompleteMigrationPath() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.INCOMPLETE_MIGRATION_PATH_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_missingDatabaseVersionFieldInClass() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = 2)
public class FakeDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.MISSING_DATABASE_VERSION_FIELD_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_missingDatabaseVersionAnnotationField() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    })
public class FakeDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(
                RoomDatabaseMigrationDetector.MISSING_DATABASE_VERSION_ANNOTATION_ATTRIBUTE_ERROR
            )
            .expectContains(RoomDatabaseMigrationDetector.MISSING_DATABASE_VERSION_FIELD_ERROR)
            .expectContains(createErrorCountString(2, 0))
    }

    @Test
    fun testMigrationPath_failedToReferenceDatabaseVersionError() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = 2)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(
                RoomDatabaseMigrationDetector.FAILED_REF_VERSION_FIELD_IN_ANNOTATION_ERROR
            )
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_schemaExportFalseError() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION,
    exportSchema = false)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.SCHEMA_EXPORT_FALSE_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_schemaExportTrue_happyCase() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION,
    exportSchema = true)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
    FakeDatabase mFakeDatabase;
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testMigrationPath_databaseNotRegistered() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                java(
                    """
package com.android.adservices.data;

import com.android.adservices.service.common.fake.packagename.FakeDatabase;

class RoomDatabaseRegistration {
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(
                RoomDatabaseMigrationDetector.DATABASE_NOT_REGISTERED_ERROR.format(
                    "com.android.adservices.service.common.fake.packagename.FakeDatabase"
                )
            )
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_databaseRegisterClassMissing() {
        lint()
            .files(
                java(
                    """
package com.android.adservices.service.common.fake.packagename;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {},
    autoMigrations = {
        @AutoMigration(from = 1, to = 2),
    },
    version = FakeDatabase.DATABASE_VERSION)
public class FakeDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "fake.db";

    // Singleton and dao declaration.
}
"""
                ),
                *stubs
            )
            .issues(RoomDatabaseMigrationDetector.ISSUE)
            .run()
            .expectContains(RoomDatabaseMigrationDetector.DATABASE_REGISTRATION_CLASS_MISSING_ERROR)
            .expectContains(createErrorCountString(1, 0))
    }

    @Test
    fun testMigrationPath_noDatabaseClass_happyCase() {
        lint().files(*stubs).issues(RoomDatabaseMigrationDetector.ISSUE).run().expectClean()
    }

    private val database: TestFile =
        kotlin(
            """
package androidx.room

@kotlin.annotation.Target @kotlin.annotation.Retention public final annotation class Database public constructor(entities: kotlin.Array<kotlin.reflect.KClass<*>> /* = compiled code */, views: kotlin.Array<kotlin.reflect.KClass<*>> /* = compiled code */, version: kotlin.Int, exportSchema: kotlin.Boolean /* = compiled code */, autoMigrations: kotlin.Array<androidx.room.AutoMigration> /* = compiled code */) : kotlin.Annotation {
    public final val autoMigrations: kotlin.Array<androidx.room.AutoMigration> /* compiled code */

    public final val entities: kotlin.Array<kotlin.reflect.KClass<*>> /* compiled code */

    public final val exportSchema: kotlin.Boolean /* compiled code */

    public final val version: kotlin.Int /* compiled code */

    public final val views: kotlin.Array<kotlin.reflect.KClass<*>> /* compiled code */
}
"""
        )

    private val autoMigration: TestFile =
        kotlin(
            """
package androidx.room

@kotlin.annotation.Target @kotlin.annotation.Retention public final annotation class AutoMigration public constructor(from: kotlin.Int, to: kotlin.Int, spec: kotlin.reflect.KClass<*> /* = compiled code */) : kotlin.Annotation {
    public final val from: kotlin.Int /* compiled code */

    public final val spec: kotlin.reflect.KClass<*> /* compiled code */

    public final val to: kotlin.Int /* compiled code */
}"""
        )

    private val roomDatabase: TestFile =
        kotlin(
            """
package androidx.room

abstract class RoomDatabase
"""
                .trimIndent()
        )

    private val stubs = arrayOf(database, roomDatabase, autoMigration)

    private fun createErrorCountString(errors: Int, warnings: Int): String {
        return "%d errors, %d warnings".format(errors, warnings)
    }
}

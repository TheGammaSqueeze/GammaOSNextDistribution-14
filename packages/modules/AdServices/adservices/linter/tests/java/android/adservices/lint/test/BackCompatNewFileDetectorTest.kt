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

import android.adservices.lint.BackCompatNewFileDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BackCompatNewFileDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = BackCompatNewFileDetector()

    override fun getIssues(): List<Issue> = listOf(BackCompatNewFileDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun applicableConstructorCalls_throws() {
        lint().files(
                java("""
package com.android.adservices.service.common.fake.packagename;

import java.io.File;

public final class FakeClass {
    public FakeClass() {
        File myFile1 = new File("pathname");
        File myFile2 = new File("parent", "child");
    }
}
                """), *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expect("""
                    src/com/android/adservices/service/common/fake/packagename/FakeClass.java:8: Error: Please use FileCompatUtils to ensure any newly added files have a name that begins with "adservices" or create the files in a subdirectory called "adservices/" (go/rb-extservices-ota-data-cleanup) [NewAdServicesFile]
            File myFile1 = new File("pathname");
                           ~~~~~~~~~~~~~~~~~~~~
    src/com/android/adservices/service/common/fake/packagename/FakeClass.java:9: Error: Please use FileCompatUtils to ensure any newly added files have a name that begins with "adservices" or create the files in a subdirectory called "adservices/" (go/rb-extservices-ota-data-cleanup) [NewAdServicesFile]
            File myFile2 = new File("parent", "child");
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~
    2 errors, 0 warnings
                """.trimIndent()

                )
    }

    @Test
    fun otherFileMethods_doesNotThrow() {
        lint().files(
                java("""
package com.android.adservices.service.common.fake.packagename;

import java.io.File;

public final class FakeClass {
    private boolean isDeleted = false;

    public FakeClass(File myFile) {
        if (myFile.exists()) {
            isDeleted = myFile.delete();
        }
    }
}
                """), *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_throws() {
        lint().files(
                java("""
package com.android.adservices.service.common.fake.packagename;

import android.content.Context;
import androidx.room.Room;

public final class FakeClass {
    public FakeClass(Context context) {
        context.getDatabasePath("stringName");
        context.getSharedPreferences("stringName", intMode);
        Room.databaseBuilder(context, FakeClass.class, "stringName");
    }
}
                """), *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expect("""
                    src/com/android/adservices/service/common/fake/packagename/FakeClass.java:9: Error: Please use FileCompatUtils to ensure any newly added files have a name that begins with "adservices" or create the files in a subdirectory called "adservices/" (go/rb-extservices-ota-data-cleanup) [NewAdServicesFile]
            context.getDatabasePath("stringName");
                    ~~~~~~~~~~~~~~~
    src/com/android/adservices/service/common/fake/packagename/FakeClass.java:10: Error: Please use FileCompatUtils to ensure any newly added files have a name that begins with "adservices" or create the files in a subdirectory called "adservices/" (go/rb-extservices-ota-data-cleanup) [NewAdServicesFile]
            context.getSharedPreferences("stringName", intMode);
                    ~~~~~~~~~~~~~~~~~~~~
    src/com/android/adservices/service/common/fake/packagename/FakeClass.java:11: Error: Please use FileCompatUtils to ensure any newly added files have a name that begins with "adservices" or create the files in a subdirectory called "adservices/" (go/rb-extservices-ota-data-cleanup) [NewAdServicesFile]
            Room.databaseBuilder(context, FakeClass.class, "stringName");
                 ~~~~~~~~~~~~~~~
    3 errors, 0 warnings
                """.trimIndent()

                )
    }

    @Test
    fun sameMethodNames_differentClasses_doesNotThrow() {
        lint().files(
                java("""
package com.android.adservices.service.common.fake.packagename;

import android.content.OtherContext;
import androidx.room.OtherRoom;

public final class FakeClass {
    public FakeClass(OtherContext context) {
        context.getDatabasePath("stringName");
        context.getSharedPreferences("stringName", intMode);
        OtherRoom.databaseBuilder(context, FakeClass.class, "stringName");
    }
}
                """), *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expectClean()
    }

    private val sharedPreferences: TestFile =
            java(
                    """
            package android.content;

            public class SharedPreferences {
            }
        """
            )
                    .indented()

    private val fileClass: TestFile =
            java(
                    """
            package java.io;
            public class File {
                    public File(String pathname) {}
                    public File(String parent, String child) {}
                    public boolean delete() { return true; }
                    public boolean exists() { return true; }
            }
        """
            )
                    .indented()

    private val context: TestFile =
            java(
                    """
            package android.content;

            import android.content.SharedPreferences;

            public abstract class Context {
                    public abstract void getDatabasePath(String name);
                    public abstract SharedPreferences getSharedPreferences(String name, int mode);
            }
        """
            )
                    .indented()

    private val room: TestFile =
            java(
                    """
            package androidx.room;

            import android.content.Context;

            public class Room {
                public static void databaseBuilder(Context context, Class<T> klass, String name) {
                }
            }
        """
            )
                    .indented()

    private val otherContext: TestFile =
            java(
                    """
            package android.content;

            import android.content.SharedPreferences;

            public abstract class OtherContext {
                    public abstract void getDatabasePath(String name);
                    public abstract SharedPreferences getSharedPreferences(String name, int mode);
            }
        """
            )
                    .indented()

    private val otherRoom: TestFile =
            java(
                    """
            package androidx.room;

            import android.content.Context;

            public class OtherRoom {
                public static void databaseBuilder(Context context, Class<T> klass, String name) {
                }
            }
        """
            )
                    .indented()

    private val stubs =
            arrayOf(
                    sharedPreferences,
                    fileClass,
                    context,
                    room,
                    otherContext,
                    otherRoom
            )
}
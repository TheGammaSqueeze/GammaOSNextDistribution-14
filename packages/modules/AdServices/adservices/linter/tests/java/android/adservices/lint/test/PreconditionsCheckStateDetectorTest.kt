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
import android.adservices.lint.PreconditionsCheckStateDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreconditionsCheckStateDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = PreconditionsCheckStateDetector()

    override fun getIssues(): List<Issue> = listOf(PreconditionsCheckStateDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    override fun allowCompilationErrors(): Boolean {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true
    }

    @Test
    fun applicableMethodCalls_throws() {
        lint().files(java("""
package com.android.adservices;

import com.android.internal.util.Preconditions;

public final class FakeClass {
    public FakeClass() {
        Preconditions.checkState(
                true,
                fakeStringFormat,
                fakeObject);
    }
}
                """).indented(),
                *stubs)
                .issues(PreconditionsCheckStateDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT USE com.android.internal.util.Preconditions.CheckState(boolean, String, Object...) because it is not available in R-. Use Preconditions.CheckState(boolean, String, Object...) from adservices-shared-util instead. [AvoidPreconditions.CheckState]
        Preconditions.checkState(
                      ~~~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_noPreconditionsCheckState_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.internal.util.Preconditions;

public final class FakeClass {
    public FakeClass() {
        Preconditions.checkArgument( // Use other method from Preconditions class will not trigger lint errors.
                true,
                fakeString,
                fakeObject);
    }
}
                """).indented(),
                *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_usingSharedUtilClass_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.shared.util.Preconditions;

public final class FakeClass {
    public FakeClass() {
        Preconditions.checkState( // Use checkState from Helper class will not trigger lint errors.
                true,
                fakeStringFormat,
                fakeObject);
    }
}
                """).indented(),
                *stubs)
                .issues(BackCompatNewFileDetector.ISSUE)
                .run()
                .expectClean()
    }

    private val preconditionsClassStub: TestFile =
            java("""
            package com.android.internal.util;

            public class Preconditions {
                public static void checkState(boolean b, String errorMessageTemplate, Object o1) {
                }
            }
        """).indented()

    private val helperClassWithCheckStateStub: TestFile =
            java("""
            package com.android.adservices.shared.util;

            public class Preconditions {
                public static void checkState(boolean b, String errorMessageTemplate, Object o1) {
                }
            }
        """).indented()

    private val stubs =
            arrayOf(
                    preconditionsClassStub,
                    helperClassWithCheckStateStub
            )
}
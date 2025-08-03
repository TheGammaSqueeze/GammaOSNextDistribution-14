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

package android.adservices.lint

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class BackCompatNewFileDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> {
        return listOf("getDatabasePath", "getSharedPreferences", "databaseBuilder")
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("java.io.File")
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
            context.report(
                    issue = ISSUE,
                    location = context.getNameLocation(node),
                    message =
                    "Please use FileCompatUtils to ensure any newly added files have a name " +
                            "that begins with \"adservices\" or create the files in a subdirectory " +
                            "called \"adservices/\" (go/rb-extservices-ota-data-cleanup)"
            )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (
                (method.name == "getDatabasePath" &&
                        method.containingClass?.qualifiedName == "android.content.Context") ||
                (method.name == "getSharedPreferences" &&
                        method.containingClass?.qualifiedName == "android.content.Context") ||
                (method.name == "databaseBuilder" &&
                        method.containingClass?.qualifiedName == "androidx.room.Room")
        ) {
            context.report(
                    issue = ISSUE,
                    location = context.getNameLocation(node),
                    message =
                    "Please use FileCompatUtils to ensure any newly added files have a name " +
                            "that begins with \"adservices\" or create the files in a subdirectory " +
                            "called \"adservices/\" (go/rb-extservices-ota-data-cleanup)"
            )
        }
    }

    companion object {
        val ISSUE =
                Issue.create(
                        id = "NewAdServicesFile",
                        briefDescription = "New Adservices file must have \"adservices\" in the name or path",
                        explanation =
                        """
                            Newly added AdServices files must be indicated as AdServices files so they can be removed from ExtServices on S- after OTA. Please ensure the name of the file
                            begins with \"adservices\" or the file is created inside an \"adservices/\" subdirectory so that it can later be deleted by the AdServicesFileCleanupReceiver
                    """,
                        moreInfo = "http://go/rb-extservices-ota-data-cleanup",
                        category = Category.COMPLIANCE,
                        severity = Severity.ERROR,
                        implementation =
                        Implementation(BackCompatNewFileDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
    }
}
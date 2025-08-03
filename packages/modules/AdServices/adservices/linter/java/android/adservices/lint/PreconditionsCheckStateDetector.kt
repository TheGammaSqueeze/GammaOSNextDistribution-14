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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PreconditionsCheckStateDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String>? {
        return listOf("checkState")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "checkState" && method.containingClass?.qualifiedName ==
                "com.android.internal.util.Preconditions" && method.parameterList.parametersCount >= 3) {
            context.report(issue = ISSUE, location = context.getNameLocation(node),
                    message = "DO NOT USE com.android.internal.util.Preconditions.CheckState(boolean, String, Object...)" +
                            " because it is not available in R-. Use Preconditions.CheckState(boolean, String, Object...) " +
                            "from adservices-shared-util instead.")
        }
    }

    companion object {
        val ISSUE = Issue.create(
                id = "AvoidPreconditions.CheckState",
                briefDescription = "DO NOT USE Preconditions.CheckState",
                explanation = """
                      DO NOT USE com.android.internal.util.Preconditions.CheckState(boolean, String, Object...)
                      because it is not available in R-. Use Preconditions.CheckState(boolean, String, Object...) from adservices-shared-util instead.
                    """,
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation = Implementation(PreconditionsCheckStateDetector::class.java, Scope.JAVA_FILE_SCOPE))
    }
}
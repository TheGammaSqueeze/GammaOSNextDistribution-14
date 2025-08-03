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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression

/** Lint check for detecting invalid AdServices JobService */
class BackCompatJobServiceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val JOB_SERVICE_CLASS = "android.app.job.JobService"
        private const val ISSUE_DESCRIPTION =
                "Avoid using new classes in AdServices JobService field Initializers. Due to the " +
                        "fact that ExtServices can OTA to any AdServices build, JobServices code " +
                        "needs to be properly gated to avoid NoClassDefFoundError. " +
                        "NoClassDefFoundError can happen when new class is used in ExtServices " +
                        "build, and the error happens when the device OTA to old AdServices " +
                        "build on T which does not contain the new class definition " +
                        "(go/rbc-jobservice-lint)."

        // The following list of classes can be safely used in adservices JobService field
        // initialiser
        private val SAFE_CLASSES = listOf(
                "com.android.adservices.concurrency.AdServicesExecutors",
                "com.android.adservices.spe.AdservicesJobInfo"
        )

        val ISSUE = Issue.create(
                id = "InvalidAdServicesJobService",
                briefDescription = "The Back Compat JobService parser detected an error",
                explanation = """Rubidium Back Compat design introduces additional constraints
                    | on how JobServices can be used in AdServices codebase. Check the error
                    | message to find out the specific constraint not met""".trimMargin(),
                category = Category.CORRECTNESS,
                severity = Severity.WARNING,
                implementation = Implementation(
                        BackCompatJobServiceDetector::class.java,
                        Scope.JAVA_FILE_SCOPE
                ),
                androidSpecific = true
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (isJobServiceSubclass(node)) {
                    checkJobServiceAttributes(node, context)
                }
            }
        }
    }

    private fun isJobServiceSubclass(node: UClass): Boolean {
        val superClass = node.javaPsi.superClass
        return superClass?.qualifiedName == JOB_SERVICE_CLASS
    }

    private fun checkJobServiceAttributes(node: UClass, context: JavaContext) {
        for (field in node.fields) {
            val initializer = field.uastInitializer
            if (initializer != null && !isInitializerValid(initializer)) {
                val location = context.getLocation(initializer)
                context.report(
                        ISSUE,
                        initializer,
                        location,
                        ISSUE_DESCRIPTION
                )
            }
        }
    }

    private fun isInitializerValid(initializer: UExpression): Boolean {
        return when (initializer) {
            // literal is always valid, ex, "abc", 123
            is ULiteralExpression -> true
            // Check whether unsafe classes are used in reference
            is UReferenceExpression -> {
                val resolved = initializer.resolve()
                if (resolved != null && resolved is PsiMethod) {
                    val typeName = resolved.getContainingClass()?.getQualifiedName()
                    SAFE_CLASSES.contains(typeName)
                } else {
                    true
                }
            }
            // Check whether unsafe classes are used in function call
            is UCallExpression -> {
                val resolved = initializer.resolve()
                if (resolved != null && resolved is PsiMethod) {
                    val typeName = resolved.getContainingClass()?.getQualifiedName()
                    SAFE_CLASSES.contains(typeName)
                } else {
                    true
                }
            }
            // Recursively check whether BinaryExpression is allowed
            is UBinaryExpression -> isInitializerValid(initializer.leftOperand) && isInitializerValid(initializer.rightOperand)
            // Recursively check whether PolyadicExpression is allowed
            is UPolyadicExpression -> initializer.operands.all { isInitializerValid(it) }
            // Other expressions are always allowed
            else -> true
        }
    }
}
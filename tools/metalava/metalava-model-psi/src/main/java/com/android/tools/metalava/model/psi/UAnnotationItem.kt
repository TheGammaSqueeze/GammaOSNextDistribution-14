/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.Item
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import org.jetbrains.kotlin.asJava.elements.KtLightNullabilityAnnotation
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isArrayInitializer

class UAnnotationItem
private constructor(
    override val codebase: PsiBasedCodebase,
    val uAnnotation: UAnnotation,
    originalName: String?
) :
    DefaultAnnotationItem(
        codebase,
        originalName,
        { getAnnotationAttributes(codebase, uAnnotation) }
    ) {

    override fun toSource(target: AnnotationTarget, showDefaultAttrs: Boolean): String {
        val sb = StringBuilder(60)
        appendAnnotation(codebase, sb, uAnnotation, qualifiedName, target, showDefaultAttrs)
        return sb.toString()
    }

    override fun resolve(): ClassItem? {
        return codebase.findOrCreateClass(originalName ?: return null)
    }

    override fun isNonNull(): Boolean {
        if (uAnnotation.javaPsi is KtLightNullabilityAnnotation<*> && originalName == "") {
            // Hack/workaround: some UAST annotation nodes do not provide qualified name :=(
            return true
        }
        return super.isNonNull()
    }

    override val targets: Set<AnnotationTarget> by lazy {
        codebase.annotationManager.computeTargets(this, codebase::findOrCreateClass)
    }

    companion object {
        private fun getAnnotationAttributes(
            codebase: PsiBasedCodebase,
            uAnnotation: UAnnotation
        ): List<AnnotationAttribute> =
            uAnnotation.attributeValues
                .map { attribute ->
                    DefaultAnnotationAttribute(
                        attribute.name ?: ANNOTATION_ATTR_VALUE,
                        createValue(codebase, attribute.expression)
                    )
                }
                .toList()

        fun create(
            codebase: PsiBasedCodebase,
            uAnnotation: UAnnotation,
            qualifiedName: String? = uAnnotation.qualifiedName
        ): AnnotationItem {
            return UAnnotationItem(codebase, uAnnotation, qualifiedName)
        }

        private fun getAttributes(
            annotation: UAnnotation,
            showDefaultAttrs: Boolean
        ): List<Pair<String?, UExpression?>> {
            val annotationClass = annotation.javaPsi?.nameReferenceElement?.resolve() as? PsiClass
            val list = mutableListOf<Pair<String?, UExpression?>>()
            if (annotationClass != null && showDefaultAttrs) {
                for (method in annotationClass.methods) {
                    if (method !is PsiAnnotationMethod) {
                        continue
                    }
                    list.add(Pair(method.name, annotation.findAttributeValue(method.name)))
                }
            } else {
                for (attr in annotation.attributeValues) {
                    list.add(Pair(attr.name, attr.expression))
                }
            }
            return list
        }

        private fun appendAnnotation(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            uAnnotation: UAnnotation,
            originalName: String?,
            target: AnnotationTarget,
            showDefaultAttrs: Boolean
        ) {
            val qualifiedName =
                codebase.annotationManager.normalizeOutputName(originalName, target) ?: return

            val attributes = getAttributes(uAnnotation, showDefaultAttrs)
            if (attributes.isEmpty()) {
                sb.append("@$qualifiedName")
                return
            }

            sb.append("@")
            sb.append(qualifiedName)
            sb.append("(")
            if (
                attributes.size == 1 &&
                    (attributes[0].first == null || attributes[0].first == ANNOTATION_ATTR_VALUE)
            ) {
                // Special case: omit "value" if it's the only attribute
                appendValue(codebase, sb, attributes[0].second, target, showDefaultAttrs)
            } else {
                var first = true
                for (attribute in attributes) {
                    if (first) {
                        first = false
                    } else {
                        sb.append(", ")
                    }
                    sb.append(attribute.first ?: ANNOTATION_ATTR_VALUE)
                    sb.append('=')
                    appendValue(codebase, sb, attribute.second, target, showDefaultAttrs)
                }
            }
            sb.append(")")
        }

        private fun appendValue(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: UExpression?,
            target: AnnotationTarget,
            showDefaultAttrs: Boolean
        ) {
            // Compute annotation string -- we don't just use value.text here
            // because that may not use fully qualified names, e.g. the source may say
            //  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            // and we want to compute
            //
            // @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            when (value) {
                null -> sb.append("null")
                is ULiteralExpression -> sb.append(CodePrinter.constantToSource(value.value))
                is UQualifiedReferenceExpression -> { // the value is a Foo.BAR type of reference.
                    // expand `Foo` to fully qualified name `com.example.Foo`
                    appendQualifiedName(codebase, sb, value.receiver as UReferenceExpression)
                    // append accessor `.`
                    sb.append(value.accessType.name)
                    // append `BAR`
                    sb.append(value.selector.asRenderString())
                }
                is UReferenceExpression -> {
                    // expand Foo to fully qualified name com.example.Foo
                    appendQualifiedName(codebase, sb, value)
                }
                is UBinaryExpression -> {
                    appendValue(codebase, sb, value.leftOperand, target, showDefaultAttrs)
                    sb.append(' ')
                    sb.append(value.operator.text)
                    sb.append(' ')
                    appendValue(codebase, sb, value.rightOperand, target, showDefaultAttrs)
                }
                is UCallExpression -> {
                    if (value.isArrayInitializer()) {
                        sb.append('{')
                        var first = true
                        for (initializer in value.valueArguments) {
                            if (first) {
                                first = false
                            } else {
                                sb.append(", ")
                            }
                            appendValue(codebase, sb, initializer, target, showDefaultAttrs)
                        }
                        sb.append('}')
                    } // TODO: support UCallExpression for other cases than array initializers
                }
                is UAnnotation -> {
                    appendAnnotation(
                        codebase,
                        sb,
                        value,
                        // Normalize the input name of the annotation.
                        codebase.annotationManager.normalizeInputName(value.qualifiedName),
                        target,
                        showDefaultAttrs
                    )
                }
                else -> {
                    val source = getConstantSource(value)
                    if (source != null) {
                        sb.append(source)
                        return
                    }
                    sb.append(value.sourcePsi?.text ?: value.asSourceString())
                }
            }
        }

        private fun appendQualifiedName(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: UReferenceExpression
        ) {
            when (val resolved = value.resolve()) {
                is PsiField -> {
                    val containing = resolved.containingClass
                    if (containing != null) {
                        // If it's a field reference, see if it looks like the field is hidden; if
                        // so, inline the value
                        val cls = codebase.findOrCreateClass(containing)
                        val initializer = resolved.initializer
                        if (initializer != null) {
                            val fieldItem = cls.findField(resolved.name)
                            if (fieldItem == null || fieldItem.isHiddenOrRemoved()) {
                                // Use the literal value instead
                                val source = getConstantSource(initializer)
                                if (source != null) {
                                    sb.append(source)
                                    return
                                }
                            }
                        }
                        containing.qualifiedName?.let { sb.append(it).append('.') }
                    }

                    sb.append(resolved.name)
                }
                is PsiClass -> resolved.qualifiedName?.let { sb.append(it) }
                else -> {
                    sb.append(value.sourcePsi?.text ?: value.asSourceString())
                }
            }
        }

        private fun getConstantSource(value: UExpression): String? {
            val constant = value.evaluate()
            return CodePrinter.constantToExpression(constant)
        }

        private fun getConstantSource(value: PsiExpression): String? {
            val constant = JavaConstantExpressionEvaluator.computeConstantExpression(value, false)
            return CodePrinter.constantToExpression(constant)
        }
    }
}

private fun createValue(codebase: PsiBasedCodebase, value: UExpression): AnnotationAttributeValue {
    return if (value.isArrayInitializer()) {
        val uCallExpression = value as UCallExpression
        DefaultAnnotationArrayAttributeValue(
            { getText(uCallExpression) },
            { uCallExpression.valueArguments.map { createValue(codebase, it) }.toList() }
        )
    } else {
        UAnnotationSingleAttributeValue(codebase, value)
    }
}

class UAnnotationSingleAttributeValue(
    private val codebase: PsiBasedCodebase,
    private val psiValue: UExpression
) : DefaultAnnotationSingleAttributeValue({ getText(psiValue) }, { getValue(psiValue) }) {

    companion object {
        private fun getValue(psiValue: UExpression): Any? {
            if (psiValue is ULiteralExpression) {
                val value = psiValue.value
                if (value != null) {
                    return value
                } else if (psiValue.isNull) {
                    return null
                }
            }
            if (psiValue is PsiLiteral) {
                return psiValue.value ?: getText(psiValue).removeSurrounding("\"")
            }

            val value = ConstantEvaluator.evaluate(null, psiValue)
            if (value != null) {
                return value
            }

            if (psiValue is UClassLiteralExpression) {
                // The value of a class literal expression like String.class or String::class
                // is the fully qualified name, java.lang.String
                val type = psiValue.type
                if (type != null) {
                    return type.canonicalText
                }
            }

            return getText(psiValue).removeSurrounding("\"")
        }
    }

    override fun resolve(): Item? {
        if (psiValue is UReferenceExpression) {
            when (val resolved = psiValue.resolve()) {
                is PsiField -> return codebase.findField(resolved)
                is PsiClass -> return codebase.findOrCreateClass(resolved)
                is PsiMethod -> return codebase.findMethod(resolved)
            }
        }
        return null
    }
}

private fun getText(element: UElement): String {
    return element.sourcePsi?.text ?: element.asSourceString()
}

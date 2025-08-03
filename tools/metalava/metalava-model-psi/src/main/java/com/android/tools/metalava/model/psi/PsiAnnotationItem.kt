/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.psi.CodePrinter.Companion.constantToExpression
import com.android.tools.metalava.model.psi.CodePrinter.Companion.constantToSource
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import org.jetbrains.kotlin.asJava.elements.KtLightNullabilityAnnotation

class PsiAnnotationItem
private constructor(
    override val codebase: PsiBasedCodebase,
    val psiAnnotation: PsiAnnotation,
    originalName: String?
) :
    DefaultAnnotationItem(
        codebase,
        originalName,
        { getAnnotationAttributes(codebase, psiAnnotation) }
    ) {

    override fun toSource(target: AnnotationTarget, showDefaultAttrs: Boolean): String {
        val sb = StringBuilder(60)
        appendAnnotation(codebase, sb, psiAnnotation, qualifiedName, target, showDefaultAttrs)
        return sb.toString()
    }

    override fun resolve(): ClassItem? {
        return codebase.findOrCreateClass(originalName ?: return null)
    }

    override fun isNonNull(): Boolean {
        if (psiAnnotation is KtLightNullabilityAnnotation<*> && originalName == "") {
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
            psiAnnotation: PsiAnnotation
        ): List<AnnotationAttribute> =
            psiAnnotation.parameterList.attributes
                .mapNotNull { attribute ->
                    attribute.value?.let { value ->
                        DefaultAnnotationAttribute(
                            attribute.name ?: ANNOTATION_ATTR_VALUE,
                            createValue(codebase, value),
                        )
                    }
                }
                .toList()

        fun create(
            codebase: PsiBasedCodebase,
            psiAnnotation: PsiAnnotation,
            qualifiedName: String? = psiAnnotation.qualifiedName
        ): AnnotationItem {
            return PsiAnnotationItem(codebase, psiAnnotation, qualifiedName)
        }

        fun create(
            codebase: Codebase,
            originalName: String,
            attributes: List<AnnotationAttribute> = emptyList(),
            context: Item? = null
        ): AnnotationItem {
            if (codebase is PsiBasedCodebase) {
                val source = formatAnnotationItem(originalName, attributes)
                return codebase.createAnnotation(source, context)
            } else {
                codebase.unsupported("Converting to PSI annotation requires PSI codebase")
            }
        }

        private fun getAttributes(
            annotation: PsiAnnotation,
            showDefaultAttrs: Boolean
        ): List<Pair<String?, PsiAnnotationMemberValue?>> {
            val annotationClass = annotation.nameReferenceElement?.resolve() as? PsiClass
            val list = mutableListOf<Pair<String?, PsiAnnotationMemberValue?>>()
            if (annotationClass != null && showDefaultAttrs) {
                for (method in annotationClass.methods) {
                    if (method !is PsiAnnotationMethod) {
                        continue
                    }
                    list.add(Pair(method.name, annotation.findAttributeValue(method.name)))
                }
            } else {
                for (attr in annotation.parameterList.attributes) {
                    list.add(Pair(attr.name, attr.value))
                }
            }
            return list
        }

        private fun appendAnnotation(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            psiAnnotation: PsiAnnotation,
            qualifiedName: String?,
            target: AnnotationTarget,
            showDefaultAttrs: Boolean
        ) {
            val alwaysInlineValues = qualifiedName == "android.annotation.FlaggedApi"
            val outputName =
                codebase.annotationManager.normalizeOutputName(qualifiedName, target) ?: return

            val attributes = getAttributes(psiAnnotation, showDefaultAttrs)
            if (attributes.isEmpty()) {
                sb.append("@$outputName")
                return
            }

            sb.append("@")
            sb.append(outputName)
            sb.append("(")
            if (
                attributes.size == 1 &&
                    (attributes[0].first == null || attributes[0].first == ANNOTATION_ATTR_VALUE)
            ) {
                // Special case: omit "value" if it's the only attribute
                appendValue(
                    codebase,
                    sb,
                    attributes[0].second,
                    target,
                    showDefaultAttrs = showDefaultAttrs,
                    alwaysInlineValues = alwaysInlineValues,
                )
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
                    appendValue(
                        codebase,
                        sb,
                        attribute.second,
                        target,
                        showDefaultAttrs = showDefaultAttrs,
                        alwaysInlineValues = alwaysInlineValues,
                    )
                }
            }
            sb.append(")")
        }

        private fun appendValue(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: PsiAnnotationMemberValue?,
            target: AnnotationTarget,
            showDefaultAttrs: Boolean,
            alwaysInlineValues: Boolean,
        ) {
            // Compute annotation string -- we don't just use value.text here
            // because that may not use fully qualified names, e.g. the source may say
            //  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            // and we want to compute
            //
            // @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            when (value) {
                null -> sb.append("null")
                is PsiLiteral -> sb.append(constantToSource(value.value))
                is PsiReference -> {
                    when (val resolved = value.resolve()) {
                        is PsiField -> {
                            val containing = resolved.containingClass
                            if (containing != null) {
                                // If it's a field reference, see if it looks like the field is
                                // hidden; if
                                // so, inline the value
                                val cls = codebase.findOrCreateClass(containing)
                                val initializer = resolved.initializer
                                if (initializer != null) {
                                    val fieldItem = cls.findField(resolved.name)
                                    if (
                                        alwaysInlineValues ||
                                            fieldItem == null ||
                                            fieldItem.isHiddenOrRemoved() ||
                                            !fieldItem.isPublic
                                    ) {
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
                            sb.append(value.text)
                        }
                    }
                }
                is PsiBinaryExpression -> {
                    appendValue(
                        codebase,
                        sb,
                        value.lOperand,
                        target,
                        showDefaultAttrs = showDefaultAttrs,
                        alwaysInlineValues = alwaysInlineValues,
                    )
                    sb.append(' ')
                    sb.append(value.operationSign.text)
                    sb.append(' ')
                    appendValue(
                        codebase,
                        sb,
                        value.rOperand,
                        target,
                        showDefaultAttrs = showDefaultAttrs,
                        alwaysInlineValues = alwaysInlineValues,
                    )
                }
                is PsiArrayInitializerMemberValue -> {
                    sb.append('{')
                    var first = true
                    for (initializer in value.initializers) {
                        if (first) {
                            first = false
                        } else {
                            sb.append(", ")
                        }
                        appendValue(
                            codebase,
                            sb,
                            initializer,
                            target,
                            showDefaultAttrs = showDefaultAttrs,
                            alwaysInlineValues = alwaysInlineValues,
                        )
                    }
                    sb.append('}')
                }
                is PsiAnnotation -> {
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
                    if (value is PsiExpression) {
                        val source = getConstantSource(value)
                        if (source != null) {
                            sb.append(source)
                            return
                        }
                    }
                    sb.append(value.text)
                }
            }
        }

        private fun getConstantSource(value: PsiExpression): String? {
            val constant = JavaConstantExpressionEvaluator.computeConstantExpression(value, false)
            return constantToExpression(constant)
        }
    }
}

private fun createValue(
    codebase: PsiBasedCodebase,
    value: PsiAnnotationMemberValue
): AnnotationAttributeValue {
    return if (value is PsiArrayInitializerMemberValue) {
        DefaultAnnotationArrayAttributeValue(
            { value.text },
            { value.initializers.map { createValue(codebase, it) }.toList() }
        )
    } else {
        PsiAnnotationSingleAttributeValue(codebase, value)
    }
}

class PsiAnnotationSingleAttributeValue(
    private val codebase: PsiBasedCodebase,
    private val psiValue: PsiAnnotationMemberValue
) : DefaultAnnotationSingleAttributeValue({ psiValue.text }, { getValue(psiValue) }) {

    companion object {
        private fun getValue(psiValue: PsiAnnotationMemberValue): Any {
            if (psiValue is PsiLiteral) {
                return psiValue.value ?: psiValue.text.removeSurrounding("\"")
            }

            val value = ConstantEvaluator.evaluate(null, psiValue)
            if (value != null) {
                return value
            }

            if (psiValue is PsiClassObjectAccessExpression) {
                // The value of a class literal expression like String.class or String::class
                // is the fully qualified name, java.lang.String
                return psiValue.operand.type.canonicalText
            }

            return psiValue.text ?: psiValue.text.removeSurrounding("\"")
        }
    }

    override fun resolve(): Item? {
        if (psiValue is PsiReference) {
            when (val resolved = psiValue.resolve()) {
                is PsiField -> return codebase.findField(resolved)
                is PsiClass -> return codebase.findOrCreateClass(resolved)
                is PsiMethod -> return codebase.findMethod(resolved)
            }
        }
        return null
    }
}

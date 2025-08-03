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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.isNonNullAnnotation
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.JavaConstantExpressionEvaluator

class PsiFieldItem(
    codebase: PsiBasedCodebase,
    private val psiField: PsiField,
    containingClass: PsiClassItem,
    name: String,
    modifiers: DefaultModifierList,
    documentation: String,
    private val fieldType: TypeItem,
    private val isEnumConstant: Boolean,
    private val fieldValue: PsiFieldValue?,
) :
    PsiMemberItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiField,
        containingClass = containingClass,
        name = name,
    ),
    FieldItem {

    override var emit: Boolean = !modifiers.isExpect()

    override var property: PsiPropertyItem? = null

    override fun type(): TypeItem = fieldType

    override fun initialValue(requireConstant: Boolean): Any? {
        return fieldValue?.initialValue(requireConstant)
    }

    override fun isEnumConstant(): Boolean = isEnumConstant

    override fun psi(): PsiField = psiField

    override fun duplicate(targetContainingClass: ClassItem): PsiFieldItem {
        val duplicated =
            create(
                codebase,
                targetContainingClass as PsiClassItem,
                psiField,
                codebase.globalTypeItemFactory.from(targetContainingClass),
            )
        duplicated.inheritedFrom = containingClass
        duplicated.finishInitialization()

        // Preserve flags that may have been inherited (propagated) from surrounding packages
        if (targetContainingClass.hidden) {
            duplicated.hidden = true
        }
        if (targetContainingClass.removed) {
            duplicated.removed = true
        }
        if (targetContainingClass.docOnly) {
            duplicated.docOnly = true
        }

        return duplicated
    }

    override var inheritedFrom: ClassItem? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is FieldItem &&
            name == other.name() &&
            containingClass == other.containingClass()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: PsiClassItem,
            psiField: PsiField,
            enclosingClassTypeItemFactory: PsiTypeItemFactory
        ): PsiFieldItem {
            val name = psiField.name
            val commentText = javadoc(psiField)
            val modifiers = modifiers(codebase, psiField, commentText)

            val isEnumConstant = psiField is PsiEnumConstant

            // Wrap the PsiField in a PsiFieldValue that can provide the field's initial value.
            val fieldValue = PsiFieldValue(psiField)

            // Create a type for the field, taking into account the modifiers, whether it is an
            // enum constant and whether the field's initial value is non-null.
            val fieldType =
                enclosingClassTypeItemFactory.getFieldType(
                    underlyingType = PsiTypeInfo(psiField.type, psiField),
                    itemAnnotations = modifiers.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = modifiers.isFinal(),
                    isInitialValueNonNull = {
                        // The initial value is non-null if the field initializer is a method that
                        // is annotated as being non-null so would produce a non-null value, or the
                        // value is a literal which is not null.
                        psiField.isFieldInitializerNonNull() ||
                            fieldValue.initialValue(false) != null
                    },
                )

            return PsiFieldItem(
                codebase = codebase,
                psiField = psiField,
                containingClass = containingClass,
                name = name,
                documentation = commentText,
                modifiers = modifiers,
                fieldType = fieldType,
                isEnumConstant = isEnumConstant,
                fieldValue = fieldValue
            )
        }
    }
}

/**
 * Check to see whether the [PsiField] on which this is called has an initializer whose
 * [TypeNullability] is known to be [TypeNullability.NONNULL].
 */
private fun PsiField.isFieldInitializerNonNull(): Boolean {
    // If we're looking at a final field, look on the right hand side of the field to the
    // field initialization. If that right hand side for example represents a method call,
    // and the method we're calling is annotated with @NonNull, then the field (since it is
    // final) will always be @NonNull as well.
    val resolved =
        when (val initializer = initializer) {
            is PsiReference -> {
                initializer.resolve()
            }
            is PsiCallExpression -> {
                initializer.resolveMethod()
            }
            else -> null
        }
            ?: return false

    return resolved is PsiModifierListOwner &&
        resolved.annotations.any { isNonNullAnnotation(it.qualifiedName ?: "") }
}

/**
 * Wrapper around a [PsiField] that will provide access to the initial value of the field, if
 * available, or `null` otherwise.
 */
class PsiFieldValue(private val psiField: PsiField) {

    fun initialValue(requireConstant: Boolean): Any? {
        val constant = psiField.computeConstantValue()
        // Offset [ClsFieldImpl#computeConstantValue] for [TYPE] field in boxed primitive types.
        // Those fields hold [Class] object, but the constant value should not be of [PsiType].
        if (
            constant is PsiPrimitiveType &&
                psiField.name == "TYPE" &&
                (psiField.type as? PsiClassType)?.computeQualifiedName() == "java.lang.Class"
        ) {
            return null
        }
        if (constant != null) {
            return constant
        }

        return if (!requireConstant) {
            val initializer = psiField.initializer ?: return null
            JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false)
        } else {
            null
        }
    }
}

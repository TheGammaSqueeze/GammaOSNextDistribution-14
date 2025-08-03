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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeSuperMethods
import com.android.tools.metalava.reporter.FileLocation
import java.util.function.Predicate

internal open class TextMethodItem(
    codebase: TextCodebase,
    name: String,
    containingClass: ClassItem,
    modifiers: DefaultModifierList,
    private val returnType: TypeItem,
    private val parameters: List<TextParameterItem>,
    fileLocation: FileLocation,
) :
    TextMemberItem(codebase, name, containingClass, fileLocation, modifiers = modifiers),
    MethodItem {
    init {
        parameters.forEach { it.containingMethod = this }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodItem) return false

        if (name() != other.name()) {
            return false
        }

        if (containingClass() != other.containingClass()) {
            return false
        }

        val parameters1 = parameters()
        val parameters2 = other.parameters()

        if (parameters1.size != parameters2.size) {
            return false
        }

        for (i in parameters1.indices) {
            val parameter1 = parameters1[i]
            val parameter2 = parameters2[i]
            if (parameter1.type() != parameter2.type()) {
                return false
            }
        }

        val typeParameters1 = typeParameterList
        val typeParameters2 = other.typeParameterList

        if (typeParameters1.size != typeParameters2.size) {
            return false
        }

        for (i in typeParameters1.indices) {
            val typeParameter1 = typeParameters1[i]
            val typeParameter2 = typeParameters2[i]
            if (typeParameter1.typeBounds() != typeParameter2.typeBounds()) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        return name().hashCode()
    }

    override fun isConstructor(): Boolean = false

    override fun returnType(): TypeItem = returnType

    override fun superMethods(): List<MethodItem> {
        return computeSuperMethods()
    }

    override fun findMainDocumentation(): String = documentation

    override fun findPredicateSuperMethod(predicate: Predicate<Item>): MethodItem? = null

    override var typeParameterList: TypeParameterList = TypeParameterList.NONE
        internal set

    override fun duplicate(targetContainingClass: ClassItem): MethodItem {
        val typeVariableMap = targetContainingClass.mapTypeVariables(containingClass())
        val duplicated =
            TextMethodItem(
                codebase,
                name(),
                targetContainingClass,
                modifiers.duplicate(),
                returnType.convertType(typeVariableMap),
                parameters.map { it.duplicate(typeVariableMap) },
                fileLocation
            )
        duplicated.inheritedFrom = containingClass()

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

        duplicated.deprecated = deprecated
        duplicated.annotationDefault = annotationDefault
        duplicated.throwsTypes = this.throwsTypes
        duplicated.typeParameterList = typeParameterList

        return duplicated
    }

    private var throwsTypes: List<ExceptionTypeItem> = emptyList()

    override fun throwsTypes(): List<ExceptionTypeItem> = this.throwsTypes

    fun setThrowsTypes(throwsClasses: List<ExceptionTypeItem>) {
        this.throwsTypes = throwsClasses
    }

    override fun parameters(): List<ParameterItem> = parameters

    override fun isExtensionMethod(): Boolean = codebase.unsupported()

    override var inheritedFrom: ClassItem? = null

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    private var annotationDefault = ""

    fun setAnnotationDefault(default: String) {
        annotationDefault = default
    }

    override fun defaultValue(): String {
        return annotationDefault
    }
}

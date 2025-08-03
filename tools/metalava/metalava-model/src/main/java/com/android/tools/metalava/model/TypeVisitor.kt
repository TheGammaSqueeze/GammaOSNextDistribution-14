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

package com.android.tools.metalava.model

interface TypeVisitor {
    fun visit(primitiveType: PrimitiveTypeItem) = Unit

    fun visit(arrayType: ArrayTypeItem) = Unit

    fun visit(classType: ClassTypeItem) = Unit

    fun visit(variableType: VariableTypeItem) = Unit

    fun visit(wildcardType: WildcardTypeItem) = Unit
}

open class BaseTypeVisitor : TypeVisitor {
    override fun visit(primitiveType: PrimitiveTypeItem) {
        visitType(primitiveType)
        visitPrimitiveType(primitiveType)
    }

    override fun visit(arrayType: ArrayTypeItem) {
        visitType(arrayType)
        visitArrayType(arrayType)

        arrayType.componentType.accept(this)
    }

    override fun visit(classType: ClassTypeItem) {
        visitType(classType)
        visitClassType(classType)

        classType.outerClassType?.accept(this)
        classType.arguments.forEach { it.accept(this) }
    }

    override fun visit(variableType: VariableTypeItem) {
        visitType(variableType)
        visitVariableType(variableType)
    }

    override fun visit(wildcardType: WildcardTypeItem) {
        visitType(wildcardType)
        visitWildcardType(wildcardType)

        wildcardType.extendsBound?.accept(this)
        wildcardType.superBound?.accept(this)
    }

    open fun visitType(type: TypeItem) = Unit

    open fun visitPrimitiveType(primitiveType: PrimitiveTypeItem) = Unit

    open fun visitArrayType(arrayType: ArrayTypeItem) = Unit

    open fun visitClassType(classType: ClassTypeItem) = Unit

    open fun visitVariableType(variableType: VariableTypeItem) = Unit

    open fun visitWildcardType(wildcardType: WildcardTypeItem) = Unit
}

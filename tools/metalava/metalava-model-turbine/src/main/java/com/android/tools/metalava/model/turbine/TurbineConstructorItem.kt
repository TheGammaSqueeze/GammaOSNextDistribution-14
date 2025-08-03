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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.binder.sym.MethodSymbol

internal class TurbineConstructorItem(
    codebase: TurbineBasedCodebase,
    fileLocation: FileLocation,
    private val name: String,
    methodSymbol: MethodSymbol,
    containingClass: TurbineClassItem,
    returnType: ClassTypeItem,
    modifiers: DefaultModifierList,
    typeParameters: TypeParameterList,
    documentation: String,
    private val defaultValue: String,
) :
    TurbineMethodItem(
        codebase,
        fileLocation,
        methodSymbol,
        containingClass,
        returnType,
        modifiers,
        typeParameters,
        documentation,
        defaultValue,
    ),
    ConstructorItem {

    override fun name(): String = name

    override var superConstructor: ConstructorItem? = null

    override fun isConstructor(): Boolean = true

    internal fun setReturnType(type: TypeItem) {
        returnType = type
    }

    companion object {
        fun createDefaultConstructor(
            codebase: TurbineBasedCodebase,
            containingClass: TurbineClassItem,
            symbol: MethodSymbol
        ): TurbineConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())
            val typeParameterList = DefaultTypeParameterList(emptyList())

            val ctorItem =
                TurbineConstructorItem(
                    codebase,
                    // Use the location of the containing class for the implicit default
                    // constructor.
                    containingClass.fileLocation,
                    name,
                    symbol,
                    containingClass,
                    containingClass.type(),
                    modifiers,
                    typeParameterList,
                    "",
                    "",
                )
            ctorItem.parameters = emptyList()
            ctorItem.throwableTypes = emptyList()
            return ctorItem
        }
    }
}

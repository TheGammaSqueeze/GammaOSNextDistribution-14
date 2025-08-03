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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeSuperMethods
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.binder.sym.MethodSymbol

internal open class TurbineMethodItem(
    codebase: TurbineBasedCodebase,
    fileLocation: FileLocation,
    private val methodSymbol: MethodSymbol,
    private val containingClass: ClassItem,
    protected var returnType: TypeItem,
    modifiers: DefaultModifierList,
    override val typeParameterList: TypeParameterList,
    documentation: String,
    private val defaultValue: String,
) : TurbineItem(codebase, fileLocation, modifiers, documentation), MethodItem {

    private lateinit var superMethodList: List<MethodItem>
    internal lateinit var throwableTypes: List<ExceptionTypeItem>
    internal lateinit var parameters: List<ParameterItem>

    override var inheritedFrom: ClassItem? = null

    override fun name(): String = methodSymbol.name()

    override fun parameters(): List<ParameterItem> = parameters

    override fun returnType(): TypeItem = returnType

    override fun throwsTypes(): List<ExceptionTypeItem> = throwableTypes

    override fun isExtensionMethod(): Boolean = false // java does not support extension methods

    override fun isConstructor(): Boolean = false

    override fun containingClass(): ClassItem = containingClass

    /**
     * Super methods for a given method M with containing class C are calculated as follows:
     * 1) Superclass Search: Traverse the class hierarchy, starting from C's direct superclass, and
     *    add the first method that matches M's signature to the list.
     * 2) Interface Supermethod Search: For each direct interface implemented by C, check if it
     *    contains a method matching M's signature. If found, return that method. If not,
     *    recursively apply this method to the direct interfaces of the current interface.
     *
     * Note: This method's implementation is based on MethodItem.matches method which only checks
     * that name and parameter list types match. Parameter names, Return types and Throws list types
     * are not matched
     */
    override fun superMethods(): List<MethodItem> {
        if (!::superMethodList.isInitialized) {
            superMethodList = computeSuperMethods()
        }
        return superMethodList
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TurbineMethodItem

        if (methodSymbol != other.methodSymbol) return false

        return true
    }

    override fun hashCode(): Int {
        return methodSymbol.hashCode()
    }

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    override fun duplicate(targetContainingClass: ClassItem): TurbineMethodItem {
        // Duplicate the parameters
        val params = parameters.map { TurbineParameterItem.duplicate(codebase, it, emptyMap()) }
        val retType = returnType.duplicate()
        val mods = modifiers.duplicate()
        val duplicateMethod =
            TurbineMethodItem(
                codebase,
                fileLocation,
                methodSymbol,
                targetContainingClass,
                retType,
                mods,
                typeParameterList,
                documentation,
                defaultValue,
            )
        duplicateMethod.parameters = params
        duplicateMethod.inheritedFrom = containingClass
        duplicateMethod.throwableTypes = throwableTypes

        // Preserve flags that may have been inherited (propagated) from surrounding packages
        if (targetContainingClass.hidden) {
            duplicateMethod.hidden = true
        }
        if (targetContainingClass.removed) {
            duplicateMethod.removed = true
        }
        if (targetContainingClass.docOnly) {
            duplicateMethod.docOnly = true
        }
        if (targetContainingClass.deprecated) {
            duplicateMethod.deprecated = true
        }

        return duplicateMethod
    }

    override fun findMainDocumentation(): String = TODO("b/295800205")

    internal fun setThrowsTypes(throwsList: List<ExceptionTypeItem>) {
        throwableTypes = throwsList
    }

    internal fun getSymbol(): MethodSymbol = methodSymbol

    override fun defaultValue(): String = defaultValue
}

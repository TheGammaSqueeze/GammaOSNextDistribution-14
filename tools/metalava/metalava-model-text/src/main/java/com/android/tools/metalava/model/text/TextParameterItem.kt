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

import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.reporter.FileLocation

const val UNKNOWN_DEFAULT_VALUE = "__unknown_default_value__"

internal class TextParameterItem(
    codebase: TextCodebase,
    private var name: String,
    private var publicName: String?,
    private val hasDefaultValue: Boolean,
    private var defaultValueBody: String? = UNKNOWN_DEFAULT_VALUE,
    override val parameterIndex: Int,
    private var type: TypeItem,
    modifiers: DefaultModifierList,
    fileLocation: FileLocation
) :
    // TODO: We need to pass in parameter modifiers here (synchronized etc)
    TextItem(codebase, fileLocation, modifiers = modifiers),
    ParameterItem {

    internal lateinit var containingMethod: TextMethodItem

    override fun isVarArgs(): Boolean {
        return modifiers.isVarArg()
    }

    override fun type(): TypeItem = type

    override fun name(): String = name

    override fun publicName(): String? = publicName

    override fun hasDefaultValue(): Boolean = hasDefaultValue

    override fun isDefaultValueKnown(): Boolean = defaultValueBody != UNKNOWN_DEFAULT_VALUE

    override fun defaultValue(): String? = defaultValueBody

    override fun containingMethod(): MethodItem = containingMethod

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParameterItem) return false

        return parameterIndex == other.parameterIndex
    }

    override fun hashCode(): Int = parameterIndex

    internal fun duplicate(typeVariableMap: TypeParameterBindings): TextParameterItem {
        return TextParameterItem(
            codebase,
            name,
            publicName,
            hasDefaultValue,
            defaultValueBody,
            parameterIndex,
            type.convertType(typeVariableMap),
            modifiers.duplicate(),
            fileLocation
        )
    }
}

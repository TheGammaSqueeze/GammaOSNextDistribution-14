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

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.reporter.FileLocation

internal class TurbineTypeParameterItem(
    codebase: TurbineBasedCodebase,
    modifiers: DefaultModifierList,
    private val name: String,
) :
    TurbineItem(
        codebase,
        FileLocation.UNKNOWN,
        modifiers,
        "",
    ),
    TypeParameterItem {

    lateinit var bounds: List<BoundsTypeItem>

    override fun name() = name

    // Java does not supports reified generics
    override fun isReified(): Boolean = false

    override fun typeBounds(): List<BoundsTypeItem> = bounds

    override fun type(): VariableTypeItem {
        return DefaultVariableTypeItem(DefaultTypeModifiers.create(emptyList()), this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.name()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers

class DefaultResolvedClassTypeItem(
    modifiers: TypeModifiers,
    private val classItem: ClassItem,
    override val arguments: List<TypeArgumentTypeItem>,
) : ClassTypeItem, DefaultTypeItem(modifiers) {

    override val qualifiedName = classItem.qualifiedName()

    override val outerClassType = classItem.containingClass()?.type()

    override val className = classItem.simpleName()

    override fun asClass() = classItem

    override fun duplicate(
        outerClass: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return DefaultResolvedClassTypeItem(modifiers.duplicate(), classItem, arguments)
    }

    companion object {
        fun createForClass(classItem: ClassItem): ClassTypeItem {
            val arguments = classItem.typeParameterList.map { it.type() }
            val modifiers = DefaultTypeModifiers.emptyNonNullModifiers
            return DefaultResolvedClassTypeItem(modifiers, classItem, arguments)
        }
    }
}

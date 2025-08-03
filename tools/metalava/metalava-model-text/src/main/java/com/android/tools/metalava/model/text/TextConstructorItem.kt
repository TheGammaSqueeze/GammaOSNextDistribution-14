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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.reporter.FileLocation

internal class TextConstructorItem(
    codebase: TextCodebase,
    name: String,
    containingClass: TextClassItem,
    modifiers: DefaultModifierList,
    returnType: ClassTypeItem,
    parameters: List<TextParameterItem>,
    fileLocation: FileLocation
) :
    TextMethodItem(
        codebase,
        name,
        containingClass,
        modifiers,
        returnType,
        parameters,
        fileLocation
    ),
    ConstructorItem {

    override var superConstructor: ConstructorItem? = null

    override fun isConstructor(): Boolean = true

    companion object {
        fun createDefaultConstructor(
            codebase: TextCodebase,
            containingClass: TextClassItem,
            fileLocation: FileLocation,
        ): TextConstructorItem {
            val name = containingClass.simpleName
            // The default constructor is package private because while in Java a class without
            // a constructor has a default public constructor in a signature file a class
            // without a constructor has no public constructors.
            val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)

            val item =
                TextConstructorItem(
                    codebase = codebase,
                    name = name,
                    containingClass = containingClass,
                    modifiers = modifiers,
                    returnType = containingClass.type(),
                    parameters = emptyList(),
                    fileLocation = fileLocation,
                )
            return item
        }
    }
}

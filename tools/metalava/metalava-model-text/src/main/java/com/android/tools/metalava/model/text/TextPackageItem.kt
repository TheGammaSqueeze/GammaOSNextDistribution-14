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
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.FileLocation

internal class TextPackageItem(
    codebase: TextCodebase,
    private val name: String,
    modifiers: DefaultModifierList,
    fileLocation: FileLocation
) : TextItem(codebase, fileLocation, modifiers = modifiers), PackageItem {

    private val classes = ArrayList<ClassItem>(100)

    private val classesNames = HashSet<String>(100)

    fun name() = name

    fun addClass(classInfo: ClassItem) {
        val classFullName = classInfo.fullName()
        if (classFullName in classesNames) {
            return
        }
        classes.add(classInfo)
        classesNames.add(classFullName)
    }

    override fun topLevelClasses(): Sequence<ClassItem> = classes.asSequence()

    override fun qualifiedName(): String = name

    override fun containingClass(): ClassItem? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackageItem) return false

        return name == other.qualifiedName()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
